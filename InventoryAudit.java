import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Inventory change audit log (separate from write-offs and sales). */
public final class InventoryAudit {

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

    public static void touchMarketPriceUpdated(Connection connection, String itemCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE inventory SET market_price_updated_at = ? WHERE `Item Code` = ?")) {
            ps.setString(1, dateTime.nowDisplayString());
            ps.setString(2, itemCode);
            ps.executeUpdate();
        }
    }
}
