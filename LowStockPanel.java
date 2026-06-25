import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.JPanel;

public final class LowStockPanel {
    private LowStockPanel() {}

    /**
     * Builds low-stock and replenishment recommendation view.
     *
     * @param connection active database connection
     * @return replenishment panel
     * @throws SQLException when query fails
     */
    public static JPanel build(Connection connection) throws SQLException {
        return WorkspaceShell.buildFilterableTablePanel(
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
}
