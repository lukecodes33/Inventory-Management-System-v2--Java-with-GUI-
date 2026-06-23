import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Inventory change audit log and stock adjustments (separate from write-offs and sales).
 */
public final class InventoryAudit {

    public static final String CHANGE_STOCK_ADJUST = "STOCK_ADJUST";
    public static final String CHANGE_MARKET_PRICE = "MARKET_PRICE";
    public static final String CHANGE_NOTE = "NOTE";

    private InventoryAudit() {
    }

    /** Creates {@code inventory_change_log} when missing (via schema evolution). */
    public static void ensureChangeLogTable(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_change_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        item_code TEXT NOT NULL,
                        username TEXT,
                        change_type TEXT NOT NULL,
                        quantity_delta INTEGER NOT NULL DEFAULT 0,
                        reason TEXT,
                        details TEXT,
                        created_at TEXT NOT NULL
                    )
                    """);
            st.execute(
                    "CREATE INDEX IF NOT EXISTS idx_inventory_change_log_item ON inventory_change_log(item_code, created_at)");
        }
    }

    public static void logChange(
            Connection connection,
            String username,
            String itemCode,
            String changeType,
            int quantityDelta,
            String reason,
            String details
    ) throws SQLException {
        ensureChangeLogTable(connection);
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO inventory_change_log
                    (item_code, username, change_type, quantity_delta, reason, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, itemCode);
            ps.setString(2, username);
            ps.setString(3, changeType);
            ps.setInt(4, quantityDelta);
            ps.setString(5, reason);
            ps.setString(6, details);
            ps.setString(7, dateTime.nowDisplayString());
            ps.executeUpdate();
        }
    }

    /** Rows for change-history table UI (newest first). */
    public static List<ChangeLogRow> loadRecentChanges(Connection connection, int limit) throws SQLException {
        ensureChangeLogTable(connection);
        int cap = Math.max(1, Math.min(limit, 500));
        List<ChangeLogRow> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT item_code, username, change_type, quantity_delta, reason, details, created_at
                FROM inventory_change_log
                ORDER BY id DESC
                LIMIT ?
                """)) {
            ps.setInt(1, cap);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ChangeLogRow(
                            rs.getString("item_code"),
                            rs.getString("username"),
                            rs.getString("change_type"),
                            rs.getInt("quantity_delta"),
                            rs.getString("reason"),
                            rs.getString("details"),
                            rs.getString("created_at")));
                }
            }
        }
        return rows;
    }

    public record ChangeLogRow(
            String itemCode,
            String username,
            String changeType,
            int quantityDelta,
            String reason,
            String details,
            String createdAt
    ) {
    }

    /**
     * Adjusts stock by {@code delta} (positive adds a FIFO layer at {@code unitCost}; negative consumes FIFO).
     */
    public static void applyStockAdjustment(
            Connection connection,
            String username,
            String itemCode,
            int delta,
            double unitCost,
            String reason,
            String note
    ) throws SQLException {
        if (itemCode == null || itemCode.isBlank()) {
            throw new SQLException("Item code is required.");
        }
        if (delta == 0) {
            throw new SQLException("Adjustment quantity cannot be zero.");
        }
        String code = itemCode.trim();
        String now = dateTime.nowDisplayString();
        boolean savedAc = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (delta > 0) {
                if (unitCost < 0) {
                    throw new SQLException("Unit cost must be zero or greater for stock increases.");
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE inventory SET Stock = Stock + ? WHERE `Item Code` = ?")) {
                    ps.setInt(1, delta);
                    ps.setString(2, code);
                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Item code not found.");
                    }
                }
                try (PreparedStatement layer = connection.prepareStatement(
                        """
                        INSERT INTO inventory_cost_layers
                            (item_code, reference, unit_cost, qty_received, qty_remaining, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    layer.setString(1, code);
                    layer.setString(2, "ADJUST");
                    layer.setDouble(3, unitCost);
                    layer.setInt(4, delta);
                    layer.setInt(5, delta);
                    layer.setString(6, now);
                    layer.executeUpdate();
                }
            } else {
                int remove = -delta;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE inventory SET Stock = Stock - ? WHERE `Item Code` = ? AND Stock >= ?")) {
                    ps.setInt(1, remove);
                    ps.setString(2, code);
                    ps.setInt(3, remove);
                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Insufficient stock for adjustment.");
                    }
                }
                InventoryFifo.consumeFifoCost(connection, code, remove);
            }
            try (PreparedStatement movement = connection.prepareStatement(
                    "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                movement.setString(1, code);
                movement.setString(2, String.valueOf(Math.abs(delta)));
                movement.setString(3, delta > 0 ? "ADJUST IN" : "ADJUST OUT");
                movement.setString(4, reason == null ? "" : reason);
                movement.setString(5, username);
                movement.setString(6, now);
                movement.executeUpdate();
            }
            String detail = note == null || note.isBlank() ? null : note.trim();
            logChange(connection, username, code, CHANGE_STOCK_ADJUST, delta, reason, detail);
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(savedAc);
        }
    }

    public static void touchMarketPriceUpdated(Connection connection, String itemCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE inventory SET market_price_updated_at = ? WHERE `Item Code` = ?")) {
            ps.setString(1, dateTime.nowDisplayString());
            ps.setString(2, itemCode);
            ps.executeUpdate();
        }
    }
}
