import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stress-test loader: inserts 200 Pokémon TCG–style SKU rows ({@link #ITEM_PREFIX}), minimal FIFO cost layers,
 * and exactly {@value #TOTAL_TX_ROWS} transactional rows spread across {@code sales}, mirrored {@code movements}
 * SALE lines, additional {@code movements}, and {@code pendingOrders}.
 * <p>
 * Product captions remix widely published Pokémon TCG expansion names (Scarlet & Violet series, Mega Evolution,
 * select Sword & Shield sets) paired with typical retail formats (Elite Trainer Box, Booster Bundle, etc.; see Pokémon
 * product summaries at https://tcg.pokemon.com/en-us/all-expansions/).
 * SKU codes remain synthetic ({@code PKM0001}…) for inventory keys.
 * </p>
 * <p>Purge wipes only rows keyed by {@link #ITEM_PREFIX}. Run:</p>
 * <pre>{@code java -cp ".:lib/*" StressTestPokemonSeeder}</pre>
 */
public final class StressTestPokemonSeeder {

    private static final String ITEM_PREFIX = "PKM";

    /** 25 official-style set names × 8 product shells = {@value #TARGET_ITEM_COUNT}. */
    private static final int TARGET_ITEM_COUNT = 200;

    /** sales + SALE movements + extra movements + pendingOrders = total inserted beyond inventory/layers */
    private static final int SALE_LINES = 65_000;
    private static final int EXTRA_MOVEMENTS = 62_750;
    private static final int PENDING_LINES = 7_250;
    /** 2 × SALE_LINES + EXTRA_MOVEMENTS + PENDING_LINES */
    private static final int TOTAL_TX_ROWS = SALE_LINES + SALE_LINES + EXTRA_MOVEMENTS + PENDING_LINES;

    private static final int LAYERS_PER_ITEM = 3;
    private static final String SEED_USER = "stress_demo";
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    /** Scarlet/Violet-era + Mega Evolution headlines + Sword/Shield staples (subset), per TCG press/gallery wording. */
    private static final String[] SET_LINES = new String[]{
            "Mega Evolution",
            "Mega Evolution—Perfect Order",
            "Mega Evolution—Ascended Heroes",
            "Mega Evolution—Phantasmal Flames",
            "Scarlet & Violet",
            "Scarlet & Violet—Paldea Evolved",
            "Scarlet & Violet—Obsidian Flames",
            "Scarlet & Violet—151",
            "Scarlet & Violet—Paradox Rift",
            "Scarlet & Violet—Paldean Fates",
            "Scarlet & Violet—Temporal Forces",
            "Scarlet & Violet—Twilight Masquerade",
            "Scarlet & Violet—Shrouded Fable",
            "Scarlet & Violet—Stellar Crown",
            "Scarlet & Violet—Surging Sparks",
            "Scarlet & Violet—Prismatic Evolutions",
            "Scarlet & Violet—Journey Together",
            "Scarlet & Violet—Destined Rivals",
            "Scarlet & Violet—Black Bolt",
            "Scarlet & Violet—White Flare",
            "Sword & Shield—Brilliant Stars",
            "Sword & Shield—Lost Origin",
            "Sword & Shield—Silver Tempest",
            "Sword & Shield—Crown Zenith",
            "Sword & Shield—Evolving Skies"
    };

    private static final String[] PRODUCT_SHELLS = new String[]{
            "Elite Trainer Box",
            "Pokemon Center Elite Trainer Box",
            "Booster Bundle",
            "Booster Display",
            "Build & Battle Box",
            "Premium Collection",
            "Stacking Tin",
            "League Battle Deck"
    };

    private static final String[] EXTRA_MOV_TYPES = {"RECEIVED", "ORDERED", "ADD", "WRITE OFF"};
    private static final String[] EXTRA_MOV_REASONS = {
            "STRESS_SEED", "PO_RECEIPT", "PO_CREATED", "ADJUSTMENT", "RETAIL_RECEIVE"
    };

    private static final List<String> CATALOG_NAMES = buildCatalog();

    private StressTestPokemonSeeder() {
    }

    public static void main(String[] args) throws Exception {
        if (TOTAL_TX_ROWS != 200_000) {
            throw new IllegalStateException("Expected 200k tx rows: " + TOTAL_TX_ROWS);
        }
        DatabaseManager.initializeEnterpriseDatabase();

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        try (Connection connection = DatabaseManager.getConnection()) {
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA synchronous = NORMAL");
                pragma.execute("PRAGMA journal_mode = WAL");
            }

            connection.setAutoCommit(false);
            purgeStressData(connection);

            int[] soldTotals = new int[TARGET_ITEM_COUNT];
            int[] saleItemIdx = new int[SALE_LINES];
            int[] saleAmt = new int[SALE_LINES];
            for (int n = 0; n < SALE_LINES; n++) {
                int idx = rnd.nextInt(TARGET_ITEM_COUNT);
                int amt = 1 + rnd.nextInt(6);
                saleItemIdx[n] = idx;
                saleAmt[n] = amt;
                soldTotals[idx] += amt;
            }

            int[] stockNow = new int[TARGET_ITEM_COUNT];
            for (int i = 0; i < TARGET_ITEM_COUNT; i++) {
                stockNow[i] = 25 + rnd.nextInt(475);
            }

            seedInventory(connection, rnd, stockNow);
            seedCostLayers(connection, rnd, soldTotals, stockNow);
            seedSalesAndMirroredSaleMovements(connection, rnd, saleItemIdx, saleAmt);
            seedExtraMovements(connection, rnd);
            seedPendingOrders(connection, rnd);
            syncInventoryOnOrderFromPending(connection);
            DatabaseManager.reconcileStorageBucketsFromInventoryForSkuPrefix(connection, ITEM_PREFIX + "%");

            connection.commit();
        }
        printCounts();
        System.out.println("Stress seed complete (" + TARGET_ITEM_COUNT + " SKUs prefixed " + ITEM_PREFIX
                + "; " + TOTAL_TX_ROWS + " sales/movement/order rows)");
    }

    private static List<String> buildCatalog() {
        List<String> out = new ArrayList<>(TARGET_ITEM_COUNT);
        for (String set : SET_LINES) {
            for (String shell : PRODUCT_SHELLS) {
                out.add("Pokemon TCG — " + set + " — " + shell);
            }
        }
        if (out.size() != TARGET_ITEM_COUNT) {
            throw new IllegalStateException("Catalog size != 200: " + out.size());
        }
        return Collections.unmodifiableList(out);
    }

    private static void purgeStressData(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            String like = "'" + ITEM_PREFIX + "%'";
            st.executeUpdate("DELETE FROM inventory_cost_layers WHERE item_code LIKE " + like);
            st.executeUpdate("DELETE FROM sales WHERE \"Item Code\" LIKE " + like);
            st.executeUpdate("DELETE FROM movements WHERE \"Item\" LIKE " + like);
            st.executeUpdate("DELETE FROM pendingOrders WHERE \"Item Code\" LIKE " + like);
            st.executeUpdate("DELETE FROM Inventory WHERE \"Item Code\" LIKE " + like);
        }
    }

    private static void seedInventory(Connection c, ThreadLocalRandom rnd, int[] stockNow) throws SQLException {
        LocalDateTime anchor = LocalDateTime.now().minusDays(900);
        String sql = "INSERT INTO Inventory (\"Item Code\", \"Item Name\", \"Stock\", \"On Order\", \"ReOrder Trigger\", "
                + "\"Supplier\", \"Lead Time\", \"Notes\", \"Market Price\") VALUES (?,?,?,0,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int batch = 0;
            for (int i = 0; i < TARGET_ITEM_COUNT; i++) {
                ps.setString(1, itemCode(i));
                ps.setString(2, truncate(CATALOG_NAMES.get(i), 120));
                ps.setInt(3, stockNow[i]);
                ps.setInt(4, 4 + rnd.nextInt(72));
                ps.setString(5, distroName(rnd));
                ps.setInt(6, 1 + rnd.nextInt(21));
                ps.setString(7,
                        "Stress SKU | names derived from Pokémon TCG expansion gallery product families | seeded "
                                + DISPLAY_TS.format(anchor));
                ps.setDouble(8, Math.round((19.99 + rnd.nextDouble() * 249) * 100) / 100.0);
                ps.addBatch();
                batch++;
                if (batch >= 250) {
                    ps.executeBatch();
                    batch = 0;
                }
            }
            if (batch > 0) {
                ps.executeBatch();
            }
        }
    }

    private static void seedCostLayers(
            Connection c,
            ThreadLocalRandom rnd,
            int[] soldTotals,
            int[] stockNow
    ) throws SQLException {
        LocalDateTime horizon = LocalDateTime.now().minusDays(800);
        String sql = "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) "
                + "VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int batch = 0;
            for (int i = 0; i < TARGET_ITEM_COUNT; i++) {
                String code = itemCode(i);
                int sold = soldTotals[i];
                int stock = stockNow[i];
                LocalDateTime t = horizon.plusDays(rnd.nextInt(540));
                int consumedSlots = LAYERS_PER_ITEM - 1;
                int base = consumedSlots > 0 ? sold / consumedSlots : 0;
                int rem = consumedSlots > 0 ? sold % consumedSlots : 0;
                for (int slot = 0; slot < consumedSlots; slot++) {
                    int chunk = base + (slot < rem ? 1 : 0);
                    double unitCost = round2(9 + rnd.nextDouble() * 60);
                    ps.setString(1, code);
                    ps.setString(2, "STRESS-L-" + code + "-" + slot);
                    ps.setDouble(3, unitCost);
                    ps.setInt(4, chunk);
                    ps.setInt(5, 0);
                    ps.setString(6, DISPLAY_TS.format(t));
                    ps.addBatch();
                    batch++;
                    if (batch >= 250) {
                        ps.executeBatch();
                        batch = 0;
                    }
                    t = t.plusDays(14 + rnd.nextInt(180));
                }
                ps.setString(1, code);
                ps.setString(2, "STRESS-L-" + code + "-open");
                ps.setDouble(3, round2(11 + rnd.nextDouble() * 55));
                ps.setInt(4, stock);
                ps.setInt(5, stock);
                ps.setString(6, DISPLAY_TS.format(t.plusDays(rnd.nextInt(40))));
                ps.addBatch();
                batch++;
                if (batch >= 250) {
                    ps.executeBatch();
                    batch = 0;
                }
            }
            if (batch > 0) {
                ps.executeBatch();
            }
        }
    }

    private static void seedSalesAndMirroredSaleMovements(
            Connection c,
            ThreadLocalRandom rnd,
            int[] saleItemIdx,
            int[] saleAmt
    ) throws SQLException {
        LocalDateTime horizon = LocalDateTime.now().minusDays(720);
        String saleSql = "INSERT INTO sales (\"Item Code\", \"Item Name\", \"Amount\", \"Total Price\", \"Total Cost\", "
                + "\"Reference\", \"User\", \"Date\", \"DateISO\", \"Note\") VALUES (?,?,?,?,?,?,?,?,?,?)";
        String movSql = "INSERT INTO movements (\"Item\", \"Amount\", \"Type\", \"Reason\", \"User\", \"Date\") VALUES (?,?,?,?,?,?)";
        int batchSale = 0;
        int batchMov = 0;
        try (PreparedStatement salePs = c.prepareStatement(saleSql);
             PreparedStatement movPs = c.prepareStatement(movSql)) {
            for (int n = 0; n < SALE_LINES; n++) {
                int idx = saleItemIdx[n];
                int amt = saleAmt[n];
                String code = itemCode(idx);
                String nm = truncate(CATALOG_NAMES.get(idx), 96);
                double unitSale = 25 + rnd.nextDouble() * 140;
                double unitCost = 12 + rnd.nextDouble() * 70;
                double totalSale = round2(unitSale * amt);
                double totalCost = round2(unitCost * amt);
                LocalDateTime when = horizon.plusDays(rnd.nextInt(650)).plusMinutes(rnd.nextInt(1440));
                String disp = DISPLAY_TS.format(when);
                String iso = toIsoTail(disp);
                String ref = "PKM-SALE-" + n;

                salePs.setString(1, code);
                salePs.setString(2, nm);
                salePs.setInt(3, amt);
                salePs.setDouble(4, totalSale);
                salePs.setDouble(5, totalCost);
                salePs.setString(6, ref);
                salePs.setString(7, SEED_USER);
                salePs.setString(8, disp);
                salePs.setString(9, iso);
                salePs.setNull(10, java.sql.Types.VARCHAR);
                salePs.addBatch();
                batchSale++;

                movPs.setString(1, code);
                movPs.setInt(2, amt);
                movPs.setString(3, "SALE");
                movPs.setString(4, "CUSTOMER_STRESS");
                movPs.setString(5, SEED_USER);
                movPs.setString(6, disp);
                movPs.addBatch();
                batchMov++;

                if (batchSale >= 2_500) {
                    salePs.executeBatch();
                    batchSale = 0;
                }
                if (batchMov >= 2_500) {
                    movPs.executeBatch();
                    batchMov = 0;
                }
            }
            if (batchSale > 0) {
                salePs.executeBatch();
            }
            if (batchMov > 0) {
                movPs.executeBatch();
            }
        }
    }

    private static void seedExtraMovements(Connection c, ThreadLocalRandom rnd) throws SQLException {
        LocalDateTime horizon = LocalDateTime.now().minusDays(720);
        String sql = "INSERT INTO movements (\"Item\", \"Amount\", \"Type\", \"Reason\", \"User\", \"Date\") VALUES (?,?,?,?,?,?)";
        int batch = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int n = 0; n < EXTRA_MOVEMENTS; n++) {
                String code = itemCode(rnd.nextInt(TARGET_ITEM_COUNT));
                int amt = 1 + rnd.nextInt(48);
                String type = EXTRA_MOV_TYPES[rnd.nextInt(EXTRA_MOV_TYPES.length)];
                String reason = EXTRA_MOV_REASONS[rnd.nextInt(EXTRA_MOV_REASONS.length)];
                LocalDateTime when = horizon.plusDays(rnd.nextInt(690)).plusMinutes(rnd.nextInt(1440));
                String disp = DISPLAY_TS.format(when);
                ps.setString(1, code);
                ps.setInt(2, amt);
                ps.setString(3, type);
                ps.setString(4, reason);
                ps.setString(5, SEED_USER);
                ps.setString(6, disp);
                ps.addBatch();
                batch++;
                if (batch >= 2_500) {
                    ps.executeBatch();
                    batch = 0;
                }
            }
            if (batch > 0) {
                ps.executeBatch();
            }
        }
    }

    private static void seedPendingOrders(Connection c, ThreadLocalRandom rnd) throws SQLException {
        LocalDateTime horizon = LocalDateTime.now().minusDays(180);
        String sql = "INSERT INTO pendingOrders (\"Item Code\", \"Amount\", \"Purchase Price\", \"Purchased From\", "
                + "\"Reference\", \"User\", \"Date\") VALUES (?,?,?,?,?,?,?)";
        int batch = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int n = 0; n < PENDING_LINES; n++) {
                String code = itemCode(rnd.nextInt(TARGET_ITEM_COUNT));
                int amt = 1 + rnd.nextInt(120);
                double price = round2(14 + rnd.nextDouble() * 95);
                LocalDateTime when = horizon.plusDays(rnd.nextInt(150)).plusMinutes(rnd.nextInt(2880));
                ps.setString(1, code);
                ps.setInt(2, amt);
                ps.setDouble(3, price);
                ps.setString(4, distroName(rnd));
                ps.setString(5, "PKMPO-" + n);
                ps.setString(6, SEED_USER);
                ps.setString(7, DISPLAY_TS.format(when));
                ps.addBatch();
                batch++;
                if (batch >= 2_500) {
                    ps.executeBatch();
                    batch = 0;
                }
            }
            if (batch > 0) {
                ps.executeBatch();
            }
        }
    }

    private static void syncInventoryOnOrderFromPending(Connection connection) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(
                "UPDATE Inventory SET \"On Order\" = (SELECT COALESCE(SUM(po.\"Amount\"),0) FROM pendingOrders po "
                        + "WHERE po.\"Item Code\" = Inventory.\"Item Code\") WHERE Inventory.\"Item Code\" LIKE ?")) {
            upsert.setString(1, ITEM_PREFIX + "%");
            upsert.executeUpdate();
        }
    }

    private static String distroName(ThreadLocalRandom rnd) {
        return "Distributor-" + (1 + rnd.nextInt(14));
    }

    private static String itemCode(int index) {
        return ITEM_PREFIX + String.format("%04d", index + 1);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(max - 1, 0)).trim();
    }

    private static String toIsoTail(String displayDateTime) {
        if (displayDateTime.length() < 19) {
            return null;
        }
        return displayDateTime.substring(6, 10) + "-"
                + displayDateTime.substring(3, 5) + "-"
                + displayDateTime.substring(0, 2)
                + displayDateTime.substring(10);
    }

    private static void printCounts() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection(); Statement st = connection.createStatement()) {
            dump(st, "Inventory");
            dump(st, "inventory_cost_layers");
            dump(st, "sales");
            dump(st, "movements");
            dump(st, "pendingOrders");
        }
    }

    private static void dump(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) {
                System.out.println(table + ": " + rs.getLong(1));
            }
        }
    }
}
