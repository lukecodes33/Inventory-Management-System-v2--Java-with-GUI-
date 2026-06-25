import java.awt.BorderLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

public final class SuppliersPanel {
    private SuppliersPanel() {}

        public static JPanel build(User user, Connection connection) throws SQLException {
        WorkspaceShell.ensureAdmin(user, "Suppliers");
        JPanel panel = WorkspaceShell.buildFormPanel("Suppliers");
        JPanel body = new JPanel(new BorderLayout(0, 10));
        AppUI.applyPanelBackground(body);
        body.add(WorkspaceShell.buildSectionText(
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
        WorkspaceShell.installTableCopyMenu(table);
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
                            WorkspaceShell.formatUsdMoney(rs.getDouble("po_exposure")),
                            rs.getInt("inv_links"),
                            days == null ? "Never" : Integer.toString(Math.max(0, days))
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not load suppliers: " + ex.getMessage(),
                        "Suppliers", JOptionPane.ERROR_MESSAGE);
            }
            WorkspaceShell.deferPackTableColumns(table);
        };
        reload.run();

        JButton refresh = new JButton("Refresh");
        WorkspaceShell.styleSecondaryButton(refresh);
        refresh.addActionListener(e -> reload.run());
        panel.add(body, BorderLayout.CENTER);
        panel.add(WorkspaceShell.buildActionBar(refresh, null), BorderLayout.SOUTH);
        return panel;
    }
}
