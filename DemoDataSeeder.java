import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One-shot demo loader: replaces prior {@code DMO%} inventory with synthetic items, cost layers, sales,
 * movements, and pending purchase lines totaling 10,000 rows (excluding purge).
 * <p>
 * Run from the project root: {@code java -cp ".:lib/*" DemoDataSeeder}
 * </p>
 */
public final class DemoDataSeeder {

    /** Item codes use this prefix so re-runs only delete demo SKUs. */
    private static final String ITEM_PREFIX = "DMO";
    private static final int ITEM_COUNT = 100;
    private static final int LAYERS_PER_ITEM = 14;
    private static final int SALES_ROWS = 3200;
    private static final int MISC_MOVEMENT_ROWS = 1800;
    private static final int PENDING_ORDER_ROWS = 300;
    private static final int TARGET_TOTAL_ROWS =
            ITEM_COUNT + ITEM_COUNT * LAYERS_PER_ITEM + SALES_ROWS + SALES_ROWS + MISC_MOVEMENT_ROWS + PENDING_ORDER_ROWS;

    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final String[] MISC_TYPES = {"RECEIVED", "ORDERED", "ADD", "WRITE OFF"};
    private static final String[] MISC_REASONS = {
            "SEED_DEMO",
            "PURCHASE_ORDER_RECEIPT",
            "PURCHASE_ORDER_CREATED",
            "INITIAL_STOCK",
            "ADJUSTMENT"
    };

    private DemoDataSeeder() {
    }

    /**
     * Seeds ~10k deterministic demo rows ({@value #TARGET_TOTAL_ROWS}) into the enterprise SQLite database.
     *
     * @param args unused
     * @throws Exception when bootstrap, SQL, or invariant checks fail
     */
    public static void main(String[] args) throws Exception {
        if (TARGET_TOTAL_ROWS != 10_000) {
            throw new IllegalStateException("Row budget must sum to 10000, got " + TARGET_TOTAL_ROWS);
        }
        DatabaseManager.initializeEnterpriseDatabase();
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            purgeDemoData(connection);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            LocalDateTime horizon = LocalDateTime.now().minusDays(730);

            int[] soldTotals = new int[ITEM_COUNT];
            int[] saleItemIdx = new int[SALES_ROWS];
            int[] saleAmt = new int[SALES_ROWS];
            for (int n = 0; n < SALES_ROWS; n++) {
                int idx = rnd.nextInt(ITEM_COUNT);
                int amt = 1 + rnd.nextInt(5);
                saleItemIdx[n] = idx;
                saleAmt[n] = amt;
                soldTotals[idx] += amt;
            }
            int[] stockNow = new int[ITEM_COUNT];
            for (int i = 0; i < ITEM_COUNT; i++) {
                stockNow[i] = 20 + rnd.nextInt(160);
            }

            seedInventory(connection, rnd, stockNow);
            seedCostLayers(connection, rnd, horizon, soldTotals, stockNow);
            seedSalesAndSaleMovements(connection, rnd, horizon, saleItemIdx, saleAmt);
            seedMiscMovements(connection, rnd, horizon);
            seedPendingOrders(connection, rnd, horizon);

            connection.commit();
        }
        printCounts();
        System.out.println("Demo seed complete (" + TARGET_TOTAL_ROWS + " inserted rows, prefix " + ITEM_PREFIX + ").");
    }

    /** Deletes rows whose primary keys start with {@link #ITEM_PREFIX}. */
    private static void purgeDemoData(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM inventory_cost_layers WHERE item_code LIKE '" + ITEM_PREFIX + "%'");
            statement.executeUpdate("DELETE FROM sales WHERE \"Item Code\" LIKE '" + ITEM_PREFIX + "%'");
            statement.executeUpdate("DELETE FROM movements WHERE \"Item\" LIKE '" + ITEM_PREFIX + "%'");
            statement.executeUpdate("DELETE FROM pendingOrders WHERE \"Item Code\" LIKE '" + ITEM_PREFIX + "%'");
            statement.executeUpdate("DELETE FROM Inventory WHERE \"Item Code\" LIKE '" + ITEM_PREFIX + "%'");
        }
    }

    /** Inserts demo {@code Inventory} rows with {@code On Order = 0}. */
    private static void seedInventory(Connection connection, ThreadLocalRandom rnd, int[] stockNow) throws SQLException {
        String sql = "INSERT INTO Inventory (\"Item Code\", \"Item Name\", \"Stock\", \"On Order\", "
                + "\"ReOrder Trigger\", \"Supplier\", \"Lead Time\", \"Notes\", \"Market Price\") VALUES (?, ?, ?, 0, ?, ?, ?, ?, NULL)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < ITEM_COUNT; i++) {
                String code = itemCode(i);
                ps.setString(1, code);
                ps.setString(2, "Demo SKU " + (i + 1));
                ps.setInt(3, stockNow[i]);
                ps.setInt(4, 5 + rnd.nextInt(45));
                ps.setString(5, "Supplier " + (1 + rnd.nextInt(12)));
                ps.setInt(6, 1 + rnd.nextInt(14));
                ps.setString(7, "Synthetic demo item for reports.");
                ps.executeUpdate();
            }
        }
    }

    /**
     * Inserts synthetic FIFO layers per item: mostly consumed history plus one open layer matching {@code stockNow}.
     *
     * @param soldTotals cumulative units “sold” driving historical layer sizes
     */
    private static void seedCostLayers(
            Connection connection,
            ThreadLocalRandom rnd,
            LocalDateTime horizon,
            int[] soldTotals,
            int[] stockNow
    ) throws SQLException {
        String sql = "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < ITEM_COUNT; i++) {
                String code = itemCode(i);
                int sold = soldTotals[i];
                int stock = stockNow[i];
                int consumedSlots = LAYERS_PER_ITEM - 1;
                int base = consumedSlots > 0 ? sold / consumedSlots : 0;
                int rem = consumedSlots > 0 ? sold % consumedSlots : 0;
                LocalDateTime t = horizon.plusDays(rnd.nextInt(400));
                for (int j = 0; j < consumedSlots; j++) {
                    int qty = base + (j < rem ? 1 : 0);
                    double unitCost = 4.5 + rnd.nextDouble() * 35;
                    ps.setString(1, code);
                    ps.setString(2, "SEED-L-" + code + "-" + j);
                    ps.setDouble(3, Math.round(unitCost * 100) / 100.0);
                    ps.setInt(4, qty);
                    ps.setInt(5, 0);
                    ps.setString(6, formatDisplay(t));
                    ps.executeUpdate();
                    t = t.plusHours(1 + rnd.nextInt(48));
                }
                double unitCostLast = 4.5 + rnd.nextDouble() * 35;
                ps.setString(1, code);
                ps.setString(2, "SEED-L-" + code + "-active");
                ps.setDouble(3, Math.round(unitCostLast * 100) / 100.0);
                ps.setInt(4, stock);
                ps.setInt(5, stock);
                ps.setString(6, formatDisplay(t.plusDays(rnd.nextInt(30))));
                ps.executeUpdate();
            }
        }
    }

    /** Writes parallel {@code sales} rows and {@code SALE} movement audit lines. */
    private static void seedSalesAndSaleMovements(
            Connection connection,
            ThreadLocalRandom rnd,
            LocalDateTime horizon,
            int[] saleItemIdx,
            int[] saleAmt
    ) throws SQLException {
        String saleSql = "INSERT INTO sales (\"Item Code\", \"Item Name\", \"Amount\", \"Total Price\", \"Total Cost\", "
                + "\"Reference\", \"User\", \"Date\", \"DateISO\", \"Note\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String movSql = "INSERT INTO movements (\"Item\", \"Amount\", \"Type\", \"Reason\", \"User\", \"Date\") VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement salePs = connection.prepareStatement(saleSql);
             PreparedStatement movPs = connection.prepareStatement(movSql)) {
            for (int n = 0; n < SALES_ROWS; n++) {
                int idx = saleItemIdx[n];
                int amt = saleAmt[n];
                String code = itemCode(idx);
                String name = "Demo SKU " + (idx + 1);
                double unitSale = 12 + rnd.nextDouble() * 55;
                double unitCost = 6 + rnd.nextDouble() * 28;
                double totalSale = Math.round(unitSale * amt * 100) / 100.0;
                double totalCost = Math.round(unitCost * amt * 100) / 100.0;
                LocalDateTime when = horizon.plusDays(rnd.nextInt(700)).plusMinutes(rnd.nextInt(24 * 60));
                String disp = formatDisplay(when);
                String iso = toIsoDateTime(disp);
                String reference = "S-DMO-" + String.format("%05d", n);

                salePs.setString(1, code);
                salePs.setString(2, name);
                salePs.setInt(3, amt);
                salePs.setDouble(4, totalSale);
                salePs.setDouble(5, totalCost);
                salePs.setString(6, reference);
                salePs.setString(7, "Admin");
                salePs.setString(8, disp);
                salePs.setString(9, iso);
                salePs.setNull(10, java.sql.Types.VARCHAR);
                salePs.executeUpdate();

                movPs.setString(1, code);
                movPs.setInt(2, amt);
                movPs.setString(3, "SALE");
                movPs.setString(4, "CUSTOMER_SALE");
                movPs.setString(5, "Admin");
                movPs.setString(6, disp);
                movPs.executeUpdate();
            }
        }
    }

    /** Seeds additional {@code movements} rows with assorted {@link #MISC_TYPES} for report variety. */
    private static void seedMiscMovements(Connection connection, ThreadLocalRandom rnd, LocalDateTime horizon) throws SQLException {
        String sql = "INSERT INTO movements (\"Item\", \"Amount\", \"Type\", \"Reason\", \"User\", \"Date\") VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int n = 0; n < MISC_MOVEMENT_ROWS; n++) {
                String code = itemCode(rnd.nextInt(ITEM_COUNT));
                int amt = 1 + rnd.nextInt(40);
                String type = MISC_TYPES[rnd.nextInt(MISC_TYPES.length)];
                String reason = MISC_REASONS[rnd.nextInt(MISC_REASONS.length)];
                LocalDateTime when = horizon.plusDays(rnd.nextInt(700)).plusMinutes(rnd.nextInt(24 * 60));
                String disp = formatDisplay(when);
                ps.setString(1, code);
                ps.setInt(2, amt);
                ps.setString(3, type);
                ps.setString(4, reason);
                ps.setString(5, "Admin");
                ps.setString(6, disp);
                ps.executeUpdate();
            }
        }
    }

    /** Inserts {@link #PENDING_ORDER_ROWS} open purchase lines referencing demo SKUs. */
    private static void seedPendingOrders(Connection connection, ThreadLocalRandom rnd, LocalDateTime horizon) throws SQLException {
        String sql = "INSERT INTO pendingOrders (\"Item Code\", \"Amount\", \"Purchase Price\", \"Reference\", \"User\", \"Date\") "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int n = 0; n < PENDING_ORDER_ROWS; n++) {
                String code = itemCode(rnd.nextInt(ITEM_COUNT));
                int amt = 1 + rnd.nextInt(60);
                double price = Math.round((8 + rnd.nextDouble() * 40) * 100) / 100.0;
                LocalDateTime when = horizon.plusDays(rnd.nextInt(120)).plusMinutes(rnd.nextInt(24 * 60));
                ps.setString(1, code);
                ps.setInt(2, amt);
                ps.setDouble(3, price);
                ps.setString(4, "P-DMO-" + String.format("%04d", n));
                ps.setString(5, "Admin");
                ps.setString(6, formatDisplay(when));
                ps.executeUpdate();
            }
        }
    }

    /** Demo item code formatter ({@code DMO0001…}). */
    private static String itemCode(int index) {
        return ITEM_PREFIX + String.format("%04d", index + 1);
    }

    /** Formats timestamps using {@link #DISPLAY_TS}. */
    private static String formatDisplay(LocalDateTime t) {
        return DISPLAY_TS.format(t);
    }

    /** Converts seeded display timestamps into ISO-compatible {@code yyyy-MM-dd…} tails for indexed columns. */
    private static String toIsoDateTime(String displayDateTime) {
        if (displayDateTime == null || displayDateTime.length() < 19) {
            return null;
        }
        return displayDateTime.substring(6, 10) + "-"
                + displayDateTime.substring(3, 5) + "-"
                + displayDateTime.substring(0, 2)
                + displayDateTime.substring(10);
    }

    /** Prints row counts after seed completes (diagnostic CLI output). */
    private static void printCounts() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement st = connection.createStatement()) {
            printCount(st, "Inventory");
            printCount(st, "inventory_cost_layers");
            printCount(st, "sales");
            printCount(st, "movements");
            printCount(st, "pendingOrders");
        }
    }

    /** Executes {@code SELECT COUNT(*) FROM table} helper for {@link #printCounts()}. */
    private static void printCount(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) {
                System.out.println(table + ": " + rs.getLong(1));
            }
        }
    }
}
