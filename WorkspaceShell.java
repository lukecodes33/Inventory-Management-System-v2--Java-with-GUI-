import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.Timer;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.Toolkit;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JDialog;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;

/**
 * Main Swing workspace after sign-in: session bar, sidebar navigation, card-based views for inventory and sales,
 * admin-only metrics rail and item photo rail, embedded flows (purchase orders, returns, reports), and helpers
 * for item images and CSV export.
 * <p>
 * Each item may have one on-disk JPEG at {@code item_images/&lt;Item Code&gt;.jpeg}. Every place that shows that
 * image uses the same maximum width and height (420×420 pixels): admin item rail and read-only item detail.
 * Images are never upscaled beyond their file resolution (see {@link #loadScaledItemPhotoIcon(Path, int, int)}).
 * </p>
 * <p>{@link AccountActions} provides backup flows and modal password reset from the sidebar.
 * </p>
 * <p>Larger flows are grouped in {@code build*} panel factories (inventory, PO tracking, receipts, sales, reporting,
 * user admin) plus {@link #open} wiring the shell frame split layout.</p>
 * <p>Hundreds of small {@code private static} Swing helpers compose those screens; undocumented helpers use
 * descriptive names aligned with sidebar labels {@code (“buildXxxPanel”)}, reserving block comments where behavior is subtle.</p>
 */
public final class WorkspaceShell {
    private static final int INPUT_HEIGHT = 34;
    /** Add Item row field height (~20% above prior compact height); horizontal size uses preferred width. */
    private static final int ADD_ITEM_INPUT_HEIGHT = 37;
    /** Preferred side length for staged JPEG preview on Add Item (square). */
    private static final int ADD_ITEM_PHOTO_PREVIEW_MAX = 300;
    private static final int TABLE_ROW_HEIGHT = 28;
    /** Max characters stored on {@code movements.Reason} for purchase-order cancellations. */
    private static final int PO_CANCEL_REASON_MAX_CHARS = 500;
    private static final int ITEM_DESC_COLUMN_MIN_WIDTH = 80;
    private static final int ITEM_DESC_COLUMN_MAX_WIDTH = 560;
    /** Optional centered image on the initial workspace card (PNG/JPG/GIF); omitted when missing. */
    private static final Path WORKSPACE_WELCOME_IMAGE = Paths.get("workspace_welcome.png");
    /** On-disk JPEG per item: {@code item_images/<Item Code>.jpeg}. */
    private static final Path ITEM_IMAGES_DIR = Paths.get("item_images");
    /** Max width when downscaling item JPEGs for display (shared by rail, detail, and upload preview). */
    private static final int ITEM_PHOTO_DISPLAY_MAX_W = 420;
    /** Max height when downscaling item JPEGs for display (shared by rail, detail, and upload preview). */
    private static final int ITEM_PHOTO_DISPLAY_MAX_H = 420;
    /** Default frame height scale; width uses the same base percent then {@link #MAIN_FRAME_WIDTH_EXTRA_FACTOR}. */
    private static final int MAIN_FRAME_BASE_W = 1180;
    private static final int MAIN_FRAME_BASE_H = 760;
    private static final int MAIN_FRAME_HEIGHT_SCALE_PERCENT = 125;
    /** Multiplies default width after height-scale (e.g. 1.25 → 25% wider than the height-scaled width). */
    private static final double MAIN_FRAME_WIDTH_EXTRA_FACTOR = 1.25;
    private static final int SIDEBAR_TARGET_WIDTH = 280;
    private static final int WORKSPACE_MIN_WIDTH = 360;
    /** Metrics / photo rail width (admin layout); fits {@link #ITEM_PHOTO_DISPLAY_MAX_W} plus padding. */
    private static final int ADMIN_METRICS_RAIL_OUTER_PX = 448;
    private static final int MAIN_AREA_MIN_FOR_METRICS = 420;
    /** Max length for {@code Inventory.Notes} (photo rail and Add Item). */
    private static final int ITEM_NOTES_MAX_CHARS = 4000;
    /** Thumbnail box in View Items card grid. */
    private static final int VIEW_ITEM_CARD_PHOTO_PX = 148;
    private static final int VIEW_ITEM_CARD_COLUMNS = 4;
    private static final int VIEW_ITEMS_SEARCH_DEBOUNCE_MS = 200;

    /** Cached View Items card thumbnails; {@link #VIEW_ITEM_THUMB_CACHE_MISS} marks a known-missing file. */
    private static final ConcurrentHashMap<String, Object> viewItemThumbCache = new ConcurrentHashMap<>();
    private static final Object VIEW_ITEM_THUMB_CACHE_MISS = new Object();

    /** {@link JPanel#getClientProperty(Object)} key for sidebar nav selection sync. */
    private static final String CLIENT_NAV_SIDEBAR_SELECTOR = "ims.NavSidebarSelector";
    /** View Items: pre-fill search field text. */
    private static final String CLIENT_VIEW_ITEMS_SEARCH_TEXT = "ims.viewItemsSearchText";
    private static final String CLIENT_RECENT_REFRESHER = "ims.recentRefresher";
    private static final int DEFAULT_BACKUP_REMINDER_DAYS = 7;
    private static final int DEFAULT_STALE_MARKET_PRICE_DAYS = 90;
    private static final double VIEW_ITEMS_HIGH_MARGIN_THRESHOLD_PCT = 20.0;
    private static final String VIEW_ITEMS_FILTER_ALL = "All items";
    private static final String VIEW_ITEMS_FILTER_FAVOURITES = "Favourites only";
    private static final String VIEW_ITEMS_FILTER_LOW_STOCK = "Low stock";
    private static final String VIEW_ITEMS_FILTER_STALE_PRICE = "Stale market price";
    private static final String VIEW_ITEMS_FILTER_MISSING_PHOTO = "Missing photo";
    private static final String VIEW_ITEMS_FILTER_HIGH_MARGIN = "High margin";
    private static final String[] VIEW_ITEMS_FILTER_OPTIONS = {
            VIEW_ITEMS_FILTER_ALL,
            VIEW_ITEMS_FILTER_FAVOURITES,
            VIEW_ITEMS_FILTER_LOW_STOCK,
            VIEW_ITEMS_FILTER_STALE_PRICE,
            VIEW_ITEMS_FILTER_MISSING_PHOTO,
            VIEW_ITEMS_FILTER_HIGH_MARGIN
    };

    /** CardLayout orphans prior components if the same sidebar key builds a new panel; we remove the old instance before re-adding. */
    private static final String CLIENT_WORKSPACE_CARD_REGISTRY = "ims.workspaceCardRegistry";

    /** Workspace card key and sidebar label for creating POs and viewing pending lines (reference / tracking). */
    private static final String VIEW_PO_TRACKING = "PO/Tracking Number";
    /** Admin workspace card: user management, password reset, and backups in tabs. */
    private static final String VIEW_ADMIN_TOOLS = "Administration Tools";

    /** Sentinel {@code StorageLocationPick.id} meaning "every location" in Stock by Location. */
    private static final int STOCK_REPORT_ALL_LOCATIONS_ID = -1;

    /** Sidebar nav: default (card surface). */
    private static final Color SIDEBAR_NAV_DEFAULT_BG = AppUI.SURFACE;
    private static final Color SIDEBAR_NAV_DEFAULT_FG = AppUI.TEXT;
    /** Sidebar nav: selected tab (elevated + teal label). */
    private static final Color SIDEBAR_NAV_SELECTED_BG = AppUI.SURFACE_ELEVATED;
    private static final Color SIDEBAR_NAV_SELECTED_FG = AppUI.PRIMARY;

    private static final int MAX_SALE_TRANSACTION_NOTE_LENGTH = 2000;

    /**
     * Normalizes optional per-checkout sale note for storage (trim, empty → {@code null}, max length).
     *
     * @param raw user-entered note, may be null
     * @return trimmed text or {@code null} when absent
     */
    private static String normalizedSaleTransactionNote(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > MAX_SALE_TRANSACTION_NOTE_LENGTH) {
            return t.substring(0, MAX_SALE_TRANSACTION_NOTE_LENGTH);
        }
        return t;
    }

    /**
     * @param amount USD scalar (may be negative for losses)
     * @return localized currency string ({@code $} prefix, separators)
     */
    private static String formatUsdMoney(double amount) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("$#,##0.00;-$#,##0.00", sym);
        return df.format(amount);
    }

    /**
     * Parses a currency-like or decimal market price ({@code $}, commas stripped). Blank or null ⇒ caller leaves DB untouched.
     *
     * @param raw field text supplied by admins
     * @return boxed price or {@code null} when intentionally blank
     * @throws NumberFormatException negative, NaN, infinite, or non-numeric text
     */
    private static Double parseOptionalMarketPriceInput(String raw) throws NumberFormatException {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        t = t.replace("$", "").replace(",", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        double v = Double.parseDouble(t);
        if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) {
            throw new NumberFormatException("Use a non-negative number for market price.");
        }
        return v;
    }

    /**
     * Top session strip labeled with signed-in username and optional backup status.
     *
     * @param user session identity
     * @return bordered north strip component ready for frame attachment
     */
    private static JPanel buildSessionTopBar(
            User user,
            Connection connection,
            JFrame frame,
            JPanel workspaceContainer,
            AccountActions accountActions
    ) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppUI.BORDER),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));
        AppUI.applyPanelBackground(bar);

        JLabel brand = new JLabel(user.getUsername(), SwingConstants.CENTER);
        Font base = brand.getFont();
        brand.setFont(base.deriveFont(Font.BOLD, 16f));
        brand.setForeground(AppUI.TEXT);
        bar.add(brand, BorderLayout.CENTER);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        AppUI.applyPanelBackground(east);
        east.setOpaque(false);
        if (user.hasAdminRights()) {
            try {
                int reminderDays = readBackupReminderDays(connection);
                int since = accountActions.daysSinceLatestBackup();
                if (since < 0 || since > reminderDays) {
                    String backupText = since < 0 ? "No backups found" : "Backup " + since + "d ago";
                    JButton backupLink = new JButton(backupText);
                    backupLink.setBorderPainted(false);
                    backupLink.setContentAreaFilled(false);
                    backupLink.setFocusPainted(false);
                    backupLink.setForeground(SETUP_FG);
                    backupLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    backupLink.addActionListener(e -> showView(workspaceContainer, VIEW_ADMIN_TOOLS,
                            buildAdministrationToolsPanel(user, connection, frame, accountActions)));
                    east.add(backupLink);
                }
            } catch (SQLException | IOException ignored) {
                // Omit backup chip when status cannot be loaded.
            }
        }
        if (east.getComponentCount() > 0) {
            bar.add(east, BorderLayout.EAST);
        }

        return bar;
    }

    private static final Color SETUP_FG = new Color(0xfbbf24);

    /** Live session strip; cleared when the workspace frame closes. */
    private static volatile FinancialMetricsStrip activeMetricsStrip;

    /** Admin layout only: right rail toggles between financial metrics and selected item photo. */
    private static volatile AdminMetricsRailHost adminMetricsRailHost;

    /** While the workspace frame is open: recomputes the bottom profit-alert banner from {@code app_metadata}. */
    private static volatile Consumer<Connection> profitAlertBannerRefreshAction;
    /** Sidebar card key for the view currently shown in the workspace (persisted on close). */
    private static volatile String activeWorkspaceViewKey = "home";
    private static final int RECENT_ITEMS_MAX = 10;
    private static final Deque<RecentItemEntry> recentItems = new ArrayDeque<>();
    private static final List<Runnable> recentItemRefreshers = new ArrayList<>();

    private record RecentItemEntry(String itemCode, String label) {
    }

    /**
     * Refreshes the scrolling profit-alert banner after settings change (invokes on the EDT).
     *
     * @param connection active session DB connection
     */
    public static void scheduleProfitAlertBannerRefresh(Connection connection) {
        Consumer<Connection> action = profitAlertBannerRefreshAction;
        if (action == null || connection == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> action.accept(connection));
    }

    private static int readBackupReminderDays(Connection connection) throws SQLException {
        String raw = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_BACKUP_REMINDER_DAYS);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_BACKUP_REMINDER_DAYS;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v < 1 ? DEFAULT_BACKUP_REMINDER_DAYS : Math.min(v, 365);
        } catch (NumberFormatException ex) {
            return DEFAULT_BACKUP_REMINDER_DAYS;
        }
    }

    private static boolean readBackupOnLogoutEnabled(Connection connection) throws SQLException {
        String raw = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_BACKUP_ON_LOGOUT_ENABLED);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return "1".equals(raw.trim()) || "true".equalsIgnoreCase(raw.trim());
    }

    private static int readStaleMarketPriceDays(Connection connection) throws SQLException {
        String raw = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_STALE_MARKET_PRICE_DAYS);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_STALE_MARKET_PRICE_DAYS;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v < 1 ? DEFAULT_STALE_MARKET_PRICE_DAYS : Math.min(v, 3650);
        } catch (NumberFormatException ex) {
            return DEFAULT_STALE_MARKET_PRICE_DAYS;
        }
    }

    private static String favouriteItemsMetadataKey(String username) {
        return "favourite_items:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> readFavouriteItemCodes(Connection connection, String username) throws SQLException {
        String raw = DatabaseManager.getAppMetadata(connection, favouriteItemsMetadataKey(username));
        Set<String> codes = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return codes;
        }
        for (String part : raw.split(",")) {
            String code = part.trim();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return codes;
    }

    private static void writeFavouriteItemCodes(Connection connection, String username, Set<String> codes)
            throws SQLException {
        String value = String.join(",", codes);
        DatabaseManager.putAppMetadata(connection, favouriteItemsMetadataKey(username), value);
    }

    private static boolean toggleFavouriteItem(Connection connection, String username, String itemCode)
            throws SQLException {
        String code = itemCode == null ? "" : itemCode.trim();
        if (code.isEmpty()) {
            return false;
        }
        Set<String> codes = readFavouriteItemCodes(connection, username);
        boolean nowFavourite;
        if (codes.remove(code)) {
            nowFavourite = false;
        } else {
            codes.add(code);
            nowFavourite = true;
        }
        writeFavouriteItemCodes(connection, username, codes);
        return nowFavourite;
    }

    private static boolean viewItemHasReadablePhoto(String itemCode) {
        return Files.isReadable(itemImagePath(itemCode));
    }

    private static boolean matchesViewItemsSmartFilter(
            ViewItemShelfRow row,
            String filter,
            Set<String> favourites
    ) {
        if (VIEW_ITEMS_FILTER_FAVOURITES.equals(filter)) {
            return favourites.contains(row.itemCode());
        }
        if (VIEW_ITEMS_FILTER_LOW_STOCK.equals(filter)) {
            return row.reorderTrigger() > 0 && row.reorderTrigger() >= row.stock();
        }
        if (VIEW_ITEMS_FILTER_STALE_PRICE.equals(filter)) {
            return row.staleMarketPrice();
        }
        if (VIEW_ITEMS_FILTER_MISSING_PHOTO.equals(filter)) {
            return !row.hasPhoto();
        }
        if (VIEW_ITEMS_FILTER_HIGH_MARGIN.equals(filter)) {
            return row.unrealizedMarginPercent() != null
                    && row.unrealizedMarginPercent() >= VIEW_ITEMS_HIGH_MARGIN_THRESHOLD_PCT;
        }
        return true;
    }

    private static void sortViewItemsShelfRows(List<ViewItemShelfRow> rows, String filter, Set<String> favourites) {
        if (VIEW_ITEMS_FILTER_ALL.equals(filter)) {
            rows.sort((a, b) -> {
                boolean fa = favourites.contains(a.itemCode());
                boolean fb = favourites.contains(b.itemCode());
                if (fa != fb) {
                    return fa ? -1 : 1;
                }
                return a.itemCode().compareToIgnoreCase(b.itemCode());
            });
        } else {
            rows.sort(Comparator.comparing(ViewItemShelfRow::itemCode, String.CASE_INSENSITIVE_ORDER));
        }
    }

    private static synchronized List<RecentItemEntry> snapshotRecentItems() {
        return new ArrayList<>(recentItems);
    }

    private static synchronized void registerRecentItemRefresher(Runnable refresher) {
        if (refresher == null) {
            return;
        }
        recentItemRefreshers.add(refresher);
    }

    private static synchronized void unregisterRecentItemRefresher(Runnable refresher) {
        recentItemRefreshers.remove(refresher);
    }

    private static void recordRecentItem(String itemCode, String label) {
        String code = itemCode == null ? "" : itemCode.trim();
        if (code.isEmpty()) {
            return;
        }
        String cleanedLabel = label == null ? "" : label.trim();
        synchronized (WorkspaceShell.class) {
            recentItems.removeIf(e -> code.equalsIgnoreCase(e.itemCode()));
            recentItems.addFirst(new RecentItemEntry(code, cleanedLabel));
            while (recentItems.size() > RECENT_ITEMS_MAX) {
                recentItems.removeLast();
            }
        }
        List<Runnable> refreshers;
        synchronized (WorkspaceShell.class) {
            refreshers = new ArrayList<>(recentItemRefreshers);
        }
        for (Runnable refresher : refreshers) {
            SwingUtilities.invokeLater(refresher);
        }
    }

    private static boolean isLowStockSku(int stock, int reorderTrigger) {
        return reorderTrigger > 0 && reorderTrigger >= stock;
    }

    private static int parseMetadataInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static void restoreWorkspaceLayout(
            Connection connection,
            JSplitPane sidebarSplit,
            JSplitPane adminTripleSplit,
            JSplitPane photoSplit
    ) {
        try {
            int sidebar = parseMetadataInt(
                    DatabaseManager.getAppMetadata(connection, DatabaseManager.META_WORKSPACE_SIDEBAR_DIVIDER),
                    SIDEBAR_TARGET_WIDTH);
            if (sidebarSplit.getWidth() > 0) {
                sidebarSplit.setDividerLocation(Math.max(160, Math.min(sidebar, sidebarSplit.getWidth() - WORKSPACE_MIN_WIDTH)));
            } else {
                sidebarSplit.setDividerLocation(sidebar);
            }
            if (adminTripleSplit != null) {
                int rail = parseMetadataInt(
                        DatabaseManager.getAppMetadata(connection, DatabaseManager.META_WORKSPACE_ADMIN_RAIL_DIVIDER),
                        -1);
                if (rail > 0 && adminTripleSplit.getWidth() > 0) {
                    adminTripleSplit.setDividerLocation(rail);
                }
            }
            if (photoSplit != null) {
                int photoDiv = parseMetadataInt(
                        DatabaseManager.getAppMetadata(connection, DatabaseManager.META_WORKSPACE_PHOTO_SPLIT_DIVIDER),
                        -1);
                if (photoDiv > 0) {
                    photoSplit.setDividerLocation(photoDiv);
                }
            }
        } catch (SQLException ignored) {
            // Keep defaults when metadata cannot be read.
        }
    }

    private static void persistWorkspaceLayout(
            Connection connection,
            JSplitPane sidebarSplit,
            JSplitPane adminTripleSplit,
            JSplitPane photoSplit,
            String lastViewKey
    ) {
        try {
            if (lastViewKey != null && !lastViewKey.isBlank() && !"home".equals(lastViewKey)) {
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_WORKSPACE_LAST_VIEW, lastViewKey);
            }
            DatabaseManager.putAppMetadata(connection, DatabaseManager.META_WORKSPACE_SIDEBAR_DIVIDER,
                    Integer.toString(sidebarSplit.getDividerLocation()));
            if (adminTripleSplit != null) {
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_WORKSPACE_ADMIN_RAIL_DIVIDER,
                        Integer.toString(adminTripleSplit.getDividerLocation()));
            }
            if (photoSplit != null) {
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_WORKSPACE_PHOTO_SPLIT_DIVIDER,
                        Integer.toString(photoSplit.getDividerLocation()));
            }
        } catch (SQLException ignored) {
            // Best-effort persistence on close.
        }
    }

    private static void installDividerPersistence(Connection connection, JSplitPane... splits) {
        for (JSplitPane split : splits) {
            if (split == null) {
                continue;
            }
            split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
                if (connection == null) {
                    return;
                }
                try {
                    if (split.getName() != null) {
                        DatabaseManager.putAppMetadata(connection, split.getName(),
                                Integer.toString(split.getDividerLocation()));
                    }
                } catch (SQLException ignored) {
                    // Ignore intermittent write failures while dragging.
                }
            });
        }
    }

    private static void restoreLastWorkspaceView(
            User user,
            Connection connection,
            JFrame frame,
            JPanel workspaceContainer,
            AccountActions accountActions
    ) {
        try {
            String last = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_WORKSPACE_LAST_VIEW);
            if (last == null || last.isBlank() || "home".equals(last) || "Log Out".equals(last)) {
                return;
            }
            for (NavItem item : getItems(user, connection, accountActions, frame, workspaceContainer)) {
                if (last.equals(item.label) && item.viewBuilder != null) {
                    showView(workspaceContainer, item.label, item.viewBuilder.build());
                    return;
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(frame, "Could not restore last view: " + ex.getMessage(),
                    "Workspace", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void installWorkspaceKeyboardShortcuts(
            JFrame frame,
            JPanel root,
            User user,
            Connection connection,
            JPanel workspaceContainer,
            AccountActions accountActions
    ) {
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        List<NavItem> items = getItems(user, connection, accountActions, frame, workspaceContainer);
        int shortcut = 1;
        for (NavItem item : items) {
            if (item.viewBuilder == null || shortcut > 9) {
                continue;
            }
            String actionKey = "ims.nav." + shortcut;
            int keyCode = KeyEvent.VK_0 + shortcut;
            int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            im.put(KeyStroke.getKeyStroke(keyCode, mask), actionKey);
            final String label = item.label;
            am.put(actionKey, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    try {
                        showView(workspaceContainer, label, item.viewBuilder.build());
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(frame, "Unable to open " + label + ": " + ex.getMessage(),
                                "View Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            shortcut++;
        }
    }

    private static void maybeShowBackupReminderDialog(
            User user,
            Connection connection,
            JFrame frame,
            AccountActions accountActions
    ) {
        if (!user.hasAdminRights()) {
            return;
        }
        try {
            int reminderDays = readBackupReminderDays(connection);
            int since = accountActions.daysSinceLatestBackup();
            if (since >= 0 && since <= reminderDays) {
                return;
            }
            String msg = since < 0
                    ? "No database backups were found under database_backups.\n"
                    + "Create a backup from Admin tools → Database backup."
                    : "Your newest backup is " + since + " days old (reminder threshold: " + reminderDays + " days).\n"
                    + "Consider creating a backup from Admin tools.";
            JOptionPane.showMessageDialog(frame, msg, "Backup reminder", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException | IOException ex) {
            // Skip dialog when reminder status cannot be determined.
        }
    }

    private static void maybePromptBackupOnLogout(
            User user,
            Connection connection,
            JFrame frame,
            AccountActions accountActions
    ) {
        if (user == null || !user.hasAdminRights()) {
            return;
        }
        try {
            if (!readBackupOnLogoutEnabled(connection)) {
                return;
            }
            int reminderDays = readBackupReminderDays(connection);
            int since = accountActions.daysSinceLatestBackup();
            if (since < 0 || since > reminderDays) {
                String msg = since < 0
                        ? "No backups were found. Run a backup before logging out?"
                        : "Latest backup is " + since + " days old (threshold " + reminderDays + ").\nRun a backup before logging out?";
                int result = JOptionPane.showConfirmDialog(
                        frame,
                        msg,
                        "Backup before logout",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    accountActions.backUpDatabase(user, frame);
                }
            }
        } catch (SQLException | IOException ex) {
            // Skip the prompt when metadata or backup status cannot be read.
        }
    }

    /** One in-progress sale line (quantity and unit price) before checkout. */
    private static final class SaleDraftLine {
        final String itemCode;
        int quantity;
        double unitSalePrice;
        /** {@code Inventory.Item Name} captured when the line is added (for the draft grid). */
        String itemDescription;
        /**
         * Bin for this line when {@link DatabaseManager#hasInventoryStorageQtyTable(Connection)} is true;
         * otherwise {@code null} (allocation uses legacy spread across bins).
         */
        final Integer storageLocationId;
        /** Display label for {@link #storageLocationId} (draft table). */
        String storageLocationLabel;

        SaleDraftLine(
                String itemCode,
                int quantity,
                double unitSalePrice,
                String itemDescription,
                Integer storageLocationId,
                String storageLocationLabel
        ) {
            this.itemCode = itemCode == null ? "" : itemCode;
            this.quantity = quantity;
            this.unitSalePrice = unitSalePrice;
            this.itemDescription = itemDescription == null ? "" : itemDescription;
            this.storageLocationId = storageLocationId;
            this.storageLocationLabel = storageLocationLabel == null ? "" : storageLocationLabel;
        }
    }

    /** Map key for sale draft lines (same SKU may appear from different bins). */
    private static String saleDraftLineMapKey(String itemCode, Integer storageLocationId) {
        return itemCode + '\u0001' + (storageLocationId == null ? "-" : storageLocationId.toString());
    }

    /** One draft row for batch Add Item (item codes assigned on commit). */
    private static final class AddItemDraftLine {
        final String itemName;
        final int stock;
        final int reorder;
        final String supplier;
        final Integer leadTime;
        /** Optional item note (shown on View Items photo rail only). */
        final String notes;
        /** Optional JPEG chosen before commit; copied to {@code item_images/<code>.jpeg} after insert. */
        final Path pendingPhoto;

        /**
         * @param itemName      display name until {@code ITM} code is assigned at commit
         * @param stock         initial stock count
         * @param reorder       reorder trigger threshold
         * @param supplier      supplier label (may be empty)
         * @param leadTime      supplier lead time days, or null
         * @param notes         free-text note (may be null)
         * @param pendingPhoto  optional JPEG path staged for copy after insert
         */
        AddItemDraftLine(String itemName, int stock, int reorder, String supplier, Integer leadTime, String notes, Path pendingPhoto) {
            this.itemName = itemName;
            this.stock = stock;
            this.reorder = reorder;
            this.supplier = supplier == null ? "" : supplier;
            this.leadTime = leadTime;
            this.notes = notes == null ? "" : notes;
            this.pendingPhoto = pendingPhoto;
        }
    }

    /** Schedules a deferred refresh of the admin financial metrics strip (noop when strip absent). */
    private static void requestMetricsRefresh() {
        FinancialMetricsStrip strip = activeMetricsStrip;
        if (strip != null) {
            SwingUtilities.invokeLater(strip::refresh);
        }
    }

    /** Re-runs metrics query and label colors on the EDT (not deferred). */
    private static void refreshActiveMetricsStripNow() {
        FinancialMetricsStrip strip = activeMetricsStrip;
        if (strip != null) {
            strip.refresh();
        }
    }

    /** Keeps the sidebar/workspace divider sane when the frame grows or shrinks. */
    private static void syncSidebarWorkspaceSplit(JSplitPane splitPane) {
        int w = splitPane.getWidth();
        if (w <= 0) {
            return;
        }
        int div = splitPane.getDividerSize();
        int loc = Math.min(SIDEBAR_TARGET_WIDTH, w - WORKSPACE_MIN_WIDTH - div);
        loc = Math.max(160, loc);
        splitPane.setDividerLocation(loc);
    }

    /** Keeps the admin metrics rail ~fixed width so the main block stretches with the window. */
    private static void syncAdminMetricsSplit(JSplitPane triplePane) {
        int w = triplePane.getWidth();
        if (w <= 0) {
            return;
        }
        int div = triplePane.getDividerSize();
        int loc = w - ADMIN_METRICS_RAIL_OUTER_PX - div;
        loc = Math.max(MAIN_AREA_MIN_FOR_METRICS, loc);
        int maxLeft = w - div - 96;
        if (maxLeft < MAIN_AREA_MIN_FOR_METRICS) {
            loc = Math.max(200, Math.min(loc, maxLeft));
        } else {
            loc = Math.min(loc, maxLeft);
        }
        triplePane.setDividerLocation(loc);
    }

    /**
     * Admin-only right column: on-order exposure (open PO value), market-valued on-hand stock, period and lifetime P/L,
     * margin, and top movers for the current calendar month. Refreshes from {@link InventoryFifo} on the EDT.
     */
    private static final class FinancialMetricsStrip extends JPanel {
        private static final long serialVersionUID = 1L;

        private static final Color CARD_BORDER = AppUI.BORDER;
        private static final Color CARD_HEAD = AppUI.SURFACE_ELEVATED;
        private static final Color ROW_ZEBRA = AppUI.SURFACE_ELEVATED;
        private static final Color MUTED = AppUI.TEXT_MUTED;
        private static final Color LABEL = AppUI.TEXT_MUTED;

        private final JLabel marketStockValueLabel = new JLabel("—");
        private final JLabel profitLabel = new JLabel("—");
        private final JLabel plTodayLabel = new JLabel("—");
        private final JLabel plWeekLabel = new JLabel("—");
        private final JLabel plMonthLabel = new JLabel("—");
        private final JLabel marginLabel = new JLabel("—");
        private final JLabel poExposureLabel = new JLabel("—");
        private final JPanel topMoversRows = new JPanel();
        private final Connection connection;

        /**
         * @param connection open enterprise connection; retained for repeated {@link #refresh()} queries
         */
        FinancialMetricsStrip(Connection connection) {
            this.connection = connection;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 1, 0, 0, AppUI.BORDER),
                    BorderFactory.createEmptyBorder(14, 12, 22, 12)));
            AppUI.applyPanelBackground(this);

            Font base = marketStockValueLabel.getFont();

            JLabel title = new JLabel("Financials");
            title.setFont(base.deriveFont(Font.BOLD, 17f));
            title.setForeground(AppUI.TEXT);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(title);
            add(Box.createVerticalStrut(14));

            JPanel moversCard = new JPanel(new BorderLayout(0, 0));
            AppUI.markCardSurface(moversCard);
            moversCard.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
            moversCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel moversHead = new JLabel("Top 10 · this month");
            moversHead.setOpaque(true);
            moversHead.setBackground(CARD_HEAD);
            moversHead.setFont(base.deriveFont(Font.BOLD, 11f));
            moversHead.setForeground(AppUI.TEXT_MUTED);
            moversHead.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            moversCard.add(moversHead, BorderLayout.NORTH);
            topMoversRows.setLayout(new BoxLayout(topMoversRows, BoxLayout.Y_AXIS));
            topMoversRows.setOpaque(false);
            moversCard.add(topMoversRows, BorderLayout.CENTER);
            add(moversCard);
            add(Box.createVerticalStrut(20));

            JPanel periodCard = newCardShell();
            periodCard.add(sectionHeading(base, "P/L by period"), BorderLayout.NORTH);
            JPanel periodBody = new JPanel();
            periodBody.setLayout(new BoxLayout(periodBody, BoxLayout.Y_AXIS));
            periodBody.setOpaque(false);
            AppUI.applyPanelBackground(periodBody);
            addPeriodRowInto(periodBody, base, "Today", plTodayLabel, true);
            addPeriodRowInto(periodBody, base, "This week (ISO)", plWeekLabel, true);
            addPeriodRowInto(periodBody, base, "This month", plMonthLabel, false);
            periodCard.add(periodBody, BorderLayout.CENTER);
            add(periodCard);
            add(Box.createVerticalStrut(20));

            JPanel totalsCard = newCardShell();
            totalsCard.add(sectionHeading(base, "Exposure & totals"), BorderLayout.NORTH);
            JPanel totalsBody = new JPanel();
            totalsBody.setLayout(new BoxLayout(totalsBody, BoxLayout.Y_AXIS));
            totalsBody.setOpaque(false);
            AppUI.applyPanelBackground(totalsBody);
            addPeriodRowInto(totalsBody, base, "Value on order", poExposureLabel, true);
            addPeriodRowInto(totalsBody, base, "Market value · stock", marketStockValueLabel, true);
            addPeriodRowInto(totalsBody, base, "Total P/L", profitLabel, true);
            addPeriodRowInto(totalsBody, base, "Total P/L %", marginLabel, false);
            totalsCard.add(totalsBody, BorderLayout.CENTER);
            add(totalsCard);
            add(Box.createVerticalStrut(20));

            setMinimumSize(new Dimension(220, 80));

            for (JLabel dynamicPl : new JLabel[]{plTodayLabel, plWeekLabel, plMonthLabel, profitLabel, marginLabel}) {
                dynamicPl.putClientProperty("ims.preserveForeground", Boolean.TRUE);
            }
        }

        /** Bordered card panel used for period and totals sections. */
        private static JPanel newCardShell() {
            JPanel p = new JPanel(new BorderLayout(0, 0));
            AppUI.markCardSurface(p);
            p.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            return p;
        }

        /** Card section title bar (gray header band). */
        private static JLabel sectionHeading(Font base, String text) {
            JLabel h = new JLabel(text);
            h.setOpaque(true);
            h.setBackground(CARD_HEAD);
            h.setFont(base.deriveFont(Font.BOLD, 11f));
            h.setForeground(AppUI.TEXT_MUTED);
            h.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            return h;
        }

        /**
         * Adds a caption / value row to a metrics card (period P/L lines and exposure totals share the same chrome).
         *
         * @param parent        vertical box host
         * @param base          reference font for sizing
         * @param caption       left label
         * @param valueLabel    right-aligned value control
         * @param dividerBelow  when true, paints a bottom divider on the row
         */
        private void addPeriodRowInto(JPanel parent, Font base, String caption, JLabel valueLabel, boolean dividerBelow) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            AppUI.applyPanelBackground(row);
            if (dividerBelow) {
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            } else {
                row.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
            }
            JLabel cap = new JLabel(caption);
            cap.setFont(base.deriveFont(Font.PLAIN, 11f));
            cap.setForeground(MUTED);
            valueLabel.setFont(base.deriveFont(Font.BOLD, 14f));
            valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            valueLabel.setVerticalAlignment(SwingConstants.CENTER);
            row.add(cap, BorderLayout.WEST);
            row.add(valueLabel, BorderLayout.EAST);
            parent.add(row);
        }

        /** One ranked line in the “Top movers” list. */
        private JPanel buildMoverRow(int rank, InventoryFifo.TopMoverRow row, Font base, boolean zebra) {
            JPanel line = new JPanel(new BorderLayout(8, 0));
            line.setOpaque(true);
            line.setBackground(zebra ? ROW_ZEBRA : AppUI.SURFACE);
            line.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel rankLab = new JLabel(String.format(Locale.US, "%2d", rank));
            rankLab.setFont(base.deriveFont(Font.BOLD, 11f));
            rankLab.setForeground(AppUI.TEXT_MUTED);
            rankLab.setHorizontalAlignment(SwingConstants.RIGHT);
            rankLab.setPreferredSize(new Dimension(22, 18));
            JLabel codeLab = new JLabel(row.itemCode);
            codeLab.setFont(base.deriveFont(Font.BOLD, 12f));
            codeLab.setForeground(AppUI.TEXT);
            JLabel unitLab = new JLabel(row.units + " sold");
            unitLab.setFont(base.deriveFont(Font.PLAIN, 11f));
            unitLab.setForeground(LABEL);
            unitLab.setHorizontalAlignment(SwingConstants.RIGHT);
            JPanel mid = new JPanel(new BorderLayout());
            mid.setOpaque(false);
            AppUI.applyPanelBackground(mid);
            mid.add(codeLab, BorderLayout.CENTER);
            line.add(rankLab, BorderLayout.WEST);
            line.add(mid, BorderLayout.CENTER);
            line.add(unitLab, BorderLayout.EAST);
            return line;
        }

        /** Colors a P/L label green (≥0) or red (&lt;0). */
        private static void setPlColor(JLabel label, double pl) {
            label.setForeground(pl >= 0 ? AppUI.SUCCESS : AppUI.DANGER);
        }

        /** Replaces the movers list with a single error message. */
        private void fillMoversError(Font base, String message) {
            topMoversRows.removeAll();
            JLabel err = new JLabel(message);
            err.setFont(base.deriveFont(Font.PLAIN, 11f));
            err.setForeground(AppUI.DANGER);
            err.setBorder(BorderFactory.createEmptyBorder(10, 10, 12, 10));
            err.setAlignmentX(Component.LEFT_ALIGNMENT);
            topMoversRows.add(err);
            topMoversRows.revalidate();
            topMoversRows.repaint();
        }

        /** Re-queries SQLite and updates all labels and the top-movers list (safe to call on the EDT). */
        void refresh() {
            Font base = marketStockValueLabel.getFont();
            try {
                double marketVal = InventoryFifo.totalMarketValueOfOnHandStock(connection);
                double plLife = InventoryFifo.lifetimeProfitLoss(connection);
                marketStockValueLabel.setText(formatUsdMoney(marketVal));
                profitLabel.setText(formatUsdMoney(plLife));
                profitLabel.setForeground(plLife >= 0 ? AppUI.SUCCESS : AppUI.DANGER);

                LocalDate today = LocalDate.now();
                double plToday = InventoryFifo.profitLossBetweenDates(connection, today, today);
                double plWeek = InventoryFifo.profitLossBetweenDates(
                        connection,
                        InventoryFifo.startOfIsoWeek(today),
                        InventoryFifo.endOfIsoWeek(today)
                );
                double plMonth = InventoryFifo.profitLossBetweenDates(
                        connection,
                        InventoryFifo.firstDayOfMonth(today),
                        InventoryFifo.lastDayOfMonth(today)
                );

                plTodayLabel.setText(formatUsdMoney(plToday));
                setPlColor(plTodayLabel, plToday);
                plWeekLabel.setText(formatUsdMoney(plWeek));
                setPlColor(plWeekLabel, plWeek);
                plMonthLabel.setText(formatUsdMoney(plMonth));
                setPlColor(plMonthLabel, plMonth);

                double revenue = InventoryFifo.lifetimeTotalRevenue(connection);
                if (revenue <= 1e-9) {
                    marginLabel.setText("—");
                    marginLabel.setForeground(MUTED);
                } else {
                    double pct = (plLife / revenue) * 100.0;
                    marginLabel.setText(String.format(Locale.US, "%.1f%%", pct));
                    marginLabel.setForeground(pct >= 0 ? AppUI.SUCCESS : AppUI.DANGER);
                }

                double poExp = InventoryFifo.openPurchaseOrderExposure(connection);
                poExposureLabel.setText(formatUsdMoney(poExp));
                poExposureLabel.setForeground(AppUI.TEXT);

                List<InventoryFifo.TopMoverRow> movers = InventoryFifo.topMoversByUnitsBetweenDates(
                        connection,
                        InventoryFifo.firstDayOfMonth(today),
                        InventoryFifo.lastDayOfMonth(today),
                        10
                );
                topMoversRows.removeAll();
                if (movers.isEmpty()) {
                    JLabel empty = new JLabel("No sales with DateISO this month");
                    empty.setFont(base.deriveFont(Font.PLAIN, 11f));
                    empty.setForeground(MUTED);
                    empty.setBorder(BorderFactory.createEmptyBorder(10, 10, 12, 10));
                    empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                    topMoversRows.add(empty);
                } else {
                    int i = 0;
                    for (InventoryFifo.TopMoverRow m : movers) {
                        i++;
                        topMoversRows.add(buildMoverRow(i, m, base, (i & 1) == 0));
                    }
                }
                topMoversRows.revalidate();
                topMoversRows.repaint();
            } catch (SQLException ex) {
                marketStockValueLabel.setText("error");
                profitLabel.setText("error");
                profitLabel.setForeground(AppUI.DANGER);
                plTodayLabel.setText("error");
                plWeekLabel.setText("error");
                plMonthLabel.setText("error");
                marginLabel.setText("error");
                poExposureLabel.setText("error");
                fillMoversError(base, "Could not load metrics");
            }
        }
    }

    /** Utility holder for workspace shell UI composition and actions. */
    private WorkspaceShell() {
    }

    /**
     * Opens the signed-in Swing workspace frame (sidebar + stacked cards; optional metrics/photo rails for admins).
     * Runs on the EDT; must receive an open JDBC connection whose lifecycle usually ends inside the frame dispose hook.
     *
     * @param user active signed-in user
     * @param connection enterprise DB connection reused for sidebar views during the session
     * @param accountActions dialogs for backups and modal password resets
     * @param whenWindowClosed optional EDT callback invoked after dispose (e.g. return to login); may be {@code null}
     */
    public static void open(
            User user,
            Connection connection,
            AccountActions accountActions,
            Runnable whenWindowClosed
    ) {
        JFrame frame = new JFrame("Inventory Management System");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int scaledH = MAIN_FRAME_BASE_H * MAIN_FRAME_HEIGHT_SCALE_PERCENT / 100;
        int scaledW = (int) Math.round(
                MAIN_FRAME_BASE_W * (MAIN_FRAME_HEIGHT_SCALE_PERCENT / 100.0) * MAIN_FRAME_WIDTH_EXTRA_FACTOR
        );
        int capW = Math.max(MAIN_FRAME_BASE_W, usable.width - 24);
        int capH = Math.max(MAIN_FRAME_BASE_H, usable.height - 24);
        frame.setSize(Math.min(scaledW, capW), Math.min(scaledH, capH));
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        JPanel workspaceContainer = new JPanel(new CardLayout());
        AppUI.applyPanelBackground(workspaceContainer);
        workspaceContainer.add(buildEmptyWorkspaceCanvas(), "home");

        JPanel sidebar = buildSidebar(user, frame, workspaceContainer, connection, accountActions);

        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, workspaceContainer);
        splitPane.setDividerLocation(SIDEBAR_TARGET_WIDTH);
        splitPane.setResizeWeight(0);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        splitPane.setOpaque(true);
        splitPane.setBackground(AppUI.BACKGROUND);

        java.awt.Component center;
        final JSplitPane[] adminMetricsTripleHolder = new JSplitPane[1];
        final JSplitPane[] photoSplitHolder = new JSplitPane[1];
        splitPane.setName(DatabaseManager.META_WORKSPACE_SIDEBAR_DIVIDER);
        if (user.hasAdminRights()) {
            FinancialMetricsStrip metricsStrip = new FinancialMetricsStrip(connection);
            activeMetricsStrip = metricsStrip;

            JScrollPane metricsScroll = new JScrollPane(metricsStrip);
            metricsScroll.setBorder(null);
            metricsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            metricsScroll.getVerticalScrollBar().setUnitIncrement(16);

            JPanel metricsRailHost = new JPanel(new CardLayout());
            AppUI.applyPanelBackground(metricsRailHost);
            metricsRailHost.add(metricsScroll, "metrics");

            JPanel photoRailCard = new JPanel(new BorderLayout(8, 8));
            AppUI.applyPanelBackground(photoRailCard);
            photoRailCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JLabel photoRailTitle = new JLabel(" ", SwingConstants.CENTER);
            photoRailTitle.setFont(photoRailTitle.getFont().deriveFont(Font.BOLD, 14f));
            JLabel photoRailImage = new JLabel(" ", SwingConstants.CENTER);
            photoRailImage.setVerticalAlignment(SwingConstants.CENTER);
            photoRailImage.setHorizontalAlignment(SwingConstants.CENTER);
            photoRailImage.setForeground(AppUI.TEXT_MUTED);
            JScrollPane photoRailScroll = new JScrollPane(photoRailImage);
            photoRailScroll.setBorder(AppUI.newRoundedBorder(8));
            photoRailScroll.getVerticalScrollBar().setUnitIncrement(16);
            photoRailScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            photoRailScroll.setPreferredSize(new Dimension(ITEM_PHOTO_DISPLAY_MAX_W + 16, ITEM_PHOTO_DISPLAY_MAX_H + 16));

            JTextArea photoRailStats = new JTextArea();
            photoRailStats.setEditable(false);
            photoRailStats.setOpaque(false);
            photoRailStats.setLineWrap(true);
            photoRailStats.setWrapStyleWord(true);
            photoRailStats.setRows(8);
            photoRailStats.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            photoRailStats.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            JScrollPane photoRailStatsScroll = new JScrollPane(photoRailStats);
            photoRailStatsScroll.setBorder(AppUI.newRoundedBorder(8));
            photoRailStatsScroll.getVerticalScrollBar().setUnitIncrement(16);

            JTextArea photoRailNotes = new JTextArea(4, 18);
            photoRailNotes.setLineWrap(true);
            photoRailNotes.setWrapStyleWord(true);
            photoRailNotes.setToolTipText("Inventory note (max " + ITEM_NOTES_MAX_CHARS + " characters).");
            photoRailNotes.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            JScrollPane photoRailNotesScroll = new JScrollPane(photoRailNotes);
            photoRailNotesScroll.setBorder(AppUI.newRoundedBorder(8));
            photoRailNotesScroll.getVerticalScrollBar().setUnitIncrement(16);

            JLabel photoRailNotesHeading = new JLabel("Notes");
            photoRailNotesHeading.setFont(photoRailNotesHeading.getFont().deriveFont(Font.BOLD, 12f));

            JButton railUpdateNote = new JButton("Update note");
            JButton railUpdateImage = new JButton("Update image");
            JButton railSaveNote = new JButton("Save note");
            JButton railCancelNote = new JButton("Cancel");
            styleSecondaryButton(railUpdateNote);
            styleSecondaryButton(railUpdateImage);
            styleSecondaryButton(railSaveNote);
            styleSecondaryButton(railCancelNote);
            railSaveNote.setVisible(false);
            railCancelNote.setVisible(false);

            JPanel railNoteActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            AppUI.applyPanelBackground(railNoteActions);
            railNoteActions.setOpaque(false);
            railNoteActions.add(railUpdateNote);
            railNoteActions.add(railSaveNote);
            railNoteActions.add(railCancelNote);
            railNoteActions.add(railUpdateImage);

            JPanel photoRailNotesBlock = new JPanel(new BorderLayout(0, 4));
            AppUI.applyPanelBackground(photoRailNotesBlock);
            photoRailNotesBlock.setOpaque(false);
            photoRailNotesBlock.add(photoRailNotesHeading, BorderLayout.NORTH);
            photoRailNotesBlock.add(photoRailNotesScroll, BorderLayout.CENTER);
            photoRailNotesBlock.add(railNoteActions, BorderLayout.SOUTH);

            JPanel photoRailLower = new JPanel(new BorderLayout(0, 6));
            AppUI.applyPanelBackground(photoRailLower);
            photoRailLower.add(photoRailStatsScroll, BorderLayout.CENTER);
            photoRailLower.add(photoRailNotesBlock, BorderLayout.SOUTH);

            JPanel photoRailPhotoActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            AppUI.applyPanelBackground(photoRailPhotoActions);
            photoRailPhotoActions.setOpaque(false);
            JButton railNextWebPhoto = new JButton("Next web photo");
            styleSecondaryButton(railNextWebPhoto);
            photoRailPhotoActions.add(railNextWebPhoto);

            JPanel photoRailTop = new JPanel(new BorderLayout());
            AppUI.applyPanelBackground(photoRailTop);
            photoRailTop.add(photoRailScroll, BorderLayout.CENTER);
            photoRailTop.add(photoRailPhotoActions, BorderLayout.SOUTH);
            JSplitPane photoRailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, photoRailTop, photoRailLower);
            photoRailSplit.setResizeWeight(0.66);
            photoRailSplit.setContinuousLayout(true);
            photoRailSplit.setBorder(null);
            photoRailSplit.setDividerSize(5);
            photoRailSplit.setName(DatabaseManager.META_WORKSPACE_PHOTO_SPLIT_DIVIDER);
            photoSplitHolder[0] = photoRailSplit;
            SwingUtilities.invokeLater(() -> photoRailSplit.setDividerLocation(0.66));

            photoRailCard.add(photoRailTitle, BorderLayout.NORTH);
            photoRailCard.add(photoRailSplit, BorderLayout.CENTER);
            metricsRailHost.add(photoRailCard, "photo");
            adminMetricsRailHost = new AdminMetricsRailHost(
                    connection,
                    user.getUsername(),
                    metricsRailHost,
                    photoRailTitle,
                    photoRailImage,
                    photoRailStats,
                    photoRailNotes,
                    railUpdateNote,
                    railSaveNote,
                    railCancelNote,
                    railUpdateImage,
                    railNextWebPhoto,
                    () -> refreshViewItemsIfOpen(workspaceContainer, user, connection, frame));

            final JSplitPane triplePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane, metricsRailHost);
            triplePane.setResizeWeight(1.0);
            triplePane.setBorder(null);
            triplePane.setContinuousLayout(true);
            triplePane.setName(DatabaseManager.META_WORKSPACE_ADMIN_RAIL_DIVIDER);
            center = triplePane;
            adminMetricsTripleHolder[0] = triplePane;
        } else {
            activeMetricsStrip = null;
            adminMetricsRailHost = null;
            center = splitPane;
        }

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(true);
        root.setBackground(AppUI.BACKGROUND);

        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.setOpaque(false);
        if (AppUI.usesEmbeddedTitleBar()) {
            northStack.add(AppUI.createApplicationTitleBar("Inventory Management System"));
        }
        northStack.add(buildSessionTopBar(user, connection, frame, workspaceContainer, accountActions));
        root.add(northStack, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        ProfitAlertMarqueeBanner profitBanner = new ProfitAlertMarqueeBanner();
        root.add(profitBanner, BorderLayout.SOUTH);
        profitAlertBannerRefreshAction = profitBanner::refreshFromDatabase;
        scheduleProfitAlertBannerRefresh(connection);

        installDividerPersistence(connection, splitPane, adminMetricsTripleHolder[0], photoSplitHolder[0]);
        installWorkspaceKeyboardShortcuts(frame, root, user, connection, workspaceContainer, accountActions);
        activeWorkspaceViewKey = "home";

        final boolean[] backupPromptHandled = new boolean[]{false};
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (backupPromptHandled[0]) {
                    return;
                }
                backupPromptHandled[0] = true;
                maybePromptBackupOnLogout(user, connection, frame, accountActions);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                persistWorkspaceLayout(connection, splitPane, adminMetricsTripleHolder[0], photoSplitHolder[0],
                        activeWorkspaceViewKey);
                Object refresher = sidebar.getClientProperty(CLIENT_RECENT_REFRESHER);
                if (refresher instanceof Runnable r) {
                    unregisterRecentItemRefresher(r);
                }
                activeMetricsStrip = null;
                adminMetricsRailHost = null;
                profitAlertBannerRefreshAction = null;
                activeWorkspaceViewKey = "home";
                profitBanner.stopTimer();
                closeConnectionQuietly(connection);
                if (whenWindowClosed != null) {
                    SwingUtilities.invokeLater(whenWindowClosed);
                }
            }
        });

        root.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    JSplitPane outer = adminMetricsTripleHolder[0];
                    if (outer != null) {
                        syncAdminMetricsSplit(outer);
                    }
                    syncSidebarWorkspaceSplit(splitPane);
                });
            }
        });
        frame.setContentPane(root);

        AppUI.applyWindowChrome(frame);
        AppUI.styleWindow(frame);
        refreshActiveMetricsStripNow();
        frame.setVisible(true);
        maybeShowBackupReminderDialog(user, connection, frame, accountActions);
        SwingUtilities.invokeLater(() -> {
            restoreWorkspaceLayout(connection, splitPane, adminMetricsTripleHolder[0], photoSplitHolder[0]);
            JSplitPane outer = adminMetricsTripleHolder[0];
            if (outer != null) {
                syncAdminMetricsSplit(outer);
            }
            syncSidebarWorkspaceSplit(splitPane);
            restoreLastWorkspaceView(user, connection, frame, workspaceContainer, accountActions);
        });
    }

    /**
     * Builds the left sidebar containing role-aware navigation actions.
     *
     * @return sidebar panel
     */
    private static JPanel buildSidebar(
            User user,
            JFrame frame,
            JPanel workspaceContainer,
            Connection connection,
            AccountActions accountActions
    ) {
        JPanel container = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(container);
        container.setBorder(BorderFactory.createEmptyBorder(14, 12, 14, 12));

        JLabel title = new JLabel("Please select a function:", SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(header);
        header.add(title);
        header.add(Box.createVerticalStrut(16));

        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(nav);

        int betweenNavButtons = user.hasAdminRights() ? 5 : 8;

        NavSidebarSelector navSelector = new NavSidebarSelector();
        workspaceContainer.putClientProperty(CLIENT_NAV_SIDEBAR_SELECTOR, navSelector);

        for (NavItem item : getItems(user, connection, accountActions, frame, workspaceContainer)) {
            JButton button = new JButton(item.label);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setBorder(AppUI.newRoundedBorder(8));
            button.setFocusPainted(false);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            if (item.viewBuilder != null) {
                navSelector.registerNavViewButton(item.label, button);
            } else {
                applyNavButtonDefaultStyle(button);
            }
            button.addActionListener(e -> {
                if (item.viewBuilder != null) {
                    try {
                        showView(workspaceContainer, item.label, item.viewBuilder.build());
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(frame, "Unable to open view: " + ex.getMessage(), "View Error", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                }
                new Thread(() -> runAction(item.action), "ims-action-" + item.label.replace(" ", "-")).start();
            });
            nav.add(button);
            nav.add(Box.createVerticalStrut(betweenNavButtons));
        }

        JScrollPane scrollPane = new JScrollPane(nav);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel recentBlock = new JPanel();
        recentBlock.setLayout(new BoxLayout(recentBlock, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(recentBlock);
        recentBlock.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        Runnable refreshRecent = () -> rebuildRecentSidebarBlock(
                recentBlock, frame, workspaceContainer, user, connection, accountActions);
        registerRecentItemRefresher(refreshRecent);
        container.putClientProperty(CLIENT_RECENT_REFRESHER, refreshRecent);
        refreshRecent.run();

        container.add(header, BorderLayout.NORTH);
        container.add(scrollPane, BorderLayout.CENTER);
        container.add(recentBlock, BorderLayout.SOUTH);
        return container;
    }

    private static void rebuildRecentSidebarBlock(
            JPanel recentBlock,
            JFrame frame,
            JPanel workspaceContainer,
            User user,
            Connection connection,
            AccountActions accountActions
    ) {
        recentBlock.removeAll();
        JLabel heading = new JLabel("Recent");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentBlock.add(heading);
        recentBlock.add(Box.createVerticalStrut(6));
        List<RecentItemEntry> snapshot = snapshotRecentItems();
        if (snapshot.isEmpty()) {
            JLabel none = new JLabel("No recent items");
            none.setForeground(AppUI.TEXT_MUTED);
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            recentBlock.add(none);
        } else {
            for (RecentItemEntry entry : snapshot) {
                String label = entry.label() == null || entry.label().isBlank()
                        ? entry.itemCode()
                        : entry.itemCode() + " - " + entry.label();
                JButton recentButton = new JButton(label);
                recentButton.setToolTipText("Open View Items filtered by " + entry.itemCode());
                recentButton.setAlignmentX(Component.LEFT_ALIGNMENT);
                recentButton.setHorizontalAlignment(SwingConstants.LEFT);
                recentButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                styleSecondaryButton(recentButton);
                recentButton.addActionListener(e -> {
                    workspaceContainer.putClientProperty(CLIENT_VIEW_ITEMS_SEARCH_TEXT, entry.itemCode());
                    try {
                        showView(workspaceContainer, "View Items",
                                buildInventoryTablePanel(user, connection, frame, workspaceContainer));
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(frame, "Unable to open view: " + ex.getMessage(),
                                "View Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
                recentBlock.add(recentButton);
                recentBlock.add(Box.createVerticalStrut(4));
            }
        }
        recentBlock.revalidate();
        recentBlock.repaint();
    }

    /**
     * Initial workspace card: blank themed canvas so the center feels empty until a function is chosen.
     * If {@link #WORKSPACE_WELCOME_IMAGE} exists and loads, it is shown centered (e.g. logo); otherwise the panel stays empty.
     */
    private static JPanel buildEmptyWorkspaceCanvas() {
        JPanel canvas = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(canvas);
        if (!Files.isReadable(WORKSPACE_WELCOME_IMAGE)) {
            return canvas;
        }
        try {
            ImageIcon icon = new ImageIcon(WORKSPACE_WELCOME_IMAGE.toAbsolutePath().toString());
            if (icon.getIconWidth() <= 0) {
                return canvas;
            }
            JLabel picture = new JLabel(icon);
            picture.setHorizontalAlignment(SwingConstants.CENTER);
            picture.setVerticalAlignment(SwingConstants.CENTER);
            canvas.add(picture, BorderLayout.CENTER);
        } catch (Exception ignored) {
            // Keep empty canvas on any load failure.
        }
        return canvas;
    }

    /**
     * Returns navigation items for the signed-in role: one flat list in product order; admin-only entries omitted for standard users.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param accountActions account dialogs (backup, password reset)
     * @param frame parent workspace frame
     * @param workspaceContainer workspace card container
     * @return navigation item list
     */
    private static List<NavItem> getItems(
            User user,
            Connection connection,
            AccountActions accountActions,
            JFrame frame,
            JPanel workspaceContainer
    ) {
        List<NavItem> items = new ArrayList<>();
        boolean admin = user.hasAdminRights();

        if (admin) {
            items.add(new NavItem("Add Item", () -> buildAddItemPanel(user, connection, workspaceContainer, frame)));
            items.add(new NavItem("Storage Locations", () -> buildStorageLocationsPanel(connection)));
            items.add(new NavItem("View Items", () -> buildInventoryTablePanel(user, connection, frame, workspaceContainer)));
        } else {
            items.add(new NavItem("View Items", () -> buildInventoryTablePanel(user, connection, frame, workspaceContainer)));
            items.add(new NavItem("Storage Locations", () -> buildStorageLocationsPanel(connection)));
        }
        items.add(new NavItem("Stock by Location", () -> buildStockByLocationPanel(user, connection)));
        items.add(new NavItem("Quick Transfer", () -> buildQuickTransferPanel(user, connection)));
        items.add(new NavItem(VIEW_PO_TRACKING, () -> buildPurchaseOrdersPanel(user, connection, workspaceContainer)));
        if (admin) {
            items.add(new NavItem("Suppliers", () -> buildSuppliersPanel(user, connection)));
        }
        items.add(new NavItem("Receive Order", () -> buildReceiveOrderPanel(user, connection, workspaceContainer)));
        if (!admin) {
            items.add(new NavItem("Low Stock Check", () -> buildLowStockPanel(connection)));
        }
        items.add(new NavItem("Process Sale", () -> buildProcessSalePanel(user, connection, workspaceContainer)));
        items.add(new NavItem("View Sales Transaction", () -> buildSalesPanel(user, connection, workspaceContainer)));
        if (admin) {
            items.add(new NavItem("Write Off Stock", () -> buildWriteOffPanel(user, connection)));
            items.add(new NavItem("Market Prices", () -> buildMarketPricesBulkPanel(user, connection, workspaceContainer)));
            items.add(new NavItem("Change Reorder Triggers", () -> buildAdjustReorderPanel(user, connection, workspaceContainer)));
            items.add(new NavItem("Low Stock Check", () -> buildLowStockPanel(connection)));
            items.add(new NavItem("Generate Reports", () -> buildReportsPanel(user, connection)));
            items.add(new NavItem(VIEW_ADMIN_TOOLS, () -> buildAdministrationToolsPanel(user, connection, frame, accountActions)));
        }
        if (!admin) {
            items.add(new NavItem("Reset Password", () -> buildResetPasswordPanel(user, frame, accountActions)));
        }
        items.add(new NavItem("Log Out", frame::dispose));
        return items;
    }

    /**
     * CardLayout registry keyed by sidebar label — prior component removed before re-add to avoid duplicate hidden cards.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Component> workspaceCardRegistry(JPanel workspaceContainer) {
        Object raw = workspaceContainer.getClientProperty(CLIENT_WORKSPACE_CARD_REGISTRY);
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Component>) map;
        }
        Map<String, Component> created = new HashMap<>();
        workspaceContainer.putClientProperty(CLIENT_WORKSPACE_CARD_REGISTRY, created);
        return created;
    }

    /**
     * Displays a panel in the workspace card container under a stable key (replaces any prior panel for that key).
     *
     * @param workspaceContainer card layout host panel
     * @param key unique card key (matches sidebar nav label)
     * @param panel panel to display
     */
    private static void showView(JPanel workspaceContainer, String key, JPanel panel) {
        AppUI.applyPanelBackground(panel);
        activeWorkspaceViewKey = key;
        Map<String, Component> cards = workspaceCardRegistry(workspaceContainer);
        Component prior = cards.get(key);
        if (prior != null && prior.getParent() == workspaceContainer) {
            workspaceContainer.remove(prior);
        }
        cards.put(key, panel);
        workspaceContainer.add(panel, key);
        CardLayout layout = (CardLayout) workspaceContainer.getLayout();
        layout.show(workspaceContainer, key);
        workspaceContainer.revalidate();
        workspaceContainer.repaint();
        Object selector = workspaceContainer.getClientProperty(CLIENT_NAV_SIDEBAR_SELECTOR);
        if (selector instanceof NavSidebarSelector navSidebarSelector) {
            navSidebarSelector.setSelectedCardKey(key);
        }
        if (!"View Items".equals(key)) {
            restoreMetricsRailToDefault();
        }
        requestMetricsRefresh();
    }

    /** Restores the admin right rail to the financial metrics card after leaving item-specific views. */
    private static void restoreMetricsRailToDefault() {
        AdminMetricsRailHost host = adminMetricsRailHost;
        if (host != null) {
            host.showMetrics();
        }
    }

    /** Default (unselected) sidebar navigation button styling. */
    private static void applyNavButtonDefaultStyle(JButton button) {
        button.setBackground(SIDEBAR_NAV_DEFAULT_BG);
        button.setForeground(SIDEBAR_NAV_DEFAULT_FG);
        button.setBorder(AppUI.newRoundedBorder(8));
    }

    /** Highlights the active sidebar workspace card tab. */
    private static void applyNavButtonSelectedStyle(JButton button) {
        button.setBackground(SIDEBAR_NAV_SELECTED_BG);
        button.setForeground(SIDEBAR_NAV_SELECTED_FG);
        button.setBorder(AppUI.newRoundedBorder(8));
    }

    /**
     * Tracks which workspace card key is active and paints the matching sidebar button darker.
     */
    private static final class NavSidebarSelector {
        private final Map<String, JButton> keyToButton = new HashMap<>();
        private String selectedKey;

        /** Associates a sidebar button with a workspace card key and applies default styling. */
        private void registerNavViewButton(String cardKey, JButton button) {
            keyToButton.put(cardKey, button);
            applyNavButtonDefaultStyle(button);
        }

        /** Updates which sidebar button renders as selected for the visible card key. */
        private void setSelectedCardKey(String key) {
            JButton previous = selectedKey == null ? null : keyToButton.get(selectedKey);
            if (previous != null) {
                applyNavButtonDefaultStyle(previous);
            }
            String nextKey = key != null && keyToButton.containsKey(key) ? key : null;
            selectedKey = nextKey;
            JButton next = nextKey == null ? null : keyToButton.get(nextKey);
            if (next != null) {
                applyNavButtonSelectedStyle(next);
            }
        }

        private boolean isCardActive(String key) {
            return key != null && key.equals(selectedKey);
        }
    }

    /**
     * Swaps the admin right rail between financial metrics and the View Items shelf (JPEG, stats, editable notes/actions).
     */
    private static final class AdminMetricsRailHost {
        private final Connection connection;
        private final String username;
        private final JPanel host;
        private final JLabel titleLabel;
        private final JLabel imageLabel;
        private final JTextArea statsArea;
        private final JTextArea notesArea;
        private final JButton btnUpdateNote;
        private final JButton btnSaveNote;
        private final JButton btnCancelNote;
        private final JButton btnUpdateImage;
        private final Runnable refreshViewItemsIfOpen;

        /** Active SKU when showing the photo card; reused by note/image actions. */
        private String selectedItemCode;

        private AdminMetricsRailHost(
                Connection connection,
                String username,
                JPanel host,
                JLabel titleLabel,
                JLabel imageLabel,
                JTextArea statsArea,
                JTextArea notesArea,
                JButton btnUpdateNote,
                JButton btnSaveNote,
                JButton btnCancelNote,
                JButton btnUpdateImage,
                JButton btnNextWebPhoto,
                Runnable refreshViewItemsIfOpen
        ) {
            this.connection = connection;
            this.username = username;
            this.host = host;
            this.titleLabel = titleLabel;
            this.imageLabel = imageLabel;
            this.statsArea = statsArea;
            this.notesArea = notesArea;
            this.btnUpdateNote = btnUpdateNote;
            this.btnSaveNote = btnSaveNote;
            this.btnCancelNote = btnCancelNote;
            this.btnUpdateImage = btnUpdateImage;
            this.refreshViewItemsIfOpen = refreshViewItemsIfOpen;

            btnUpdateNote.addActionListener(e -> enterNotesEditMode());
            btnSaveNote.addActionListener(e -> commitRailNotes());
            btnCancelNote.addActionListener(e -> cancelNotesEdit());
            btnUpdateImage.addActionListener(e -> chooseAndReplacePhoto());
            btnNextWebPhoto.addActionListener(e -> cycleNextWebPhoto());

            applyNotesViewerStyle();
        }

        private void showMetrics() {
            exitNotesQuiet();
            CardLayout cl = (CardLayout) host.getLayout();
            cl.show(host, "metrics");
            host.revalidate();
            host.repaint();
        }

        private void showItemPhoto(String rawCode) {
            selectedItemCode = rawCode == null ? "" : rawCode.trim();
            if (selectedItemCode.isEmpty()) {
                showMetrics();
                return;
            }
            titleLabel.setText(selectedItemCode);
            exitNotesQuiet();
            refreshStatsPhotoAndNotes();
            CardLayout cl = (CardLayout) host.getLayout();
            cl.show(host, "photo");
            host.revalidate();
            host.repaint();
        }

        private void refreshStatsPhotoAndNotes() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                return;
            }
            try {
                statsArea.setText(buildItemRailStatsText(connection, selectedItemCode));
            } catch (SQLException ex) {
                statsArea.setText("Could not load stats:\n" + ex.getMessage());
            }
            statsArea.setCaretPosition(0);
            refillNotesAreaFromDb();
            updatePhotoRailImageLabel(imageLabel, selectedItemCode);
        }

        /** Reloads the JPEG preview when the on-disk file changed (e.g. after a bulk photo fetch). */
        private void refreshSelectedItemPhoto() {
            refreshStatsPhotoAndNotes();
        }

        private void refillNotesAreaFromDb() {
            try {
                notesArea.setText(fetchInventoryNotes(connection, selectedItemCode));
            } catch (SQLException ex) {
                notesArea.setText("(Could not load notes: " + ex.getMessage() + ")");
            }
            notesArea.setCaretPosition(0);
        }

        private void applyNotesViewerStyle() {
            notesArea.setEditable(false);
            notesArea.setOpaque(false);
            notesArea.setBackground(host.getBackground());
        }

        private void enterNotesEditMode() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                return;
            }
            btnUpdateNote.setEnabled(false);
            btnSaveNote.setVisible(true);
            btnCancelNote.setVisible(true);
            notesArea.setEditable(true);
            notesArea.setOpaque(true);
            notesArea.setBackground(AppUI.INPUT);
            notesArea.requestFocusInWindow();
        }

        /** Exits edit mode without reloading displayed text from the DB. */
        private void exitNotesQuiet() {
            btnUpdateNote.setEnabled(true);
            btnSaveNote.setVisible(false);
            btnCancelNote.setVisible(false);
            applyNotesViewerStyle();
        }

        private void cancelNotesEdit() {
            refillNotesAreaFromDb();
            exitNotesQuiet();
        }

        private void commitRailNotes() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                return;
            }
            try {
                String raw = notesArea.getText();
                if (raw != null && raw.length() > ITEM_NOTES_MAX_CHARS) {
                    JOptionPane.showMessageDialog(
                            host,
                            "Notes must be at most " + ITEM_NOTES_MAX_CHARS + " characters.",
                            "Input",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (!inventoryItemExists(connection, selectedItemCode)) {
                    JOptionPane.showMessageDialog(host, "That item code is no longer in inventory.", "Change note", JOptionPane.WARNING_MESSAGE);
                    cancelNotesEdit();
                    return;
                }
                persistInventoryNotes(connection, selectedItemCode, raw);
                InventoryAudit.logChange(
                        connection,
                        username,
                        selectedItemCode,
                        InventoryAudit.CHANGE_NOTE,
                        0,
                        "RAIL_NOTE_SAVE",
                        raw == null ? "" : raw.trim());
                JOptionPane.showMessageDialog(host, "Note saved.", "Notes", JOptionPane.INFORMATION_MESSAGE);
                exitNotesQuiet();
                refillNotesAreaFromDb();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(host, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void openItemForNotesEdit(String itemCode) {
            if (itemCode == null || itemCode.isBlank()) {
                return;
            }
            showItemPhoto(itemCode.trim());
            enterNotesEditMode();
        }

        private void chooseAndReplacePhoto() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                return;
            }
            try {
                if (!inventoryItemExists(connection, selectedItemCode)) {
                    JOptionPane.showMessageDialog(host, "That item code is no longer in inventory.", "Photo", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "JPEG images (.jpg, .jpeg)", "jpg", "jpeg"));
                if (fc.showOpenDialog(host) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                Path src = fc.getSelectedFile().toPath();
                copySourceJpegToItemPhoto(src, selectedItemCode);
                ItemPhotoFetcher.clearPhotoCycle(selectedItemCode);
                updatePhotoRailImageLabel(imageLabel, selectedItemCode);
                if (refreshViewItemsIfOpen != null) {
                    refreshViewItemsIfOpen.run();
                }
                JOptionPane.showMessageDialog(host, "Photo updated on disk.", "Photo", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(host, "Photo replace failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(host, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        /** Fetches the next DuckDuckGo image for the selected SKU and replaces its on-disk JPEG. */
        private void cycleNextWebPhoto() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                JOptionPane.showMessageDialog(host, "Select an item first.", "Next web photo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                if (!inventoryItemExists(connection, selectedItemCode)) {
                    JOptionPane.showMessageDialog(host, "That item code is no longer in inventory.", "Next web photo", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(host, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String itemName = queryInventoryItemDescription(connection, selectedItemCode);
            if (itemName.isEmpty()) {
                JOptionPane.showMessageDialog(
                        host,
                        "This item has no description to search with.",
                        "Next web photo",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            final String code = selectedItemCode;
            Window window = SwingUtilities.getWindowAncestor(host);
            if (window != null) {
                window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
            new Thread(() -> {
                boolean saved;
                String error = null;
                try {
                    saved = ItemPhotoFetcher.saveNextWebPhoto(code, itemName);
                } catch (Exception ex) {
                    saved = false;
                    error = ex.getMessage();
                }
                final boolean ok = saved;
                final String err = error;
                SwingUtilities.invokeLater(() -> {
                    if (window != null) {
                        window.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                    if (err != null) {
                        JOptionPane.showMessageDialog(
                                host,
                                "Photo search failed:\n" + err,
                                "Next web photo",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!ok) {
                        JOptionPane.showMessageDialog(
                                host,
                                "No more web images found for:\n" + itemName,
                                "Next web photo",
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    invalidateViewItemThumbCache(code);
                    updatePhotoRailImageLabel(imageLabel, code);
                    if (refreshViewItemsIfOpen != null) {
                        refreshViewItemsIfOpen.run();
                    }
                });
            }, "ims-photo-cycle").start();
        }
    }

    /** Summary metrics for View Items rail (notes are edited in their own pane). */
    private static String buildItemRailStatsText(Connection connection, String itemCode) throws SQLException {
        int stock = 0;
        int reorderTrigger = 0;
        Double marketPrice = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Stock`, `ReOrder Trigger`, `Market Price` FROM inventory WHERE `Item Code` = ?"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stock = rs.getInt("Stock");
                    reorderTrigger = rs.getInt("ReOrder Trigger");
                    double mp = rs.getDouble("Market Price");
                    marketPrice = rs.wasNull() ? null : mp;
                }
            }
        }
        long soldQty = 0;
        double sumRev = 0;
        double sumCost = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(SUM(`Amount`),0), COALESCE(SUM(`Total Price`),0), COALESCE(SUM(`Total Cost`),0) "
                        + "FROM sales WHERE `Item Code` = ?"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    soldQty = rs.getLong(1);
                    sumRev = rs.getDouble(2);
                    sumCost = rs.getDouble(3);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Current Stock:\n").append(stock).append("\n\n");
        if (reorderTrigger > 0) {
            sb.append("Reorder trigger:\n").append(reorderTrigger);
            if (isLowStockSku(stock, reorderTrigger)) {
                sb.append("  (low stock)");
            }
            sb.append("\n\n");
        }
        Double fifoUnitCost = InventoryFifo.weightedAverageFifoUnitCost(connection, itemCode);
        if (fifoUnitCost != null) {
            sb.append("Avg FIFO unit cost:\n").append(formatUsdMoney(fifoUnitCost)).append("\n\n");
        }
        if (marketPrice != null) {
            sb.append("Market price:\n").append(formatUsdMoney(marketPrice)).append("\n\n");
            if (fifoUnitCost != null && fifoUnitCost > 1e-12) {
                double marginPct = ((marketPrice - fifoUnitCost) / fifoUnitCost) * 100.0;
                sb.append("Unrealized margin:\n")
                        .append(String.format(Locale.US, "%.1f%%", marginPct))
                        .append(" (")
                        .append(formatUsdMoney(marketPrice - fifoUnitCost))
                        .append(" per unit)\n\n");
            }
        }
        sb.append("Amount Sold:\n").append(soldQty).append("\n\n");
        if (soldQty > 0) {
            double avgPurchase = sumCost / soldQty;
            double avgSale = sumRev / soldQty;
            sb.append("Average Purchase Price:\n").append(formatUsdMoney(avgPurchase)).append("\n\n");
            sb.append("Average Sale Price:\n").append(formatUsdMoney(avgSale)).append("\n\n");
        } else {
            sb.append("Average Purchase Price:\n—\n\n");
            sb.append("Average Sale Price:\n—\n\n");
        }
        sb.append("Total Revenue:\n").append(formatUsdMoney(sumRev)).append("\n\n");
        sb.append("Total P/L:\n").append(formatUsdMoney(sumRev - sumCost));
        return sb.toString();
    }

    /** Loads {@code Inventory.Notes} text for an item code. */
    private static String fetchInventoryNotes(Connection connection, String itemCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(`Notes`, '') AS n FROM inventory WHERE `Item Code` = ? LIMIT 1"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Objects.toString(rs.getString("n"), "") : "";
            }
        }
    }

    /** Persists trimmed notes; empty clears the column. */
    private static void persistInventoryNotes(Connection connection, String itemCode, String rawNotesText)
            throws SQLException {
        String t = rawNotesText == null ? "" : rawNotesText.trim();
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE inventory SET `Notes` = ? WHERE `Item Code` = ?")) {
            if (t.isEmpty()) {
                ps.setNull(1, java.sql.Types.VARCHAR);
            } else {
                ps.setString(1, t);
            }
            ps.setString(2, itemCode);
            ps.executeUpdate();
        }
    }

    /** Refreshes JPEG preview on an image label for the rail. */
    private static void updatePhotoRailImageLabel(JLabel imageLabel, String itemCode) {
        Path p = itemImagePath(itemCode);
        ImageIcon icon = loadScaledItemPhotoIcon(p, ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H);
        if (icon != null) {
            imageLabel.setIcon(icon);
            imageLabel.setText(null);
            imageLabel.setForeground(AppUI.TEXT);
        } else {
            imageLabel.setIcon(null);
            imageLabel.setForeground(AppUI.TEXT_MUTED);
            imageLabel.setText("<html><center>No JPEG on file for this item.<br>"
                    + "<span style='font-size:11px'>item_images/" + itemCode + ".jpeg</span></center></html>");
        }
    }
    /**
     * Builds a generic launcher panel for simple one-click actions.
     *
     * @param title panel title
     * @param description supporting text
     * @param action action to run
     * @return launcher panel
     */
    private static JPanel buildLauncherPanel(String title, String description, CheckedAction action) {
        JPanel panel = buildFormPanel(title);
        JPanel content = buildSectionPanel();

        JLabel heading = buildSectionTitle(title);
        JLabel desc = buildSectionText(description);

        JButton runButton = new JButton("Run " + title);
        AppUI.stylePrimaryButton(runButton);
        runButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        runButton.addActionListener(e -> new Thread(() -> runAction(action), "ims-runner").start());

        content.add(heading);
        content.add(Box.createVerticalStrut(8));
        content.add(desc);
        content.add(Box.createVerticalStrut(14));
        content.add(runButton);
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    /** Ensures {@link #ITEM_IMAGES_DIR} exists on disk (no-op when already present). */
    private static void ensureItemImagesDir() throws IOException {
        Files.createDirectories(ITEM_IMAGES_DIR);
    }

    /**
     * Downloads item JPEGs on a worker thread (see {@link ItemPhotoFetcher}), then refreshes the photo rail
     * and View Items when that screen is open.
     */
    private static void startItemPhotoFetchTask(
            Component parent,
            JPanel workspaceContainer,
            User user,
            JFrame frame,
            Connection connection,
            boolean replaceExisting
    ) {
        if (user != null) {
            ensureAdmin(user, "Fetch item photos");
        }
        Window window = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        if (window != null) {
            window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        new Thread(() -> {
            ItemPhotoFetcher.FetchResult result;
            try {
                result = ItemPhotoFetcher.fetchAll(replaceExisting);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (window != null) {
                        window.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                    JOptionPane.showMessageDialog(
                            parent,
                            "Photo fetch failed:\n" + ex.getMessage(),
                            "Fetch photos",
                            JOptionPane.ERROR_MESSAGE);
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (window != null) {
                    window.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
                JOptionPane.showMessageDialog(
                        parent,
                        "Photos saved: " + result.saved()
                                + "\nSkipped (already on file): " + result.skipped()
                                + "\nFailed: " + result.failed(),
                        "Fetch photos",
                        result.failed() > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                if (result.saved() > 0) {
                    clearViewItemThumbCache();
                }
                AdminMetricsRailHost rail = adminMetricsRailHost;
                if (rail != null) {
                    rail.refreshSelectedItemPhoto();
                }
                refreshViewItemsIfOpen(workspaceContainer, user, connection, frame);
            });
        }, "ims-photo-fetch").start();
    }

    private static void refreshViewItemsIfOpen(
            JPanel workspaceContainer, User user, Connection connection, JFrame frame
    ) {
        if (workspaceContainer == null || user == null || connection == null) {
            return;
        }
        Object selector = workspaceContainer.getClientProperty(CLIENT_NAV_SIDEBAR_SELECTOR);
        if (!(selector instanceof NavSidebarSelector nav) || !nav.isCardActive("View Items")) {
            return;
        }
        try {
            showView(workspaceContainer, "View Items", buildInventoryTablePanel(user, connection, frame, workspaceContainer));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(frame, "Could not refresh View Items: " + ex.getMessage());
        }
    }

    /** Canonical on-disk JPEG path for an item code ({@code .jpeg}). */
    private static Path itemImagePath(String itemCode) {
        return ITEM_IMAGES_DIR.resolve(itemCode + ".jpeg");
    }

    /** Accepts {@code .jpg}/{@code .jpeg} filenames (case-insensitive). */
    private static boolean isJpegFileName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpeg") || n.endsWith(".jpg");
    }

    /**
     * Validates and copies {@code source} into {@code item_images/&lt;itemCode&gt;.jpeg}.
     *
     * @throws IOException when the source is unreadable or not a valid JPEG
     */
    private static void copySourceJpegToItemPhoto(Path source, String itemCode) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Photo path is not a readable file.");
        }
        if (!isJpegFileName(source.getFileName().toString())) {
            throw new IOException("Only JPEG files (.jpg or .jpeg) are allowed.");
        }
        if (ImageIO.read(source.toFile()) == null) {
            throw new IOException("File is not a valid JPEG image.");
        }
        ensureItemImagesDir();
        Files.copy(source, itemImagePath(itemCode), StandardCopyOption.REPLACE_EXISTING);
        invalidateViewItemThumbCache(itemCode);
    }

    private static String viewItemThumbCacheKey(String itemCode, int boxPx) {
        return itemCode + "\u0001" + boxPx;
    }

    private static void invalidateViewItemThumbCache(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return;
        }
        String prefix = itemCode + "\u0001";
        viewItemThumbCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static void clearViewItemThumbCache() {
        viewItemThumbCache.clear();
    }

    /**
     * Loads a View Items card thumbnail, using an in-memory cache (including negative hits for missing files).
     */
    private static ImageIcon loadCachedViewItemThumbIcon(String itemCode, int boxPx) {
        String key = viewItemThumbCacheKey(itemCode, boxPx);
        Object cached = viewItemThumbCache.get(key);
        if (cached == VIEW_ITEM_THUMB_CACHE_MISS) {
            return null;
        }
        if (cached instanceof ImageIcon icon) {
            return icon;
        }
        ImageIcon loaded = loadScaledItemPhotoIcon(itemImagePath(itemCode), boxPx, boxPx);
        viewItemThumbCache.put(key, loaded == null ? VIEW_ITEM_THUMB_CACHE_MISS : loaded);
        return loaded;
    }

    /**
     * Loads an item JPEG and returns an icon scaled down to fit a square box, preserving aspect ratio.
     *
     * @return icon, or {@code null} if the file is missing or not a readable image
     */
    private static ImageIcon loadScaledItemPhotoIcon(Path path, int maxSide) {
        return loadScaledItemPhotoIcon(path, maxSide, maxSide);
    }

    /**
     * Loads an item JPEG and returns an icon whose size fits inside {@code maxWidth}×{@code maxHeight} without
     * upscaling (aspect ratio preserved). Missing or invalid files yield {@code null}.
     */
    private static ImageIcon loadScaledItemPhotoIcon(Path path, int maxWidth, int maxHeight) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            BufferedImage raw = ImageIO.read(path.toFile());
            if (raw == null) {
                return null;
            }
            int w = raw.getWidth();
            int h = raw.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            double scale = Math.min(1.0, Math.min((double) maxWidth / w, (double) maxHeight / h));
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            Image scaled = raw.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
            BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            g2.drawImage(scaled, 0, 0, null);
            g2.dispose();
            return new ImageIcon(out);
        } catch (IOException e) {
            return null;
        }
    }

    /** Appends label/value pair rows to the item detail dialog grid. */
    private static void addDetailFieldRow(JPanel grid, String label, String value) {
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        JLabel v = new JLabel(value == null ? "" : value);
        grid.add(l);
        grid.add(v);
    }

    /** Modal item summary with optional JPEG; admins can replace the photo. */
    private static void showItemDetailDialog(Component parent, User user, Connection connection, String itemCode) {
        String itemName;
        int stock;
        int onOrder;
        String supplier;
        int leadVal;
        boolean leadNull;
        int reorder;
        double marketUnit;
        boolean marketNull;
        double latestLayerUnit;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Item Name`, `Stock`, `On Order`, COALESCE(`Supplier`, '') AS sup, "
                        + "`Lead Time`, `ReOrder Trigger`, `Market Price` FROM inventory WHERE `Item Code` = ?"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(parent, "Item not found: " + itemCode);
                    return;
                }
                itemName = rs.getString("Item Name");
                stock = rs.getInt("Stock");
                onOrder = rs.getInt("On Order");
                supplier = rs.getString("sup");
                leadVal = rs.getInt("Lead Time");
                leadNull = rs.wasNull();
                reorder = rs.getInt("ReOrder Trigger");
                marketUnit = rs.getDouble("Market Price");
                marketNull = rs.wasNull();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Unable to load item: " + ex.getMessage());
            return;
        }
        try {
            latestLayerUnit = InventoryFifo.latestRecordedUnitCost(connection, itemCode);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Unable to load cost layers: " + ex.getMessage());
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner);
        dialog.setTitle("Item: " + itemCode);
        dialog.setModal(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        AppUI.applyPanelBackground(root);

        JLabel photoLabel = new JLabel(" ", SwingConstants.CENTER);
        photoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        photoLabel.setVerticalAlignment(SwingConstants.CENTER);
        photoLabel.setForeground(AppUI.TEXT_MUTED);

        JPanel fields = new JPanel(new GridLayout(0, 2, 10, 8));
        AppUI.applyPanelBackground(fields);
        addDetailFieldRow(fields, "Item code:", itemCode);
        addDetailFieldRow(fields, "Item name:", itemName == null ? "" : itemName);
        addDetailFieldRow(fields, "Supplier:", supplier == null || supplier.isEmpty() ? "—" : supplier);
        addDetailFieldRow(fields, "Stock:", String.valueOf(stock));
        addDetailFieldRow(fields, "On order:", String.valueOf(onOrder));
        addDetailFieldRow(fields, "Lead time (days):", leadNull ? "—" : String.valueOf(leadVal));
        addDetailFieldRow(fields, "Reorder trigger:", String.valueOf(reorder));
        addDetailFieldRow(fields, "Market price (ea.):", marketNull ? "—" : formatUsdMoney(marketUnit));
        addDetailFieldRow(fields, "Latest receipt unit cost (layers):",
                Double.isNaN(latestLayerUnit) ? "—" : formatUsdMoney(latestLayerUnit));

        Runnable reloadPhoto = () -> {
            Path p = itemImagePath(itemCode);
            ImageIcon icon = loadScaledItemPhotoIcon(p, ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H);
            if (icon != null) {
                photoLabel.setIcon(icon);
                photoLabel.setText(null);
            } else {
                photoLabel.setIcon(null);
                photoLabel.setText("<html><center>No photo on file.<br>JPEG path: <code>item_images/" + itemCode + ".jpeg</code></center></html>");
            }
        };

        reloadPhoto.run();

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        AppUI.applyPanelBackground(south);
        JButton close = new JButton("Close");
        styleSecondaryButton(close);
        close.addActionListener(e -> dialog.dispose());
        south.add(close);
        if (user != null && user.hasAdminRights()) {
            JButton changePhoto = new JButton("Change photo…");
            styleSecondaryButton(changePhoto);
            changePhoto.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileNameExtensionFilter("JPEG images (.jpg, .jpeg)", "jpg", "jpeg"));
                if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                    try {
                        copySourceJpegToItemPhoto(fc.getSelectedFile().toPath(), itemCode);
                        reloadPhoto.run();
                        photoLabel.revalidate();
                        JOptionPane.showMessageDialog(dialog, "Photo updated.");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Photo error", JOptionPane.WARNING_MESSAGE);
                    }
                }
            });
            south.add(changePhoto);
        }

        root.add(photoLabel, BorderLayout.NORTH);
        root.add(fields, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);

        JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(true);
        shell.setBackground(AppUI.BACKGROUND);
        if (AppUI.usesEmbeddedTitleBar()) {
            shell.add(AppUI.createApplicationTitleBar("Item: " + itemCode), BorderLayout.NORTH);
        }
        shell.add(root, BorderLayout.CENTER);
        dialog.setContentPane(shell);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        AppUI.applyWindowChrome(dialog);
        AppUI.styleWindow(dialog);
        dialog.setVisible(true);
    }

    /** One row in the View Items card shelf (stock or on-order must be &gt; 0). */
    private record ViewItemShelfRow(
            String itemCode,
            String itemName,
            int stock,
            int onOrder,
            int reorderTrigger,
            Double marketPrice,
            Double unrealizedMarginPercent,
            boolean staleMarketPrice,
            boolean hasPhoto
    ) {
    }

    /**
     * Builds the View Items shelf: card grid for SKUs with stock on hand or units on order.
     * Each card shows the item JPEG (or a placeholder), code, stock, on-order qty, and market price.
     * Administrators: clicking a card swaps the right rail to that item's photo and stats.
     * Double-click a card for the full item detail dialog.
     *
     * @param user         signed-in user (for admin-only photo change in detail dialog)
     * @param connection   active database connection
     * @return inventory shelf panel
     * @throws SQLException when query fails
     */
    private static JPanel buildInventoryTablePanel(
            User user, Connection connection, JFrame frame, JPanel workspaceContainer
    ) throws SQLException {
        JPanel panel = buildFormPanel("Inventory Items");
        JPanel centerWrap = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(centerWrap);

        JPanel intro = buildSectionPanel();
        intro.add(buildSectionText(
                "Only items with stock on hand or units on order are listed. "
                        + "Double-click a card for full item details. "
                        + "Use ⌘/Ctrl+1–9 for sidebar shortcuts."));
        if (user.hasAdminRights()) {
            intro.add(Box.createVerticalStrut(10));
            JPanel photoActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            photoActions.setOpaque(false);
            photoActions.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton fetchMissing = new JButton("Fetch missing photos");
            AppUI.stylePrimaryButton(fetchMissing);
            fetchMissing.setToolTipText(
                    "Download product images from the web using each item's description (skips items that already have a JPEG).");
            fetchMissing.addActionListener(e -> startItemPhotoFetchTask(
                    frame, workspaceContainer, user, frame, connection, false));
            JButton refetchAll = new JButton("Re-fetch all photos");
            styleSecondaryButton(refetchAll);
            refetchAll.setToolTipText("Replace every item_images/<code>.jpeg file (may take a minute).");
            refetchAll.addActionListener(e -> {
                int ok = JOptionPane.showConfirmDialog(
                        frame,
                        "Re-download photos for every inventory item?\nExisting JPEG files will be replaced.",
                        "Re-fetch all photos",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (ok == JOptionPane.OK_OPTION) {
                    startItemPhotoFetchTask(frame, workspaceContainer, user, frame, connection, true);
                }
            });
            photoActions.add(fetchMissing);
            photoActions.add(refetchAll);
            intro.add(photoActions);
        }
        centerWrap.add(intro, BorderLayout.NORTH);

        List<ViewItemShelfRow> allRows = loadViewItemShelfRows(connection);
        Set<String> favouriteCodes = readFavouriteItemCodes(connection, user.getUsername());
        Object prefillSearch = workspaceContainer.getClientProperty(CLIENT_VIEW_ITEMS_SEARCH_TEXT);
        workspaceContainer.putClientProperty(CLIENT_VIEW_ITEMS_SEARCH_TEXT, null);

        JTextField searchField = new JTextField(24);
        styleInput(searchField);
        if (prefillSearch instanceof String s) {
            searchField.setText(s);
        }

        JComboBox<String> smartFilter = new JComboBox<>(VIEW_ITEMS_FILTER_OPTIONS);
        styleComboMatchInputRow(smartFilter);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        AppUI.applyPanelBackground(filterRow);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterRow.add(new JLabel("Search code or name"));
        filterRow.add(searchField);
        filterRow.add(new JLabel("Filter"));
        filterRow.add(smartFilter);
        final List<ViewItemShelfRow>[] filteredRowsHolder = new List[]{new ArrayList<>()};
        JButton exportCsv = new JButton("Export CSV");
        styleSecondaryButton(exportCsv);
        exportCsv.addActionListener(e -> exportViewItemsCsv(frame, filteredRowsHolder[0]));
        filterRow.add(exportCsv);
        JLabel matchCount = new JLabel(" ");
        matchCount.setForeground(AppUI.TEXT_MUTED);
        filterRow.add(matchCount);
        intro.add(Box.createVerticalStrut(8));
        intro.add(filterRow);

        JPanel gridHost = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(gridHost);
        final JPanel[] selectedCard = new JPanel[1];
        final Runnable[] rebuildGridHolder = new Runnable[1];

        Runnable rebuildGrid = () -> {
            String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
            String filter = Objects.toString(smartFilter.getSelectedItem(), VIEW_ITEMS_FILTER_ALL);
            List<ViewItemShelfRow> filtered = new ArrayList<>();
            for (ViewItemShelfRow row : allRows) {
                if (!q.isEmpty()) {
                    String code = row.itemCode().toLowerCase(Locale.ROOT);
                    String name = row.itemName().toLowerCase(Locale.ROOT);
                    if (!code.contains(q) && !name.contains(q)) {
                        continue;
                    }
                }
                if (!matchesViewItemsSmartFilter(row, filter, favouriteCodes)) {
                    continue;
                }
                filtered.add(row);
            }
            sortViewItemsShelfRows(filtered, filter, favouriteCodes);
            filteredRowsHolder[0] = new ArrayList<>(filtered);
            selectedCard[0] = null;
            gridHost.removeAll();
            if (filtered.isEmpty()) {
                gridHost.add(buildSectionText(
                        allRows.isEmpty()
                                ? "No items with stock on hand or on order."
                                : "No items match the current search and filter."), BorderLayout.CENTER);
                matchCount.setText(allRows.isEmpty() ? "" : "0 shown");
            } else {
                JPanel scrollBody = new JPanel(new GridBagLayout());
                scrollBody.setOpaque(true);
                AppUI.applyPanelBackground(scrollBody);
                populateViewItemsGrid(
                        scrollBody, user, connection, workspaceContainer, filtered, selectedCard,
                        favouriteCodes,
                        () -> {
                            try {
                                favouriteCodes.clear();
                                favouriteCodes.addAll(readFavouriteItemCodes(connection, user.getUsername()));
                            } catch (SQLException ex) {
                                JOptionPane.showMessageDialog(panel, "Could not reload favourites: " + ex.getMessage(),
                                        "View Items", JOptionPane.ERROR_MESSAGE);
                            }
                            rebuildGridHolder[0].run();
                        });
                JScrollPane scroll = new JScrollPane(scrollBody,
                        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scroll.setBorder(AppUI.newRoundedBorder(8));
                scroll.getViewport().setBackground(scrollBody.getBackground());
                scroll.getVerticalScrollBar().setUnitIncrement(16);
                scroll.setPreferredSize(new Dimension(1080, Math.min(MAIN_FRAME_BASE_H - 260, 520)));
                gridHost.add(scroll, BorderLayout.CENTER);
                matchCount.setText(filtered.size() + " of " + allRows.size() + " shown");
            }
            gridHost.revalidate();
            gridHost.repaint();
        };
        rebuildGridHolder[0] = rebuildGrid;

        Timer searchDebounceTimer = new Timer(VIEW_ITEMS_SEARCH_DEBOUNCE_MS, e -> rebuildGrid.run());
        searchDebounceTimer.setRepeats(false);

        DocumentListener filterListener = new DocumentListener() {
            private void schedule() {
                searchDebounceTimer.restart();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                schedule();
            }
        };
        searchField.getDocument().addDocumentListener(filterListener);
        smartFilter.addActionListener(e -> {
            searchDebounceTimer.stop();
            rebuildGrid.run();
        });

        rebuildGrid.run();
        centerWrap.add(gridHost, BorderLayout.CENTER);
        panel.add(centerWrap, BorderLayout.CENTER);
        return panel;
    }

    private static List<ViewItemShelfRow> loadViewItemShelfRows(Connection connection) throws SQLException {
        List<ViewItemShelfRow> rows = new ArrayList<>();
        int staleDays = readStaleMarketPriceDays(connection);
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT i.`Item Code`,
                       i.`Item Name`,
                       i.`Stock`,
                       i.`On Order`,
                       i.`ReOrder Trigger`,
                       i.`Market Price`,
                       i.market_price_updated_at,
                       fifo.avg_unit_cost
                FROM inventory i
                LEFT JOIN (
                    SELECT item_code,
                           SUM(CAST(qty_remaining AS REAL) * unit_cost)
                               / SUM(CAST(qty_remaining AS REAL)) AS avg_unit_cost
                    FROM inventory_cost_layers
                    WHERE qty_remaining > 0
                    GROUP BY item_code
                ) fifo ON fifo.item_code = i.`Item Code`
                WHERE i.`Stock` > 0 OR i.`On Order` > 0
                ORDER BY i.`Item Code` ASC
                """
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("Item Code");
                    String name = Objects.toString(rs.getString("Item Name"), "");
                    int stock = rs.getInt("Stock");
                    int onOrder = rs.getInt("On Order");
                    int reorder = rs.getInt("ReOrder Trigger");
                    double mp = rs.getDouble("Market Price");
                    Double market = rs.wasNull() ? null : mp;
                    double avgRaw = rs.getDouble("avg_unit_cost");
                    Double avgCost = rs.wasNull() ? null : avgRaw;
                    Double margin = computeUnrealizedMarginPercent(avgCost, market);
                    String updatedAt = rs.getString("market_price_updated_at");
                    boolean stale = market == null
                            || (stock > 0 && (updatedAt == null || updatedAt.isBlank()
                            || sqlDaysSinceDisplayDate(updatedAt) > staleDays));
                    boolean hasPhoto = viewItemHasReadablePhoto(code);
                    rows.add(new ViewItemShelfRow(
                            code, name, stock, onOrder, reorder, market, margin, stale, hasPhoto));
                }
            }
        }
        return rows;
    }

    private static Double computeUnrealizedMarginPercent(Double avgUnitCost, Double marketPrice) {
        if (avgUnitCost == null || marketPrice == null || avgUnitCost <= 1e-12) {
            return null;
        }
        return ((marketPrice - avgUnitCost) / avgUnitCost) * 100.0;
    }

    private static void populateViewItemsGrid(
            JPanel scrollBody,
            User user,
            Connection connection,
            JPanel workspaceContainer,
            List<ViewItemShelfRow> rows,
            JPanel[] selectedCard,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged
    ) {
        int n = rows.size();
        final int cols = VIEW_ITEM_CARD_COLUMNS;
        int numRows = (n + cols - 1) / cols;

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1.0 / cols;
        gbc.weighty = 0;

        for (int r = 0; r < numRows; r++) {
            gbc.gridy = r;
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                gbc.gridx = c;
                gbc.insets = new Insets(r == 0 ? 2 : 10, 6, 0, 6);

                JPanel slot = null;
                if (idx < n) {
                    ViewItemShelfRow row = rows.get(idx);
                    slot = buildViewItemShelfCard(user, connection, workspaceContainer, row, favouriteCodes, onFavouritesChanged, card -> {
                        if (selectedCard[0] != null && selectedCard[0] != card) {
                            styleViewItemShelfCard(selectedCard[0], false);
                        }
                        selectedCard[0] = card;
                        styleViewItemShelfCard(card, true);
                        notifyAdminItemSelected(row.itemCode());
                        recordRecentItem(row.itemCode(), row.itemName());
                    });
                }

                JPanel cell = new JPanel(new BorderLayout());
                cell.setOpaque(false);
                if (slot != null) {
                    cell.add(slot, BorderLayout.CENTER);
                }
                scrollBody.add(cell, gbc);
            }
        }

        GridBagConstraints glue = new GridBagConstraints();
        glue.gridy = numRows;
        glue.gridx = 0;
        glue.gridwidth = cols;
        glue.weighty = 1.0;
        glue.weightx = 1.0;
        glue.fill = GridBagConstraints.VERTICAL;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        scrollBody.add(spacer, glue);
    }

    /** One View Items card: photo (or placeholder), code, stock, on order, market price. */
    private static JPanel buildViewItemShelfCard(
            User user,
            Connection connection,
            JPanel workspaceContainer,
            ViewItemShelfRow row,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged,
            Consumer<JPanel> onSelect
    ) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        AppUI.markCardSurface(card);
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setOpaque(true);
        card.setBackground(viewItemMarginTint(row.unrealizedMarginPercent()));
        styleViewItemShelfCard(card, false);

        JPanel starRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        starRow.setOpaque(false);
        starRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        starRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        boolean isFavourite = favouriteCodes.contains(row.itemCode());
        JButton starBtn = new JButton(isFavourite ? "\u2605" : "\u2606");
        starBtn.setToolTipText(isFavourite ? "Remove from favourites" : "Add to favourites");
        starBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        starBtn.setFocusPainted(false);
        starBtn.setContentAreaFilled(false);
        starBtn.setForeground(isFavourite ? new Color(0xfbbf24) : AppUI.TEXT_MUTED);
        starBtn.addActionListener(e -> {
            try {
                toggleFavouriteItem(connection, user.getUsername(), row.itemCode());
                onFavouritesChanged.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(card, "Could not update favourite: " + ex.getMessage(),
                        "View Items", JOptionPane.ERROR_MESSAGE);
            }
        });
        starRow.add(starBtn);
        card.add(starRow);

        JComponent photo = buildViewItemPhotoThumb(row.itemCode(), VIEW_ITEM_CARD_PHOTO_PX);
        photo.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(photo);
        card.add(Box.createVerticalStrut(10));

        JLabel codeLabel = new JLabel(row.itemCode(), SwingConstants.CENTER);
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, 13f));
        codeLabel.setForeground(AppUI.TEXT);
        codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(codeLabel);
        card.add(Box.createVerticalStrut(6));

        card.add(viewItemShelfStatLine("In stock:", Integer.toString(row.stock())));
        card.add(Box.createVerticalStrut(2));
        card.add(viewItemShelfStatLine("On order:", Integer.toString(row.onOrder())));
        card.add(Box.createVerticalStrut(2));
        String marketText = row.marketPrice() == null ? "—" : formatUsdMoney(row.marketPrice());
        card.add(viewItemShelfStatLine("Market price:", marketText));
        card.add(Box.createVerticalStrut(2));
        String marginText = row.unrealizedMarginPercent() == null
                ? "—"
                : String.format(Locale.US, "%.1f%%", row.unrealizedMarginPercent());
        card.add(viewItemShelfStatLine("Margin:", marginText));
        if (row.staleMarketPrice()) {
            JLabel stale = new JLabel("Stale price", SwingConstants.CENTER);
            stale.setForeground(new Color(0xfbbf24));
            stale.setFont(stale.getFont().deriveFont(Font.BOLD, 11f));
            stale.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(Box.createVerticalStrut(4));
            card.add(stale);
        }

        JPopupMenu menu = new JPopupMenu();
        if (user.hasAdminRights()) {
            JMenuItem editNote = new JMenuItem("Edit note");
            editNote.addActionListener(e -> openItemNoteEditor(user, connection, card, row.itemCode(), row.itemName()));
            menu.add(editNote);

            JMenuItem setMarketPrice = new JMenuItem("Set market price");
            setMarketPrice.addActionListener(e -> showSetMarketPriceDialog(user, connection, card, row.itemCode()));
            menu.add(setMarketPrice);
        }
        JMenuItem openLowStock = new JMenuItem("Open Low Stock Check");
        openLowStock.addActionListener(e -> {
            try {
                showView(workspaceContainer, "Low Stock Check", buildLowStockPanel(connection));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(card, "Unable to open Low Stock Check: " + ex.getMessage(),
                        "View Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(openLowStock);
        JMenuItem toggleFav = new JMenuItem(isFavourite ? "Remove from favourites" : "Add to favourites");
        toggleFav.addActionListener(e -> {
            try {
                toggleFavouriteItem(connection, user.getUsername(), row.itemCode());
                onFavouritesChanged.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(card, "Could not update favourite: " + ex.getMessage(),
                        "View Items", JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(toggleFav);

        card.addMouseListener(new MouseAdapter() {
            private void maybeShowMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    onSelect.accept(card);
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getSource() == starBtn) {
                    return;
                }
                if (e.isPopupTrigger()) {
                    maybeShowMenu(e);
                    return;
                }
                onSelect.accept(card);
                if (e.getClickCount() >= 2) {
                    showItemDetailDialog(card, user, connection, row.itemCode());
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }
        });

        return card;
    }

    private static JLabel viewItemShelfStatLine(String label, String value) {
        JLabel line = new JLabel("<html><center><span style='color:#a1a1a1'>" + label + "</span> "
                + "<b><span style='color:#fafafa'>" + value + "</span></b></center></html>", SwingConstants.CENTER);
        line.setFont(line.getFont().deriveFont(Font.PLAIN, 12f));
        line.setAlignmentX(Component.CENTER_ALIGNMENT);
        return line;
    }

    private static void styleViewItemShelfCard(JPanel card, boolean selected) {
        Color border = selected ? AppUI.PRIMARY : AppUI.BORDER;
        int width = selected ? 2 : 1;
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, width),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
    }

    private static Color viewItemMarginTint(Double marginPct) {
        if (marginPct == null) {
            return AppUI.SURFACE;
        }
        if (marginPct >= 10.0) {
            return new Color(0x1a2a1f);
        }
        if (marginPct < -5.0) {
            return new Color(0x2b1a1a);
        }
        return new Color(0x2c2619);
    }

    private static void openItemNoteEditor(
            User user,
            Connection connection,
            Component parent,
            String itemCode,
            String itemName
    ) {
        AdminMetricsRailHost rail = adminMetricsRailHost;
        if (rail != null) {
            notifyAdminItemSelected(itemCode);
            rail.openItemForNotesEdit(itemCode);
            return;
        }
        try {
            String current = fetchInventoryNotes(connection, itemCode);
            JTextArea editor = new JTextArea(current, 10, 34);
            editor.setLineWrap(true);
            editor.setWrapStyleWord(true);
            JScrollPane scroll = new JScrollPane(editor);
            int ok = JOptionPane.showConfirmDialog(
                    parent,
                    scroll,
                    "Edit note - " + itemCode + (itemName == null || itemName.isBlank() ? "" : " (" + itemName + ")"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
            String raw = editor.getText();
            if (raw != null && raw.length() > ITEM_NOTES_MAX_CHARS) {
                JOptionPane.showMessageDialog(parent,
                        "Notes must be at most " + ITEM_NOTES_MAX_CHARS + " characters.",
                        "Input",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            persistInventoryNotes(connection, itemCode, raw);
            InventoryAudit.logChange(connection, user.getUsername(), itemCode,
                    InventoryAudit.CHANGE_NOTE, 0, "VIEW_ITEMS_NOTE_EDIT",
                    raw == null ? "" : raw.trim());
            JOptionPane.showMessageDialog(parent, "Note saved.", "Notes", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void showSetMarketPriceDialog(User user, Connection connection, Component parent, String itemCode) {
        String raw = JOptionPane.showInputDialog(parent, "Enter market price for " + itemCode + ":", "Set market price",
                JOptionPane.PLAIN_MESSAGE);
        if (raw == null) {
            return;
        }
        try {
            Double parsed = parseOptionalMarketPriceInput(raw);
            if (parsed == null) {
                JOptionPane.showMessageDialog(parent, "Market price is required.", "Input", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Double previous = null;
            try (PreparedStatement sel = connection.prepareStatement(
                    "SELECT `Market Price` FROM inventory WHERE `Item Code` = ?")) {
                sel.setString(1, itemCode);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        double v = rs.getDouble(1);
                        previous = rs.wasNull() ? null : v;
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE inventory SET `Market Price` = ? WHERE `Item Code` = ?")) {
                ps.setDouble(1, parsed);
                ps.setString(2, itemCode);
                ps.executeUpdate();
            }
            InventoryAudit.touchMarketPriceUpdated(connection, itemCode);
            String beforeText = previous == null ? "null" : String.format(Locale.US, "%.4f", previous);
            String afterText = String.format(Locale.US, "%.4f", parsed);
            InventoryAudit.logChange(connection, user.getUsername(), itemCode,
                    InventoryAudit.CHANGE_MARKET_PRICE, 0, "VIEW_ITEMS_CONTEXT_MENU",
                    "from=" + beforeText + " to=" + afterText);
            refreshActiveMetricsStripNow();
            notifyAdminItemSelected(itemCode);
            JOptionPane.showMessageDialog(parent, "Market price updated.", "Market price",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Input", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void exportViewItemsCsv(Component parent, List<ViewItemShelfRow> rows) {
        if (rows == null || rows.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No filtered rows to export.", "Export CSV",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose export folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dir = chooser.getSelectedFile().toPath();
        String base = sanitizeFileName("view_items_filtered_" + System.currentTimeMillis());
        Path csv = dir.resolve(base + ".csv");
        try {
            Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(csv)) {
                writer.write("Item Code,Item Name,Stock,On Order,Market Price,Margin %");
                writer.newLine();
                for (ViewItemShelfRow row : rows) {
                    String market = row.marketPrice() == null ? "" : String.format(Locale.US, "%.2f", row.marketPrice());
                    String margin = row.unrealizedMarginPercent() == null ? ""
                            : String.format(Locale.US, "%.2f", row.unrealizedMarginPercent());
                    writer.write(csvCell(row.itemCode()) + "," + csvCell(row.itemName()) + ","
                            + row.stock() + "," + row.onOrder() + "," + market + "," + margin);
                    writer.newLine();
                }
            }
            JOptionPane.showMessageDialog(parent, "Export complete:\n" + csv, "Export CSV",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Could not export CSV: " + ex.getMessage(), "Export CSV",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csvCell(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    /** JPEG thumbnail for View Items cards, or a bordered “?” placeholder when no image is saved. */
    private static JComponent buildViewItemPhotoThumb(String itemCode, int boxPx) {
        ImageIcon icon = loadCachedViewItemThumbIcon(itemCode, boxPx);
        if (icon != null) {
            JLabel label = new JLabel(icon, SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(boxPx, boxPx));
            label.setMinimumSize(new Dimension(boxPx, boxPx));
            label.setMaximumSize(new Dimension(boxPx, boxPx));
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppUI.BORDER, 1),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            return label;
        }
        return buildViewItemPhotoPlaceholder(boxPx);
    }

    private static JLabel buildViewItemPhotoPlaceholder(int boxPx) {
        JLabel label = new JLabel("?", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 42f));
        label.setForeground(AppUI.TEXT_MUTED);
        label.setPreferredSize(new Dimension(boxPx, boxPx));
        label.setMinimumSize(new Dimension(boxPx, boxPx));
        label.setMaximumSize(new Dimension(boxPx, boxPx));
        label.setOpaque(true);
        label.setBackground(AppUI.SURFACE_ELEVATED);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppUI.BORDER, 1),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return label;
    }

    /** Updates the admin right rail for a selected inventory SKU (or restores metrics when code is blank). */
    private static void notifyAdminItemSelected(String itemCode) {
        AdminMetricsRailHost rail = adminMetricsRailHost;
        if (rail == null) {
            return;
        }
        if (itemCode == null || itemCode.isEmpty()) {
            rail.showMetrics();
        } else {
            recordRecentItem(itemCode, "");
            rail.showItemPhoto(itemCode);
        }
    }

    /** Builds the pending orders table view panel. */
    private static JPanel buildPendingOrdersPanel(Connection connection) throws SQLException {
        return buildFilterableTablePanel(
                "Pending Orders",
                new String[]{"Item Code", "Item Description", "Amount", "Purchase Price", "Remaining Payment", "Purchased From", "Reference", "Date"},
                """
                SELECT po.`Item Code`,
                       COALESCE(inv.`Item Name`, '') AS `Item Description`,
                       po.`Amount`,
                       po.`Purchase Price`,
                       po.`Remaining Payment`,
                       po.`Purchased From`,
                       po.`Reference`,
                       po.`Date`
                FROM pendingOrders po
                LEFT JOIN inventory inv ON inv.`Item Code` = po.`Item Code`
                """,
                connection
        );
    }

    /**
     * Builds low-stock and replenishment recommendation view.
     *
     * @param connection active database connection
     * @return replenishment panel
     * @throws SQLException when query fails
     */
    private static JPanel buildLowStockPanel(Connection connection) throws SQLException {
        return buildFilterableTablePanel(
                "Low Stock - Items will show here if their stock levels fall below the re order trigger",
                new String[]{
                        "Item Code",
                        "Item Name",
                        "Supplier",
                        "Lead Time",
                        "Stock",
                        "On Order",
                        "Recent Sales (14d)",
                        "Suggested Reorder Qty"
                },
                """
                SELECT
                    i.`Item Code` AS `Item Code`,
                    i.`Item Name` AS `Item Name`,
                    COALESCE(i.`Supplier`, 'N/A') AS `Supplier`,
                    CASE
                        WHEN i.`Lead Time` IS NULL THEN 'N/A'
                        ELSE CAST(i.`Lead Time` AS TEXT)
                    END AS `Lead Time`,
                    i.`Stock` AS `Stock`,
                    i.`On Order` AS `On Order`,
                    COALESCE(s.recent_sales, 0) AS `Recent Sales (14d)`,
                    (
                        CASE
                            WHEN (i.`ReOrder Trigger` - (i.`Stock` + i.`On Order`)) > 0
                            THEN (i.`ReOrder Trigger` - (i.`Stock` + i.`On Order`))
                            ELSE 0
                        END
                    ) +
                    (
                        CASE
                            WHEN COALESCE(i.`Lead Time`, 0) > 0
                            THEN CAST(((COALESCE(s.recent_sales, 0) / 14.0) * i.`Lead Time`) + 0.9999 AS INTEGER)
                            ELSE 0
                        END
                    ) AS `Suggested Reorder Qty`
                FROM Inventory i
                LEFT JOIN (
                    SELECT `Item Code`, SUM(`Amount`) AS recent_sales
                    FROM sales
                    WHERE `DateISO` >= datetime('now', '-14 days')
                    GROUP BY `Item Code`
                ) s ON s.`Item Code` = i.`Item Code`
                WHERE i.`ReOrder Trigger` >= i.`Stock`
                ORDER BY `Suggested Reorder Qty` DESC, i.`Item Code` ASC
                """,
                connection,
                null,
                null,
                false,
                true,
                null
        );
    }

    /** Model column indices for the sales transaction table (see {@link #buildSalesPanel}). */
    private static final int SALES_VIEW_COL_ITEM_CODE = 0;
    private static final int SALES_VIEW_COL_AMOUNT = 3;
    private static final int SALES_VIEW_COL_REFERENCE = 5;
    /** Internal SQLite row id — present in the model but removed from the visible table. */
    private static final int SALES_MODEL_COL_ROWID = 9;

    /**
     * Sales history with Excel-style column filters and return processing for the selected line
     * (same database rules as the former Return Item screen: restock when condition is {@code New}, otherwise reversal only).
     */
    private static JPanel buildSalesPanel(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        JLabel selectionSummary = new JLabel("Select a row to return against that sale line.");

        JPanel returnForm = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(returnForm);
        GridBagConstraints gb = new GridBagConstraints();
        gb.insets = new Insets(4, 0, 4, 10);

        JTextField returnQty = new JTextField();
        JComboBox<String> returnCondition = new JComboBox<>(new String[]{"New", "Damaged"});
        JComboBox<String> damagedReason = new JComboBox<>(new String[]{
                "DAMAGED_IN_TRANSIT",
                "DAMAGED_BY_CUSTOMER",
                "DEFECTIVE",
                "EXPIRED",
                "OTHER"
        });
        styleInput(returnQty);
        styleComboMatchInputRow(returnCondition, damagedReason);
        JLabel damagedLabel = new JLabel("Damaged reason *");
        gb.gridx = 0;
        gb.gridy = 0;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        returnForm.add(new JLabel("Return quantity *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        returnForm.add(returnQty, gb);
        gb.gridx = 0;
        gb.gridy = 1;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        returnForm.add(new JLabel("Return condition *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        returnForm.add(returnCondition, gb);
        gb.gridx = 0;
        gb.gridy = 2;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        returnForm.add(damagedLabel, gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        returnForm.add(damagedReason, gb);
        damagedReason.setEnabled(false);
        Runnable syncDamageWidgets = () -> {
            boolean damaged = "Damaged".equals(returnCondition.getSelectedItem());
            damagedReason.setEnabled(damaged);
            damagedLabel.setEnabled(damaged);
        };
        returnCondition.addActionListener(e -> syncDamageWidgets.run());
        syncDamageWidgets.run();

        JButton processReturnBtn = new JButton("Process Return");
        AppUI.stylePrimaryButton(processReturnBtn);

        JPanel returnSouth = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(returnSouth);
        returnSouth.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        JPanel intro = buildSectionPanel();
        selectionSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        intro.add(selectionSummary);
        returnSouth.add(intro, BorderLayout.NORTH);
        returnSouth.add(returnForm, BorderLayout.CENTER);
        JPanel returnFooter = buildActionBar(null, processReturnBtn);
        JPanel combinedSouth = new JPanel(new BorderLayout(0, 0));
        AppUI.applyPanelBackground(combinedSouth);
        combinedSouth.add(returnSouth, BorderLayout.CENTER);
        combinedSouth.add(returnFooter, BorderLayout.SOUTH);

        final JTable[] salesTableRef = new JTable[1];
        Consumer<JTable> onTableBuilt = table -> {
            salesTableRef[0] = table;
            table.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                int vr = table.getSelectedRow();
                if (vr < 0) {
                    selectionSummary.setText("Select a row to return against that sale line.");
                    return;
                }
                int mr = table.convertRowIndexToModel(vr);
                TableModel tm = table.getModel();
                String code = Objects.toString(tm.getValueAt(mr, SALES_VIEW_COL_ITEM_CODE), "").trim();
                String ref = Objects.toString(tm.getValueAt(mr, SALES_VIEW_COL_REFERENCE), "").trim();
                Object amtObj = tm.getValueAt(mr, SALES_VIEW_COL_AMOUNT);
                String amtStr = amtObj instanceof Number n ? Integer.toString(n.intValue()) : Objects.toString(amtObj, "");
                String nm = Objects.toString(tm.getValueAt(mr, 1), "");
                String loc = Objects.toString(tm.getValueAt(mr, 2), "");
                String locSummary = ("—".equals(loc) || loc.isBlank()) ? "no bin on record" : abbreviateForTableCell(loc, 28);
                selectionSummary.setText(String.format(Locale.US,
                        "Selected: Reference %s · %s (%s) · %s — line quantity remaining: %s",
                        ref, code, abbreviateForTableCell(nm, 40), locSummary, amtStr));
            });
        };

        processReturnBtn.addActionListener(e -> {
            JTable saleTable = salesTableRef[0];
            if (saleTable == null) {
                JOptionPane.showMessageDialog(processReturnBtn, "Sales table is not ready yet.");
                return;
            }
            int vr = saleTable.getSelectedRow();
            if (vr < 0) {
                JOptionPane.showMessageDialog(processReturnBtn, "Select a sale line in the table first.");
                return;
            }
            int modelRow = saleTable.convertRowIndexToModel(vr);
            TableModel tm = saleTable.getModel();
            String ref = Objects.toString(tm.getValueAt(modelRow, SALES_VIEW_COL_REFERENCE), "").trim();
            String code = Objects.toString(tm.getValueAt(modelRow, SALES_VIEW_COL_ITEM_CODE), "").trim();
            if (ref.isEmpty() || code.isEmpty()) {
                JOptionPane.showMessageDialog(processReturnBtn, "Selected row is missing reference or item code.");
                return;
            }
            try {
                int qty = Integer.parseInt(returnQty.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(processReturnBtn, "Return quantity must be greater than zero.");
                    return;
                }
                String displayCond = (String) returnCondition.getSelectedItem();
                boolean newLike = "New".equals(displayCond);
                String dmgReason = null;
                if (!newLike) {
                    dmgReason = (String) damagedReason.getSelectedItem();
                    if (dmgReason == null || dmgReason.isBlank()) {
                        JOptionPane.showMessageDialog(processReturnBtn, "Choose a damaged reason.");
                        return;
                    }
                }
                Object ridObj = tm.getValueAt(modelRow, SALES_MODEL_COL_ROWID);
                long saleRowId;
                if (ridObj instanceof Number n) {
                    saleRowId = n.longValue();
                } else {
                    saleRowId = Long.parseLong(Objects.toString(ridObj, "0").trim());
                }
                if (saleRowId <= 0) {
                    JOptionPane.showMessageDialog(processReturnBtn, "Could not resolve the sale line row.");
                    return;
                }
                processSaleReturn(connection, user, saleRowId, qty, newLike, dmgReason);
                if (newLike) {
                    JOptionPane.showMessageDialog(processReturnBtn, "Return completed and stock restored.");
                } else {
                    JOptionPane.showMessageDialog(processReturnBtn,
                            "Damaged return recorded. Item was not restored to sellable stock.");
                }
                try {
                    showView(workspaceContainer, "View Sales Transaction", buildSalesPanel(user, connection, workspaceContainer));
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(processReturnBtn,
                            "Return saved but the view could not refresh: " + ex.getMessage());
                }
                requestMetricsRefresh();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(processReturnBtn, "Enter a valid return quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(processReturnBtn, "Database error: " + ex.getMessage());
            }
        });

        return buildFilterableTablePanel(
                "Sales Transactions",
                new String[]{"Item Code", "Item Name", "Location", "Amount", "Total Price", "Reference", "User", "Date", "Note", "__rowid"},
                """
                        SELECT sales.`Item Code`,
                               COALESCE(inventory.`Item Name`, 'N/A') AS `Item Name`,
                               CASE WHEN sales.storage_location_id IS NULL THEN '—'
                                    ELSE COALESCE(sl.name, '#' || sales.storage_location_id) END AS Location,
                               sales.`Amount`,
                               sales.`Total Price`,
                               sales.`Reference`,
                               sales.`User`,
                               sales.`Date`,
                               COALESCE(sales.`Note`, '') AS `Note`,
                               sales.rowid AS __rowid
                        FROM sales
                        LEFT JOIN inventory ON sales.`Item Code` = inventory.`Item Code`
                        LEFT JOIN storage_locations sl ON sl.id = sales.storage_location_id
                        ORDER BY sales.`Date` DESC
                        """,
                connection,
                null,
                table -> {
                    if (table.getColumnModel().getColumnCount() > 0) {
                        table.removeColumn(table.getColumnModel().getColumn(table.getColumnCount() - 1));
                    }
                    onTableBuilt.accept(table);
                },
                false,
                true,
                combinedSouth
        );
    }

    /**
     * Reverses part of a sale line: updates {@code sales}, optional stock restock, and {@code movements}.
     * When {@code newCondition} is true ({@code New} in the UI), stock is increased; when false (damaged), only the sale row and a {@code RETURN_DAMAGED} movement are recorded.
     *
     * @param saleSqliteRowId {@code sales.rowid} for the selected line (supports multiple lines per reference + SKU when bins differ)
     * @param damagedReasonCode required when {@code newCondition} is false (reason stored on the movement)
     */
    private static void processSaleReturn(
            Connection connection,
            User user,
            long saleSqliteRowId,
            int returnQty,
            boolean newCondition,
            String damagedReasonCode
    ) throws SQLException {
        boolean restoredAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int sold;
            double totalPrice;
            double totalCost;
            String itemCode;
            Integer storageLocId = null;
            try (PreparedStatement saleLine = connection.prepareStatement(
                    "SELECT `Item Code`, `Amount`, `Total Price`, `Total Cost`, storage_location_id FROM sales WHERE rowid = ?")) {
                saleLine.setLong(1, saleSqliteRowId);
                try (ResultSet rs = saleLine.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Sale line was not found.");
                    }
                    itemCode = rs.getString("Item Code");
                    sold = rs.getInt("Amount");
                    totalPrice = rs.getDouble("Total Price");
                    totalCost = rs.getDouble("Total Cost");
                    int locRaw = rs.getInt("storage_location_id");
                    if (!rs.wasNull()) {
                        storageLocId = locRaw;
                    }
                }
            }
            if (sold <= 0 || returnQty > sold) {
                throw new SQLException("Return quantity exceeds sold amount on this line.");
            }
            double unitPrice = sold == 0 ? 0 : totalPrice / sold;
            double priceReduction = unitPrice * returnQty;
            double unitCost = sold == 0 ? 0 : totalCost / sold;
            double costReduction = unitCost * returnQty;
            try (PreparedStatement updateSales = connection.prepareStatement(
                    "UPDATE sales SET Amount = Amount - ?, `Total Price` = CASE WHEN `Total Price` - ? < 0 THEN 0 ELSE `Total Price` - ? END, `Total Cost` = CASE WHEN `Total Cost` - ? < 0 THEN 0 ELSE `Total Cost` - ? END WHERE rowid = ?"
            )) {
                updateSales.setInt(1, returnQty);
                updateSales.setDouble(2, priceReduction);
                updateSales.setDouble(3, priceReduction);
                updateSales.setDouble(4, costReduction);
                updateSales.setDouble(5, costReduction);
                updateSales.setLong(6, saleSqliteRowId);
                updateSales.executeUpdate();
            }
            if (newCondition) {
                try (PreparedStatement updateStock = connection.prepareStatement(
                        "UPDATE Inventory SET `Stock` = `Stock` + ? WHERE `Item Code` = ?")) {
                    updateStock.setInt(1, returnQty);
                    updateStock.setString(2, itemCode);
                    updateStock.executeUpdate();
                }
                int restockBin = storageLocId != null ? storageLocId : DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
                incrementInventoryStorageQty(connection, itemCode, restockBin, returnQty);
            }
            String movementType = newCondition ? "RETURN" : "RETURN_DAMAGED";
            String reason = newCondition ? "RESALABLE" : damagedReasonCode;
            try (PreparedStatement movement = connection.prepareStatement(
                    "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                movement.setString(1, itemCode);
                movement.setString(2, String.valueOf(returnQty));
                movement.setString(3, movementType);
                movement.setString(4, reason);
                movement.setString(5, user.getUsername());
                movement.setString(6, dateTime.nowDisplayString());
                movement.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM sales WHERE rowid = ? AND Amount = 0")) {
                delete.setLong(1, saleSqliteRowId);
                delete.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(restoredAutoCommit);
        }
    }

    /** Builds a reusable filterable table panel from SQL query results. */
    private static JPanel buildFilterableTablePanel(String title, String[] columns, String query, Connection connection) throws SQLException {
        return buildFilterableTablePanel(title, columns, query, connection, null, null, true, false, null);
    }

    /**
     * Builds a reusable filterable table panel from SQL query results.
     *
     * @param onRowDoubleClickModelIndex when non-null, invoked on double-click with the table and model row index
     * @param onTableBuilt               when non-null, invoked with the table after listeners are attached (e.g. View Items photo rail)
     * @param includeBottomRowFilterFields when true, a row of per-column filter fields is shown under the table
     * @param includeHeaderPopupFilters    when true, column headers gain a right-click filter menu (combined with bottom row filters if both enabled)
     * @param supplementalSouth            optional strip below the table (e.g. sales return form); mutually exclusive layout-wise with the bottom row filter bar
     */
    private static JPanel buildFilterableTablePanel(
            String title,
            String[] columns,
            String query,
            Connection connection,
            BiConsumer<JTable, Integer> onRowDoubleClickModelIndex,
            Consumer<JTable> onTableBuilt,
            boolean includeBottomRowFilterFields,
            boolean includeHeaderPopupFilters,
            JComponent supplementalSouth
    ) throws SQLException {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        AppUI.applyPanelBackground(panel);

        JLabel heading = buildSectionTitle(title);
        panel.add(heading, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);
        installTableCopyMenu(table);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        if (onRowDoubleClickModelIndex != null) {
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() != 2) {
                        return;
                    }
                    int viewRow = table.rowAtPoint(e.getPoint());
                    if (viewRow < 0) {
                        return;
                    }
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    onRowDoubleClickModelIndex.accept(table, modelRow);
                }
            });
        }

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Object[] row = new Object[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    row[i] = rs.getObject(columns[i]);
                }
                model.addRow(row);
            }
        }

        JPanel filterPanel = new JPanel(new GridLayout(1, columns.length, 8, 8));
        AppUI.applyPanelBackground(filterPanel);
        JTextField[] bottomFilters = includeBottomRowFilterFields ? new JTextField[columns.length] : null;
        Map<Integer, String> headerFilters = includeHeaderPopupFilters ? new HashMap<>() : null;

        Runnable applyCombinedRowFilter = () -> {
            if (!includeBottomRowFilterFields && !includeHeaderPopupFilters) {
                return;
            }
            List<RowFilter<Object, Integer>> parts = new ArrayList<>();
            if (bottomFilters != null) {
                for (int i = 0; i < bottomFilters.length; i++) {
                    if (bottomFilters[i] == null) {
                        continue;
                    }
                    String text = bottomFilters[i].getText();
                    if (text != null && !text.trim().isEmpty()) {
                        parts.add(RowFilter.regexFilter("(?i)" + Pattern.quote(text.trim()), i));
                    }
                }
            }
            if (headerFilters != null && !headerFilters.isEmpty()) {
                for (Map.Entry<Integer, String> e : headerFilters.entrySet()) {
                    int col = e.getKey();
                    if (col < 0 || col >= columns.length) {
                        continue;
                    }
                    String raw = e.getValue();
                    if (raw != null && !raw.trim().isEmpty()) {
                        parts.add(RowFilter.regexFilter("(?i)" + Pattern.quote(raw.trim()), col));
                    }
                }
            }
            sorter.setRowFilter(parts.isEmpty() ? null : RowFilter.andFilter(parts));
        };

        if (includeBottomRowFilterFields) {
            for (int i = 0; i < columns.length; i++) {
                JTextField filter = new JTextField();
                filter.setBorder(AppUI.newRoundedBorder(8));
                bottomFilters[i] = filter;
                filter.addCaretListener(e -> applyCombinedRowFilter.run());
                filterPanel.add(filter);
            }
        }

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(AppUI.newRoundedBorder(8));
        JPanel tableStack = new JPanel(new BorderLayout(0, 0));
        AppUI.applyPanelBackground(tableStack);
        tableStack.add(tableScroll, BorderLayout.CENTER);
        if (supplementalSouth != null) {
            tableStack.add(supplementalSouth, BorderLayout.SOUTH);
        }
        panel.add(tableStack, BorderLayout.CENTER);
        if (includeBottomRowFilterFields) {
            panel.add(filterPanel, BorderLayout.SOUTH);
        }
        if (includeHeaderPopupFilters && headerFilters != null) {
            installExcelStyleHeaderColumnFilters(table, headerFilters, applyCombinedRowFilter);
        }
        if (onTableBuilt != null) {
            onTableBuilt.accept(table);
        }
        return panel;
    }

    /** Right-click a column header: set a text contains filter; combines with other columns using AND. */
    private static void installExcelStyleHeaderColumnFilters(
            JTable table,
            Map<Integer, String> activeByModelColumn,
            Runnable refreshFilter
    ) {
        JTableHeader header = table.getTableHeader();
        if (header == null) {
            return;
        }
        header.setToolTipText("Right-click a column header to filter (like Excel).");

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }

            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int viewCol = header.columnAtPoint(e.getPoint());
                if (viewCol < 0) {
                    return;
                }
                int modelCol = table.convertColumnIndexToModel(viewCol);
                Object headerVal = table.getColumnModel().getColumn(viewCol).getHeaderValue();
                String colName = headerVal == null ? ("Column " + modelCol) : headerVal.toString();
                String current = activeByModelColumn.getOrDefault(modelCol, "");

                JPopupMenu menu = new JPopupMenu();
                JMenuItem filterItem = new JMenuItem("Filter \"" + colName + "\"...");
                filterItem.addActionListener(ev -> {
                    Object input = JOptionPane.showInputDialog(
                            table.getRootPane(),
                            "Show rows containing (text):",
                            "Filter: " + colName,
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            null,
                            current
                    );
                    if (input == null) {
                        return;
                    }
                    String t = input.toString();
                    if (t.trim().isEmpty()) {
                        activeByModelColumn.remove(modelCol);
                    } else {
                        activeByModelColumn.put(modelCol, t.trim());
                    }
                    refreshFilter.run();
                });
                menu.add(filterItem);

                JMenuItem clearCol = new JMenuItem("Clear filter for this column");
                clearCol.addActionListener(ev -> {
                    activeByModelColumn.remove(modelCol);
                    refreshFilter.run();
                });
                menu.add(clearCol);

                JMenuItem clearAll = new JMenuItem("Clear all header filters");
                clearAll.addActionListener(ev -> {
                    activeByModelColumn.clear();
                    refreshFilter.run();
                });
                menu.add(clearAll);

                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    /** Builds admin-only add-item workflow panel with compact fields and batch draft lines (like Process Sale). */
    private static JPanel buildAddItemPanel(User user, Connection connection, JPanel workspaceContainer, JFrame frame) {
        ensureAdmin(user, "Add Item");
        JPanel panel = new JPanel(new BorderLayout(10, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 14, 16));
        AppUI.applyPanelBackground(panel);

        final Path[] pendingPhotoPick = new Path[1];
        JLabel photoPreviewLabel = new JLabel("", SwingConstants.CENTER);
        photoPreviewLabel.setVerticalAlignment(SwingConstants.CENTER);
        int previewBox = ADD_ITEM_PHOTO_PREVIEW_MAX;
        photoPreviewLabel.setPreferredSize(new Dimension(previewBox, previewBox));
        photoPreviewLabel.setMinimumSize(new Dimension(previewBox, previewBox));
        photoPreviewLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppUI.BORDER, 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));

        JLabel stagedFileName = new JLabel(" ");
        stagedFileName.setFont(stagedFileName.getFont().deriveFont(Font.PLAIN, 11f));
        stagedFileName.setForeground(AppUI.TEXT_MUTED);

        Runnable refreshPhotoPreview = () -> {
            Path p = pendingPhotoPick[0];
            if (p != null && Files.isRegularFile(p)) {
                ImageIcon icon = loadScaledItemPhotoIcon(p, previewBox, previewBox);
                photoPreviewLabel.setIcon(icon);
                photoPreviewLabel.setText(null);
                stagedFileName.setText(p.getFileName().toString());
            } else {
                photoPreviewLabel.setIcon(null);
                photoPreviewLabel.setText("No JPEG");
                stagedFileName.setText(" ");
            }
        };

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints gl = new GridBagConstraints();
        gl.insets = new Insets(2, 0, 2, 6);
        gl.anchor = GridBagConstraints.LINE_END;

        JTextField nextItemCodeField = new JTextField();
        nextItemCodeField.setEditable(false);
        nextItemCodeField.setFocusable(false);
        nextItemCodeField.setToolTipText(
                "Next available ITM code. It is assigned to the first new row when you click Add all; each extra row uses the following codes in order."
        );
        try {
            nextItemCodeField.setText(getNextItemCode(connection));
        } catch (SQLException ex) {
            nextItemCodeField.setText("UNAVAILABLE");
        }
        styleInputCompact(nextItemCodeField);

        JTextField itemName = new JTextField();
        JTextField stock = new JTextField();
        JTextField reorder = new JTextField();
        JTextField supplier = new JTextField();
        JTextField leadTime = new JTextField();
        styleInputCompact(itemName, stock, reorder, supplier, leadTime);

        JTextField addItemNotesField = new JTextField();
        styleInputCompact(addItemNotesField);
        addItemNotesField.setToolTipText(
                "Optional notes. Shown on the View Items photo pane for this SKU (max "
                        + ITEM_NOTES_MAX_CHARS + " characters)."
        );

        int row = 0;
        gl.gridx = 0;
        gl.gridy = row;
        gl.gridwidth = 1;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        gl.anchor = GridBagConstraints.LINE_END;
        form.add(new JLabel("Item code (next)"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(nextItemCodeField, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Item name *"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(itemName, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Stock count *"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(stock, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Reorder trigger *"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(reorder, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Supplier (optional)"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(supplier, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Lead time (days, optional)"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(leadTime, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Notes (optional)"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(addItemNotesField, gl);

        JButton choosePhoto = new JButton("Choose JPEG…");
        JButton clearPhotoPick = new JButton("Clear photo");
        styleSecondaryButton(choosePhoto);
        styleSecondaryButton(clearPhotoPick);
        clearPhotoPick.setEnabled(false);
        choosePhoto.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("JPEG images (.jpg, .jpeg)", "jpg", "jpeg"));
            if (fc.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                pendingPhotoPick[0] = fc.getSelectedFile().toPath();
                clearPhotoPick.setEnabled(true);
                refreshPhotoPreview.run();
            }
        });
        clearPhotoPick.addActionListener(e -> {
            pendingPhotoPick[0] = null;
            clearPhotoPick.setEnabled(false);
            refreshPhotoPreview.run();
        });

        JPanel photoControls = new JPanel();
        photoControls.setLayout(new BoxLayout(photoControls, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(photoControls);
        JLabel photoTitle = new JLabel("Photo (optional)", SwingConstants.CENTER);
        photoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoControls.add(photoTitle);
        photoControls.add(Box.createVerticalStrut(6));
        photoPreviewLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoControls.add(photoPreviewLabel);
        photoControls.add(Box.createVerticalStrut(6));
        stagedFileName.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoControls.add(stagedFileName);
        photoControls.add(Box.createVerticalStrut(8));
        JPanel photoBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        AppUI.applyPanelBackground(photoBtnRow);
        photoBtnRow.add(choosePhoto);
        photoBtnRow.add(clearPhotoPick);
        photoControls.add(photoBtnRow);

        JPanel formAndPhoto = new JPanel(new BorderLayout(16, 0));
        AppUI.applyPanelBackground(formAndPhoto);
        formAndPhoto.add(form, BorderLayout.CENTER);
        formAndPhoto.add(photoControls, BorderLayout.EAST);
        refreshPhotoPreview.run();

        List<AddItemDraftLine> draftLines = new ArrayList<>();
        DefaultTableModel draftModel = new DefaultTableModel(
                new String[]{"Item name", "Stock", "Reorder", "Supplier", "Lead time", "Photo", "Notes"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable draftTable = new JTable(draftModel);
        draftTable.setRowHeight(ADD_ITEM_INPUT_HEIGHT + 10);
        installTableCopyMenu(draftTable);

        JButton addLine = new JButton("Add line");
        JButton removeLine = new JButton("Remove line");
        JButton clearDraft = new JButton("Clear draft");
        styleSecondaryButton(addLine);
        styleSecondaryButton(removeLine);
        styleSecondaryButton(clearDraft);

        addLine.addActionListener(e -> {
            String itemNameValue = itemName.getText().trim();
            if (itemNameValue.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item name is required.");
                return;
            }
            try {
                int stockCount = Integer.parseInt(stock.getText().trim());
                int reorderTrigger = Integer.parseInt(reorder.getText().trim());
                if (stockCount < 0 || reorderTrigger < 0) {
                    JOptionPane.showMessageDialog(panel, "Stock and reorder trigger must be zero or greater.");
                    return;
                }
                String supplierValue = supplier.getText().trim();
                Integer leadTimeDays = null;
                if (!leadTime.getText().trim().isEmpty()) {
                    leadTimeDays = Integer.parseInt(leadTime.getText().trim());
                    if (leadTimeDays < 0) {
                        JOptionPane.showMessageDialog(panel, "Lead time must be zero or greater.");
                        return;
                    }
                }
                String notesRaw = addItemNotesField.getText();
                if (notesRaw != null && notesRaw.length() > ITEM_NOTES_MAX_CHARS) {
                    JOptionPane.showMessageDialog(panel, "Notes must be at most " + ITEM_NOTES_MAX_CHARS + " characters.");
                    return;
                }
                draftLines.add(new AddItemDraftLine(
                        itemNameValue, stockCount, reorderTrigger, supplierValue, leadTimeDays, notesRaw == null ? "" : notesRaw, pendingPhotoPick[0]));
                refreshAddItemDraftTable(draftModel, draftLines, draftTable);
                itemName.setText("");
                stock.setText("");
                reorder.setText("");
                supplier.setText("");
                leadTime.setText("");
                addItemNotesField.setText("");
                pendingPhotoPick[0] = null;
                clearPhotoPick.setEnabled(false);
                refreshPhotoPreview.run();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter valid whole numbers for stock, reorder trigger, and optional lead time.");
            }
        });

        removeLine.addActionListener(e -> {
            int viewRow = draftTable.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(panel, "Select a row in the draft table to remove.");
                return;
            }
            int modelRow = draftTable.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < draftLines.size()) {
                draftLines.remove(modelRow);
                refreshAddItemDraftTable(draftModel, draftLines, draftTable);
            }
        });

        clearDraft.addActionListener(e -> {
            if (draftLines.isEmpty()) {
                return;
            }
            int ok = JOptionPane.showConfirmDialog(panel, "Clear all draft lines?", "Clear draft", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                draftLines.clear();
                refreshAddItemDraftTable(draftModel, draftLines, draftTable);
            }
        });

        JButton submit = new JButton("Add all to inventory");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            if (draftLines.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Add at least one line to the draft first.");
                return;
            }
            int added = 0;
            boolean originalAutoCommit = true;
            try {
                originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                List<AddItemDraftLine> snapshot = new ArrayList<>(draftLines);
                for (AddItemDraftLine line : snapshot) {
                    String itemCodeValue = getNextItemCode(connection);
                    try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) AS count FROM inventory WHERE `Item Code` = ?")) {
                        check.setString(1, itemCodeValue);
                        try (ResultSet rs = check.executeQuery()) {
                            if (rs.next() && rs.getInt("count") > 0) {
                                throw new SQLException("Item code already exists: " + itemCodeValue + ". Try again.");
                            }
                        }
                    }
                    insertNewInventoryItemRow(
                            connection,
                            user,
                            itemCodeValue,
                            line.itemName,
                            line.stock,
                            line.reorder,
                            line.supplier,
                            line.leadTime,
                            line.notes
                    );
                    if (line.pendingPhoto != null && Files.isRegularFile(line.pendingPhoto)) {
                        try {
                            copySourceJpegToItemPhoto(line.pendingPhoto, itemCodeValue);
                        } catch (IOException ioe) {
                            throw new SQLException("Photo save failed for " + itemCodeValue + ": " + ioe.getMessage(), ioe);
                        }
                    }
                    added++;
                }
                connection.commit();
                draftLines.clear();
                refreshAddItemDraftTable(draftModel, draftLines, draftTable);
                JOptionPane.showMessageDialog(
                        panel,
                        "Added " + added + " item(s). Sale price is entered at checkout. Use purchase orders for landed cost."
                );
                showView(workspaceContainer, "View Items", buildInventoryTablePanel(user, connection, frame, workspaceContainer));
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException suppressed) {
                    ex.addSuppressed(suppressed);
                }
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Unable to restore connection state: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(buttonRow);
        buttonRow.add(addLine);
        buttonRow.add(removeLine);
        buttonRow.add(clearDraft);

        JPanel northStack = new JPanel(new BorderLayout(0, 4));
        AppUI.applyPanelBackground(northStack);
        northStack.add(formAndPhoto, BorderLayout.NORTH);
        northStack.add(buttonRow, BorderLayout.SOUTH);

        JPanel footer = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(footer);
        footer.add(submit, BorderLayout.EAST);

        panel.add(northStack, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(draftTable);
        tableScroll.setBorder(AppUI.newRoundedBorder(8));
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * @param connection JDBC connection
     * @param itemCode   exact {@code Inventory.Item Code} value (already normalized by caller if needed)
     * @return {@code true} when a row exists
     */
    private static boolean inventoryItemExists(Connection connection, String itemCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM inventory WHERE `Item Code` = ? LIMIT 1")) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Builds a combined admin user-management panel with add/delete workflows.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @return user management panel
     */
    private static JPanel buildUserManagementPanel(User user, Connection connection) {
        ensureAdmin(user, "User Management");
        JPanel root = buildSectionPanel();
        root.add(adminToolsSectionTitle("User management"));
        root.add(Box.createVerticalStrut(10));

        root.add(adminToolsSectionTitle("Add user"));
        root.add(Box.createVerticalStrut(6));

        JPanel addForm = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(addForm);
        GridBagConstraints gab = new GridBagConstraints();
        gab.insets = new Insets(4, 0, 4, 10);

        JTextField addUsername = new JTextField();
        JPasswordField addPassword = new JPasswordField();
        JPasswordField addConfirm = new JPasswordField();
        styleInputCompact(addUsername);
        stylePasswordInputCompact(addPassword, addConfirm);

        JCheckBox addAdminRights = new JCheckBox("Grant administrator rights");
        AppUI.applyPanelBackground(addAdminRights);

        JPanel usernameAdminRow = new JPanel(new BorderLayout(8, 0));
        AppUI.applyPanelBackground(usernameAdminRow);
        usernameAdminRow.add(addUsername, BorderLayout.CENTER);
        usernameAdminRow.add(addAdminRights, BorderLayout.EAST);

        int row = 0;
        gab.gridx = 0;
        gab.gridy = row;
        gab.anchor = GridBagConstraints.LINE_END;
        gab.fill = GridBagConstraints.NONE;
        gab.weightx = 0;
        addForm.add(new JLabel("Username *"), gab);
        gab.gridx = 1;
        gab.anchor = GridBagConstraints.LINE_START;
        gab.fill = GridBagConstraints.HORIZONTAL;
        gab.weightx = 1;
        addForm.add(usernameAdminRow, gab);
        row++;
        gab.gridx = 0;
        gab.gridy = row;
        gab.anchor = GridBagConstraints.LINE_END;
        gab.fill = GridBagConstraints.NONE;
        gab.weightx = 0;
        addForm.add(new JLabel("Password *"), gab);
        gab.gridx = 1;
        gab.anchor = GridBagConstraints.LINE_START;
        gab.fill = GridBagConstraints.HORIZONTAL;
        gab.weightx = 1;
        addForm.add(addPassword, gab);
        row++;
        gab.gridx = 0;
        gab.gridy = row;
        gab.anchor = GridBagConstraints.LINE_END;
        gab.fill = GridBagConstraints.NONE;
        gab.weightx = 0;
        addForm.add(new JLabel("Confirm password *"), gab);
        gab.gridx = 1;
        gab.anchor = GridBagConstraints.LINE_START;
        gab.fill = GridBagConstraints.HORIZONTAL;
        gab.weightx = 1;
        addForm.add(addConfirm, gab);

        addForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(addForm);

        JButton createUser = new JButton("Create User");
        AppUI.stylePrimaryButton(createUser);
        JPanel addActions = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(addActions);
        addActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        addActions.add(createUser, BorderLayout.EAST);

        createUser.addActionListener(e -> {
            String enteredUsername = addUsername.getText().trim();
            char[] enteredPassword = addPassword.getPassword();
            char[] enteredConfirm = addConfirm.getPassword();
            try {
                if (enteredUsername.isEmpty()) {
                    JOptionPane.showMessageDialog(root, "Username is required.");
                    return;
                }
                if (!enteredUsername.matches("[A-Za-z0-9._-]{3,32}")) {
                    JOptionPane.showMessageDialog(root, "Username must be 3-32 chars and only use letters, numbers, ., _, or -.");
                    return;
                }
                if (!Arrays.equals(enteredPassword, enteredConfirm)) {
                    JOptionPane.showMessageDialog(root, "Password and confirm password do not match.");
                    return;
                }
                String policyError = AccountActions.validatePasswordPolicy(enteredPassword);
                if (policyError != null) {
                    JOptionPane.showMessageDialog(root, policyError);
                    return;
                }
                try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) AS count FROM users WHERE username = ?")) {
                    check.setString(1, enteredUsername);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt("count") > 0) {
                            JOptionPane.showMessageDialog(root, "That username already exists.");
                            return;
                        }
                    }
                }
                String hash = SecurityUtils.hashPassword(enteredPassword);
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO users (first_name, last_name, username, password, admin_rights, first_login) VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, null);
                    insert.setString(2, null);
                    insert.setString(3, enteredUsername);
                    insert.setString(4, hash);
                    insert.setInt(5, addAdminRights.isSelected() ? 1 : 0);
                    insert.setInt(6, 1);
                    insert.executeUpdate();
                }
                DatabaseManager.logSecurityEvent(
                        connection,
                        user.getUsername(),
                        "USER_CREATED",
                        "Created user '" + enteredUsername + "' (admin=" + (addAdminRights.isSelected() ? "1" : "0") + ")"
                );
                JOptionPane.showMessageDialog(root, "User created successfully. They will reset password on first sign-in.");
                addUsername.setText("");
                addPassword.setText("");
                addConfirm.setText("");
                addAdminRights.setSelected(false);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(root, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(enteredPassword, '\0');
                Arrays.fill(enteredConfirm, '\0');
            }
        });

        root.add(addActions);

        root.add(Box.createVerticalStrut(18));
        root.add(adminToolsSectionTitle("Delete user"));
        root.add(Box.createVerticalStrut(6));

        JPanel deleteForm = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(deleteForm);
        GridBagConstraints gdb = new GridBagConstraints();
        gdb.insets = new Insets(4, 0, 4, 10);

        JTextField deleteUsername = new JTextField();
        JTextField deleteReason = new JTextField();
        styleInputCompact(deleteUsername, deleteReason);

        int drow = 0;
        gdb.gridx = 0;
        gdb.gridy = drow;
        gdb.anchor = GridBagConstraints.LINE_END;
        gdb.fill = GridBagConstraints.NONE;
        gdb.weightx = 0;
        deleteForm.add(new JLabel("Username to delete *"), gdb);
        gdb.gridx = 1;
        gdb.anchor = GridBagConstraints.LINE_START;
        gdb.fill = GridBagConstraints.HORIZONTAL;
        gdb.weightx = 1;
        deleteForm.add(deleteUsername, gdb);
        drow++;
        gdb.gridx = 0;
        gdb.gridy = drow;
        gdb.anchor = GridBagConstraints.LINE_END;
        gdb.fill = GridBagConstraints.NONE;
        gdb.weightx = 0;
        deleteForm.add(new JLabel("Deletion reason *"), gdb);
        gdb.gridx = 1;
        gdb.anchor = GridBagConstraints.LINE_START;
        gdb.fill = GridBagConstraints.HORIZONTAL;
        gdb.weightx = 1;
        deleteForm.add(deleteReason, gdb);

        deleteForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(deleteForm);
        root.add(Box.createVerticalStrut(8));

        JButton deleteUser = new JButton("Delete User");
        AppUI.stylePrimaryButton(deleteUser);
        JPanel deleteActions = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(deleteActions);
        deleteActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteActions.add(deleteUser, BorderLayout.EAST);

        deleteUser.addActionListener(e -> {
            String targetUsername = deleteUsername.getText().trim();
            String reasonText = deleteReason.getText().trim();
            if (targetUsername.isEmpty()) {
                JOptionPane.showMessageDialog(root, "Username is required.");
                return;
            }
            if (reasonText.isEmpty()) {
                JOptionPane.showMessageDialog(root, "Deletion reason is required.");
                return;
            }
            if (targetUsername.equalsIgnoreCase(user.getUsername())) {
                JOptionPane.showMessageDialog(root, "You cannot delete your own account while signed in.");
                return;
            }
            try (PreparedStatement countPs = connection.prepareStatement("SELECT COUNT(*) AS n FROM users");
                 ResultSet crs = countPs.executeQuery()) {
                if (crs.next() && crs.getInt("n") <= 1) {
                    JOptionPane.showMessageDialog(root, "There is only one user account. Create another user before deleting this one.");
                    return;
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(root, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(
                    root,
                    "Delete user '" + targetUsername + "'?\nReason: " + reasonText,
                    "Confirm Account Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM users WHERE username = ?")) {
                deleteStmt.setString(1, targetUsername);
                int deleted = deleteStmt.executeUpdate();
                if (deleted == 0) {
                    JOptionPane.showMessageDialog(root, "No user found with that username.");
                    return;
                }
                DatabaseManager.logSecurityEvent(
                        connection,
                        user.getUsername(),
                        "USER_DELETED",
                        "Deleted user '" + targetUsername + "' | reason: " + reasonText
                );
                JOptionPane.showMessageDialog(root, "User deleted successfully.");
                deleteUsername.setText("");
                deleteReason.setText("");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(root, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        root.add(deleteActions);

        root.setAlignmentX(Component.LEFT_ALIGNMENT);
        return root;
    }

    /**
     * Loads {@code Item Name} from {@code inventory} for a given item code (empty when not found).
     */
    private static String queryInventoryItemDescription(Connection connection, String rawCode) {
        if (rawCode == null) {
            return "";
        }
        String code = rawCode.trim();
        if (code.isEmpty()) {
            return "";
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Item Name` FROM inventory WHERE `Item Code` = ? LIMIT 1"
        )) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "";
                }
                String n = rs.getString(1);
                return n == null ? "" : n.trim();
            }
        } catch (SQLException ex) {
            return "";
        }
    }

    /** Loads {@code Market Price} for a SKU, or {@code null} when missing or not set. */
    private static Double queryInventoryMarketPrice(Connection connection, String rawCode) {
        if (rawCode == null) {
            return null;
        }
        String code = rawCode.trim();
        if (code.isEmpty()) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Market Price` FROM inventory WHERE `Item Code` = ? LIMIT 1"
        )) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                double v = rs.getDouble(1);
                return rs.wasNull() ? null : v;
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    /**
     * When unit sale price is still blank, pre-fills from market price as the item code is typed.
     */
    private static void wireSaleUnitPriceFromMarket(
            Connection connection,
            JTextField itemCodeField,
            JTextField unitSalePriceField
    ) {
        DocumentListener dl = new DocumentListener() {
            private void apply() {
                SwingUtilities.invokeLater(() -> {
                    String current = unitSalePriceField.getText();
                    if (current != null && !current.trim().isEmpty()) {
                        return;
                    }
                    String code = itemCodeField.getText().trim();
                    if (code.isEmpty()) {
                        return;
                    }
                    Double mp = queryInventoryMarketPrice(connection, code);
                    if (mp != null) {
                        unitSalePriceField.setText(String.format(Locale.US, "%.2f", mp));
                    }
                });
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                apply();
            }
        };
        itemCodeField.getDocument().addDocumentListener(dl);
    }

    /** Keeps a read-only description field in sync as the item code is edited. */
    private static void wireInventoryItemDescriptionLookup(Connection connection, JTextField itemCodeField, JTextField descriptionTarget) {
        descriptionTarget.setEditable(false);
        DocumentListener dl = new DocumentListener() {
            private void apply() {
                SwingUtilities.invokeLater(() ->
                        descriptionTarget.setText(queryInventoryItemDescription(connection, itemCodeField.getText()))
                );
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                apply();
            }
        };
        itemCodeField.getDocument().addDocumentListener(dl);
        SwingUtilities.invokeLater(() -> descriptionTarget.setText(queryInventoryItemDescription(connection, itemCodeField.getText())));
    }

    /**
     * Builds purchase order create-and-review panel.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @return purchase-order panel
     * @throws SQLException when pending data fails to load
     */
    private static JPanel buildPurchaseOrdersPanel(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        JPanel panel = buildFormPanel(VIEW_PO_TRACKING);

        JTextField referenceField = new JTextField();
        JTextField itemCodeField = new JTextField();
        JTextField itemDescriptionField = new JTextField();
        JTextField quantityField = new JTextField();
        JTextField purchasePriceField = new JTextField();
        JTextField remainingPaymentField = new JTextField();
        JTextField purchasedFromField = new JTextField();
        styleInput(referenceField, itemCodeField, itemDescriptionField, quantityField, purchasePriceField, remainingPaymentField, purchasedFromField);
        styleAutoFilledInventoryField(itemDescriptionField);

        JPanel refReuseRow = new JPanel(new BorderLayout(8, 0));
        AppUI.applyPanelBackground(refReuseRow);
        refReuseRow.add(referenceField, BorderLayout.CENTER);
        JCheckBox reusePoReference = new JCheckBox(
                "Reuse after create",
                false
        );
        reusePoReference.setToolTipText("Keep this PO / tracking number after Create (multiple lines same reference)");
        AppUI.applyPanelBackground(reusePoReference);
        refReuseRow.add(reusePoReference, BorderLayout.EAST);

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints gb = new GridBagConstraints();
        gb.insets = new Insets(4, 0, 4, 10);
        int r = 0;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("PO / Tracking Number (optional)"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(refReuseRow, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Item Code *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(itemCodeField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Item Description"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        itemDescriptionField.setToolTipText("From inventory — updates when Item Code matches a SKU.");
        form.add(itemDescriptionField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Quantity *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(quantityField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Purchase Price *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(purchasePriceField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Remaining Payment"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        remainingPaymentField.setToolTipText("Outstanding amount still owed for this PO line (optional; defaults to 0).");
        form.add(remainingPaymentField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Purchased From *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        purchasedFromField.setToolTipText("Vendor or seller this line was purchased from (required).");
        form.add(purchasedFromField, gb);

        wireInventoryItemDescriptionLookup(connection, itemCodeField, itemDescriptionField);

        DefaultTableModel pendingModel = new DefaultTableModel(PENDING_ORDER_TABLE_COLUMNS.clone(), 0);
        JTable pendingTable = new JTable(pendingModel);
        installTableCopyMenu(pendingTable);
        hidePendingOrdersRowIdColumn(pendingTable);
        loadPendingOrders(pendingModel, connection);

        JButton submit = new JButton("Create Purchase Order");
        AppUI.stylePrimaryButton(submit);
        submit.setPreferredSize(new Dimension(240, 36));

        Runnable clearLineFieldsOnly = () -> {
            itemCodeField.setText("");
            quantityField.setText("");
            purchasePriceField.setText("");
            remainingPaymentField.setText("");
            purchasedFromField.setText("");
        };

        Runnable reloadPendingEmbedded = () -> {
            try {
                loadPendingOrders(pendingModel, connection);
                deferPackTableColumns(pendingTable);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel,
                        "Could not reload pending orders: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        submit.addActionListener(e -> {
            try {
                String enteredCode = itemCodeField.getText().trim();
                String enteredQtyText = quantityField.getText().trim();
                String enteredPurchasePriceText = purchasePriceField.getText().trim();
                String remainingPaymentRaw = remainingPaymentField.getText().trim();
                String purchasedFromRaw = purchasedFromField.getText().trim();
                if (enteredCode.isEmpty() || enteredQtyText.isEmpty() || enteredPurchasePriceText.isEmpty()
                        || purchasedFromRaw.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Item code, quantity, purchase price, and purchased from are required.");
                    return;
                }
                int enteredQty = Integer.parseInt(enteredQtyText);
                double enteredPurchasePrice = Double.parseDouble(enteredPurchasePriceText);
                double remainingPayment = remainingPaymentRaw.isEmpty() ? 0 : Double.parseDouble(remainingPaymentRaw);
                if (enteredQty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                if (enteredPurchasePrice < 0) {
                    JOptionPane.showMessageDialog(panel, "Purchase price must be zero or greater.");
                    return;
                }
                if (remainingPayment < 0) {
                    JOptionPane.showMessageDialog(panel, "Remaining payment must be zero or greater.");
                    return;
                }
                try (PreparedStatement exists = connection.prepareStatement("SELECT COUNT(*) AS count FROM inventory WHERE `Item Code` = ?")) {
                    exists.setString(1, enteredCode);
                    try (ResultSet rs = exists.executeQuery()) {
                        if (rs.next() && rs.getInt("count") == 0) {
                            JOptionPane.showMessageDialog(panel, "That item code was not found.");
                            return;
                        }
                    }
                }

                String reference = referenceField.getText().trim();
                if (reference.isEmpty()) {
                    reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                }
                String now = dateTime.nowDisplayString();

                int supplierKey = DatabaseManager.ensureSupplier(connection, purchasedFromRaw);

                try (PreparedStatement insertPending = connection.prepareStatement(
                        "INSERT INTO pendingOrders (`Item Code`, `Amount`, `Purchase Price`, `Remaining Payment`, `Purchased From`, `Reference`, `User`, `Date`, supplier_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                ) {
                    insertPending.setString(1, enteredCode);
                    insertPending.setInt(2, enteredQty);
                    insertPending.setDouble(3, enteredPurchasePrice);
                    insertPending.setDouble(4, remainingPayment);
                    insertPending.setString(5, purchasedFromRaw);
                    insertPending.setString(6, reference);
                    insertPending.setString(7, user.getUsername());
                    insertPending.setString(8, now);
                    insertPending.setInt(9, supplierKey);
                    insertPending.executeUpdate();
                }
                try (PreparedStatement updateInventory = connection.prepareStatement("UPDATE inventory SET `On Order` = `On Order` + ? WHERE `Item Code` = ?")) {
                    updateInventory.setInt(1, enteredQty);
                    updateInventory.setString(2, enteredCode);
                    updateInventory.executeUpdate();
                }
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, enteredCode);
                    movement.setString(2, String.valueOf(enteredQty));
                    movement.setString(3, "ORDERED");
                    movement.setString(4, "PURCHASE_ORDER_CREATED");
                    movement.setString(5, user.getUsername());
                    movement.setString(6, now);
                    movement.executeUpdate();
                }

                JOptionPane.showMessageDialog(panel, "Purchase order created. Reference: " + reference);
                if (reusePoReference.isSelected()) {
                    referenceField.setText(reference);
                    clearLineFieldsOnly.run();
                } else {
                    referenceField.setText("");
                    clearLineFieldsOnly.run();
                }
                reloadPendingEmbedded.run();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter valid numeric values for quantity, purchase price, and remaining payment.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setBorder(AppUI.newRoundedBorder(8));

        JButton receiveThisLine = new JButton("Receive this line");
        styleSecondaryButton(receiveThisLine);
        receiveThisLine.addActionListener(e -> {
            int vr = pendingTable.getSelectedRow();
            if (vr < 0) {
                JOptionPane.showMessageDialog(panel, "Select a pending line first.");
                return;
            }
            int mr = pendingTable.convertRowIndexToModel(vr);
            String code = Objects.toString(pendingModel.getValueAt(mr, 1), "").trim();
            String ref = Objects.toString(pendingModel.getValueAt(mr, 7), "").trim();
            if (code.isEmpty() || ref.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Could not resolve item code/reference from selected line.");
                return;
            }
            try {
                showView(workspaceContainer, "Receive Order",
                        buildReceiveOrderPanel(user, connection, workspaceContainer, ref, code));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to open Receive Order: " + ex.getMessage(),
                        "View Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton receiveEntirePo = new JButton("Receive entire PO");
        AppUI.stylePrimaryButton(receiveEntirePo);
        receiveEntirePo.setToolTipText("Receive all remaining lines for the selected PO reference into one bin.");
        receiveEntirePo.addActionListener(e -> {
            String ref = "";
            int vr = pendingTable.getSelectedRow();
            if (vr >= 0) {
                int mr = pendingTable.convertRowIndexToModel(vr);
                ref = Objects.toString(pendingModel.getValueAt(mr, 7), "").trim();
            }
            if (ref.isEmpty()) {
                ref = JOptionPane.showInputDialog(panel, "Enter PO reference to receive in full:", "Receive entire PO",
                        JOptionPane.QUESTION_MESSAGE);
                if (ref == null) {
                    return;
                }
                ref = ref.trim();
            }
            if (ref.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "A PO reference is required.");
                return;
            }
            try {
                showReceiveEntirePoDialog(panel, user, connection, ref, reloadPendingEmbedded);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not load PO lines: " + ex.getMessage(),
                        "Receive entire PO", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel centerBlock = new JPanel(new BorderLayout(8, 8));
        AppUI.applyPanelBackground(centerBlock);
        centerBlock.add(form, BorderLayout.NORTH);
        centerBlock.add(pendingScroll, BorderLayout.CENTER);
        JPanel pendingFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        AppUI.applyPanelBackground(pendingFooter);
        pendingFooter.add(receiveThisLine);
        pendingFooter.add(receiveEntirePo);
        centerBlock.add(pendingFooter, BorderLayout.SOUTH);

        panel.add(centerBlock, BorderLayout.CENTER);
        panel.add(buildActionBar(null, submit), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Builds receive-order panel: receipts post to Stock and FIFO layers; reduces open PO quantities and {@code On Order}.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @return receive-order panel
     * @throws SQLException when pending-order table fails to load
     */
    private static JPanel buildReceiveOrderPanel(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        return buildReceiveOrderPanel(user, connection, workspaceContainer, null, null);
    }

    /**
     * Builds receive-order panel with optional prefilled reference and item code.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @param prefillReference optional purchase order reference
     * @param prefillItemCode optional item code
     * @return receive-order panel
     * @throws SQLException when pending-order table fails to load
     */
    private static JPanel buildReceiveOrderPanel(
            User user,
            Connection connection,
            JPanel workspaceContainer,
            String prefillReference,
            String prefillItemCode
    ) throws SQLException {
        JPanel panel = buildFormPanel("Receive Order");
        JTextField reference = new JTextField();
        JTextField itemCode = new JTextField();
        JTextField recvItemDesc = new JTextField();
        JTextField received = new JTextField();
        JComboBox<StorageLocationPick> storageLocationCombo = new JComboBox<>();
        styleInput(reference, itemCode, recvItemDesc, received);
        styleAutoFilledInventoryField(recvItemDesc);
        refreshActiveStorageLocationCombo(storageLocationCombo, connection);
        styleComboMatchInputRow(storageLocationCombo);
        if (prefillReference != null) {
            reference.setText(prefillReference);
        }
        if (prefillItemCode != null) {
            itemCode.setText(prefillItemCode);
        }

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints rg = new GridBagConstraints();
        rg.insets = new Insets(4, 0, 4, 10);
        int rf = 0;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Reference *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(reference, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Code *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(itemCode, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Description"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        recvItemDesc.setToolTipText("From inventory row for the entered item code.");
        form.add(recvItemDesc, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Received Quantity *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(received, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Put away to"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        JLabel storageHint = new JLabel("Bins are shelf labels only — total Stock remains the source of truth.");
        storageHint.setFont(storageHint.getFont().deriveFont(11f));
        JPanel storageRow = new JPanel(new BorderLayout(8, 0));
        AppUI.applyPanelBackground(storageRow);
        storageRow.add(storageLocationCombo, BorderLayout.CENTER);
        storageRow.add(storageHint, BorderLayout.SOUTH);
        form.add(storageRow, rg);

        wireInventoryItemDescriptionLookup(connection, itemCode, recvItemDesc);

        DefaultTableModel pendingModel = new DefaultTableModel(PENDING_ORDER_TABLE_COLUMNS.clone(), 0);
        JTable pendingTable = new JTable(pendingModel);
        installTableCopyMenu(pendingTable);
        TableRowSorter<DefaultTableModel> pendingSorter = new TableRowSorter<>(pendingModel);
        pendingTable.setRowSorter(pendingSorter);
        loadPendingOrders(pendingModel, connection);
        hidePendingOrdersRowIdColumn(pendingTable);
        pendingTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int selectedRow = pendingTable.getSelectedRow();
            if (selectedRow < 0) {
                return;
            }
            int modelRow = pendingTable.convertRowIndexToModel(selectedRow);
            itemCode.setText(String.valueOf(pendingModel.getValueAt(modelRow, 1)));
            reference.setText(String.valueOf(pendingModel.getValueAt(modelRow, 7)));
        });
        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setBorder(AppUI.newRoundedBorder(8));
        JPanel searchPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        AppUI.applyPanelBackground(searchPanel);
        JTextField pendingSearch = new JTextField();
        pendingSearch.setBorder(AppUI.newRoundedBorder(8));
        searchPanel.add(new JLabel("Search pending (item code / description / purchased from / reference)"));
        searchPanel.add(pendingSearch);
        pendingSearch.getDocument().addDocumentListener(new DocumentListener() {
            private void applyFilter() {
                String text = pendingSearch.getText();
                if (text == null || text.trim().isEmpty()) {
                    pendingSorter.setRowFilter(null);
                    return;
                }
                String like = text.trim();
                pendingSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(like), 1, 2, 6, 7));
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        Runnable reloadPendingReceive = () -> {
            try {
                loadPendingOrders(pendingModel, connection);
                deferPackTableColumns(pendingTable);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to refresh pending orders: " + ex.getMessage());
            }
        };

        JButton refreshPending = new JButton("Refresh Pending Orders");
        styleSecondaryButton(refreshPending);
        refreshPending.addActionListener(e -> reloadPendingReceive.run());

        JButton cancelPendingReceive = new JButton("Cancel Pending Line…");
        styleSecondaryButton(cancelPendingReceive);
        cancelPendingReceive.setToolTipText("Clear an open line without receiving; restores On Order. Optional note is logged.");
        cancelPendingReceive.addActionListener(e -> cancelSelectedPendingOrderLineDialog(
                user, connection, panel, pendingTable, reloadPendingReceive));

        JButton submit = new JButton("Receive");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            String ref = reference.getText().trim();
            String code = itemCode.getText().trim();
            if (ref.isEmpty() || code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Reference and item code are required.");
                return;
            }
            try {
                int qty = Integer.parseInt(received.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                int ordered;
                double purchasePrice;
                try (PreparedStatement ps = connection.prepareStatement("SELECT Amount, `Purchase Price` FROM pendingOrders WHERE `Reference` = ? AND `Item Code` = ?")) {
                    ps.setString(1, ref);
                    ps.setString(2, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(panel, "No pending order line found for that reference and item.");
                            return;
                        }
                        ordered = rs.getInt("Amount");
                        purchasePrice = rs.getDouble("Purchase Price");
                    }
                }
                if (qty > ordered) {
                    JOptionPane.showMessageDialog(panel, "Received quantity cannot exceed ordered amount.");
                    return;
                }
                Object locSelection = storageLocationCombo.getSelectedItem();
                StorageLocationPick locPick = locSelection instanceof StorageLocationPick lp ? lp : null;
                int storageLocationId = locPick != null ? locPick.id : DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
                applyReceive(user, connection, ref, code, qty, purchasePrice, storageLocationId);
                reloadPendingReceive.run();
                recordRecentItem(code, queryInventoryItemDescription(connection, code));
                JOptionPane.showMessageDialog(panel, "Items received successfully.");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        AppUI.applyPanelBackground(footer);
        JPanel footerWest = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(footerWest);
        footerWest.add(refreshPending);
        footerWest.add(cancelPendingReceive);
        footer.add(footerWest, BorderLayout.WEST);
        footer.add(submit, BorderLayout.EAST);
        panel.add(form, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(center);
        center.add(pendingScroll, BorderLayout.CENTER);
        center.add(searchPanel, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** Same grid cell pattern as market prices: item code + wrapped name · compact reorder field. */
    private static JPanel buildReorderTriggerItemSlot(String code, String name, JTextField triggerField) {
        JPanel outer = new JPanel(new BorderLayout(8, 0));
        outer.setOpaque(false);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        east.setOpaque(false);
        styleInput(triggerField);
        triggerField.setHorizontalAlignment(JTextField.TRAILING);
        attachAdaptiveBoundedFieldWidth(triggerField, INPUT_HEIGHT, 52, 100);

        outer.add(bulkEditSkuMetaPanel(code, name), BorderLayout.CENTER);
        outer.add(east, BorderLayout.EAST);
        east.add(triggerField);

        outer.setMinimumSize(new Dimension(100, INPUT_HEIGHT + 4));
        return outer;
    }

    /** Bold SKU code with a wrapping name region so labels are not abbreviated to tiny fragments. */
    private static JPanel bulkEditSkuMetaPanel(String code, String name) {
        JPanel meta = new JPanel(new BorderLayout(0, 2));
        meta.setOpaque(false);
        JLabel jc = new JLabel(code);
        jc.setFont(jc.getFont().deriveFont(Font.BOLD, 12f));
        jc.setOpaque(false);

        String safeName = Objects.toString(name, "");
        JTextArea nameArea = new JTextArea(safeName);
        nameArea.setEditable(false);
        nameArea.setFocusable(false);
        nameArea.setOpaque(false);
        nameArea.setLineWrap(true);
        nameArea.setWrapStyleWord(true);
        nameArea.setRows(2);
        nameArea.setFont(nameArea.getFont().deriveFont(Font.PLAIN, 11f));
        nameArea.setForeground(AppUI.TEXT_MUTED);
        nameArea.setBorder(BorderFactory.createEmptyBorder());

        meta.add(jc, BorderLayout.NORTH);
        meta.add(nameArea, BorderLayout.CENTER);
        return meta;
    }

    /**
     * Bulk-edit {@code Inventory.ReOrder Trigger} for every SKU (same layout as Market Prices).
     * Submit applies updates only where the typed value differs from the loaded value.
     */
    private static JPanel buildAdjustReorderPanel(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        ensureAdmin(user, "Adjust Reorder Trigger");
        JPanel panel = buildFormPanel("Change reorder triggers");
        JPanel centerWrap = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(centerWrap);

        JPanel intro = buildSectionPanel();
        intro.add(buildSectionText(
                "Each line shows the saved reorder threshold. Change any value and submit — only rows you actually edit "
                        + "are written to the database; SKUs left at their original numbers are skipped."));
        centerWrap.add(intro, BorderLayout.NORTH);

        LinkedHashMap<String, Integer> originalTriggers = new LinkedHashMap<>();
        LinkedHashMap<String, JTextField> codeToTriggerField = new LinkedHashMap<>();
        List<String> codesOrdered = new ArrayList<>();
        List<String> namesOrdered = new ArrayList<>();

        boolean anyRows = false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Item Code`, `Item Name`, `ReOrder Trigger` FROM inventory ORDER BY `Item Code` ASC"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    anyRows = true;
                    String code = rs.getString("Item Code");
                    String nm = Objects.toString(rs.getString("Item Name"), "");
                    int trigger = rs.getInt("ReOrder Trigger");
                    originalTriggers.put(code, trigger);

                    JTextField triggerField = new JTextField(Integer.toString(trigger), 8);
                    codeToTriggerField.put(code, triggerField);
                    codesOrdered.add(code);
                    namesOrdered.add(nm);
                }
            }
        }

        if (!anyRows) {
            centerWrap.add(buildSectionText("No inventory rows found."), BorderLayout.CENTER);
        } else {
            JPanel scrollBody = new JPanel(new GridBagLayout());
            scrollBody.setOpaque(true);
            AppUI.applyPanelBackground(scrollBody);

            int n = codesOrdered.size();
            final int cols = 3;
            int numRows = (n + cols - 1) / cols;

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTH;
            gbc.weightx = 1.0 / cols;
            gbc.weighty = 0;

            for (int r = 0; r < numRows; r++) {
                gbc.gridy = r;
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;
                    gbc.gridx = c;
                    gbc.insets = new Insets(r == 0 ? 2 : 8, 0, 0, 0);

                    JPanel slot = null;
                    if (idx < n) {
                        slot = buildReorderTriggerItemSlot(
                                codesOrdered.get(idx),
                                namesOrdered.get(idx),
                                codeToTriggerField.get(codesOrdered.get(idx)));
                    }

                    JPanel cell = marketPriceColumnDividerWrap(slot, c < cols - 1);
                    scrollBody.add(cell, gbc);
                }
            }

            GridBagConstraints glue = new GridBagConstraints();
            glue.gridy = numRows;
            glue.gridx = 0;
            glue.gridwidth = cols;
            glue.weighty = 1.0;
            glue.weightx = 1.0;
            glue.fill = GridBagConstraints.VERTICAL;
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            scrollBody.add(spacer, glue);

            JScrollPane scroll = new JScrollPane(scrollBody,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(AppUI.newRoundedBorder(8));
            scroll.getViewport().setBackground(scrollBody.getBackground());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.setPreferredSize(new Dimension(1080, Math.min(MAIN_FRAME_BASE_H - 260, 520)));
            centerWrap.add(scroll, BorderLayout.CENTER);
        }

        JButton submit = new JButton("Submit reorder trigger updates");
        AppUI.stylePrimaryButton(submit);
        submit.setEnabled(anyRows);
        submit.addActionListener(e -> {
            List<Object[]> toPersist = new ArrayList<>();
            for (String code : codesOrdered) {
                JTextField field = codeToTriggerField.get(code);
                int original = originalTriggers.get(code);
                String raw = field.getText().trim();
                int newVal;
                try {
                    if (raw.isEmpty()) {
                        JOptionPane.showMessageDialog(panel,
                                "Reorder trigger cannot be blank for " + code + ".",
                                "Input error",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    newVal = Integer.parseInt(raw);
                } catch (NumberFormatException nf) {
                    JOptionPane.showMessageDialog(panel,
                            "Invalid reorder trigger for " + code + ": enter a whole number.",
                            "Input error",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (newVal < 0) {
                    JOptionPane.showMessageDialog(panel,
                            "Reorder trigger cannot be negative for " + code + ".",
                            "Input error",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (newVal != original) {
                    toPersist.add(new Object[]{code, newVal});
                }
            }

            if (toPersist.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Nothing to save — no reorder triggers were changed.");
                return;
            }

            boolean savedAc = true;
            try {
                savedAc = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement updatePs = connection.prepareStatement(
                        "UPDATE inventory SET `ReOrder Trigger` = ? WHERE `Item Code` = ?");
                     PreparedStatement movement = connection.prepareStatement(
                             "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (Object[] row : toPersist) {
                        String code = (String) row[0];
                        int newVal = (Integer) row[1];
                        updatePs.setInt(1, newVal);
                        updatePs.setString(2, code);
                        updatePs.executeUpdate();
                        movement.setString(1, code);
                        movement.setString(2, " ");
                        movement.setString(3, "UPDATED TRIGGER");
                        movement.setString(4, "REORDER_TRIGGER_UPDATE");
                        movement.setString(5, user.getUsername());
                        movement.setString(6, dateTime.nowDisplayString());
                        movement.executeUpdate();
                    }
                }
                connection.commit();
                JOptionPane.showMessageDialog(panel, "Updated reorder triggers for " + toPersist.size() + " item(s).");
                refreshActiveMetricsStripNow();
                try {
                    showView(workspaceContainer, "Change Reorder Triggers", buildAdjustReorderPanel(user, connection, workspaceContainer));
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel,
                            "Changes saved, but the screen could not refresh: " + ex.getMessage());
                }
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // ignore rollback failure
                }
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            } finally {
                try {
                    connection.setAutoCommit(savedAc);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        });

        JPanel footer = buildActionBar(null, submit);
        panel.add(centerWrap, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** Tweaks preferred width while typing so compact numeric editors grow within bounds. */
    private static void attachAdaptiveBoundedFieldWidth(JTextField field, int heightPx, int minWidthPx, int maxWidthPx) {
        Runnable sync = () -> {
            FontMetrics fm = field.getFontMetrics(field.getFont());
            String s = field.getText();
            if (s == null) {
                s = "";
            }
            int measured = fm.stringWidth(s.isEmpty() ? "0" : s) + 32;
            int w = Math.min(maxWidthPx, Math.max(minWidthPx, measured));
            field.setPreferredSize(new Dimension(w, heightPx));
            field.setMaximumSize(new Dimension(maxWidthPx, heightPx));
            Component p = field.getParent();
            if (p != null) {
                p.revalidate();
            }
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                sync.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                sync.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                sync.run();
            }
        });
        sync.run();
    }

    /** One grid cell: bold code + wrapped name · compact price field. */
    private static JPanel buildMarketPriceItemSlot(String code, String name, JTextField priceField) {
        JPanel outer = new JPanel(new BorderLayout(8, 0));
        outer.setOpaque(false);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        east.setOpaque(false);
        styleInput(priceField);
        attachAdaptiveBoundedFieldWidth(priceField, INPUT_HEIGHT, 56, 112);

        outer.add(bulkEditSkuMetaPanel(code, name), BorderLayout.CENTER);
        outer.add(east, BorderLayout.EAST);
        east.add(priceField);

        outer.setMinimumSize(new Dimension(100, INPUT_HEIGHT + 4));
        return outer;
    }

    /** Cell wrapper with a thin vertical rule between columns when {@code drawRightDivider} is true (three-column grid). */
    private static JPanel marketPriceColumnDividerWrap(JPanel inner, boolean drawRightDivider) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setOpaque(false);
        int ri = drawRightDivider ? 1 : 0;
        javax.swing.border.Border outer = BorderFactory.createMatteBorder(0, 0, 0, ri, AppUI.BORDER);
        javax.swing.border.Border padded = BorderFactory.createCompoundBorder(outer,
                BorderFactory.createEmptyBorder(6, 6, 6, 6));
        cell.setBorder(padded);
        if (inner != null) {
            cell.add(inner, BorderLayout.CENTER);
        }
        return cell;
    }

    /** Bulk-edit {@code Inventory.Market Price} for SKUs with {@code Stock > 0}; blank fields leave existing DB values. */
    private static JPanel buildMarketPricesBulkPanel(User user, Connection connection, JPanel workspaceContainer)
            throws SQLException {
        ensureAdmin(user, "Market Prices");
        JPanel panel = buildFormPanel("Market prices");
        JPanel centerWrap = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(centerWrap);

        JPanel intro = buildSectionPanel();
        intro.add(buildSectionText(
                "Only items with stock on hand are listed. Leave the price box blank to keep the current saved value; "
                        + "enter a unit amount (for example 12.99 or $12.99) to replace it."));
        centerWrap.add(intro, BorderLayout.NORTH);

        LinkedHashMap<String, JTextField> codeToPriceField = new LinkedHashMap<>();
        LinkedHashMap<String, Double> originalPrice = new LinkedHashMap<>();
        List<String> codesOrdered = new ArrayList<>();
        List<String> namesOrdered = new ArrayList<>();

        boolean anyRows = false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Item Code`, `Item Name`, `Stock`, `Market Price` FROM inventory WHERE `Stock` > 0 ORDER BY `Item Code` ASC"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    anyRows = true;
                    String code = rs.getString("Item Code");
                    String nm = Objects.toString(rs.getString("Item Name"), "");
                    double mp = rs.getDouble("Market Price");
                    boolean mpNull = rs.wasNull();
                    originalPrice.put(code, mpNull ? null : mp);

                    JTextField priceField = new JTextField(mpNull ? "" : String.format(Locale.US, "%.2f", mp), 8);
                    priceField.setBorder(AppUI.newRoundedBorder(8));
                    codeToPriceField.put(code, priceField);
                    codesOrdered.add(code);
                    namesOrdered.add(nm);
                }
            }
        }

        if (!anyRows) {
            centerWrap.add(buildSectionText("No items with stock on hand."), BorderLayout.CENTER);
        } else {
            JPanel scrollBody = new JPanel(new GridBagLayout());
            scrollBody.setOpaque(true);
            AppUI.applyPanelBackground(scrollBody);

            int n = codesOrdered.size();
            final int cols = 3;
            int numRows = (n + cols - 1) / cols;

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTH;
            gbc.weightx = 1.0 / cols;
            gbc.weighty = 0;

            for (int r = 0; r < numRows; r++) {
                gbc.gridy = r;
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;
                    gbc.gridx = c;
                    gbc.insets = new Insets(r == 0 ? 2 : 8, 0, 0, 0);

                    JPanel slot = null;
                    if (idx < n) {
                        slot = buildMarketPriceItemSlot(
                                codesOrdered.get(idx),
                                namesOrdered.get(idx),
                                codeToPriceField.get(codesOrdered.get(idx)));
                    }

                    JPanel cell = marketPriceColumnDividerWrap(slot, c < cols - 1);
                    scrollBody.add(cell, gbc);
                }
            }

            GridBagConstraints glue = new GridBagConstraints();
            glue.gridy = numRows;
            glue.gridx = 0;
            glue.gridwidth = cols;
            glue.weighty = 1.0;
            glue.weightx = 1.0;
            glue.fill = GridBagConstraints.VERTICAL;
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            scrollBody.add(spacer, glue);

            JScrollPane scroll = new JScrollPane(scrollBody,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(AppUI.newRoundedBorder(8));
            scroll.getViewport().setBackground(scrollBody.getBackground());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.setPreferredSize(new Dimension(1080, Math.min(MAIN_FRAME_BASE_H - 260, 520)));
            centerWrap.add(scroll, BorderLayout.CENTER);
        }

        JButton submit = new JButton("Submit price updates");
        AppUI.stylePrimaryButton(submit);
        submit.setEnabled(anyRows);
        submit.addActionListener(e -> {
            List<Object[]> updates = new ArrayList<>();
            for (Map.Entry<String, JTextField> entry : codeToPriceField.entrySet()) {
                try {
                    Double v = parseOptionalMarketPriceInput(entry.getValue().getText());
                    if (v != null) {
                        Double prior = originalPrice.get(entry.getKey());
                        if (prior != null && Math.abs(prior - v) < 1e-9) {
                            continue;
                        }
                        updates.add(new Object[]{entry.getKey(), v});
                    }
                } catch (NumberFormatException nf) {
                    JOptionPane.showMessageDialog(panel,
                            "Invalid price for " + entry.getKey() + ": " + nf.getMessage(),
                            "Input error",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            if (updates.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Nothing to update — every price field was left blank.");
                return;
            }
            boolean savedAc = true;
            try {
                savedAc = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE inventory SET `Market Price` = ? WHERE `Item Code` = ?")) {
                    for (Object[] row : updates) {
                        String code = (String) row[0];
                        Double newPrice = (Double) row[1];
                        Double prior = originalPrice.get(code);
                        ps.setDouble(1, newPrice);
                        ps.setString(2, code);
                        ps.executeUpdate();
                        InventoryAudit.touchMarketPriceUpdated(connection, code);
                        String beforeText = prior == null ? "null" : String.format(Locale.US, "%.4f", prior);
                        String afterText = String.format(Locale.US, "%.4f", newPrice);
                        InventoryAudit.logChange(connection, user.getUsername(), code,
                                InventoryAudit.CHANGE_MARKET_PRICE, 0,
                                "BULK_MARKET_PRICE",
                                "from=" + beforeText + " to=" + afterText);
                    }
                }
                connection.commit();
                JOptionPane.showMessageDialog(panel, "Updated market price for " + updates.size() + " item(s).");
                refreshActiveMetricsStripNow();
                try {
                    showView(workspaceContainer, "Market Prices", buildMarketPricesBulkPanel(user, connection, workspaceContainer));
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Prices saved, but the screen could not refresh: " + ex.getMessage());
                }
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // ignore rollback failure
                }
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            } finally {
                try {
                    connection.setAutoCommit(savedAc);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        });

        JPanel footer = buildActionBar(null, submit);
        panel.add(centerWrap, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel buildSuppliersPanel(User user, Connection connection) throws SQLException {
        ensureAdmin(user, "Suppliers");
        JPanel panel = buildFormPanel("Suppliers");
        JPanel body = new JPanel(new BorderLayout(0, 10));
        AppUI.applyPanelBackground(body);
        body.add(buildSectionText(
                "Exposure shows pending quantity x purchase price. Days since last receive checks movements with RECEIVE/RECEIVED."),
                BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Supplier", "Open PO Exposure", "Inventory Link Count", "Days Since Last Receive"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        installTableCopyMenu(table);
        table.setAutoCreateRowSorter(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));
        body.add(scroll, BorderLayout.CENTER);

        Runnable reload = () -> {
            model.setRowCount(0);
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    WITH po AS (
                        SELECT supplier_id, COALESCE(SUM(CAST(`Amount` AS REAL) * `Purchase Price`), 0) AS exposure
                        FROM pendingOrders
                        WHERE supplier_id IS NOT NULL AND `Amount` > 0
                        GROUP BY supplier_id
                    ),
                    inv_links AS (
                        SELECT supplier_id, COUNT(*) AS link_count
                        FROM inventory
                        WHERE supplier_id IS NOT NULL
                        GROUP BY supplier_id
                    ),
                    recv AS (
                        SELECT i.supplier_id AS supplier_id,
                               MAX(substr(m.`Date`, 7, 4) || '-' || substr(m.`Date`, 4, 2) || '-' || substr(m.`Date`, 1, 2)) AS last_receive_iso
                        FROM movements m
                        JOIN inventory i ON i.`Item Code` = m.`Item`
                        WHERE m.`Type` IN ('RECEIVE', 'RECEIVED')
                          AND i.supplier_id IS NOT NULL
                        GROUP BY i.supplier_id
                    )
                    SELECT s.name AS supplier_name,
                           COALESCE(po.exposure, 0) AS po_exposure,
                           COALESCE(inv_links.link_count, 0) AS inv_links,
                           CASE
                               WHEN recv.last_receive_iso IS NULL THEN NULL
                               ELSE CAST(julianday('now') - julianday(recv.last_receive_iso) AS INTEGER)
                           END AS days_since_receive
                    FROM suppliers s
                    LEFT JOIN po ON po.supplier_id = s.id
                    LEFT JOIN inv_links ON inv_links.supplier_id = s.id
                    LEFT JOIN recv ON recv.supplier_id = s.id
                    WHERE s.active = 1
                    ORDER BY lower(s.name) ASC
                    """
            );
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer days = (Integer) rs.getObject("days_since_receive");
                    model.addRow(new Object[]{
                            rs.getString("supplier_name"),
                            formatUsdMoney(rs.getDouble("po_exposure")),
                            rs.getInt("inv_links"),
                            days == null ? "Never" : Integer.toString(Math.max(0, days))
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not load suppliers: " + ex.getMessage(),
                        "Suppliers", JOptionPane.ERROR_MESSAGE);
            }
            deferPackTableColumns(table);
        };
        reload.run();

        JButton refresh = new JButton("Refresh");
        styleSecondaryButton(refresh);
        refresh.addActionListener(e -> reload.run());
        panel.add(body, BorderLayout.CENTER);
        panel.add(buildActionBar(refresh, null), BorderLayout.SOUTH);
        return panel;
    }

    /** Builds admin-only write-off panel for stock reductions. */
    private static JPanel buildWriteOffPanel(User user, Connection connection) {
        ensureAdmin(user, "Write Off Stock");
        JPanel panel = buildFormPanel("Write Off Stock");
        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints rg = new GridBagConstraints();
        rg.insets = new Insets(4, 0, 4, 10);

        JTextField itemCode = new JTextField();
        JTextField itemDesc = new JTextField();
        JTextField quantity = new JTextField();
        JComboBox<String> reasonCode = new JComboBox<>(new String[]{
                "DAMAGED",
                "EXPIRED",
                "COUNT_CORRECTION",
                "THEFT_LOSS",
                "RETURN_DAMAGED",
                "OTHER"
        });
        styleInput(itemCode, itemDesc, quantity);
        styleAutoFilledInventoryField(itemDesc);
        styleComboMatchInputRow(reasonCode);

        int row = 0;
        rg.gridx = 0;
        rg.gridy = row;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Code *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(itemCode, rg);
        row++;
        rg.gridx = 0;
        rg.gridy = row;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Description"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        itemDesc.setToolTipText("From inventory row for the entered item code.");
        form.add(itemDesc, rg);
        row++;
        rg.gridx = 0;
        rg.gridy = row;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Write-off Quantity *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(quantity, rg);
        row++;
        rg.gridx = 0;
        rg.gridy = row;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Reason Code *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(reasonCode, rg);

        wireInventoryItemDescriptionLookup(connection, itemCode, itemDesc);

        DefaultTableModel writeOffHistoryModel = new DefaultTableModel(
                new String[]{"Item Code", "Item Description", "Amount", "Reason", "User", "Date"},
                0
        );
        JTable writeOffHistoryTable = new JTable(writeOffHistoryModel);
        installTableCopyMenu(writeOffHistoryTable);
        TableRowSorter<DefaultTableModel> writeOffSorter = new TableRowSorter<>(writeOffHistoryModel);
        writeOffHistoryTable.setRowSorter(writeOffSorter);
        try {
            loadWriteOffHistory(writeOffHistoryModel, connection);
            deferPackTableColumns(writeOffHistoryTable);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panel, "Unable to load write-off history: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        JScrollPane historyScroll = new JScrollPane(writeOffHistoryTable);
        historyScroll.setBorder(AppUI.newRoundedBorder(8));

        JButton submit = new JButton("Write Off");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            String selectedReason = (String) reasonCode.getSelectedItem();
            if (selectedReason == null || selectedReason.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Reason code is required.");
                return;
            }
            try {
                int qty = Integer.parseInt(quantity.getText().trim());
                if (qty < 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be zero or greater.");
                    return;
                }
                boolean savedAc;
                try {
                    savedAc = connection.getAutoCommit();
                    connection.setAutoCommit(false);
                } catch (SQLException acSetup) {
                    JOptionPane.showMessageDialog(panel,
                            "Could not prepare the database transaction: " + acSetup.getMessage(),
                            "Database",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                boolean persisted = false;
                try {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE Inventory SET Stock = Stock - ? WHERE `Item Code` = ? AND Stock >= ?")) {
                        update.setInt(1, qty);
                        update.setString(2, code);
                        update.setInt(3, qty);
                        if (update.executeUpdate() == 0) {
                            connection.rollback();
                            JOptionPane.showMessageDialog(panel,
                                    "Write-off failed. Check item code and available stock.");
                            return;
                        }
                    }
                    deductInventoryStorageQtySpread(connection, code, qty);
                    try (PreparedStatement movement = connection.prepareStatement(
                            "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                        movement.setString(1, code);
                        movement.setString(2, String.valueOf(qty));
                        movement.setString(3, "WRITE OFF");
                        movement.setString(4, selectedReason);
                        movement.setString(5, user.getUsername());
                        movement.setString(6, dateTime.nowDisplayString());
                        movement.executeUpdate();
                    }
                    connection.commit();
                    persisted = true;
                } catch (SQLException db) {
                    try {
                        connection.rollback();
                    } catch (SQLException suppressed) {
                        db.addSuppressed(suppressed);
                    }
                    JOptionPane.showMessageDialog(panel, "Database error: " + db.getMessage(), "Database",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    try {
                        connection.setAutoCommit(savedAc);
                    } catch (SQLException acEx) {
                        JOptionPane.showMessageDialog(panel,
                                "Unable to restore connection state after write-off; restart the session. " + acEx.getMessage(),
                                "Database",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
                if (persisted) {
                    JOptionPane.showMessageDialog(panel, "Stock write-off completed.");
                    try {
                        loadWriteOffHistory(writeOffHistoryModel, connection);
                        deferPackTableColumns(writeOffHistoryTable);
                    } catch (SQLException reloadEx) {
                        JOptionPane.showMessageDialog(panel, "Saved, but history table could not refresh: " + reloadEx.getMessage(),
                                "Warning", JOptionPane.WARNING_MESSAGE);
                    }
                    requestMetricsRefresh();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid whole number.");
            }
        });
        JPanel footer = buildActionBar(null, submit);
        panel.add(form, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(center);
        center.add(historyScroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** Manage named bins; {@link DatabaseManager#STORAGE_LOCATION_UNASSIGNED_ID Unassigned} is fixed in the database for untracked stock. */
    private static JPanel buildStorageLocationsPanel(Connection connection) throws SQLException {
        DatabaseManager.ensureStorageLocationsAndBuckets(connection);
        JPanel panel = buildFormPanel("Storage Locations");
        JPanel intro = buildSectionPanel();
        intro.add(buildSectionText(
                "Shelf labels only — totals still live on Stock. Deletes are blocked until the bin has no qty. "
                        + "\"Unassigned\" cannot be renamed or deleted. Select a bin to see SKUs aggregated on the right."));
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            intro.add(Box.createVerticalStrut(12));
            intro.add(buildSectionText("Storage tables are not available on this database file."));
            panel.add(intro, BorderLayout.NORTH);
            return panel;
        }

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"ID", "Name", "Active"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        installTableCopyMenu(table);
        deferPackTableColumns(table);

        DefaultTableModel binDetailModel = new DefaultTableModel(
                new String[]{"Item code", "Item name", "Qty in bin"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable binDetailTable = new JTable(binDetailModel);
        binDetailTable.setAutoCreateRowSorter(true);
        installTableCopyMenu(binDetailTable);
        deferPackTableColumns(binDetailTable);

        JLabel binDetailHeading = new JLabel("Select a bin on the left to see its contents.");

        Runnable refreshBinContentsForSelection = () -> {
            try {
                int vr = table.getSelectedRow();
                if (vr < 0) {
                    binDetailModel.setRowCount(0);
                    binDetailHeading.setText("Select a bin on the left to see its contents.");
                    return;
                }
                int mr = table.convertRowIndexToModel(vr);
                int locId = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), "-1"));
                String locName = Objects.toString(model.getValueAt(mr, 1), "");
                reloadStorageBinContentsTable(binDetailModel, connection, locId);
                deferPackTableColumns(binDetailTable);
                binDetailHeading.setText(
                        locName.isBlank() ? ("Bin contents · #" + locId) : ("Bin contents · " + locName));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel,
                        "Unable to load bin contents: " + ex.getMessage(),
                        "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        };

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            refreshBinContentsForSelection.run();
        });

        Runnable reloadTable = () -> {
            try {
                int keepId = -1;
                int sr = table.getSelectedRow();
                if (sr >= 0) {
                    int mr = table.convertRowIndexToModel(sr);
                    keepId = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), "-1"));
                }
                reloadStorageLocationsIntoTable(model, connection);
                deferPackTableColumns(table);
                table.clearSelection();
                if (keepId >= 0) {
                    boolean found = false;
                    for (int mr = 0; mr < model.getRowCount(); mr++) {
                        int rowLocId = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), "-999"));
                        if (rowLocId == keepId) {
                            int vr = table.convertRowIndexToView(mr);
                            if (vr >= 0) {
                                table.getSelectionModel().setSelectionInterval(vr, vr);
                                found = true;
                            }
                            break;
                        }
                    }
                    if (!found) {
                        binDetailModel.setRowCount(0);
                        binDetailHeading.setText("Select a bin on the left to see its contents.");
                    }
                } else {
                    binDetailModel.setRowCount(0);
                    binDetailHeading.setText("Select a bin on the left to see its contents.");
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to refresh locations: " + ex.getMessage(), "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        };

        JTextField nameField = new JTextField();
        styleInput(nameField);

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 0, 4, 8);
        gc.gridy = 0;
        gc.gridx = 0;
        gc.anchor = GridBagConstraints.LINE_END;
        form.add(new JLabel("New bin name"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        gc.anchor = GridBagConstraints.LINE_START;
        form.add(nameField, gc);

        JButton addBtn = new JButton("Add bin");
        AppUI.stylePrimaryButton(addBtn);
        addBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Bin name cannot be blank.");
                return;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    """
                            INSERT INTO storage_locations (name, active, system_reserved)
                            VALUES (?,?,0)
                            """)) {
                insert.setString(1, name);
                insert.setInt(2, 1);
                insert.executeUpdate();
                nameField.setText("");
                reloadTable.run();
            } catch (SQLException ex) {
                String msg = ex.getMessage();
                if (msg != null && msg.toUpperCase(Locale.ROOT).contains("UNIQUE")) {
                    JOptionPane.showMessageDialog(panel,
                            "That name is already taken (names are compared case-insensitively).");
                } else {
                    JOptionPane.showMessageDialog(panel, "Unable to add bin: " + ex.getMessage(), "Database",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton renameBtn = new JButton("Rename selected…");
        styleSecondaryButton(renameBtn);
        renameBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Select a row first.");
                return;
            }
            int mr = table.convertRowIndexToModel(row);
            int id = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), ""));
            String current = Objects.toString(model.getValueAt(mr, 1), "");
            if (id == DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID) {
                JOptionPane.showMessageDialog(panel, "\"Unassigned\" cannot be renamed.");
                return;
            }
            String next = JOptionPane.showInputDialog(panel, "Rename bin", current);
            if (next == null) {
                return;
            }
            String trimmed = next.trim();
            if (trimmed.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Name cannot be blank.");
                return;
            }
            try (PreparedStatement up = connection.prepareStatement(
                    """
                            UPDATE storage_locations SET name = ?
                            WHERE id = ? AND id <> ? AND system_reserved = 0
                            """)) {
                up.setString(1, trimmed);
                up.setInt(2, id);
                up.setInt(3, DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID);
                if (up.executeUpdate() == 0) {
                    JOptionPane.showMessageDialog(panel, "Rename failed.");
                    return;
                }
                reloadTable.run();
            } catch (SQLException ex) {
                String msg = ex.getMessage();
                if (msg != null && msg.toUpperCase(Locale.ROOT).contains("UNIQUE")) {
                    JOptionPane.showMessageDialog(panel, "That name is already taken.");
                } else {
                    JOptionPane.showMessageDialog(panel, "Rename failed: " + ex.getMessage(), "Database",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton deleteBtn = new JButton("Delete selected");
        styleSecondaryButton(deleteBtn);
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Select a row first.");
                return;
            }
            int mr = table.convertRowIndexToModel(row);
            int id = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), ""));
            if (id == DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID) {
                JOptionPane.showMessageDialog(panel, "\"Unassigned\" cannot be deleted.");
                return;
            }
            int ok = JOptionPane.showConfirmDialog(panel, "Delete this bin?", "Confirm", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
            try (PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM storage_locations
                    WHERE id = ? AND id <> ? AND system_reserved = 0 AND NOT EXISTS (
                        SELECT 1 FROM inventory_storage_qty WHERE location_id = ?
                    )
                    """)) {
                int uid = DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
                delete.setInt(1, id);
                delete.setInt(2, uid);
                delete.setInt(3, id);
                if (delete.executeUpdate() == 0) {
                    JOptionPane.showMessageDialog(panel,
                            "Delete blocked — bin still holds stock or is the fixed Unassigned bucket.");
                    return;
                }
                reloadTable.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Delete failed: " + ex.getMessage(), "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton toggleActiveBtn = new JButton("Toggle active");
        styleSecondaryButton(toggleActiveBtn);
        toggleActiveBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Select a row first.");
                return;
            }
            int mr = table.convertRowIndexToModel(row);
            int id = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), ""));
            if (id == DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID) {
                JOptionPane.showMessageDialog(panel, "\"Unassigned\" cannot be deactivated.");
                return;
            }
            try (PreparedStatement up = connection.prepareStatement("""
                        UPDATE storage_locations
                        SET active = CASE WHEN active = 1 THEN 0 ELSE 1 END
                        WHERE id = ? AND system_reserved = 0
                        """)) {
                up.setInt(1, id);
                if (up.executeUpdate() == 0) {
                    JOptionPane.showMessageDialog(panel, "Protected system bins cannot be toggled.");
                    return;
                }
                reloadTable.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Toggle failed: " + ex.getMessage(), "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton refreshBtn = new JButton("Refresh");
        styleSecondaryButton(refreshBtn);
        refreshBtn.addActionListener(ae -> reloadTable.run());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(actions);
        actions.add(addBtn);
        actions.add(renameBtn);
        actions.add(deleteBtn);
        actions.add(toggleActiveBtn);
        actions.add(refreshBtn);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));
        scroll.setMinimumSize(new Dimension(200, 160));

        JScrollPane binDetailScroll = new JScrollPane(binDetailTable);
        binDetailScroll.setBorder(AppUI.newRoundedBorder(8));
        binDetailScroll.setMinimumSize(new Dimension(160, 160));

        JPanel binDetailWrap = new JPanel(new BorderLayout(0, 6));
        AppUI.applyPanelBackground(binDetailWrap);
        binDetailHeading.setForeground(AppUI.TEXT_MUTED);
        binDetailHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        binDetailWrap.add(binDetailHeading, BorderLayout.NORTH);
        binDetailWrap.add(binDetailScroll, BorderLayout.CENTER);
        binDetailWrap.setMinimumSize(new Dimension(200, 80));

        JSplitPane locSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, binDetailWrap);
        locSplit.setResizeWeight(2.0 / 3.0);
        locSplit.setContinuousLayout(true);
        locSplit.setBorder(null);
        locSplit.setDividerSize(6);
        locSplit.setMinimumSize(new Dimension(420, 200));
        SwingUtilities.invokeLater(() -> {
            int w = locSplit.getWidth();
            if (w > 0) {
                locSplit.setDividerLocation(Math.max(200, (int) (w * (2.0 / 3.0))));
            } else {
                locSplit.setDividerLocation(2.0 / 3.0);
            }
        });

        JPanel top = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(top);
        top.add(intro, BorderLayout.NORTH);
        top.add(locSplit, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(footer);
        footer.add(form, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.CENTER);

        reloadTable.run();
        panel.add(top, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * One row per SKU in a bin with {@code SUM(qty)} so duplicates collapse if they ever exist for the same bin.
     */
    private static void reloadStorageBinContentsTable(
            DefaultTableModel detailModel,
            Connection connection,
            int locationId
    ) throws SQLException {
        detailModel.setRowCount(0);
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT s.item_code AS item_code,
                       MAX(COALESCE(i.`Item Name`, '')) AS item_name,
                       SUM(s.qty) AS total_qty
                FROM inventory_storage_qty s
                LEFT JOIN inventory i ON i.`Item Code` = s.item_code
                WHERE s.location_id = ? AND s.qty > 0
                GROUP BY s.item_code
                ORDER BY s.item_code COLLATE NOCASE ASC
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    detailModel.addRow(new Object[]{
                            rs.getString("item_code"),
                            rs.getString("item_name"),
                            rs.getInt("total_qty")
                    });
                }
            }
        }
    }

    private static void reloadStorageLocationsIntoTable(DefaultTableModel model, Connection connection) throws SQLException {
        model.setRowCount(0);
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                """
                        SELECT id, name, active FROM storage_locations
                        ORDER BY sort_order ASC, name COLLATE NOCASE ASC
                        """)) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("active") != 0 ? "Yes" : "No"
                    });
                }
            }
        }
    }

    /** Shelf placement report filtered by SKU text / optional bin. */
    private static JPanel buildStockByLocationPanel(User user, Connection connection) throws SQLException {
        DatabaseManager.ensureStorageLocationsAndBuckets(connection);
        JPanel panel = buildFormPanel("Stock by Location");
        JLabel hint = buildSectionText(
                "Search by item code or name substring. Select a row to move qty to another bin.");

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Item code", "Item name", "Location", "Qty in bin", "Total stock"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        installTableCopyMenu(table);
        deferPackTableColumns(table);

        JTextField filter = new JTextField();
        filter.setBorder(AppUI.newRoundedBorder(8));
        JComboBox<StorageLocationPick> locPick = new JComboBox<>();

        Runnable reload = () -> {
            Object sel = locPick.getSelectedItem();
            int lid = sel instanceof StorageLocationPick sp ? sp.id : STOCK_REPORT_ALL_LOCATIONS_ID;
            try {
                reloadStockByLocationTable(model, connection, lid, filter.getText().trim());
                deferPackTableColumns(table);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not load report: " + ex.getMessage(), "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        };

        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            JPanel fallback = buildSectionPanel();
            fallback.add(hint);
            fallback.add(Box.createVerticalStrut(10));
            fallback.add(buildSectionText("Storage tables are not available."));
            panel.add(fallback, BorderLayout.NORTH);
            return panel;
        }

        refreshStockReportLocationCombo(locPick, connection);
        reload.run();

        filter.getDocument().addDocumentListener(new DocumentListener() {
            private void go() {
                reload.run();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                go();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                go();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                go();
            }
        });
        locPick.addActionListener(e -> reload.run());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));

        JPanel top = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(top);
        top.add(hint, BorderLayout.NORTH);
        top.add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(2, 2, 8, 8));
        AppUI.applyPanelBackground(controls);
        controls.add(new JLabel("Location"));
        styleComboMatchInputRow(locPick);
        controls.add(locPick);
        controls.add(new JLabel("Item filter"));
        controls.add(filter);

        JButton moveBtn = new JButton("Move selected bin quantity…");
        styleSecondaryButton(moveBtn);
        moveBtn.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(panel, "Select a bin line in the table first.");
                return;
            }
            int mr = table.convertRowIndexToModel(viewRow);
            String itemCode = Objects.toString(model.getValueAt(mr, 0), "").trim();
            String locationName = Objects.toString(model.getValueAt(mr, 2), "").trim();
            Object qCell = model.getValueAt(mr, 3);
            int maxQty = qCell instanceof Number n ? n.intValue() : Integer.parseInt(Objects.toString(qCell, "0").trim());
            if (itemCode.isBlank() || locationName.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Selected row is missing item code or location.");
                return;
            }
            if (maxQty <= 0) {
                JOptionPane.showMessageDialog(panel, "Nothing available to move from this bin line.");
                return;
            }
            try {
                int fromLocId = resolveStorageLocationIdForItemBin(connection, itemCode, locationName);
                JComboBox<StorageLocationPick> destCombo = new JComboBox<>();
                styleComboMatchInputRow(destCombo);
                fillStorageLocationComboExcluding(destCombo, connection, fromLocId);
                if (destCombo.getItemCount() == 0) {
                    JOptionPane.showMessageDialog(panel, "There is no other bin to move quantity to.");
                    return;
                }
                JTextField qtyField = new JTextField(String.valueOf(maxQty));
                styleInput(qtyField);
                JPanel dialogBody = new JPanel(new GridBagLayout());
                AppUI.applyPanelBackground(dialogBody);
                GridBagConstraints dc = new GridBagConstraints();
                dc.insets = new Insets(4, 0, 4, 10);
                dc.anchor = GridBagConstraints.WEST;
                dc.gridx = 0;
                dc.gridy = 0;
                dc.gridwidth = 2;
                JLabel summary = new JLabel("<html>"
                        + "Item <b>" + htmlEscapePlainTextForJLabel(itemCode) + "</b><br>"
                        + "From <b>" + htmlEscapePlainTextForJLabel(locationName) + "</b> &nbsp;(max " + maxQty + ")"
                        + "</html>");
                dialogBody.add(summary, dc);
                dc.gridwidth = 1;
                dc.gridy = 1;
                dc.fill = GridBagConstraints.HORIZONTAL;
                dc.weightx = 0;
                dialogBody.add(new JLabel("Destination bin *"), dc);
                dc.gridx = 1;
                dc.weightx = 1;
                dialogBody.add(destCombo, dc);
                dc.gridx = 0;
                dc.gridy = 2;
                dc.weightx = 0;
                dialogBody.add(new JLabel("Quantity *"), dc);
                dc.gridx = 1;
                dialogBody.add(qtyField, dc);

                int choice = JOptionPane.showConfirmDialog(
                        panel,
                        dialogBody,
                        "Move bin quantity",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);
                if (choice != JOptionPane.OK_OPTION) {
                    return;
                }
                Object destSel = destCombo.getSelectedItem();
                if (!(destSel instanceof StorageLocationPick destPick)) {
                    JOptionPane.showMessageDialog(panel, "Choose a destination bin.");
                    return;
                }
                int qtyMove = Integer.parseInt(qtyField.getText().trim());
                if (qtyMove <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                if (qtyMove > maxQty) {
                    JOptionPane.showMessageDialog(panel,
                            "Quantity cannot exceed qty in bin (" + maxQty + ").");
                    return;
                }
                moveInventoryBetweenStorageLocations(connection, user, itemCode, fromLocId, destPick.id, qtyMove);
                JOptionPane.showMessageDialog(panel, "Moved " + qtyMove + " units to " + destPick.label + ".");
                reload.run();
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(panel, "Enter a valid whole number for quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel,
                        "Could not move: " + ex.getMessage(),
                        "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(footer);
        footer.add(controls, BorderLayout.NORTH);
        JPanel moveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(moveRow);
        moveRow.add(moveBtn);
        footer.add(moveRow, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** Short bin-to-bin move flow starting from item code (same backend as Stock by Location). */
    private static JPanel buildQuickTransferPanel(User user, Connection connection) throws SQLException {
        DatabaseManager.ensureStorageLocationsAndBuckets(connection);
        JPanel panel = buildFormPanel("Quick Transfer");
        JPanel intro = buildSectionPanel();
        intro.add(buildSectionText(
                "Move quantity between bins without searching the full stock-by-location table. "
                        + "Total stock on hand is unchanged — only shelf placement updates."));
        panel.add(intro, BorderLayout.NORTH);

        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            panel.add(buildSectionText("Storage tables are not available."), BorderLayout.CENTER);
            return panel;
        }

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints gb = new GridBagConstraints();
        gb.insets = new Insets(4, 0, 4, 10);

        JTextField itemCode = new JTextField();
        JTextField itemDesc = new JTextField();
        JComboBox<StorageLocationPick> fromCombo = new JComboBox<>();
        JComboBox<StorageLocationPick> toCombo = new JComboBox<>();
        JTextField qtyField = new JTextField();
        styleInput(itemCode, itemDesc, qtyField);
        styleAutoFilledInventoryField(itemDesc);
        styleComboMatchInputRow(fromCombo, toCombo);

        int r = 0;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Item Code *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(itemCode, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Item Description"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(itemDesc, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("From bin *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(fromCombo, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("To bin *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(toCombo, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Quantity *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(qtyField, gb);

        wireInventoryItemDescriptionLookup(connection, itemCode, itemDesc);

        final int[] maxAtSource = {0};

        Runnable reloadDestination = () -> {
            StorageLocationPick from = (StorageLocationPick) fromCombo.getSelectedItem();
            int fromId = from != null ? from.id : -1;
            toCombo.removeAllItems();
            if (fromId > 0) {
                try {
                    fillStorageLocationComboExcluding(toCombo, connection, fromId);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Could not load destination bins: " + ex.getMessage(),
                            "Quick Transfer", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        Runnable reloadSourceBins = () -> {
            String code = itemCode.getText().trim();
            fromCombo.removeAllItems();
            toCombo.removeAllItems();
            maxAtSource[0] = 0;
            qtyField.setText("");
            if (code.isEmpty()) {
                return;
            }
            try {
                fillStorageLocationComboForItemBins(fromCombo, connection, code);
                if (fromCombo.getItemCount() == 1) {
                    fromCombo.setSelectedIndex(0);
                    StorageLocationPick pick = (StorageLocationPick) fromCombo.getSelectedItem();
                    if (pick != null) {
                        maxAtSource[0] = pick.qtyAvailable;
                        qtyField.setText(String.valueOf(maxAtSource[0]));
                    }
                    reloadDestination.run();
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not load source bins: " + ex.getMessage(),
                        "Quick Transfer", JOptionPane.ERROR_MESSAGE);
            }
        };

        DocumentListener codeListener = new DocumentListener() {
            private void go() {
                reloadSourceBins.run();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                go();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                go();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                go();
            }
        };
        itemCode.getDocument().addDocumentListener(codeListener);

        fromCombo.addActionListener(e -> {
            StorageLocationPick pick = (StorageLocationPick) fromCombo.getSelectedItem();
            maxAtSource[0] = pick != null ? pick.qtyAvailable : 0;
            if (maxAtSource[0] > 0) {
                qtyField.setText(String.valueOf(maxAtSource[0]));
            }
            reloadDestination.run();
        });

        JButton transfer = new JButton("Transfer");
        AppUI.stylePrimaryButton(transfer);
        transfer.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            StorageLocationPick from = (StorageLocationPick) fromCombo.getSelectedItem();
            StorageLocationPick to = (StorageLocationPick) toCombo.getSelectedItem();
            if (from == null || from.id <= 0) {
                JOptionPane.showMessageDialog(panel, "Choose a source bin that holds this item.");
                return;
            }
            if (to == null || to.id <= 0) {
                JOptionPane.showMessageDialog(panel, "Choose a destination bin.");
                return;
            }
            try {
                int qty = Integer.parseInt(qtyField.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                if (qty > maxAtSource[0]) {
                    JOptionPane.showMessageDialog(panel,
                            "Source bin only has " + maxAtSource[0] + " units.");
                    return;
                }
                moveInventoryBetweenStorageLocations(connection, user, code, from.id, to.id, qty);
                JOptionPane.showMessageDialog(panel,
                        "Moved " + qty + " from " + from.label + " to " + to.label + ".",
                        "Quick Transfer", JOptionPane.INFORMATION_MESSAGE);
                recordRecentItem(code, queryInventoryItemDescription(connection, code));
                reloadSourceBins.run();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid whole number for quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not transfer: " + ex.getMessage(),
                        "Quick Transfer", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(center);
        center.add(form, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(buildActionBar(null, transfer), BorderLayout.SOUTH);
        return panel;
    }

    private static void refreshStockReportLocationCombo(JComboBox<StorageLocationPick> combo, Connection connection) throws SQLException {
        Object prior = combo.getSelectedItem();
        int priorId = prior instanceof StorageLocationPick sp ? sp.id : STOCK_REPORT_ALL_LOCATIONS_ID;
        combo.removeAllItems();
        combo.addItem(new StorageLocationPick(STOCK_REPORT_ALL_LOCATIONS_ID, "— All locations —"));
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                        SELECT id, name FROM storage_locations
                        ORDER BY sort_order ASC, name COLLATE NOCASE ASC
                        """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    combo.addItem(new StorageLocationPick(rs.getInt("id"), rs.getString("name")));
                }
            }
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            StorageLocationPick cand = combo.getItemAt(i);
            if (cand != null && cand.id == priorId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(0);
    }

    private static void reloadStockByLocationTable(
            DefaultTableModel model,
            Connection connection,
            int locationPickId,
            String filterTrimmed
    ) throws SQLException {
        model.setRowCount(0);
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        StringBuilder sb = new StringBuilder("""
                SELECT s.item_code AS item_code,
                       i.`Item Name` AS item_name,
                       sl.name AS loc_name,
                       s.qty AS bin_qty,
                       i.`Stock` AS total_stock
                FROM inventory_storage_qty s
                JOIN inventory i ON i.`Item Code` = s.item_code
                JOIN storage_locations sl ON sl.id = s.location_id
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (locationPickId != STOCK_REPORT_ALL_LOCATIONS_ID) {
            sb.append(" AND s.location_id = ?");
            params.add(locationPickId);
        }
        if (filterTrimmed != null && !filterTrimmed.isBlank()) {
            sb.append("""
                     AND (
                        LOWER(i.`Item Code`) LIKE '%' || LOWER(?) || '%'
                        OR LOWER(COALESCE(i.`Item Name`, '')) LIKE '%' || LOWER(?) || '%'
                    )
                    """);
            params.add(filterTrimmed);
            params.add(filterTrimmed);
        }
        sb.append("""
                 ORDER BY sl.sort_order ASC,
                          sl.name COLLATE NOCASE ASC,
                          item_code COLLATE NOCASE ASC
                """);
        try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer integ) {
                    ps.setInt(idx++, integ);
                } else {
                    ps.setString(idx++, Objects.toString(p, ""));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getString("item_code"),
                            rs.getString("item_name"),
                            rs.getString("loc_name"),
                            rs.getInt("bin_qty"),
                            rs.getInt("total_stock")
                    });
                }
            }
        }
    }

    /**
     * Builds process-sale panel with multi-line sale draft table.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer card layout host panel
     * @return sale processing panel
     * @throws SQLException when storage tables cannot be prepared for bin-aware checkout
     */
    private static JPanel buildProcessSalePanel(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        DatabaseManager.ensureStorageLocationsAndBuckets(connection);
        boolean trackBins = DatabaseManager.hasInventoryStorageQtyTable(connection);

        JPanel panel = buildFormPanel("Process Sale");

        JLabel hint = buildSectionText(trackBins
                ? "Pick which bin each line ships from (sell-from quantity must be available there). Revenue and profit use sale prices minus FIFO cost."
                : "Enter the unit sale price for each line. Revenue and profit in the top bar use these prices minus FIFO cost.");

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints rg = new GridBagConstraints();
        rg.insets = new Insets(4, 0, 4, 10);
        int rf = 0;
        JTextField itemCode = new JTextField();
        JTextField itemDesc = new JTextField();
        JTextField quantity = new JTextField();
        JTextField unitSalePrice = new JTextField();
        JComboBox<StorageLocationPick> saleLocationCombo = new JComboBox<>();
        styleInput(itemCode, itemDesc, quantity, unitSalePrice);
        styleAutoFilledInventoryField(itemDesc);
        if (trackBins) {
            refreshActiveStorageLocationCombo(saleLocationCombo, connection);
            styleComboMatchInputRow(saleLocationCombo);
        }
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Code *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(itemCode, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Description"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        itemDesc.setToolTipText("From inventory — updates when Item Code matches a SKU.");
        form.add(itemDesc, rg);
        rf++;
        if (trackBins) {
            rg.gridx = 0;
            rg.gridy = rf;
            rg.anchor = GridBagConstraints.LINE_END;
            rg.fill = GridBagConstraints.NONE;
            rg.weightx = 0;
            form.add(new JLabel("Sell from *"), rg);
            rg.gridx = 1;
            rg.anchor = GridBagConstraints.LINE_START;
            rg.fill = GridBagConstraints.HORIZONTAL;
            rg.weightx = 1;
            JLabel saleLocHint = new JLabel("Bins are shelf labels — total Stock must still cover the sale.");
            saleLocHint.setFont(saleLocHint.getFont().deriveFont(11f));
            JPanel storageRow = new JPanel(new BorderLayout(8, 0));
            AppUI.applyPanelBackground(storageRow);
            storageRow.add(saleLocationCombo, BorderLayout.CENTER);
            storageRow.add(saleLocHint, BorderLayout.SOUTH);
            form.add(storageRow, rg);
            rf++;
        }
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Quantity *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(quantity, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Unit sale price *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(unitSalePrice, rg);
        rf++;

        wireInventoryItemDescriptionLookup(connection, itemCode, itemDesc);
        wireSaleUnitPriceFromMarket(connection, itemCode, unitSalePrice);

        Map<String, SaleDraftLine> items = new LinkedHashMap<>();
        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Item Code", "Location", "Item Description", "Quantity", "Unit sale price"}, 0);
        JTable table = new JTable(model);
        configureProcessSaleDraftTable(table);

        JButton addLine = new JButton("Add Line");
        styleSecondaryButton(addLine);
        JButton removeLine = new JButton("Remove selected");
        styleSecondaryButton(removeLine);
        JButton clearCart = new JButton("Clear cart");
        styleSecondaryButton(clearCart);
        JButton submit = new JButton("Complete Sale");
        AppUI.stylePrimaryButton(submit);

        JLabel cartSummary = new JLabel(" ");
        cartSummary.setForeground(AppUI.TEXT_MUTED);

        JTextField transactionNote = new JTextField();
        styleInput(transactionNote);
        transactionNote.setToolTipText("Optional — one note for this entire sale (e.g. discount reason).");

        Runnable refreshCartUi = () -> {
            refreshSaleDraftTable(model, items, table);
            int units = 0;
            double total = 0;
            for (SaleDraftLine line : items.values()) {
                units += line.quantity;
                total += line.quantity * line.unitSalePrice;
            }
            cartSummary.setText(items.isEmpty()
                    ? "Cart is empty"
                    : String.format(Locale.US, "%d lines, %d units, %s",
                    items.size(), units, formatUsdMoney(total)));
        };

        addLine.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            Integer locId = null;
            String locLabel = "";
            if (trackBins) {
                StorageLocationPick pick = (StorageLocationPick) saleLocationCombo.getSelectedItem();
                if (pick == null) {
                    JOptionPane.showMessageDialog(panel, "Choose a storage location for this line.");
                    return;
                }
                locId = pick.id;
                locLabel = pick.label;
            }
            String key = saleDraftLineMapKey(code, locId);
            try {
                int qty = Integer.parseInt(quantity.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                double unitPrice = Double.parseDouble(unitSalePrice.getText().trim());
                if (unitPrice < 0) {
                    JOptionPane.showMessageDialog(panel, "Unit sale price cannot be negative.");
                    return;
                }
                int stock = getIntValue(connection, "SELECT `Stock` FROM inventory WHERE `Item Code` = ?", code);
                if (stock <= 0) {
                    JOptionPane.showMessageDialog(panel, "Item is out of stock or not found.");
                    return;
                }
                int sumSameSku = 0;
                for (SaleDraftLine dl : items.values()) {
                    if (dl.itemCode.equals(code)) {
                        sumSameSku += dl.quantity;
                    }
                }
                int projectedSkuTotal = sumSameSku + qty;
                if (items.containsKey(key)) {
                    projectedSkuTotal -= items.get(key).quantity;
                }
                if (projectedSkuTotal > stock) {
                    JOptionPane.showMessageDialog(panel, "Not enough stock available for this SKU across this sale (including other bins).");
                    return;
                }
                if (trackBins && locId != null) {
                    int binQty = getInventoryStorageQtyAtLocation(connection, code, locId);
                    int alreadyAtKey = items.containsKey(key) ? items.get(key).quantity : 0;
                    if (alreadyAtKey + qty > binQty) {
                        JOptionPane.showMessageDialog(panel,
                                "Not enough quantity in the selected bin for this line (including lines already in the sale).");
                        return;
                    }
                }
                String nameForLine = queryInventoryItemDescription(connection, code);
                if (items.containsKey(key)) {
                    SaleDraftLine existing = items.get(key);
                    if (Double.compare(existing.unitSalePrice, unitPrice) != 0) {
                        JOptionPane.showMessageDialog(panel,
                                "This item from this bin is already on the sale at a different unit price. Remove it first or use the same price.");
                        return;
                    }
                    existing.quantity += qty;
                } else {
                    items.put(key, new SaleDraftLine(code, qty, unitPrice, nameForLine, locId, locLabel));
                }
                refreshCartUi.run();
                itemCode.setText("");
                quantity.setText("");
                unitSalePrice.setText("");
                itemDesc.setText("");
                itemCode.requestFocusInWindow();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid quantity and unit sale price.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        removeLine.addActionListener(e -> {
            int vr = table.getSelectedRow();
            if (vr < 0) {
                JOptionPane.showMessageDialog(panel, "Select a cart line to remove.");
                return;
            }
            int mr = table.convertRowIndexToModel(vr);
            String code = Objects.toString(model.getValueAt(mr, 0), "").trim();
            String locLabel = Objects.toString(model.getValueAt(mr, 1), "").trim();
            String keyToRemove = null;
            for (Map.Entry<String, SaleDraftLine> entry : items.entrySet()) {
                SaleDraftLine line = entry.getValue();
                String label = line.storageLocationLabel.isEmpty() ? "—" : line.storageLocationLabel;
                if (line.itemCode.equals(code) && label.equals(locLabel)) {
                    keyToRemove = entry.getKey();
                    break;
                }
            }
            if (keyToRemove != null) {
                items.remove(keyToRemove);
                refreshCartUi.run();
            }
        });

        clearCart.addActionListener(e -> {
            if (items.isEmpty()) {
                return;
            }
            int ok = JOptionPane.showConfirmDialog(panel, "Remove all lines from the cart?", "Clear cart",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok == JOptionPane.OK_OPTION) {
                items.clear();
                refreshCartUi.run();
                itemCode.requestFocusInWindow();
            }
        });

        submit.addActionListener(e -> {
            if (items.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Add at least one item first.");
                return;
            }
            String rawNote = transactionNote.getText();
            if (rawNote != null && rawNote.length() > MAX_SALE_TRANSACTION_NOTE_LENGTH) {
                JOptionPane.showMessageDialog(panel,
                        "Transaction note must be at most " + MAX_SALE_TRANSACTION_NOTE_LENGTH + " characters.");
                return;
            }
            String reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String now = dateTime.nowDisplayString();
            try {
                processSaleTransaction(connection, user, items, reference, now, rawNote);
                for (SaleDraftLine line : items.values()) {
                    recordRecentItem(line.itemCode, line.itemDescription);
                }
                JOptionPane.showMessageDialog(panel, "Sale completed. Reference: " + reference);
                transactionNote.setText("");
                showView(workspaceContainer, "View Sales Transaction", buildSalesPanel(user, connection, workspaceContainer));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        JPanel upper = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(upper);
        upper.add(hint, BorderLayout.NORTH);
        upper.add(form, BorderLayout.CENTER);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(addRow);
        addRow.add(addLine);
        upper.add(addRow, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(body);
        body.add(upper, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(AppUI.newRoundedBorder(8));
        body.add(tableScroll, BorderLayout.CENTER);
        JPanel cartActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(cartActions);
        cartActions.add(removeLine);
        cartActions.add(clearCart);
        cartActions.add(cartSummary);
        body.add(cartActions, BorderLayout.SOUTH);

        refreshCartUi.run();

        JPanel footer = new JPanel(new GridBagLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        AppUI.applyPanelBackground(footer);
        GridBagConstraints gf = new GridBagConstraints();
        gf.insets = new Insets(4, 0, 4, 10);
        gf.gridx = 0;
        gf.gridy = 0;
        gf.anchor = GridBagConstraints.LINE_END;
        gf.fill = GridBagConstraints.NONE;
        gf.weightx = 0;
        footer.add(new JLabel("Note (optional)"), gf);
        gf.gridx = 1;
        gf.anchor = GridBagConstraints.LINE_START;
        gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1;
        footer.add(transactionNote, gf);
        gf.gridx = 2;
        gf.anchor = GridBagConstraints.LINE_END;
        gf.fill = GridBagConstraints.NONE;
        gf.weightx = 0;
        footer.add(submit, gf);

        panel.add(body, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(() -> itemCode.requestFocusInWindow());
        return panel;
    }

    /**
     * Executes a sale transaction atomically across all related tables.
     *
     * @param connection active database connection
     * @param user active signed-in user
     * @param items map keyed by {@link #saleDraftLineMapKey}; values hold SKU, bin (when tracked), quantity and unit sale price
     * @param reference sale reference
     * @param now formatted timestamp
     * @param transactionNote optional single note for the whole checkout (stored on each line row)
     * @throws SQLException when transaction fails
     */
    private static void processSaleTransaction(
            Connection connection,
            User user,
            Map<String, SaleDraftLine> items,
            String reference,
            String now,
            String transactionNote
    ) throws SQLException {
        String noteForDb = normalizedSaleTransactionNote(transactionNote);
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Map.Entry<String, SaleDraftLine> entry : items.entrySet()) {
                SaleDraftLine line = entry.getValue();
                String code = line.itemCode;
                int qty = line.quantity;
                double unitSalePrice = line.unitSalePrice;
                String itemName;
                try (PreparedStatement fetch = connection.prepareStatement("SELECT `Item Name` FROM inventory WHERE `Item Code` = ?")) {
                    fetch.setString(1, code);
                    try (ResultSet rs = fetch.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Item not found: " + code);
                        }
                        itemName = rs.getString("Item Name");
                    }
                }

                double fifoCost = InventoryFifo.fifoCostWithLatestLayerFallback(connection, code, qty);

                String dateIso = toIsoDateTime(now);
                try (PreparedStatement insertSale = connection.prepareStatement(
                        "INSERT INTO sales (`Item Code`, `Item Name`, `Amount`, `Total Price`, `Total Cost`, `Reference`, `User`, `Date`, `DateISO`, `Note`, storage_location_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    insertSale.setString(1, code);
                    insertSale.setString(2, itemName);
                    insertSale.setInt(3, qty);
                    insertSale.setDouble(4, unitSalePrice * qty);
                    insertSale.setDouble(5, fifoCost);
                    insertSale.setString(6, reference);
                    insertSale.setString(7, user.getUsername());
                    insertSale.setString(8, now);
                    insertSale.setString(9, dateIso);
                    insertSale.setString(10, noteForDb);
                    if (line.storageLocationId == null) {
                        insertSale.setNull(11, java.sql.Types.INTEGER);
                    } else {
                        insertSale.setInt(11, line.storageLocationId);
                    }
                    insertSale.executeUpdate();
                }
                try (PreparedStatement updateStock = connection.prepareStatement("UPDATE inventory SET `Stock` = `Stock` - ? WHERE `Item Code` = ? AND `Stock` >= ?")) {
                    updateStock.setInt(1, qty);
                    updateStock.setString(2, code);
                    updateStock.setInt(3, qty);
                    int affected = updateStock.executeUpdate();
                    if (affected == 0) {
                        throw new SQLException("Insufficient stock while processing sale for item: " + code);
                    }
                }
                if (line.storageLocationId != null) {
                    deductInventoryStorageQtyAtLocation(connection, code, line.storageLocationId, qty);
                } else {
                    deductInventoryStorageQtySpread(connection, code, qty);
                }
                String saleReason = "CUSTOMER_SALE";
                if (line.storageLocationId != null) {
                    saleReason += " locId=" + line.storageLocationId;
                }
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, code);
                    movement.setString(2, String.valueOf(qty));
                    movement.setString(3, "SALE");
                    movement.setString(4, saleReason);
                    movement.setString(5, user.getUsername());
                    movement.setString(6, now);
                    movement.executeUpdate();
                }
            }

            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /** Opens the shared password-reset dialog; on success closes the workspace so the user can sign in again. */
    private static JPanel buildResetPasswordPanel(User user, JFrame frame, AccountActions accountActions) {
        JPanel panel = buildFormPanel("Reset Password");
        JPanel content = buildSectionPanel();
        content.add(buildSectionText("Update your password. After a successful change you will return to the sign-in screen."));
        content.add(Box.createVerticalStrut(14));
        JButton openDialog = new JButton("Change password...");
        AppUI.stylePrimaryButton(openDialog);
        openDialog.setAlignmentX(Component.LEFT_ALIGNMENT);
        openDialog.addActionListener(e -> {
            AccountActions.PasswordResetOutcome outcome = accountActions.showPasswordResetDialog(frame, user);
            if (outcome == AccountActions.PasswordResetOutcome.SUCCESS) {
                frame.dispose();
            }
        });
        content.add(openDialog);
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    /** Compact strip for Administration Tools page (avoid nested full-page headings). */
    private static JPanel buildResetPasswordInlineForAdminTools(User user, JFrame frame, AccountActions accountActions) {
        JPanel block = buildSectionPanel();
        block.add(adminToolsSectionTitle("Reset password"));
        block.add(Box.createVerticalStrut(6));
        JLabel hint = buildSectionText("Updates your signed-in password. After a successful change you return to the sign-in screen.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(hint);
        block.add(Box.createVerticalStrut(10));
        JButton openDialog = new JButton("Change password…");
        AppUI.stylePrimaryButton(openDialog);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        AppUI.applyPanelBackground(row);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(openDialog);
        openDialog.addActionListener(e -> {
            AccountActions.PasswordResetOutcome outcome = accountActions.showPasswordResetDialog(frame, user);
            if (outcome == AccountActions.PasswordResetOutcome.SUCCESS) {
                frame.dispose();
            }
        });
        block.add(row);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        return block;
    }

    private static final String PROFIT_ALERT_BANNER_SETUP_MESSAGE =
            "Go to admin tools to setup profit alert feature or disable banner";

    private static final int PROFIT_ALERT_BANNER_MAX_ITEMS_LISTED = 35;

    private static String profitAlertItemListPhrase(List<String> labels) {
        if (labels.isEmpty()) {
            return "none currently meet this threshold";
        }
        int n = labels.size();
        if (n > PROFIT_ALERT_BANNER_MAX_ITEMS_LISTED) {
            List<String> sub = new ArrayList<>(labels.subList(0, PROFIT_ALERT_BANNER_MAX_ITEMS_LISTED));
            return String.join(", ", sub) + " — +" + (n - PROFIT_ALERT_BANNER_MAX_ITEMS_LISTED) + " more SKU(s)";
        }
        return String.join(", ", labels);
    }

    private static List<String> profitAlertQualifyingItemLabels(Connection connection, int goalPct) throws SQLException {
        double mult = 1.0 + goalPct / 100.0;
        List<String> labels = new ArrayList<>();
        String sql = """
                SELECT i.`Item Code` AS ic,
                       i.`Item Name` AS iname
                FROM inventory i
                INNER JOIN (
                    SELECT item_code AS icode,
                           SUM(CAST(qty_remaining AS REAL) * unit_cost) AS w,
                           SUM(qty_remaining) AS r
                    FROM inventory_cost_layers
                    WHERE qty_remaining > 0
                    GROUP BY item_code
                ) layer_tot ON layer_tot.icode = i.`Item Code`
                WHERE i.`Stock` > 0
                  AND i.`Market Price` IS NOT NULL
                  AND layer_tot.r > 0
                  AND (CAST(layer_tot.w AS REAL) / layer_tot.r) > 0
                  AND i.`Market Price` >= (CAST(layer_tot.w AS REAL) / layer_tot.r) * ?
                ORDER BY i.`Item Code` COLLATE NOCASE ASC
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, mult);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("ic");
                    String nm = Objects.toString(rs.getString("iname"), "").trim();
                    String shortNm = nm.length() > 28 ? nm.substring(0, 27) + "…" : nm;
                    if (shortNm.isEmpty()) {
                        labels.add(code);
                    } else {
                        labels.add(code + " (" + shortNm + ")");
                    }
                }
            }
        }
        return labels;
    }

    /** Single horizontal scrolling stripe (profit green or amber setup prompt). */
    private static final class AlertMarqueeStripe extends JPanel {
        private static final int STRIPE_HEIGHT_PX = 34;
        private static final int TIMER_MS = 32;
        private static final int SCROLL_STEP_PX = 2;
        private static final String MARQUEE_GAP = "     ";

        private final Color stripeFg;
        private final Timer timer;
        private int scrollPx;
        private volatile String marqueeSegment = "";
        private volatile int marqueeLoopWidthPx = 400;

        AlertMarqueeStripe(Color stripeBg, Color stripeFg, Color stripeBorderTop) {
            this.stripeFg = stripeFg;
            setOpaque(true);
            setBackground(stripeBg);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, stripeBorderTop),
                    BorderFactory.createEmptyBorder(4, 0, 4, 0)));
            setPreferredSize(new Dimension(480, STRIPE_HEIGHT_PX));
            setMinimumSize(new Dimension(80, STRIPE_HEIGHT_PX));
            timer = new Timer(TIMER_MS, e -> {
                if (!isShowing()) {
                    return;
                }
                int w = Math.max(1, marqueeLoopWidthPx);
                scrollPx = (scrollPx + SCROLL_STEP_PX) % w;
                repaint();
            });
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, STRIPE_HEIGHT_PX);
        }

        void stopTimer() {
            timer.stop();
        }

        void startTimerIfShowing() {
            if (isShowing()) {
                timer.start();
            }
        }

        void prepareMarquee(String segment) {
            marqueeSegment = segment == null || segment.isEmpty() ? " " : segment;
            Font font = getFont().deriveFont(Font.BOLD, 13f);
            FontMetrics fm = getFontMetrics(font);
            String unit = marqueeSegment + MARQUEE_GAP;
            marqueeLoopWidthPx = Math.max(80, fm.stringWidth(unit));
            scrollPx = 0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (marqueeSegment.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Font font = getFont().deriveFont(Font.BOLD, 13f);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();
                String unit = marqueeSegment + MARQUEE_GAP;
                int h = getHeight();
                int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;
                int wPanel = getWidth();
                int lw = Math.max(1, marqueeLoopWidthPx);
                int offset = scrollPx % lw;
                g2.setColor(getBackground());
                g2.fillRect(0, 0, wPanel, h);
                g2.setColor(stripeFg);
                for (int x = -offset; x < wPanel + lw; x += lw) {
                    g2.drawString(unit, x, baseline);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Bottom-of-frame marquee driven by {@link DatabaseManager#META_PROFIT_ALERT_GOAL_PCT} and banner disabled flag.
     */
    private static final class ProfitAlertMarqueeBanner extends JPanel {
        private static final Color SETUP_BG = new Color(0x2a2418);
        private static final Color SETUP_FG = new Color(0xfbbf24);
        private static final Color SETUP_BORDER = new Color(0xb45309);
        private static final Color PROFIT_BG = new Color(0x0f2419);
        private static final Color PROFIT_FG = AppUI.SUCCESS;
        private static final Color PROFIT_BORDER = new Color(0x059669);

        private final AlertMarqueeStripe setupStripe;
        private final AlertMarqueeStripe profitStripe;

        ProfitAlertMarqueeBanner() {
            setLayout(new BorderLayout());
            setupStripe = new AlertMarqueeStripe(SETUP_BG, SETUP_FG, SETUP_BORDER);
            profitStripe = new AlertMarqueeStripe(PROFIT_BG, PROFIT_FG, PROFIT_BORDER);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
        }

        void stopTimer() {
            setupStripe.stopTimer();
            profitStripe.stopTimer();
        }

        void refreshFromDatabase(Connection connection) {
            setupStripe.stopTimer();
            profitStripe.stopTimer();
            removeAll();
            setLayout(new BorderLayout());
            try {
                if ("1".equals(DatabaseManager.getAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_BANNER_DISABLED))) {
                    setVisible(false);
                    revalidate();
                    repaint();
                    return;
                }
                String goalRaw = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_GOAL_PCT);
                int profitGoal = -1;
                if (goalRaw != null && !goalRaw.isBlank()) {
                    try {
                        profitGoal = Integer.parseInt(goalRaw.trim());
                    } catch (NumberFormatException ex) {
                        profitGoal = -1;
                    }
                }
                boolean profitOk = profitGoal >= 0 && profitGoal <= 10_000_000;
                final AlertMarqueeStripe activeStripe;
                if (!profitOk) {
                    add(setupStripe, BorderLayout.CENTER);
                    setupStripe.prepareMarquee(PROFIT_ALERT_BANNER_SETUP_MESSAGE);
                    activeStripe = setupStripe;
                } else {
                    List<String> plabels = profitAlertQualifyingItemLabels(connection, profitGoal);
                    String profitMsg = "Goal of " + profitGoal + "% profit on the following items: "
                            + profitAlertItemListPhrase(plabels);
                    add(profitStripe, BorderLayout.CENTER);
                    profitStripe.prepareMarquee(profitMsg);
                    activeStripe = profitStripe;
                }
                setVisible(true);
                revalidate();
                repaint();
                SwingUtilities.invokeLater(activeStripe::startTimerIfShowing);
            } catch (SQLException ex) {
                removeAll();
                setLayout(new BorderLayout());
                add(setupStripe, BorderLayout.CENTER);
                setupStripe.prepareMarquee("Profit alert: could not load data.");
                setVisible(true);
                revalidate();
                repaint();
                SwingUtilities.invokeLater(setupStripe::startTimerIfShowing);
            }
        }
    }

    private static JPanel buildProfitAlertAdminToolsSection(User user, Connection connection, JFrame frame) {
        ensureAdmin(user, VIEW_ADMIN_TOOLS);
        JPanel block = buildSectionPanel();
        block.add(adminToolsSectionTitle("Profit alert banner"));
        block.add(Box.createVerticalStrut(6));
        block.add(buildSectionText(
                "Enter a profit goal as a percent above weighted-average FIFO unit cost on remaining receipts "
                        + "(for example 100 means market price ≥ double that unit cost). "
                        + "Stocked SKUs that qualify scroll along the bottom on a green stripe until you hide the banner."));
        block.add(Box.createVerticalStrut(10));

        JTextField goalField = new JTextField(12);
        styleInput(goalField);
        String goalLoaded = "";
        boolean hideLoaded = false;
        try {
            String g = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_GOAL_PCT);
            goalLoaded = g == null ? "" : g.trim();
            hideLoaded = "1".equals(DatabaseManager.getAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_BANNER_DISABLED));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(frame, "Could not load profit alert settings: " + ex.getMessage(),
                    VIEW_ADMIN_TOOLS, JOptionPane.WARNING_MESSAGE);
        }
        goalField.setText(goalLoaded);

        JCheckBox hideBanner = new JCheckBox("Hide profit alert banner (bottom of workspace)");
        hideBanner.setOpaque(false);
        hideBanner.setSelected(hideLoaded);

        JPanel goalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        AppUI.applyPanelBackground(goalRow);
        goalRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        goalRow.add(new JLabel("Profit goal (%)"));
        goalRow.add(goalField);

        block.add(goalRow);
        block.add(Box.createVerticalStrut(6));
        hideBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(hideBanner);
        block.add(Box.createVerticalStrut(12));

        JButton save = new JButton("Save profit alert settings");
        AppUI.stylePrimaryButton(save);
        save.setAlignmentX(Component.LEFT_ALIGNMENT);
        save.addActionListener(e -> {
            try {
                String raw = goalField.getText().trim();
                if (raw.isEmpty()) {
                    DatabaseManager.deleteAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_GOAL_PCT);
                } else {
                    int v = Integer.parseInt(raw);
                    if (v < 0 || v > 10_000_000) {
                        JOptionPane.showMessageDialog(frame, "Enter a non-negative whole percent, or leave blank to clear.",
                                "Profit alert", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    DatabaseManager.putAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_GOAL_PCT, Integer.toString(v));
                }
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_BANNER_DISABLED,
                        hideBanner.isSelected() ? "1" : "0");
                JOptionPane.showMessageDialog(frame, "Profit alert settings saved.", "Profit alert",
                        JOptionPane.INFORMATION_MESSAGE);
                scheduleProfitAlertBannerRefresh(connection);
            } catch (NumberFormatException nf) {
                JOptionPane.showMessageDialog(frame, "Profit goal must be a whole number.", "Profit alert",
                        JOptionPane.WARNING_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Database error: " + ex.getMessage(), "Profit alert",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        block.add(save);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        return block;
    }

    /** Admin workspace: users, signed-in reset, then backup on one scrollable column. */
    private static JPanel buildAdministrationToolsPanel(
            User user, Connection connection, JFrame frame, AccountActions accountActions
    ) {
        ensureAdmin(user, VIEW_ADMIN_TOOLS);
        JPanel shell = buildFormPanel(VIEW_ADMIN_TOOLS);
        JPanel column = buildSectionPanel();

        JPanel userMgmt = buildUserManagementPanel(user, connection);
        userMgmt.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(userMgmt);

        column.add(Box.createVerticalStrut(22));
        JPanel resetStrip = buildResetPasswordInlineForAdminTools(user, frame, accountActions);
        resetStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(resetStrip);

        column.add(Box.createVerticalStrut(22));
        JPanel profitAlert = buildProfitAlertAdminToolsSection(user, connection, frame);
        profitAlert.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(profitAlert);

        column.add(Box.createVerticalStrut(22));
        JPanel changeHistory = buildChangeHistoryAdminToolsSection(connection, frame);
        changeHistory.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(changeHistory);

        column.add(Box.createVerticalStrut(22));
        JPanel backupSection = buildBackupSectionPanel(user, connection, accountActions, frame);
        backupSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(backupSection);

        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane scroll = new JScrollPane(column);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(shell.getBackground());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        shell.add(scroll, BorderLayout.CENTER);
        return shell;
    }

    private static JPanel buildChangeHistoryAdminToolsSection(Connection connection, JFrame frame) {
        JPanel block = buildSectionPanel();
        block.add(adminToolsSectionTitle("Change history"));
        block.add(Box.createVerticalStrut(6));
        block.add(buildSectionText("Recent stock adjustments, market-price edits, and note saves."));
        block.add(Box.createVerticalStrut(8));

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"When", "Item Code", "User", "Type", "Delta", "Reason", "Details"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        installTableCopyMenu(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));
        scroll.setPreferredSize(new Dimension(780, 180));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable reload = () -> {
            model.setRowCount(0);
            try {
                for (InventoryAudit.ChangeLogRow row : InventoryAudit.loadRecentChanges(connection, 200)) {
                    model.addRow(new Object[]{
                            row.createdAt(),
                            row.itemCode(),
                            row.username(),
                            row.changeType(),
                            row.quantityDelta(),
                            row.reason(),
                            row.details()
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Could not load change history: " + ex.getMessage(),
                        VIEW_ADMIN_TOOLS, JOptionPane.WARNING_MESSAGE);
            }
            deferPackTableColumns(table);
        };
        reload.run();

        JButton refresh = new JButton("Refresh change history");
        styleSecondaryButton(refresh);
        refresh.setAlignmentX(Component.LEFT_ALIGNMENT);
        refresh.addActionListener(e -> reload.run());
        block.add(scroll);
        block.add(Box.createVerticalStrut(8));
        block.add(refresh);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        return block;
    }

    /**
     * Builds admin-only reporting panel with table preview and CSV export.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @return report panel
     */
    private static JPanel buildReportsPanel(User user, Connection connection) {
        ensureAdmin(user, "Generate Reports");
        JPanel reportsPanel = new JPanel(new BorderLayout(12, 12));
        reportsPanel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        AppUI.applyPanelBackground(reportsPanel);

        JPanel filterForm = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(filterForm);
        GridBagConstraints gb = new GridBagConstraints();
        gb.insets = new Insets(4, 0, 4, 10);

        JComboBox<String> reportType = user.hasAdminRights()
                ? new JComboBox<>(new String[]{
                "Sales",
                "Users",
                "Movements",
                "Dead stock",
                "Item margins",
                "Sell-through",
                "P/L by item",
                "Bin utilization"
        })
                : new JComboBox<>(new String[]{"Sales"});
        JTextField search = new JTextField();
        JTextField fromDate = new JTextField();
        JTextField toDate = new JTextField();
        JTextField inactiveDays = new JTextField("90", 5);
        styleInput(search, fromDate, toDate);
        styleInputCompact(inactiveDays);
        styleComboMatchInputRow(reportType);

        fromDate.setText(LocalDate.now().minusDays(30).toString());
        toDate.setText(LocalDate.now().toString());

        int r = 0;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        filterForm.add(new JLabel("Report type"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        filterForm.add(reportType, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        filterForm.add(new JLabel("Search (optional)"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        filterForm.add(search, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        filterForm.add(new JLabel("From date (yyyy-MM-dd)"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        filterForm.add(fromDate, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        filterForm.add(new JLabel("To date (yyyy-MM-dd)"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        filterForm.add(toDate, gb);

        JLabel inactiveLabel = new JLabel("Inactive days (dead stock)");
        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        filterForm.add(inactiveLabel, gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        filterForm.add(inactiveDays, gb);

        Runnable syncReportFieldState = () -> {
            String selected = (String) reportType.getSelectedItem();
            boolean deadStock = "Dead stock".equals(selected);
            boolean margins = "Item margins".equals(selected);
            boolean plByItem = "P/L by item".equals(selected);
            boolean binUtil = "Bin utilization".equals(selected);
            inactiveLabel.setEnabled(deadStock);
            inactiveDays.setEnabled(deadStock);
            fromDate.setEnabled(!margins && !plByItem && !binUtil);
            toDate.setEnabled(!margins && !plByItem && !binUtil);
        };
        reportType.addActionListener(e -> syncReportFieldState.run());
        syncReportFieldState.run();

        JPanel headingFilter = new JPanel(new BorderLayout(0, 10));
        AppUI.applyPanelBackground(headingFilter);
        headingFilter.add(buildSectionTitle("Generate Reports"), BorderLayout.NORTH);
        headingFilter.add(filterForm, BorderLayout.CENTER);
        DefaultTableModel salesTableModel = new DefaultTableModel(new String[]{
                "Item Code", "Item Name", "Amount", "Total Price", "Total Cost", "Gross Profit", "Reference", "User", "Date", "Note"
        }, 0);
        JTable salesTable = new JTable(salesTableModel);
        installTableCopyMenu(salesTable);
        JScrollPane salesScroll = new JScrollPane(salesTable);
        salesScroll.setBorder(AppUI.newRoundedBorder(8));

        DefaultTableModel usersTableModel = new DefaultTableModel(new String[]{"Username", "Event Type", "Details", "Date"}, 0);
        JTable usersTable = new JTable(usersTableModel);
        installTableCopyMenu(usersTable);
        JScrollPane usersScroll = new JScrollPane(usersTable);
        usersScroll.setBorder(AppUI.newRoundedBorder(8));

        DefaultTableModel analyticsTableModel = new DefaultTableModel(new String[]{
                "Item Code", "Item Name", "Stock", "Detail A", "Detail B", "Detail C"
        }, 0);
        JTable analyticsTable = new JTable(analyticsTableModel);
        installTableCopyMenu(analyticsTable);
        JScrollPane analyticsScroll = new JScrollPane(analyticsTable);
        analyticsScroll.setBorder(AppUI.newRoundedBorder(8));

        JPanel contentCards = new JPanel(new CardLayout());
        AppUI.applyPanelBackground(contentCards);
        contentCards.add(salesScroll, "Sales");
        contentCards.add(usersScroll, "Users");
        contentCards.add(analyticsScroll, "Analytics");

        final ReportData[] latestReport = new ReportData[1];
        final String[] latestReportType = new String[]{"Sales"};

        JButton generate = new JButton("Generate Report");
        AppUI.stylePrimaryButton(generate);
        generate.setPreferredSize(new Dimension(190, 36));
        JButton export = new JButton("Export Report");
        styleSecondaryButton(export);
        export.setPreferredSize(new Dimension(180, 36));

        generate.addActionListener(e -> {
            LocalDate from;
            LocalDate to;
            String selected = (String) reportType.getSelectedItem();
            try {
                if ("Item margins".equals(selected)) {
                    try {
                        ReportData marginData = buildItemMarginsReportData(connection, search.getText().trim());
                        populateReportTable(analyticsTableModel, marginData);
                        ((CardLayout) contentCards.getLayout()).show(contentCards, "Analytics");
                        latestReport[0] = marginData;
                        latestReportType[0] = "item_margins";
                        deferPackTableColumns(analyticsTable);
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(reportsPanel,
                                "Database error while building report: " + ex.getMessage(),
                                "Report Error", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                }
                if ("Dead stock".equals(selected)) {
                    int idleDays;
                    try {
                        idleDays = Integer.parseInt(inactiveDays.getText().trim());
                        if (idleDays < 1 || idleDays > 3650) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(reportsPanel, "Enter inactive days between 1 and 3650.");
                        return;
                    }
                    try {
                        ReportData deadData = buildDeadStockReportData(connection, search.getText().trim(), idleDays);
                        populateReportTable(analyticsTableModel, deadData);
                        ((CardLayout) contentCards.getLayout()).show(contentCards, "Analytics");
                        latestReport[0] = deadData;
                        latestReportType[0] = "dead_stock";
                        deferPackTableColumns(analyticsTable);
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(reportsPanel,
                                "Database error while building report: " + ex.getMessage(),
                                "Report Error", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                }
                if ("P/L by item".equals(selected) || "Bin utilization".equals(selected)) {
                    from = null;
                    to = null;
                } else {
                    from = LocalDate.parse(fromDate.getText().trim());
                    to = LocalDate.parse(toDate.getText().trim());
                }
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(reportsPanel, "Enter valid dates using yyyy-MM-dd.");
                return;
            }
            if (from != null && to != null && from.isAfter(to)) {
                JOptionPane.showMessageDialog(reportsPanel, "From date must be before or equal to To date.");
                return;
            }
            try {
                CardLayout cardLayout = (CardLayout) contentCards.getLayout();
                if ("Users".equals(selected)) {
                    ReportData userData = buildUsersReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(usersTableModel, userData);
                    cardLayout.show(contentCards, "Users");
                    latestReport[0] = userData;
                    latestReportType[0] = "users";
                    deferPackTableColumns(usersTable);
                } else if ("Movements".equals(selected)) {
                    ReportData movementData = buildMovementsReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(usersTableModel, movementData);
                    cardLayout.show(contentCards, "Users");
                    latestReport[0] = movementData;
                    latestReportType[0] = "movements";
                    deferPackTableColumns(usersTable);
                } else if ("Sell-through".equals(selected)) {
                    ReportData data = buildSellThroughReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(analyticsTableModel, data);
                    cardLayout.show(contentCards, "Analytics");
                    latestReport[0] = data;
                    latestReportType[0] = "sell_through";
                    deferPackTableColumns(analyticsTable);
                } else if ("P/L by item".equals(selected)) {
                    ReportData data = buildProfitLossByItemLifetimeReportData(connection, search.getText().trim());
                    populateReportTable(analyticsTableModel, data);
                    cardLayout.show(contentCards, "Analytics");
                    latestReport[0] = data;
                    latestReportType[0] = "pl_by_item";
                    deferPackTableColumns(analyticsTable);
                } else if ("Bin utilization".equals(selected)) {
                    ReportData data = buildBinUtilizationReportData(connection, search.getText().trim());
                    populateReportTable(analyticsTableModel, data);
                    cardLayout.show(contentCards, "Analytics");
                    latestReport[0] = data;
                    latestReportType[0] = "bin_utilization";
                    deferPackTableColumns(analyticsTable);
                } else {
                    ReportData salesData = buildSalesReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(salesTableModel, salesData);
                    cardLayout.show(contentCards, "Sales");
                    latestReport[0] = salesData;
                    latestReportType[0] = "sales";
                    deferPackTableColumns(salesTable);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(reportsPanel, "Database error while building report: " + ex.getMessage(), "Report Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        export.addActionListener(e -> {
            if (latestReport[0] == null) {
                JOptionPane.showMessageDialog(reportsPanel, "Generate a report first, then export.");
                return;
            }
            String from = fromDate.getText().trim();
            String to = toDate.getText().trim();
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Choose export folder");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showSaveDialog(reportsPanel) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                Path dir = chooser.getSelectedFile().toPath();
                Files.createDirectories(dir);

                String base = sanitizeFileName(System.currentTimeMillis() + "_" + user.getUsername() + "_" + latestReportType[0] + "_from_" + from + "_to_" + to);
                Path csv = dir.resolve(base + ".csv");
                writeReportCsv(csv, latestReport[0]);

                JOptionPane.showMessageDialog(reportsPanel, "Export complete:\n" + csv.toString());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(reportsPanel, "Export failed: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        reportsPanel.add(headingFilter, BorderLayout.NORTH);
        reportsPanel.add(contentCards, BorderLayout.CENTER);
        reportsPanel.add(buildActionBar(export, generate), BorderLayout.SOUTH);
        return reportsPanel;
    }

    /**
     * Requires the signed-in administrator's login password against {@code users} before destructive flows.
     *
     * @param parent dialog parent frame
     * @param connection open JDBC session
     * @param adminUser currently signed-in user (verified as admin separately)
     * @param title short context phrase for dialogs (shown in captions)
     * @return {@code true} only when verification succeeds or user cancels is false and password matches
     */
    private static boolean verifySignedInAdministratorPassword(
            Window parent,
            Connection connection,
            User adminUser,
            String title
    ) {
        final String caption = title == null || title.isBlank() ? "Administrator password" : title;
        final String username = adminUser.getUsername();
        String storedHash;
        try (PreparedStatement ps = connection.prepareStatement("SELECT password FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(
                            parent, "Cannot verify password — user record not found.", caption, JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                storedHash = rs.getString("password");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(), caption, JOptionPane.ERROR_MESSAGE);
            return false;
        }

        JPanel pwPanel = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(pwPanel);
        pwPanel.add(
                new JLabel("Enter administrator password for \"" + username + "\" to continue:"),
                BorderLayout.NORTH);
        JPasswordField pwField = new JPasswordField();
        stylePasswordInput(pwField);
        pwField.setPreferredSize(new Dimension(320, INPUT_HEIGHT));
        pwPanel.add(pwField, BorderLayout.CENTER);

        int dlg = JOptionPane.showConfirmDialog(
                parent,
                pwPanel,
                caption + " — password required",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);

        char[] typed = dlg == JOptionPane.OK_OPTION ? pwField.getPassword() : new char[0];
        pwField.setText("");
        try {
            if (dlg != JOptionPane.OK_OPTION) {
                return false;
            }
            if (!SecurityUtils.verifyPassword(typed, storedHash)) {
                JOptionPane.showMessageDialog(parent, "Incorrect password. Reset cancelled.", caption, JOptionPane.WARNING_MESSAGE);
                return false;
            }
            return true;
        } finally {
            Arrays.fill(typed, '\0');
        }
    }

    /** Compact backup list, actions, prune, and day-one reset for Administration Tools. */
    private static JPanel buildBackupSectionPanel(User user, Connection connection, AccountActions accountActions, JFrame frame) {
        ensureAdmin(user, "Create Local Backup");
        JPanel content = buildSectionPanel();

        content.add(adminToolsSectionTitle("Database backup"));
        content.add(Box.createVerticalStrut(6));
        JLabel blurb = buildSectionText("Backups live under database_backups. Restoring replaces the live database — restart the app afterward.");
        blurb.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(blurb);
        content.add(Box.createVerticalStrut(8));

        JTextField reminderDaysField = new JTextField(5);
        styleInputCompact(reminderDaysField);
        try {
            reminderDaysField.setText(Integer.toString(readBackupReminderDays(connection)));
        } catch (SQLException ex) {
            reminderDaysField.setText(Integer.toString(DEFAULT_BACKUP_REMINDER_DAYS));
        }
        JButton saveReminderDays = new JButton("Save reminder");
        styleSecondaryButton(saveReminderDays);
        saveReminderDays.addActionListener(e -> {
            try {
                int days = Integer.parseInt(reminderDaysField.getText().trim());
                if (days < 1 || days > 365) {
                    JOptionPane.showMessageDialog(frame, "Enter reminder days between 1 and 365.");
                    return;
                }
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_BACKUP_REMINDER_DAYS,
                        Integer.toString(days));
                JOptionPane.showMessageDialog(frame, "Backup reminder saved.", "Backup",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Enter a whole number of days.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Could not save reminder: " + ex.getMessage());
            }
        });
        JPanel reminderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(reminderRow);
        reminderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        reminderRow.add(new JLabel("Remind if no backup within (days):"));
        reminderRow.add(reminderDaysField);
        reminderRow.add(saveReminderDays);
        content.add(reminderRow);

        JCheckBox backupOnLogout = new JCheckBox("Prompt backup on logout when reminder threshold is exceeded");
        backupOnLogout.setOpaque(false);
        try {
            backupOnLogout.setSelected(readBackupOnLogoutEnabled(connection));
        } catch (SQLException ex) {
            backupOnLogout.setSelected(false);
        }
        backupOnLogout.addActionListener(e -> {
            try {
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_BACKUP_ON_LOGOUT_ENABLED,
                        backupOnLogout.isSelected() ? "1" : "0");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Could not save backup-on-logout setting: " + ex.getMessage());
            }
        });
        backupOnLogout.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(backupOnLogout);
        content.add(Box.createVerticalStrut(8));

        DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Backup folder"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        installTableCopyMenu(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));
        scroll.setPreferredSize(new Dimension(520, 110));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable refreshBackups = () -> {
            tableModel.setRowCount(0);
            try {
                for (String name : accountActions.listDatabaseBackupFolderNames()) {
                    tableModel.addRow(new Object[]{name});
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Could not list backups: " + ex.getMessage());
            }
            deferPackTableColumns(table);
        };

        JButton refreshButton = new JButton("Refresh list");
        styleSecondaryButton(refreshButton);
        refreshButton.addActionListener(e -> refreshBackups.run());

        JButton backupNow = new JButton("Create backup now");
        AppUI.stylePrimaryButton(backupNow);
        backupNow.addActionListener(e -> new Thread(() -> runAction(() -> accountActions.backUpDatabase(user, frame)), "ims-backup").start());

        JButton openFolder = new JButton("Open backup folder");
        styleSecondaryButton(openFolder);
        openFolder.addActionListener(e -> new Thread(() -> runAction(() -> accountActions.openBackupsFolder(user, frame)), "ims-open-backup").start());

        JButton restoreButton = new JButton("Restore selected");
        AppUI.stylePrimaryButton(restoreButton);
        restoreButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(frame, "Select a backup folder in the table.");
                return;
            }
            String folder = (String) tableModel.getValueAt(row, 0);
            new Thread(() -> runAction(() -> accountActions.restoreDatabaseFromBackup(user, frame, folder)), "ims-restore").start();
        });

        JTextField pruneDays = new JTextField("30", 5);
        styleInputCompact(pruneDays);
        JButton pruneButton = new JButton("Prune old backups");
        styleSecondaryButton(pruneButton);
        pruneButton.addActionListener(e -> {
            try {
                int days = Integer.parseInt(pruneDays.getText().trim());
                new Thread(() -> runAction(() -> accountActions.pruneDatabaseBackups(user, frame, days)), "ims-prune").start();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Enter a valid number of days.");
            }
        });

        content.add(scroll);
        content.add(Box.createVerticalStrut(8));

        JPanel dbActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(dbActions);
        dbActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        dbActions.add(refreshButton);
        dbActions.add(backupNow);
        dbActions.add(openFolder);
        dbActions.add(restoreButton);
        dbActions.add(new JLabel("Prune older than (days):"));
        dbActions.add(pruneDays);
        dbActions.add(pruneButton);
        content.add(dbActions);

        content.add(Box.createVerticalStrut(14));

        JPanel factoryBlock = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(factoryBlock);
        factoryBlock.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel resetColumn = new JPanel();
        resetColumn.setLayout(new BoxLayout(resetColumn, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(resetColumn);

        JLabel resetHeading = adminToolsSectionTitle("Factory reset (day one)");
        resetHeading.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetColumn.add(resetHeading);
        resetColumn.add(Box.createVerticalStrut(4));

        JLabel resetBlurb = buildSectionText(
                "Removes all business data and user accounts; next launch creates a new first administrator. Clears item_images/ only."
        );
        resetBlurb.setHorizontalAlignment(SwingConstants.RIGHT);
        resetBlurb.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetColumn.add(resetBlurb);
        resetColumn.add(Box.createVerticalStrut(8));

        JButton resetDayOne = new JButton("Reset database to day one…");
        resetDayOne.setForeground(AppUI.DANGER);
        styleSecondaryButton(resetDayOne);
        resetDayOne.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetDayOne.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(
                    frame,
                    "This permanently deletes business data and every user login.\n\n"
                            + "• Next app start: you create a new administrator (name + password)\n"
                            + "• All item JPEGs under item_images/ are removed\n"
                            + "• company.txt and workspace_welcome.png are kept\n\n"
                            + "Continue?",
                    "Confirm reset",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (ok != JOptionPane.YES_OPTION) {
                return;
            }
            int ok2 = JOptionPane.showConfirmDialog(
                    frame,
                    "This cannot be undone. Perform day-one reset now?",
                    "Final confirmation",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (ok2 != JOptionPane.YES_OPTION) {
                return;
            }
            if (!verifySignedInAdministratorPassword(frame, connection, user, "Day-one reset")) {
                return;
            }
            try {
                DatabaseManager.resetEnterpriseDataToDayOne(connection);
                refreshActiveMetricsStripNow();
                JOptionPane.showMessageDialog(
                        frame,
                        "Reset complete. Log out (or close the app). On the next launch you will set up a new first administrator."
                );
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Database reset failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Item images cleanup failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel resetActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        AppUI.applyPanelBackground(resetActions);
        resetActions.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetActions.add(resetDayOne);
        resetColumn.add(resetActions);

        factoryBlock.add(resetColumn, BorderLayout.EAST);
        content.add(factoryBlock);

        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshBackups.run();
        return content;
    }

    private static ReportData buildDeadStockReportData(Connection connection, String search, int inactiveDays)
            throws SQLException {
        ReportData data = new ReportData("Dead Stock Report");
        String like = "%" + search + "%";
        String sql = """
                SELECT i.`Item Code` AS code,
                       i.`Item Name` AS iname,
                       i.`Stock` AS stock,
                       MAX(s.`DateISO`) AS last_sale,
                       CASE
                           WHEN MAX(s.`DateISO`) IS NULL THEN -1
                           ELSE CAST(julianday('now') - julianday(MAX(s.`DateISO`)) AS INTEGER)
                       END AS days_idle
                FROM inventory i
                LEFT JOIN sales s ON s.`Item Code` = i.`Item Code`
                WHERE i.`Stock` > 0
                  AND (? = '' OR lower(i.`Item Code`) LIKE lower(?) OR lower(i.`Item Name`) LIKE lower(?))
                GROUP BY i.`Item Code`, i.`Item Name`, i.`Stock`
                HAVING last_sale IS NULL OR days_idle >= ?
                ORDER BY days_idle DESC, code ASC
                """;
        int rowCount = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, search);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setInt(4, inactiveDays);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rowCount++;
                    String lastSale = rs.getString("last_sale");
                    int daysIdle = rs.getInt("days_idle");
                    data.rows.add(new Object[]{
                            rs.getString("code"),
                            rs.getString("iname"),
                            rs.getInt("stock"),
                            lastSale == null ? "—" : lastSale,
                            daysIdle < 0 ? "Never sold" : Integer.toString(daysIdle),
                            "≥ " + inactiveDays + "d idle"
                    });
                }
            }
        }
        data.columns = new String[]{
                "Item Code", "Item Name", "Stock", "Last Sale", "Days Idle", "Threshold"
        };
        data.summary.put("Rows", String.valueOf(rowCount));
        data.summary.put("Inactive threshold (days)", String.valueOf(inactiveDays));
        data.summary.put("Includes never-sold SKUs", "Yes");
        return data;
    }

    private static ReportData buildItemMarginsReportData(Connection connection, String search) throws SQLException {
        ReportData data = new ReportData("Item Margins Report");
        String like = "%" + search + "%";
        String sql = """
                SELECT i.`Item Code` AS code,
                       i.`Item Name` AS iname,
                       i.`Stock` AS stock,
                       CAST(i.`Market Price` AS REAL) AS mp,
                       (CAST(layer_tot.w AS REAL) / layer_tot.r) AS avgcost
                FROM inventory i
                INNER JOIN (
                    SELECT item_code AS icode,
                           SUM(CAST(qty_remaining AS REAL) * unit_cost) AS w,
                           SUM(qty_remaining) AS r
                    FROM inventory_cost_layers
                    WHERE qty_remaining > 0
                    GROUP BY item_code
                ) layer_tot ON layer_tot.icode = i.`Item Code`
                WHERE i.`Stock` > 0
                  AND i.`Market Price` IS NOT NULL
                  AND layer_tot.r > 0
                  AND (CAST(layer_tot.w AS REAL) / layer_tot.r) > 1e-12
                  AND (? = '' OR lower(i.`Item Code`) LIKE lower(?) OR lower(i.`Item Name`) LIKE lower(?))
                ORDER BY ((mp - avgcost) / avgcost) DESC, code ASC
                """;
        int rowCount = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, search);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rowCount++;
                    double mp = rs.getDouble("mp");
                    double avg = rs.getDouble("avgcost");
                    double marginPct = ((mp - avg) / avg) * 100.0;
                    data.rows.add(new Object[]{
                            rs.getString("code"),
                            rs.getString("iname"),
                            rs.getInt("stock"),
                            formatUsdMoney(avg),
                            formatUsdMoney(mp),
                            String.format(Locale.US, "%.1f%%", marginPct),
                            formatUsdMoney(mp - avg)
                    });
                }
            }
        }
        data.columns = new String[]{
                "Item Code", "Item Name", "Stock", "Avg FIFO Cost", "Market Price", "Margin %", "Margin / Unit"
        };
        data.summary.put("Rows", String.valueOf(rowCount));
        data.summary.put("Basis", "Market price vs weighted-average FIFO unit cost on remaining stock");
        return data;
    }

    private static ReportData buildSellThroughReportData(
            Connection connection,
            String search,
            LocalDate from,
            LocalDate to
    ) throws SQLException {
        ReportData data = new ReportData("Sell-through Report");
        String sql = """
                SELECT i.`Item Code` AS code,
                       i.`Item Name` AS iname,
                       i.`Stock` AS stock,
                       COALESCE(s.units_sold, 0) AS units_sold
                FROM inventory i
                LEFT JOIN (
                    SELECT `Item Code`, SUM(`Amount`) AS units_sold
                    FROM sales
                    WHERE `DateISO` BETWEEN ? AND ?
                    GROUP BY `Item Code`
                ) s ON s.`Item Code` = i.`Item Code`
                WHERE (i.`Stock` > 0 OR COALESCE(s.units_sold, 0) > 0)
                  AND (? = '' OR lower(i.`Item Code`) LIKE lower(?) OR lower(i.`Item Name`) LIKE lower(?))
                ORDER BY (CAST(COALESCE(s.units_sold, 0) AS REAL) / CASE WHEN i.`Stock` > 0 THEN i.`Stock` ELSE 1 END) DESC, code ASC
                """;
        int rows = 0;
        int soldTotal = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setString(1, from.toString() + " 00:00:00");
            ps.setString(2, to.toString() + " 23:59:59");
            ps.setString(3, search);
            ps.setString(4, like);
            ps.setString(5, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows++;
                    int sold = rs.getInt("units_sold");
                    int stock = rs.getInt("stock");
                    soldTotal += sold;
                    double rate = sold * 100.0 / Math.max(1, stock);
                    data.rows.add(new Object[]{
                            rs.getString("code"),
                            rs.getString("iname"),
                            sold,
                            stock,
                            String.format(Locale.US, "%.1f%%", rate)
                    });
                }
            }
        }
        data.columns = new String[]{"Item Code", "Item Name", "Units Sold", "Current Stock", "Sell-through %"};
        data.summary.put("Rows", String.valueOf(rows));
        data.summary.put("Total Units Sold", String.valueOf(soldTotal));
        data.summary.put("Date Range", from + " to " + to);
        return data;
    }

    private static ReportData buildProfitLossByItemLifetimeReportData(Connection connection, String search)
            throws SQLException {
        ReportData data = new ReportData("P/L by Item (Lifetime)");
        String sql = """
                SELECT s.`Item Code` AS code,
                       COALESCE(MAX(s.`Item Name`), '') AS iname,
                       COALESCE(SUM(s.`Amount`), 0) AS units,
                       COALESCE(SUM(s.`Total Price`), 0) AS revenue,
                       COALESCE(SUM(s.`Total Cost`), 0) AS cost
                FROM sales s
                WHERE (? = '' OR lower(s.`Item Code`) LIKE lower(?) OR lower(s.`Item Name`) LIKE lower(?))
                GROUP BY s.`Item Code`
                ORDER BY (revenue - cost) DESC, code ASC
                """;
        double totalProfit = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setString(1, search);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double revenue = rs.getDouble("revenue");
                    double cost = rs.getDouble("cost");
                    double profit = revenue - cost;
                    totalProfit += profit;
                    data.rows.add(new Object[]{
                            rs.getString("code"),
                            rs.getString("iname"),
                            rs.getInt("units"),
                            formatUsdMoney(revenue),
                            formatUsdMoney(cost),
                            formatUsdMoney(profit)
                    });
                }
            }
        }
        data.columns = new String[]{"Item Code", "Item Name", "Units Sold", "Revenue", "Cost", "P/L"};
        data.summary.put("Total P/L", formatUsdMoney(totalProfit));
        data.summary.put("Scope", "Lifetime sales grouped by item");
        return data;
    }

    private static ReportData buildBinUtilizationReportData(Connection connection, String search) throws SQLException {
        ReportData data = new ReportData("Bin Utilization Report");
        data.columns = new String[]{"Item Code", "Item Name", "Stock", "Binned Qty", "Binned %"};
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            data.rows.add(new Object[]{"—", "Storage tables are not available for this database.", 0, 0, "0.0%"});
            data.summary.put("Overall Bin Utilization", "0.0%");
            return data;
        }
        double overall = InventoryFifo.binUtilizationPercent(connection);
        String sql = """
                SELECT i.`Item Code` AS code,
                       i.`Item Name` AS iname,
                       i.`Stock` AS stock,
                       COALESCE(SUM(CASE WHEN s.location_id != ? THEN s.qty ELSE 0 END), 0) AS binned
                FROM inventory i
                LEFT JOIN inventory_storage_qty s ON s.item_code = i.`Item Code`
                WHERE i.`Stock` > 0
                  AND (? = '' OR lower(i.`Item Code`) LIKE lower(?) OR lower(i.`Item Name`) LIKE lower(?))
                GROUP BY i.`Item Code`, i.`Item Name`, i.`Stock`
                ORDER BY i.`Item Code` ASC
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setInt(1, DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID);
            ps.setString(2, search);
            ps.setString(3, like);
            ps.setString(4, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int stock = rs.getInt("stock");
                    int binned = rs.getInt("binned");
                    double pct = stock <= 0 ? 0 : Math.min(100.0, (binned * 100.0) / stock);
                    data.rows.add(new Object[]{
                            rs.getString("code"),
                            rs.getString("iname"),
                            stock,
                            binned,
                            String.format(Locale.US, "%.1f%%", pct)
                    });
                }
            }
        }
        data.summary.put("Overall Bin Utilization", String.format(Locale.US, "%.1f%%", overall));
        data.summary.put("Note", "Overall % excludes the Unassigned location bucket.");
        return data;
    }

    /**
     * Queries sales rows and summary metrics for reporting and CSV export.
     *
     * @param connection active database connection
     * @param search free-text search filter (item code, name, reference, or transaction note)
     * @param from inclusive start date
     * @param to inclusive end date
     * @return computed sales report data
     * @throws SQLException when query fails
     */
    private static ReportData buildSalesReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
        ReportData data = new ReportData("Sales Report");
        String sql = "SELECT \"Item Code\", \"Item Name\", \"Amount\", \"Total Price\", \"Total Cost\", \"Reference\", \"User\", \"Date\", COALESCE(\"Note\", '') AS \"Note\" "
                + "FROM sales WHERE \"DateISO\" BETWEEN ? AND ? "
                + "AND (? = '' OR lower(\"Item Code\") LIKE lower(?) OR lower(\"Item Name\") LIKE lower(?) OR lower(\"Reference\") LIKE lower(?) "
                + "OR lower(COALESCE(\"Note\", '')) LIKE lower(?))";

        int rowCount = 0;
        int totalUnits = 0;
        double totalRevenue = 0;
        double totalCost = 0;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setString(1, from.toString() + " 00:00:00");
            ps.setString(2, to.toString() + " 23:59:59");
            ps.setString(3, search);
            ps.setString(4, like);
            ps.setString(5, like);
            ps.setString(6, like);
            ps.setString(7, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rowCount++;
                    int amount = rs.getInt("Amount");
                    double price = rs.getDouble("Total Price");
                    double cost = rs.getDouble("Total Cost");
                    totalUnits += amount;
                    totalRevenue += price;
                    totalCost += cost;
                    data.rows.add(new Object[]{
                            rs.getString("Item Code"),
                            rs.getString("Item Name"),
                            amount,
                            price,
                            cost,
                            price - cost,
                            rs.getString("Reference"),
                            rs.getString("User"),
                            rs.getString("Date"),
                            rs.getString("Note")
                    });
                }
            }
        }

        data.columns = new String[]{
                "Item Code", "Item Name", "Amount", "Total Price", "Total Cost", "Gross Profit", "Reference", "User", "Date", "Note"
        };
        data.summary.put("Rows", String.valueOf(rowCount));
        data.summary.put("Total Units Sold", String.valueOf(totalUnits));
        data.summary.put("Total Revenue", formatUsdMoney(totalRevenue));
        data.summary.put("Total Cost", formatUsdMoney(totalCost));
        data.summary.put("Gross Profit", formatUsdMoney(totalRevenue - totalCost));
        data.summary.put("Date Range", from + " to " + to);
        return data;
    }

    /**
     * Queries user/security event data for the users report table.
     *
     * @param connection active database connection
     * @param search free-text search filter
     * @param from inclusive start date
     * @param to inclusive end date
     * @return computed users report data
     * @throws SQLException when query fails
     */
    private static ReportData buildUsersReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
        ReportData data = new ReportData("Users Report");
        String normalizedAuditDate = "(substr(created_at, 7, 4) || '-' || substr(created_at, 4, 2) || '-' || substr(created_at, 1, 2))";
        String normalizedLoginDate = "(substr(Time, 7, 4) || '-' || substr(Time, 4, 2) || '-' || substr(Time, 1, 2))";
        String sql =
                "SELECT username, event_type, details, created_at " +
                "FROM security_audit " +
                "WHERE " + normalizedAuditDate + " BETWEEN ? AND ? " +
                "AND (? = '' OR lower(username) LIKE lower(?) OR lower(event_type) LIKE lower(?) OR lower(details) LIKE lower(?)) " +
                "UNION ALL " +
                "SELECT Name AS username, 'LOGIN' AS event_type, 'Login record' AS details, Time AS created_at " +
                "FROM Logins " +
                "WHERE " + normalizedLoginDate + " BETWEEN ? AND ? " +
                "AND (? = '' OR lower(Name) LIKE lower(?)) " +
                "ORDER BY created_at DESC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.setString(3, search);
            ps.setString(4, like);
            ps.setString(5, like);
            ps.setString(6, like);
            ps.setString(7, from.toString());
            ps.setString(8, to.toString());
            ps.setString(9, search);
            ps.setString(10, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.rows.add(new Object[]{
                            rs.getString("username"),
                            rs.getString("event_type"),
                            rs.getString("details"),
                            rs.getString("created_at")
                    });
                }
            }
        }

        data.columns = new String[]{"Username", "Event Type", "Details", "Date"};
        return data;
    }

    /**
     * Queries inventory movement data for audit-style movement reporting.
     *
     * @param connection active database connection
     * @param search free-text search filter
     * @param from inclusive start date
     * @param to inclusive end date
     * @return computed movement report data
     * @throws SQLException when query fails
     */
    private static ReportData buildMovementsReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
        ReportData data = new ReportData("Movements Report");
        String normalizedDate = "(substr(`Date`, 7, 4) || '-' || substr(`Date`, 4, 2) || '-' || substr(`Date`, 1, 2))";
        String sql =
                "SELECT `Item`, `Amount`, `Type`, COALESCE(`Reason`, '') AS `Reason`, `User`, `Date` " +
                "FROM movements " +
                "WHERE " + normalizedDate + " BETWEEN ? AND ? " +
                "AND (? = '' OR lower(`Item`) LIKE lower(?) OR lower(`Type`) LIKE lower(?) OR lower(COALESCE(`Reason`, '')) LIKE lower(?) OR lower(`User`) LIKE lower(?)) " +
                "ORDER BY `Date` DESC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.setString(3, search);
            ps.setString(4, like);
            ps.setString(5, like);
            ps.setString(6, like);
            ps.setString(7, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.rows.add(new Object[]{
                            rs.getString("Item"),
                            rs.getInt("Amount"),
                            rs.getString("Type"),
                            rs.getString("Reason"),
                            rs.getString("User"),
                            rs.getString("Date")
                    });
                }
            }
        }

        data.columns = new String[]{"Item Code", "Amount", "Type", "Reason", "User", "Date"};
        return data;
    }

    /** Converts display datetime (dd-MM-yyyy HH:mm:ss) into ISO datetime. */
    private static String toIsoDateTime(String displayDateTime) {
        if (displayDateTime == null || displayDateTime.length() < 19) {
            return null;
        }
        return displayDateTime.substring(6, 10) + "-" +
                displayDateTime.substring(3, 5) + "-" +
                displayDateTime.substring(0, 2) +
                displayDateTime.substring(10);
    }

    private static int sqlDaysSinceDisplayDate(String displayDateTime) {
        if (displayDateTime == null || displayDateTime.length() < 10) {
            return Integer.MAX_VALUE;
        }
        try {
            LocalDate d = LocalDate.parse(
                    displayDateTime.substring(6, 10) + "-"
                            + displayDateTime.substring(3, 5) + "-"
                            + displayDateTime.substring(0, 2));
            return (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(d, LocalDate.now()));
        } catch (RuntimeException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static void populateReportTable(DefaultTableModel model, ReportData data) {
        model.setColumnCount(0);
        for (String col : data.columns) {
            model.addColumn(col);
        }
        model.setRowCount(0);
        for (Object[] row : data.rows) {
            model.addRow(row);
        }
    }

    /** Sanitizes filename tokens for export-safe output paths. */
    private static String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Writes report table data to CSV with quoted cells.
     *
     * @param path output CSV path
     * @param data report data to export
     * @throws IOException when writing fails
     */
    private static void writeReportCsv(Path path, ReportData data) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(String.join(",", data.columns));
            writer.newLine();
            for (Object[] row : data.rows) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        line.append(',');
                    }
                    String cell = row[i] == null ? "" : row[i].toString().replace("\"", "\"\"");
                    line.append('"').append(cell).append('"');
                }
                writer.write(line.toString());
                writer.newLine();
            }
        }
    }

    /** Creates a standard form container panel with section heading. */
    private static JPanel buildFormPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        AppUI.applyPanelBackground(panel);
        JLabel heading = buildSectionTitle(title);
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    /** Creates a vertically stacked section panel for form content. */
    private static JPanel buildSectionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        AppUI.applyPanelBackground(panel);
        return panel;
    }

    /** Creates a section title label with consistent heading style. */
    private static JLabel buildSectionTitle(String text) {
        JLabel heading = new JLabel(text, SwingConstants.LEFT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 21f));
        return heading;
    }

    /** Section heading for stacked blocks on Administration Tools (smaller than page titles). */
    private static JLabel adminToolsSectionTitle(String text) {
        JLabel heading = new JLabel(text, SwingConstants.LEFT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        return heading;
    }

    /** Creates a section body text label with standard style. */
    private static JLabel buildSectionText(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        return label;
    }

    /** Creates a left/right aligned action button bar. */
    private static JPanel buildActionBar(JButton leftButton, JButton rightButton) {
        JPanel actionBar = new JPanel(new BorderLayout());
        actionBar.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        AppUI.applyPanelBackground(actionBar);
        if (leftButton != null) {
            actionBar.add(leftButton, BorderLayout.WEST);
        }
        if (rightButton != null) {
            actionBar.add(rightButton, BorderLayout.EAST);
        }
        return actionBar;
    }

    /** Applies shared secondary button styling. */
    private static void styleSecondaryButton(JButton button) {
        button.setBorder(AppUI.newRoundedBorder(8));
        button.setFocusPainted(false);
    }

    /** Rebuilds draft line table rows from map-backed values. */
    private static void refreshSaleDraftTable(DefaultTableModel model, Map<String, SaleDraftLine> map, JTable table) {
        model.setRowCount(0);
        for (SaleDraftLine line : map.values()) {
            String locCell = line.storageLocationLabel.isEmpty() ? "—" : line.storageLocationLabel;
            model.addRow(new Object[]{line.itemCode, locCell, line.itemDescription, line.quantity, line.unitSalePrice});
        }
        deferPackTableColumns(table);
    }

    /** Rebuilds Add Item draft table from list order. */
    private static void refreshAddItemDraftTable(DefaultTableModel model, List<AddItemDraftLine> lines, JTable table) {
        model.setRowCount(0);
        for (AddItemDraftLine line : lines) {
            String notesCell = line.notes.isEmpty() ? "" : abbreviateForTableCell(line.notes, 48);
            model.addRow(new Object[]{
                    line.itemName,
                    line.stock,
                    line.reorder,
                    line.supplier.isEmpty() ? "" : line.supplier,
                    line.leadTime == null ? "" : line.leadTime,
                    line.pendingPhoto != null ? "Yes" : "",
                    notesCell
            });
        }
        deferPackTableColumns(table);
    }

    /** Single-line preview for draft tables; newlines become spaces and long text ends with an ellipsis. */
    private static String abbreviateForTableCell(String text, int maxChars) {
        String t = text.replace('\n', ' ').trim();
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    /**
     * Inserts one new inventory row, optional FIFO layer for initial stock, and movement log.
     *
     * @param supplierValue trimmed supplier; empty becomes SQL NULL
     * @param notesValue    optional item note (trimmed; empty becomes SQL NULL); capped to ITEM_NOTES_MAX_CHARS
     */
    private static void insertNewInventoryItemRow(
            Connection connection,
            User user,
            String itemCodeValue,
            String itemNameValue,
            int stockCount,
            int reorderTrigger,
            String supplierValue,
            Integer leadTimeDays,
            String notesValue
    ) throws SQLException {
        String notesForDb = notesValue == null ? "" : notesValue.trim();
        if (notesForDb.length() > ITEM_NOTES_MAX_CHARS) {
            notesForDb = notesForDb.substring(0, ITEM_NOTES_MAX_CHARS);
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO Inventory (`Item Code`, `Item Name`, `Stock`, `On Order`, `ReOrder Trigger`, `Supplier`, `Lead Time`, `Notes`, `Market Price`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            insert.setString(1, itemCodeValue);
            insert.setString(2, itemNameValue);
            insert.setInt(3, stockCount);
            insert.setInt(4, 0);
            insert.setInt(5, reorderTrigger);
            if (supplierValue == null || supplierValue.isEmpty()) {
                insert.setNull(6, java.sql.Types.VARCHAR);
            } else {
                insert.setString(6, supplierValue);
            }
            if (leadTimeDays == null) {
                insert.setNull(7, java.sql.Types.INTEGER);
            } else {
                insert.setInt(7, leadTimeDays);
            }
            if (notesForDb.isEmpty()) {
                insert.setNull(8, java.sql.Types.VARCHAR);
            } else {
                insert.setString(8, notesForDb);
            }
            insert.setNull(9, java.sql.Types.REAL);
            insert.executeUpdate();
        }
        if (supplierValue != null && !supplierValue.trim().isEmpty()) {
            int sid = DatabaseManager.ensureSupplier(connection, supplierValue.trim());
            try (PreparedStatement up = connection.prepareStatement(
                    "UPDATE Inventory SET supplier_id = ? WHERE `Item Code` = ?")) {
                up.setInt(1, sid);
                up.setString(2, itemCodeValue);
                up.executeUpdate();
            }
        }
        if (stockCount > 0) {
            try (PreparedStatement addLayer = connection.prepareStatement(
                    "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                addLayer.setString(1, itemCodeValue);
                addLayer.setString(2, "INITIAL_STOCK");
                addLayer.setDouble(3, 0);
                addLayer.setInt(4, stockCount);
                addLayer.setInt(5, stockCount);
                addLayer.setString(6, dateTime.nowDisplayString());
                addLayer.executeUpdate();
            }
            incrementInventoryStorageQty(connection, itemCodeValue, DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID, stockCount);
        }
        try (PreparedStatement movement = connection.prepareStatement(
                "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            movement.setString(1, itemCodeValue);
            movement.setString(2, String.valueOf(stockCount));
            movement.setString(3, "ADD");
            movement.setString(4, "INITIAL_STOCK");
            movement.setString(5, user.getUsername());
            movement.setString(6, dateTime.nowDisplayString());
            movement.executeUpdate();
        }
    }

    /** Table model column titles for pending lines; column 0 is SQLite {@code rowid} (shown with zero-width header). */
    private static final String[] PENDING_ORDER_TABLE_COLUMNS = new String[]{
            "\u200B", "Item Code", "Item Description", "Amount", "Purchase Price", "Remaining Payment", "Purchased From", "Reference", "Date"
    };

    /** Narrow/hide SQLite rowid column (index 0) on embedded pending-order tables. */
    private static void hidePendingOrdersRowIdColumn(JTable pendingTable) {
        TableColumn col = pendingTable.getColumnModel().getColumn(0);
        col.setMinWidth(0);
        col.setMaxWidth(0);
        col.setPreferredWidth(0);
        col.setResizable(false);
    }

    /** Loads pending order lines into the supplied table model ({@link #PENDING_ORDER_TABLE_COLUMNS} including SQLite rowid). */
    private static void loadPendingOrders(DefaultTableModel model, Connection connection) throws SQLException {
        model.setRowCount(0);
        String sql = """
                SELECT po.rowid,
                       po.`Item Code`,
                       COALESCE(inv.`Item Name`, '') AS `Item Description`,
                       po.`Amount`,
                       po.`Purchase Price`,
                       po.`Remaining Payment`,
                       po.`Purchased From`,
                       po.`Reference`,
                       po.`Date`
                FROM pendingOrders po
                LEFT JOIN inventory inv ON inv.`Item Code` = po.`Item Code`
                ORDER BY po.`Reference` ASC, po.`Item Code` ASC
                """;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getLong(1),
                        rs.getString("Item Code"),
                        rs.getString("Item Description"),
                        rs.getInt("Amount"),
                        rs.getDouble("Purchase Price"),
                        rs.getDouble("Remaining Payment"),
                        rs.getString("Purchased From"),
                        rs.getString("Reference"),
                        rs.getString("Date")
                });
            }
        }
    }

    /**
     * Removes one open pending-order line by {@code pendingOrders.rowid}, restores {@code On Order}, writes a cancellation movement.
     *
     * @param reason nullable or blank; shortened to {@link #PO_CANCEL_REASON_MAX_CHARS}
     */
    private static void applyPendingOrderCancellation(User user, Connection connection, long rowId, String reason)
            throws SQLException {
        final String trimmed = reason == null ? "" : reason.trim();
        final String detail = trimmed.length() > PO_CANCEL_REASON_MAX_CHARS
                ? trimmed.substring(0, PO_CANCEL_REASON_MAX_CHARS)
                : trimmed;

        boolean savedAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String itemCode;
            int qtyOpen;
            String reference;
            try (PreparedStatement sel = connection.prepareStatement(
                    "SELECT `Item Code`, `Amount`, `Reference` FROM pendingOrders WHERE rowid = ?")) {
                sel.setLong(1, rowId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("That pending line was not found (try refreshing the table).");
                    }
                    itemCode = rs.getString("Item Code");
                    qtyOpen = rs.getInt("Amount");
                    reference = rs.getString("Reference");
                }
            }

            try (PreparedStatement del = connection.prepareStatement("DELETE FROM pendingOrders WHERE rowid = ?")) {
                del.setLong(1, rowId);
                if (del.executeUpdate() != 1) {
                    throw new SQLException("Could not delete the pending order line.");
                }
            }

            try (PreparedStatement decrease = connection.prepareStatement(
                    "UPDATE inventory SET `On Order` = `On Order` - ? WHERE `Item Code` = ? AND `On Order` >= ?")) {
                decrease.setInt(1, qtyOpen);
                decrease.setString(2, itemCode);
                decrease.setInt(3, qtyOpen);
                if (decrease.executeUpdate() != 1) {
                    throw new SQLException(
                            "`On Order` mismatch for item " + itemCode + "; cancel aborted to preserve inventory totals.");
                }
            }

            StringBuilder reasonTxt = new StringBuilder("PO_CANCEL ref=");
            reasonTxt.append(reference).append(" qty=").append(qtyOpen).append(" item=").append(itemCode);
            if (!detail.isEmpty()) {
                String oneLine = detail.replace('\r', ' ').replace('\n', ' ');
                reasonTxt.append(" | ").append(oneLine);
            }
            try (PreparedStatement movement = connection.prepareStatement(
                    "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                movement.setString(1, itemCode);
                movement.setString(2, String.valueOf(qtyOpen));
                movement.setString(3, "ORDER_CANCELLED");
                movement.setString(4, reasonTxt.toString());
                movement.setString(5, user.getUsername());
                movement.setString(6, dateTime.nowDisplayString());
                movement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // ignore rollback failure
            }
            throw e;
        } finally {
            connection.setAutoCommit(savedAutoCommit);
        }
    }

    /** Confirms cancellation, prompts for optional reason, runs {@link #applyPendingOrderCancellation}, reloads pending table. */
    private static void cancelSelectedPendingOrderLineDialog(
            User user,
            Connection connection,
            Component dialogParent,
            JTable pendingTable,
            Runnable reloadPendingSafe
    ) {
        int viewRow = pendingTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(dialogParent, "Select an open PO line in the table first.", "Cancel PO Line",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultTableModel m = (DefaultTableModel) pendingTable.getModel();
        int mr = pendingTable.convertRowIndexToModel(viewRow);
        Object ridObj = m.getValueAt(mr, 0);
        if (!(ridObj instanceof Number ridNum)) {
            JOptionPane.showMessageDialog(dialogParent, "Unable to resolve that row.", "Cancel PO Line",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        long rowId = ridNum.longValue();
        String code = Objects.toString(m.getValueAt(mr, 1), "").trim();
        String ref = Objects.toString(m.getValueAt(mr, 7), "").trim();
        Object amtObj = m.getValueAt(mr, 3);
        String amtStr = amtObj instanceof Number n ? Integer.toString(n.intValue()) : Objects.toString(amtObj, "?");

        int confirm = JOptionPane.showConfirmDialog(dialogParent,
                "Cancel open line " + amtStr + " × " + code + " — reference \"" + ref + "\"?\n"
                        + "`On Order` will decrease by this amount.",
                "Cancel PO / tracking line?",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        String reason = JOptionPane.showInputDialog(dialogParent,
                "Optional reason (e.g. lost shipment, cancelled by seller):",
                "Cancel PO line",
                JOptionPane.PLAIN_MESSAGE);
        if (reason == null) {
            return;
        }

        try {
            applyPendingOrderCancellation(user, connection, rowId, reason);
            JOptionPane.showMessageDialog(dialogParent, "Pending order line cancelled.", "Cancelled",
                    JOptionPane.INFORMATION_MESSAGE);
            reloadPendingSafe.run();
            requestMetricsRefresh();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialogParent, ex.getMessage(), "Cancel failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads historical write-off movements into the supplied table model.
     *
     * @param model target table model
     * @param connection active database connection
     * @throws SQLException when query fails
     */
    private static void loadWriteOffHistory(DefaultTableModel model, Connection connection) throws SQLException {
        model.setRowCount(0);
        String sql = """
                SELECT m.`Item` AS `Item Code`,
                       COALESCE(inv.`Item Name`, '') AS `Item Description`,
                       m.`Amount`,
                       COALESCE(m.`Reason`, '') AS `Reason`,
                       m.`User`,
                       m.`Date`
                FROM movements m
                LEFT JOIN inventory inv ON inv.`Item Code` = m.`Item`
                WHERE m.`Type` = 'WRITE OFF'
                ORDER BY m.`Date` DESC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getString("Item Code"),
                        rs.getString("Item Description"),
                        rs.getInt("Amount"),
                        rs.getString("Reason"),
                        rs.getString("User"),
                        rs.getString("Date")
                });
            }
        }
    }

    /** After data is present: widens Item Description / Item Name to fit sampled content; other columns use default resizing. */
    private static void deferPackTableColumns(JTable table) {
        if (table == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> applyItemDescriptionColumnSizing(table));
    }

    /** Model index of {@code Item Description}, else {@code Item Name} / {@code Item name}; {@code -1} if none. */
    private static int findDescriptionOrNameModelColumn(TableModel tm) {
        for (String want : new String[]{"Item Description", "Item Name", "Item name"}) {
            for (int i = 0; i < tm.getColumnCount(); i++) {
                if (want.equals(tm.getColumnName(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Sets preferred width on the Item Description / Item Name column from the header label and widest cell text
     * across all model rows ({@link FontMetrics} estimate). Other columns use
     * {@link JTable#AUTO_RESIZE_SUBSEQUENT_COLUMNS}.
     */
    private static void applyItemDescriptionColumnSizing(JTable table) {
        TableModel tm = table.getModel();
        if (tm == null || tm.getColumnCount() <= 0) {
            return;
        }
        int modelCol = findDescriptionOrNameModelColumn(tm);
        if (modelCol < 0) {
            return;
        }
        int viewCol;
        try {
            viewCol = table.convertColumnIndexToView(modelCol);
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (viewCol < 0) {
            return;
        }
        JTableHeader header = table.getTableHeader();
        TableColumn col = table.getColumnModel().getColumn(viewCol);
        int width = ITEM_DESC_COLUMN_MIN_WIDTH;

        TableCellRenderer headerRenderer = col.getHeaderRenderer();
        if (headerRenderer == null && header != null) {
            headerRenderer = header.getDefaultRenderer();
        }
        if (headerRenderer != null) {
            Component headerComp = headerRenderer.getTableCellRendererComponent(table, col.getHeaderValue(), false,
                    false, -1, viewCol);
            width = Math.max(width, headerComp.getPreferredSize().width + 16);
        } else {
            FontMetrics hfm = table.getFontMetrics(table.getFont());
            Object hv = col.getHeaderValue();
            String hs = hv == null ? "" : hv.toString();
            width = Math.max(width, hfm.stringWidth(hs.isEmpty() ? " " : hs) + 20);
        }

        FontMetrics fm = table.getFontMetrics(table.getFont());
        final int measureCapChars = 4000;
        for (int mr = 0; mr < tm.getRowCount(); mr++) {
            Object v = tm.getValueAt(mr, modelCol);
            String s = v == null ? "" : v.toString();
            if (s.length() > measureCapChars) {
                s = s.substring(0, measureCapChars);
            }
            width = Math.max(width, fm.stringWidth(s.isEmpty() ? " " : s) + 24);
            if (width >= ITEM_DESC_COLUMN_MAX_WIDTH) {
                width = ITEM_DESC_COLUMN_MAX_WIDTH;
                break;
            }
        }

        width = Math.min(width, ITEM_DESC_COLUMN_MAX_WIDTH);
        width = Math.max(width, ITEM_DESC_COLUMN_MIN_WIDTH);
        col.setPreferredWidth(width);
        table.invalidate();
        if (header != null) {
            header.revalidate();
        }
        table.revalidate();
        Component scrollAncestor = SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        if (scrollAncestor != null) {
            scrollAncestor.revalidate();
        }
    }

    /** Draft table on Process Sale: no cell right-click menu; no vertical grid lines (cleaner columns). */
    private static void configureProcessSaleDraftTable(JTable table) {
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        if (table.getTableHeader() != null) {
            table.getTableHeader().setReorderingAllowed(false);
            table.getTableHeader().setResizingAllowed(true);
        }
        deferPackTableColumns(table);
    }

    /** Installs right-click copy-cell menu behavior for a table. */
    private static void installTableCopyMenu(JTable table) {
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        if (table.getTableHeader() != null) {
            table.getTableHeader().setReorderingAllowed(false);
            table.getTableHeader().setResizingAllowed(true);
        }
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyCell = new JMenuItem("Copy Cell");
        copyCell.addActionListener(e -> copySelectedCell(table));
        popupMenu.add(copyCell);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }

            private void maybeShowMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col >= 0) {
                    table.setRowSelectionInterval(row, row);
                    table.setColumnSelectionInterval(col, col);
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        deferPackTableColumns(table);
    }

    /** Copies selected table cell text to the system clipboard. */
    private static void copySelectedCell(JTable table) {
        int row = table.getSelectedRow();
        int col = table.getSelectedColumn();
        if (row < 0 || col < 0) {
            return;
        }
        Object value = table.getValueAt(row, col);
        String text = value == null ? "" : value.toString();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private static final class StorageLocationPick {
        final int id;
        final String label;
        final int qtyAvailable;

        StorageLocationPick(int id, String label) {
            this(id, label, 0);
        }

        StorageLocationPick(int id, String label, int qtyAvailable) {
            this.id = id;
            this.label = label;
            this.qtyAvailable = qtyAvailable;
        }

        @Override
        public String toString() {
            return qtyAvailable > 0 ? label + " (" + qtyAvailable + ")" : label;
        }
    }

    private static void refreshActiveStorageLocationCombo(JComboBox<StorageLocationPick> combo, Connection connection) throws SQLException {
        combo.removeAllItems();
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        String sql = """
                SELECT id, name FROM storage_locations
                WHERE active = 1 OR id = ?
                ORDER BY sort_order ASC, name COLLATE NOCASE ASC
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int uid = DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    combo.addItem(new StorageLocationPick(rs.getInt("id"), rs.getString("name")));
                }
            }
        }
        if (combo.getItemCount() == 0) {
            combo.addItem(new StorageLocationPick(DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID, "Unassigned"));
        }
    }

    private static void incrementInventoryStorageQty(Connection connection, String itemCode, int locationId, int qty) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection) || qty <= 0) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO inventory_storage_qty (item_code, location_id, qty) VALUES (?,?,?)
                ON CONFLICT(item_code, location_id) DO UPDATE SET
                  qty = inventory_storage_qty.qty + excluded.qty
                """)) {
            ps.setString(1, itemCode);
            ps.setInt(2, locationId);
            ps.setInt(3, qty);
            ps.executeUpdate();
        }
    }

    /** Quantity of {@code itemCode} in a single bin, or zero when none. */
    private static int getInventoryStorageQtyAtLocation(Connection connection, String itemCode, int locationId) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return 0;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT qty FROM inventory_storage_qty WHERE item_code = ? AND location_id = ?")) {
            ps.setString(1, itemCode);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt(1);
            }
        }
    }

    private static String htmlEscapePlainTextForJLabel(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static int resolveStorageLocationIdForItemBin(
            Connection connection,
            String itemCode,
            String locationName
    ) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT s.location_id
                FROM inventory_storage_qty s
                INNER JOIN storage_locations sl ON sl.id = s.location_id
                WHERE s.item_code = ? AND sl.name = ? COLLATE NOCASE
                """)) {
            ps.setString(1, itemCode);
            ps.setString(2, locationName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Could not resolve the source bin for this row.");
                }
                return rs.getInt(1);
            }
        }
    }

    /**
     * Loads every storage location into the combo, omitting {@code excludeLocationId} (typically the move source).
     */
    private static void fillStorageLocationComboExcluding(
            JComboBox<StorageLocationPick> combo,
            Connection connection,
            int excludeLocationId
    ) throws SQLException {
        combo.removeAllItems();
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, name FROM storage_locations
                ORDER BY sort_order ASC, name COLLATE NOCASE ASC
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (id != excludeLocationId) {
                        combo.addItem(new StorageLocationPick(id, rs.getString("name")));
                    }
                }
            }
        }
    }

    /** Bins that currently hold positive quantity for one SKU (for Quick Transfer source picker). */
    private static void fillStorageLocationComboForItemBins(
            JComboBox<StorageLocationPick> combo,
            Connection connection,
            String itemCode
    ) throws SQLException {
        combo.removeAllItems();
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        String code = itemCode == null ? "" : itemCode.trim();
        if (code.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT sl.id, sl.name, isq.qty
                FROM inventory_storage_qty isq
                JOIN storage_locations sl ON sl.id = isq.location_id
                WHERE isq.item_code = ? AND isq.qty > 0
                ORDER BY sl.sort_order ASC, sl.name COLLATE NOCASE ASC
                """)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    combo.addItem(new StorageLocationPick(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("qty")));
                }
            }
        }
    }

    /**
     * Moves sellable quantity between {@code inventory_storage_qty} rows without changing {@code Inventory.Stock}.
     */
    private static void moveInventoryBetweenStorageLocations(
            Connection connection,
            User user,
            String itemCode,
            int fromLocationId,
            int toLocationId,
            int quantity
    ) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection) || quantity <= 0) {
            throw new SQLException("Quantity must be positive.");
        }
        if (fromLocationId == toLocationId) {
            throw new SQLException("Choose a different destination bin.");
        }
        boolean saved = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int have;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT qty FROM inventory_storage_qty WHERE item_code = ? AND location_id = ?")) {
                ps.setString(1, itemCode);
                ps.setInt(2, fromLocationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("No quantity in the source bin for this item.");
                    }
                    have = rs.getInt(1);
                }
            }
            if (have < quantity) {
                throw new SQLException("Source bin only has " + have + " units.");
            }
            int remaining = have - quantity;
            if (remaining == 0) {
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM inventory_storage_qty WHERE item_code = ? AND location_id = ?")) {
                    del.setString(1, itemCode);
                    del.setInt(2, fromLocationId);
                    del.executeUpdate();
                }
            } else {
                try (PreparedStatement up = connection.prepareStatement(
                        "UPDATE inventory_storage_qty SET qty = ? WHERE item_code = ? AND location_id = ?")) {
                    up.setInt(1, remaining);
                    up.setString(2, itemCode);
                    up.setInt(3, fromLocationId);
                    up.executeUpdate();
                }
            }
            incrementInventoryStorageQty(connection, itemCode, toLocationId, quantity);

            String reason = "BIN_TRANSFER fromLocId=" + fromLocationId + " toLocId=" + toLocationId;
            try (PreparedStatement movement = connection.prepareStatement(
                    "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                movement.setString(1, itemCode);
                movement.setString(2, String.valueOf(quantity));
                movement.setString(3, "TRANSFER");
                movement.setString(4, reason);
                movement.setString(5, user.getUsername());
                movement.setString(6, dateTime.nowDisplayString());
                movement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(saved);
        }
    }

    /**
     * Removes sellable quantity from one bin row during checkout or adjustments (never crosses bins).
     */
    private static void deductInventoryStorageQtyAtLocation(Connection connection, String itemCode, int locationId, int qtyToRemove) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection) || qtyToRemove <= 0) {
            return;
        }
        long rowid;
        int have;
        try (PreparedStatement sel = connection.prepareStatement("""
                SELECT rowid, qty FROM inventory_storage_qty WHERE item_code = ? AND location_id = ?
                """)) {
            sel.setString(1, itemCode);
            sel.setInt(2, locationId);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No quantity in the selected bin for item " + itemCode + ".");
                }
                rowid = rs.getLong(1);
                have = rs.getInt(2);
            }
        }
        if (have < qtyToRemove) {
            throw new SQLException("Selected bin only has " + have + " units for item " + itemCode + ".");
        }
        int newQty = have - qtyToRemove;
        if (newQty <= 0) {
            try (PreparedStatement del = connection.prepareStatement("DELETE FROM inventory_storage_qty WHERE rowid = ?")) {
                del.setLong(1, rowid);
                del.executeUpdate();
            }
        } else {
            try (PreparedStatement up = connection.prepareStatement("UPDATE inventory_storage_qty SET qty = ? WHERE rowid = ?")) {
                up.setInt(1, newQty);
                up.setLong(2, rowid);
                up.executeUpdate();
            }
        }
    }

    private static void deductInventoryStorageQtySpread(Connection connection, String itemCode, int qtyToRemove) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection) || qtyToRemove <= 0) {
            return;
        }
        int remaining = qtyToRemove;
        while (remaining > 0) {
            long rowid = -1;
            int have = 0;
            try (PreparedStatement sel = connection.prepareStatement("""
                    SELECT isq.rowid, isq.qty FROM inventory_storage_qty isq
                    INNER JOIN storage_locations sl ON sl.id = isq.location_id
                    WHERE isq.item_code = ?
                    ORDER BY CASE WHEN isq.location_id = ? THEN 0 ELSE 1 END,
                             sl.sort_order ASC,
                             sl.name COLLATE NOCASE ASC
                    LIMIT 1
                    """)) {
                sel.setString(1, itemCode);
                sel.setInt(2, DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("No warehouse bin rows remain for item " + itemCode
                                + ". Storage totals lost sync with Stock — reconnect after repair or reload data.");
                    }
                    rowid = rs.getLong(1);
                    have = rs.getInt(2);
                }
            }
            int take = Math.min(remaining, have);
            int newQty = have - take;
            if (newQty <= 0) {
                try (PreparedStatement del = connection.prepareStatement("DELETE FROM inventory_storage_qty WHERE rowid = ?")) {
                    del.setLong(1, rowid);
                    del.executeUpdate();
                }
            } else {
                try (PreparedStatement up = connection.prepareStatement("UPDATE inventory_storage_qty SET qty = ? WHERE rowid = ?")) {
                    up.setInt(1, newQty);
                    up.setLong(2, rowid);
                    up.executeUpdate();
                }
            }
            remaining -= take;
        }
    }

    /**
     * Executes integer scalar query helper with string parameters.
     *
     * @param connection active database connection
     * @param query SQL query returning one integer cell
     * @param params string parameters for the query
     * @return integer result or zero when no row exists
     * @throws SQLException when query fails
     */
    private static int getIntValue(Connection connection, String query, String... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private record PendingReceiveLine(String itemCode, String itemName, int amount, double purchasePrice) {
    }

    private static List<PendingReceiveLine> loadPendingReceiveLinesForReference(
            Connection connection,
            String reference
    ) throws SQLException {
        List<PendingReceiveLine> lines = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT po.`Item Code`,
                       COALESCE(inv.`Item Name`, '') AS iname,
                       po.`Amount`,
                       po.`Purchase Price`
                FROM pendingOrders po
                LEFT JOIN inventory inv ON inv.`Item Code` = po.`Item Code`
                WHERE po.`Reference` = ? AND po.`Amount` > 0
                ORDER BY po.`Item Code` ASC
                """)) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new PendingReceiveLine(
                            rs.getString("Item Code"),
                            rs.getString("iname"),
                            rs.getInt("Amount"),
                            rs.getDouble("Purchase Price")));
                }
            }
        }
        return lines;
    }

    private static void receiveEntirePurchaseOrder(
            User user,
            Connection connection,
            String reference,
            int storageLocationId,
            List<PendingReceiveLine> lines
    ) throws SQLException {
        boolean saved = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (PendingReceiveLine line : lines) {
                if (line.amount() > 0) {
                    applyReceive(user, connection, reference, line.itemCode(), line.amount(), line.purchasePrice(),
                            storageLocationId);
                }
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(saved);
        }
    }

    private static void showReceiveEntirePoDialog(
            Component parent,
            User user,
            Connection connection,
            String reference,
            Runnable onSuccess
    ) throws SQLException {
        List<PendingReceiveLine> lines = loadPendingReceiveLinesForReference(connection, reference);
        if (lines.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "No open lines remain for reference \"" + reference + "\".",
                    "Receive entire PO", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        DefaultTableModel previewModel = new DefaultTableModel(
                new String[]{"Item Code", "Item Name", "Qty to receive", "Unit cost"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        int totalUnits = 0;
        for (PendingReceiveLine line : lines) {
            previewModel.addRow(new Object[]{
                    line.itemCode(),
                    line.itemName(),
                    line.amount(),
                    formatUsdMoney(line.purchasePrice())
            });
            totalUnits += line.amount();
        }
        JTable previewTable = new JTable(previewModel);
        installTableCopyMenu(previewTable);
        JScrollPane previewScroll = new JScrollPane(previewTable);
        previewScroll.setPreferredSize(new Dimension(520, Math.min(220, 28 + lines.size() * 22)));

        JComboBox<StorageLocationPick> binCombo = new JComboBox<>();
        refreshActiveStorageLocationCombo(binCombo, connection);
        styleComboMatchInputRow(binCombo);

        JPanel dialogBody = new JPanel(new BorderLayout(0, 10));
        AppUI.applyPanelBackground(dialogBody);
        dialogBody.add(buildSectionText(
                "Receive all " + lines.size() + " open line(s) (" + totalUnits + " units) for reference "
                        + reference + "."), BorderLayout.NORTH);
        dialogBody.add(previewScroll, BorderLayout.CENTER);
        JPanel binRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(binRow);
        binRow.add(new JLabel("Receive into bin"));
        binRow.add(binCombo);
        dialogBody.add(binRow, BorderLayout.SOUTH);

        int choice = JOptionPane.showConfirmDialog(
                parent,
                dialogBody,
                "Receive entire PO",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        Object locSelection = binCombo.getSelectedItem();
        StorageLocationPick locPick = locSelection instanceof StorageLocationPick lp ? lp : null;
        int storageLocationId = locPick != null ? locPick.id : DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
        try {
            receiveEntirePurchaseOrder(user, connection, reference, storageLocationId, lines);
            for (PendingReceiveLine line : lines) {
                recordRecentItem(line.itemCode(), line.itemName());
            }
            requestMetricsRefresh();
            JOptionPane.showMessageDialog(parent,
                    "Received " + lines.size() + " line(s), " + totalUnits + " units for " + reference + ".",
                    "Receive entire PO", JOptionPane.INFORMATION_MESSAGE);
            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(),
                    "Receive entire PO", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Applies receive updates: increments sellable Stock, records FIFO layer, clears completed PO lines.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param referenceNumber purchase order reference
     * @param codeReceived item code received
     * @param amountReceived quantity received
     * @param purchasePrice received unit cost
     * @param storageLocationId physical bin ({@link DatabaseManager#STORAGE_LOCATION_UNASSIGNED_ID} when not labeled)
     * @throws SQLException when updates fail
     */
    private static void applyReceive(
            User user,
            Connection connection,
            String referenceNumber,
            String codeReceived,
            int amountReceived,
            double purchasePrice,
            int storageLocationId
    ) throws SQLException {
        try (PreparedStatement updateReceivingLog = connection.prepareStatement("UPDATE pendingOrders SET Amount = Amount - ? WHERE `Reference` = ? AND `Item Code` = ?")) {
            updateReceivingLog.setInt(1, amountReceived);
            updateReceivingLog.setString(2, referenceNumber);
            updateReceivingLog.setString(3, codeReceived);
            updateReceivingLog.executeUpdate();
        }
        try (PreparedStatement updateOnOrder = connection.prepareStatement("UPDATE Inventory SET `On Order` = `On Order` - ? WHERE `Item Code` = ?")) {
            updateOnOrder.setInt(1, amountReceived);
            updateOnOrder.setString(2, codeReceived);
            updateOnOrder.executeUpdate();
        }
        try (PreparedStatement updateStock = connection.prepareStatement("UPDATE Inventory SET Stock = Stock + ? WHERE `Item Code` = ?")) {
            updateStock.setInt(1, amountReceived);
            updateStock.setString(2, codeReceived);
            updateStock.executeUpdate();
        }
        incrementInventoryStorageQty(connection, codeReceived, storageLocationId, amountReceived);
        try (PreparedStatement addLayer = connection.prepareStatement(
                "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            addLayer.setString(1, codeReceived);
            addLayer.setString(2, referenceNumber);
            addLayer.setDouble(3, purchasePrice);
            addLayer.setInt(4, amountReceived);
            addLayer.setInt(5, amountReceived);
            addLayer.setString(6, dateTime.nowDisplayString());
            addLayer.executeUpdate();
        }
        String movementReason = "PURCHASE_ORDER_RECEIPT locId=" + storageLocationId;
        try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
            movement.setString(1, codeReceived);
            movement.setString(2, String.valueOf(amountReceived));
            movement.setString(3, "RECEIVED");
            movement.setString(4, movementReason);
            movement.setString(5, user.getUsername());
            movement.setString(6, dateTime.nowDisplayString());
            movement.executeUpdate();
        }
        try (PreparedStatement deleteCompletedOrder = connection.prepareStatement("DELETE FROM pendingOrders WHERE `Reference` = ? AND `Item Code` = ? AND Amount = 0")) {
            deleteCompletedOrder.setString(1, referenceNumber);
            deleteCompletedOrder.setString(2, codeReceived);
            deleteCompletedOrder.executeUpdate();
        }
    }

    /** Sizes combo boxes like {@link #styleInput(JTextField...)} for aligned form rows. */
    private static void styleComboMatchInputRow(JComboBox<?>... combos) {
        for (JComboBox<?> combo : combos) {
            combo.setBorder(AppUI.newRoundedBorder(8));
            Dimension p = combo.getPreferredSize();
            combo.setPreferredSize(new Dimension(p.width, INPUT_HEIGHT));
        }
    }

    /** Applies shared rounded-border styling to input fields. */
    private static void styleInput(JTextField... fields) {
        for (JTextField field : fields) {
            field.setBorder(AppUI.newRoundedBorder(8));
            field.setPreferredSize(new Dimension(field.getPreferredSize().width, INPUT_HEIGHT));
        }
    }

    /** Compact height for Add Item fields; horizontal size uses preferred width so columns align with defaults. */
    private static void styleInputCompact(JTextField... fields) {
        for (JTextField field : fields) {
            field.setBorder(AppUI.newRoundedBorder(8));
            Dimension pref = field.getPreferredSize();
            field.setPreferredSize(new Dimension(pref.width, ADD_ITEM_INPUT_HEIGHT));
        }
    }

    /** Read-only value from inventory: muted look so it reads as filled data, not a normal text box. */
    private static void styleAutoFilledInventoryField(JTextField field) {
        field.setEditable(false);
        field.setOpaque(true);
        field.setBackground(AppUI.SURFACE_ELEVATED);
        field.setForeground(AppUI.TEXT_MUTED);
    }

    /**
     * Applies consistent border and control height styling for password fields.
     *
     * @param fields password field controls to style
     */
    private static void stylePasswordInput(JPasswordField... fields) {
        for (JPasswordField field : fields) {
            field.setBorder(AppUI.newRoundedBorder(8));
            field.setPreferredSize(new Dimension(field.getPreferredSize().width, INPUT_HEIGHT));
        }
    }

    /** Matches {@link #styleInputCompact(JTextField...)} height for aligned password rows. */
    private static void stylePasswordInputCompact(JPasswordField... fields) {
        for (JPasswordField field : fields) {
            field.setBorder(AppUI.newRoundedBorder(8));
            Dimension pref = field.getPreferredSize();
            field.setPreferredSize(new Dimension(pref.width, ADD_ITEM_INPUT_HEIGHT));
        }
    }

    /**
     * Returns the next available sequential item code in ITM0001-ITM9999 format.
     *
     * @param connection active database connection
     * @return next available item code
     * @throws SQLException when query fails or all item codes are exhausted
     */
    private static String getNextItemCode(Connection connection) throws SQLException {
        boolean[] usedCodes = new boolean[10000];
        String query = "SELECT `Item Code` FROM inventory";
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String code = resultSet.getString("Item Code");
                if (code == null || code.length() != 7 || !code.startsWith("ITM")) {
                    continue;
                }
                String suffix = code.substring(3);
                if (!suffix.chars().allMatch(Character::isDigit)) {
                    continue;
                }
                int value = Integer.parseInt(suffix);
                if (value >= 1 && value <= 9999) {
                    usedCodes[value] = true;
                }
            }
        }
        for (int i = 1; i <= 9999; i++) {
            if (!usedCodes[i]) {
                return String.format("ITM%04d", i);
            }
        }
        throw new SQLException("No item codes available. ITM0001-ITM9999 are all in use.");
    }

    /** Runs an action with standardized user-facing error handling. */
    private static void runAction(CheckedAction action) {
        try {
            action.run();
        } catch (SecurityException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Access Denied", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Unable to complete action: " + e.getMessage(), "Action Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Ensures admin rights before opening restricted workflows. */
    private static void ensureAdmin(User user, String actionName) {
        if (user == null || !user.hasAdminRights()) {
            throw new SecurityException("Access denied. Administrator rights are required for: " + actionName);
        }
    }

    /** Closes database connection and suppresses shutdown-time failures. */
    private static void closeConnectionQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // Ignore close failures when exiting workspace.
        }
    }

    /** Runnable executed on worker threads where checked exceptions propagate to error handling. */
    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    /** Sidebar entry that either opens a lazily-built panel view or invokes a modal action thread. */
    private static final class NavItem {
        private final String label;
        private final CheckedAction action;
        private final ViewBuilder viewBuilder;

        /** Sidebar button that launches a background {@link CheckedAction} (logout, seeded runners, etc.). */
        private NavItem(String label, CheckedAction action) {
            this.label = label;
            this.action = action;
            this.viewBuilder = null;
        }

        /** Sidebar button whose click shows a synchronous SQL-backed panel ({@link #build}). */
        private NavItem(String label, ViewBuilder viewBuilder) {
            this.label = label;
            this.action = () -> {};
            this.viewBuilder = viewBuilder;
        }
    }

    /** Builds a workspace center panel lazily inside {@link WorkspaceShell#getItems} navigation clicks. */
    @FunctionalInterface
    private interface ViewBuilder {
        JPanel build() throws SQLException;
    }

    /** Mutable accumulator for CSV export rows and summary metadata. */
    private static final class ReportData {
        private final String title;
        private final Map<String, String> summary = new LinkedHashMap<>();
        private String[] columns = new String[0];
        private final List<Object[]> rows = new ArrayList<>();

        /** @param title report heading shown above summary rows */
        private ReportData(String title) {
            this.title = title;
        }
    }

}
