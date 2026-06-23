import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FIFO cost-layer consumption for sales and valuation helpers used by the workspace.
 */
public final class InventoryFifo {

    /** Prevents instantiation; namespace for static FIFO and reporting helpers. */
    private InventoryFifo() {
    }

    /**
     * Consumes FIFO cost layers for sold quantity and returns total cost.
     *
     * @param connection        open JDBC connection (transaction may be managed by caller)
     * @param itemCode          inventory item code
     * @param quantityToConsume units to consume from oldest layers first
     * @return total FIFO cost, or -1 when layers do not cover the requested quantity
     */
    public static double consumeFifoCost(Connection connection, String itemCode, int quantityToConsume) throws SQLException {
        try (PreparedStatement availableStatement = connection.prepareStatement(
                "SELECT COALESCE(SUM(qty_remaining), 0) AS available_qty FROM inventory_cost_layers WHERE item_code = ? AND qty_remaining > 0"
        )) {
            availableStatement.setString(1, itemCode);
            try (ResultSet rs = availableStatement.executeQuery()) {
                int availableQty = rs.next() ? rs.getInt("available_qty") : 0;
                if (availableQty < quantityToConsume) {
                    return -1;
                }
            }
        }

        String selectLayers = "SELECT id, unit_cost, qty_remaining FROM inventory_cost_layers WHERE item_code = ? AND qty_remaining > 0 ORDER BY created_at ASC, id ASC";
        int remaining = quantityToConsume;
        double totalCost = 0;
        int consumed = 0;

        try (
                PreparedStatement layerStatement = connection.prepareStatement(selectLayers);
                PreparedStatement updateLayer = connection.prepareStatement(
                        "UPDATE inventory_cost_layers SET qty_remaining = qty_remaining - ? WHERE id = ?")
        ) {
            layerStatement.setString(1, itemCode);
            try (ResultSet rs = layerStatement.executeQuery()) {
                while (rs.next() && remaining > 0) {
                    int layerId = rs.getInt("id");
                    int layerQtyRemaining = rs.getInt("qty_remaining");
                    double unitCost = rs.getDouble("unit_cost");
                    int take = Math.min(layerQtyRemaining, remaining);

                    updateLayer.setInt(1, take);
                    updateLayer.setInt(2, layerId);
                    updateLayer.executeUpdate();

                    consumed += take;
                    remaining -= take;
                    totalCost += (unitCost * take);
                }
            }
        }

        if (consumed == 0) {
            return -1;
        }
        return totalCost;
    }

    /**
     * Most recently recorded receipt unit cost for an item (any cost layer, newest first).
     *
     * @param connection open JDBC session
     * @param itemCode   inventory SKU key
     * @return unit cost, or {@link Double#NaN} when no layers exist for the item
     * @throws SQLException when the query fails
     */
    public static double latestRecordedUnitCost(Connection connection, String itemCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT unit_cost FROM inventory_cost_layers WHERE item_code = ? ORDER BY created_at DESC, id DESC LIMIT 1"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Double.NaN;
                }
                return rs.getDouble("unit_cost");
            }
        }
    }

    /**
     * FIFO cost for a sale quantity; when layers cannot supply the quantity, falls back to
     * {@code latestRecordedUnitCost × quantity} without mutating layers. Returns {@code 0} when no layer baseline exists.
     *
     * @param connection open JDBC session
     * @param itemCode     SKU identifier
     * @param quantity     units needing a cost allocation
     * @return summed FIFO cost or best-effort estimate
     * @throws SQLException when cost-layer queries fail
     */
    public static double fifoCostWithLatestLayerFallback(Connection connection, String itemCode, int quantity)
            throws SQLException {
        double fifo = consumeFifoCost(connection, itemCode, quantity);
        if (fifo >= 0) {
            return fifo;
        }
        double unit = latestRecordedUnitCost(connection, itemCode);
        if (!Double.isNaN(unit)) {
            return unit * quantity;
        }
        return 0;
    }

    /**
     * On-hand valuation: current {@code Stock} × {@code Inventory.Market Price} per SKU.
     * Items with NULL market price contribute {@code 0}; compare to FIFO carrying value via
     * {@link #totalFifoStockValue(Connection)}.
     *
     * @param connection open JDBC session
     * @return summed {@code Stock × Market Price} across inventory using SQL coalesce semantics
     * @throws SQLException when the aggregate query fails
     */
    public static double totalMarketValueOfOnHandStock(Connection connection) throws SQLException {
        String sql =
                "SELECT COALESCE(SUM(CAST(`Stock` AS REAL) * COALESCE(`Market Price`, 0)), 0) AS v FROM inventory";
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getDouble("v") : 0;
        }
    }

    /**
     * Sum of unit_cost × qty_remaining for all open FIFO layers (on-hand inventory value).
     *
     * @param connection open JDBC connection
     * @return summed layer value where {@code qty_remaining &gt; 0}
     * @throws SQLException when the aggregate query fails
     */
    public static double totalFifoStockValue(Connection connection) throws SQLException {
        String sql = "SELECT COALESCE(SUM(unit_cost * qty_remaining), 0) AS v FROM inventory_cost_layers WHERE qty_remaining > 0";
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getDouble("v") : 0;
        }
    }

    /**
     * Lifetime realized P/L: sum of (sale revenue − FIFO cost) across all sales rows.
     *
     * @param connection open JDBC connection
     * @return aggregate {@code SUM(Total Price - Total Cost)}
     * @throws SQLException when the aggregate query fails
     */
    public static double lifetimeProfitLoss(Connection connection) throws SQLException {
        String sql = "SELECT COALESCE(SUM(`Total Price` - COALESCE(`Total Cost`, 0)), 0) AS p FROM sales";
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getDouble("p") : 0;
        }
    }

    /**
     * Sum of all recorded sale revenue (for gross margin denominator).
     *
     * @param connection open JDBC connection
     * @return summed {@code Total Price}; zero when empty
     * @throws SQLException when the aggregate query fails
     */
    public static double lifetimeTotalRevenue(Connection connection) throws SQLException {
        String sql = "SELECT COALESCE(SUM(`Total Price`), 0) AS r FROM sales";
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getDouble("r") : 0;
        }
    }

    /**
     * P/L for sales whose {@code DateISO} falls on an inclusive calendar-day range
     * (start 00:00:00 through end 23:59:59). Rows with null {@code DateISO} are excluded.
     *
     * @param connection open JDBC connection
     * @param start      first calendar day (inclusive)
     * @param end        last calendar day (inclusive)
     * @return period P/L for rows with qualifying {@code DateISO}
     * @throws SQLException when the bounded query fails
     */
    public static double profitLossBetweenDates(Connection connection, LocalDate start, LocalDate end) throws SQLException {
        String from = start.toString() + " 00:00:00";
        String to = end.toString() + " 23:59:59";
        String sql = "SELECT COALESCE(SUM(`Total Price` - COALESCE(`Total Cost`, 0)), 0) AS p FROM sales "
                + "WHERE `DateISO` IS NOT NULL AND `DateISO` BETWEEN ? AND ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("p") : 0;
            }
        }
    }

    /**
     * Open purchase-order line exposure: sum of (quantity × unit purchase price).
     *
     * @param connection open JDBC connection
     * @return total open PO monetary exposure
     * @throws SQLException when the aggregate query fails
     */
    public static double openPurchaseOrderExposure(Connection connection) throws SQLException {
        String sql = "SELECT COALESCE(SUM(CAST(`Amount` AS REAL) * `Purchase Price`), 0) AS v FROM pendingOrders";
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getDouble("v") : 0;
        }
    }

    /**
     * Top {@code limit} item codes by sold units in the inclusive date range (by {@code DateISO}).
     * {@code limit} is clamped to 1–50.
     *
     * @param connection open JDBC connection
     * @param start      range start (inclusive calendar day)
     * @param end        range end (inclusive calendar day)
     * @param limit      maximum rows (clamped 1–50)
     * @return ordered list ranked by summed units descending
     * @throws SQLException when the bounded query fails
     */
    public static List<TopMoverRow> topMoversByUnitsBetweenDates(
            Connection connection,
            LocalDate start,
            LocalDate end,
            int limit
    ) throws SQLException {
        if (limit < 1) {
            return Collections.emptyList();
        }
        int cap = Math.min(50, limit);
        String from = start.toString() + " 00:00:00";
        String to = end.toString() + " 23:59:59";
        String sql = "SELECT `Item Code`, SUM(`Amount`) AS units FROM sales "
                + "WHERE `DateISO` IS NOT NULL AND `DateISO` BETWEEN ? AND ? "
                + "GROUP BY `Item Code` ORDER BY units DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.setInt(3, cap);
            try (ResultSet rs = ps.executeQuery()) {
                List<TopMoverRow> out = new ArrayList<>();
                while (rs.next()) {
                    String code = rs.getString("Item Code");
                    int units = rs.getInt("units");
                    if (units > 0) {
                        out.add(new TopMoverRow(code, units));
                    }
                }
                return out;
            }
        }
    }

    /**
     * First calendar day of the month containing {@code date}.
     *
     * @param date anchor day in the target month
     * @return first day-of-month boundary
     */
    public static LocalDate firstDayOfMonth(LocalDate date) {
        return date.with(TemporalAdjusters.firstDayOfMonth());
    }

    /**
     * Last calendar day of the month containing {@code date}.
     *
     * @param date anchor day in the target month
     * @return last day-of-month boundary
     */
    public static LocalDate lastDayOfMonth(LocalDate date) {
        return date.with(TemporalAdjusters.lastDayOfMonth());
    }

    /**
     * Monday of the ISO week containing {@code date}.
     *
     * @param date any day within the ISO week
     * @return that week's Monday (local timeline)
     */
    public static LocalDate startOfIsoWeek(LocalDate date) {
        return date.with(java.time.DayOfWeek.MONDAY);
    }

    /**
     * Sunday of the ISO week containing {@code date} ({@link #startOfIsoWeek(LocalDate)} + 6 days).
     *
     * @param date any day within the ISO week
     * @return that week's Sunday
     */
    public static LocalDate endOfIsoWeek(LocalDate date) {
        return startOfIsoWeek(date).plusDays(6);
    }

    /** One SKU line in a top-movers ranking (by units sold in a date range). */
    public static final class TopMoverRow {
        public final String itemCode;
        public final int units;

        /**
         * @param itemCode inventory item code
         * @param units    total units sold in the ranking window
         */
        public TopMoverRow(String itemCode, int units) {
            this.itemCode = itemCode;
            this.units = units;
        }
    }

    /**
     * On-hand SKUs ranked by unrealized gain percent: {@code (market − weighted FIFO avg unit cost) / avg unit cost × 100}.
     * Excludes null market price, zero stock, or non-positive average layer cost.
     *
     * @param connection open JDBC session
     * @param limit      max rows (clamped 1–50)
     * @return descending by gain percent
     * @throws SQLException when the query fails
     */
    public static List<UnrealizedGainRow> topUnrealizedGainPercentOnHand(Connection connection, int limit)
            throws SQLException {
        if (limit < 1) {
            return Collections.emptyList();
        }
        int cap = Math.min(50, limit);
        String sql = """
                SELECT icode,
                       ((mp - avgcost) / avgcost) * 100.0 AS gpct
                FROM (
                    SELECT i.`Item Code` AS icode,
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
                ) x
                ORDER BY gpct DESC
                LIMIT ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cap);
            try (ResultSet rs = ps.executeQuery()) {
                List<UnrealizedGainRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new UnrealizedGainRow(rs.getString("icode"), rs.getDouble("gpct")));
                }
                return out;
            }
        }
    }

    /** One SKU line ranked by unrealized gain vs weighted-average FIFO unit cost and market price. */
    public static final class UnrealizedGainRow {
        public final String itemCode;
        /** Percent gain of market over average unit cost (may be negative). */
        public final double gainPercent;

        public UnrealizedGainRow(String itemCode, double gainPercent) {
            this.itemCode = itemCode;
            this.gainPercent = gainPercent;
        }
    }
}
