import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Fetches product images from the web using each inventory {@code Item Name} as the search query,
 * then saves JPEGs to {@code item_images/<Item Code>.jpeg} for the workspace photo rail and View Items cards.
 * <p>
 * Run manually: {@code java -cp out:lib/sqlite-jdbc-3.46.1.3.jar ItemPhotoFetcher}
 * Pass {@code --force} to replace photos that already exist.
 */
public final class ItemPhotoFetcher {
    private static final Path ITEM_IMAGES_DIR = Paths.get("item_images");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Pattern VQD_PATTERN = Pattern.compile("vqd=['\"]?([\\d-]+)");
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("\"image\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Per-item web search state so “next photo” walks results without re-querying from the start. */
    private static final Map<String, PhotoSearchSession> PHOTO_CYCLES = new HashMap<>();

    private static final class PhotoSearchSession {
        private String query;
        private String vqd;
        private final List<String> imageUrls = new ArrayList<>();
        /** Index of the next DuckDuckGo result to try. */
        private int nextIndex;
        /** Next DuckDuckGo results page to request when {@link #imageUrls} is exhausted. */
        private int nextPage = 1;

        private void resetForQuery(String newQuery, boolean fileAlreadyOnDisk) {
            query = newQuery;
            vqd = null;
            imageUrls.clear();
            nextIndex = fileAlreadyOnDisk ? 1 : 0;
            nextPage = 1;
        }
    }

    private ItemPhotoFetcher() {
    }

    /** Outcome of a bulk photo download run. */
    public record FetchResult(int saved, int skipped, int failed) {
    }

    /**
     * Downloads JPEGs for inventory rows into {@code item_images/<Item Code>.jpeg}.
     *
     * @param replaceExisting when {@code false}, rows that already have a non-empty JPEG file are skipped
     * @return counts of saved, skipped, and failed items
     */
    public static FetchResult fetchAll(boolean replaceExisting) throws SQLException, InterruptedException, IOException {
        Files.createDirectories(ITEM_IMAGES_DIR);
        List<InventoryRow> rows = loadInventoryRows();
        if (rows.isEmpty()) {
            return new FetchResult(0, 0, 0);
        }

        int saved = 0;
        int skipped = 0;
        int failed = 0;

        for (InventoryRow row : rows) {
            Path dest = itemImagePath(row.itemCode());
            if (!replaceExisting && Files.isRegularFile(dest) && Files.size(dest) > 0) {
                skipped++;
                continue;
            }

            try {
                if (downloadAndSaveJpeg(row, dest)) {
                    noteBulkFetchUsedFirstResult(row.itemCode());
                    saved++;
                } else {
                    failed++;
                }
            } catch (Exception ex) {
                failed++;
            }
            Thread.sleep(900);
        }

        return new FetchResult(saved, skipped, failed);
    }

    /**
     * Downloads the next web image for one SKU (walks DuckDuckGo results; repeated calls advance).
     *
     * @param itemCode inventory item code
     * @param itemName inventory description used as the search query
     * @return {@code true} when a new JPEG was saved
     */
    public static boolean saveNextWebPhoto(String itemCode, String itemName)
            throws IOException, InterruptedException {
        String query = buildSearchQuery(itemName);
        if (query.isEmpty() || itemCode == null || itemCode.isBlank()) {
            return false;
        }
        String code = itemCode.trim();
        Path dest = itemImagePath(code);
        PhotoSearchSession session = PHOTO_CYCLES.computeIfAbsent(code, k -> new PhotoSearchSession());
        if (!query.equals(session.query)) {
            session.resetForQuery(query, Files.isRegularFile(dest) && Files.size(dest) > 0);
        }

        while (true) {
            while (session.nextIndex >= session.imageUrls.size()) {
                if (session.vqd == null) {
                    session.vqd = fetchDuckDuckGoVqd(query);
                    if (session.vqd == null) {
                        return false;
                    }
                }
                List<String> batch = duckDuckGoImageUrls(query, session.vqd, session.nextPage, 25);
                session.nextPage++;
                if (batch.isEmpty()) {
                    return false;
                }
                session.imageUrls.addAll(batch);
            }

            String imageUrl = session.imageUrls.get(session.nextIndex);
            session.nextIndex++;
            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }
            try {
                BufferedImage image = downloadImage(imageUrl);
                writeJpeg(image, dest);
                return true;
            } catch (IOException ignored) {
                // skip broken or unsupported URLs and try the following result
            }
        }
    }

    /** Clears cached search results for an item (e.g. after a manual file pick). */
    public static void clearPhotoCycle(String itemCode) {
        if (itemCode != null) {
            PHOTO_CYCLES.remove(itemCode.trim());
        }
    }

    /** After bulk fetch saves the first hit, the next “Next web photo” should skip that result. */
    private static void noteBulkFetchUsedFirstResult(String itemCode) {
        PhotoSearchSession session = PHOTO_CYCLES.computeIfAbsent(itemCode.trim(), k -> new PhotoSearchSession());
        if (session.nextIndex < 1) {
            session.nextIndex = 1;
        }
    }

    public static void main(String[] args) throws Exception {
        boolean force = false;
        for (String arg : args) {
            if ("--force".equalsIgnoreCase(arg)) {
                force = true;
            }
        }

        FetchResult result = fetchAll(force);
        if (result.saved() + result.skipped() + result.failed() == 0) {
            System.out.println("No inventory rows found.");
            return;
        }
        System.out.printf("Done. saved=%d skipped=%d failed=%d%n", result.saved(), result.skipped(), result.failed());
    }

    private static List<InventoryRow> loadInventoryRows() throws SQLException {
        List<InventoryRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT \"Item Code\", \"Item Name\" FROM inventory ORDER BY \"Item Code\" ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("Item Code");
                String name = rs.getString("Item Name");
                if (code != null && !code.isBlank() && name != null && !name.trim().isEmpty()) {
                    rows.add(new InventoryRow(code.trim(), name.trim()));
                }
            }
        }
        return rows;
    }

    private static boolean downloadAndSaveJpeg(InventoryRow row, Path dest) throws IOException, InterruptedException {
        String query = buildSearchQuery(row.itemName());
        if (query.isEmpty()) {
            return false;
        }
        for (String imageUrl : duckDuckGoImageUrls(query, fetchDuckDuckGoVqd(query), 1, 5)) {
            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }
            try {
                BufferedImage image = downloadImage(imageUrl);
                if (image == null) {
                    continue;
                }
                writeJpeg(image, dest);
                return true;
            } catch (IOException ignored) {
                // try next candidate
            }
        }
        return false;
    }

    /** Uses the inventory {@code Item Name} verbatim as the image search query. */
    static String buildSearchQuery(String itemName) {
        return itemName == null ? "" : itemName.trim();
    }

    private static List<String> duckDuckGoImageUrls(String query, String vqd, int page, int max)
            throws IOException, InterruptedException {
        if (vqd == null) {
            return List.of();
        }
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String apiUrl = "https://duckduckgo.com/i.js?l=us-en&o=json&q=" + encodedQuery
                + "&vqd=" + vqd + "&f=,,,,,&p=" + page;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://duckduckgo.com/")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return List.of();
        }

        List<String> urls = new ArrayList<>();
        Matcher matcher = IMAGE_URL_PATTERN.matcher(response.body());
        while (matcher.find() && urls.size() < max) {
            urls.add(unescapeJson(matcher.group(1)));
        }
        return urls;
    }

    private static String fetchDuckDuckGoVqd(String query) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://duckduckgo.com/?q=" + encodedQuery + "&iax=images&ia=images"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        Matcher matcher = VQD_PATTERN.matcher(response.body());
        return matcher.find() ? matcher.group(1) : null;
    }

    private static BufferedImage downloadImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/*,*/*")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            BufferedImage raw = ImageIO.read(in);
            if (raw == null) {
                throw new IOException("Unsupported or empty image");
            }
            return toRgb(raw);
        }
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        if (!ImageIO.write(image, "jpeg", dest.toFile())) {
            throw new IOException("JPEG encoder unavailable");
        }
    }

    private static Path itemImagePath(String itemCode) {
        return ITEM_IMAGES_DIR.resolve(itemCode + ".jpeg");
    }

    private static String unescapeJson(String value) {
        return value.replace("\\/", "/").replace("\\u0026", "&");
    }

    private record InventoryRow(String itemCode, String itemName) {
    }
}
