import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;


/** Extracted from WorkspaceShell. */
public final class ReportsPanel {
    private ReportsPanel() {}

        /**
     * Builds admin-only reporting panel with table preview and CSV export.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @return report panel
     */
    public static JPanel build(User user, Connection connection) {
        WorkspaceShell.ensureAdmin(user, "Generate Reports");
        JPanel reportsPanel = new JPanel(new BorderLayout(12, 12));
        reportsPanel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        AppUI.applyPanelBackground(reportsPanel);

        JPanel filterForm = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(filterForm);
        GridBagConstraints gb = new GridBagConstraints();
        gb.insets = WorkspaceShell.FORM_GRID_INSETS;

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
        WorkspaceShell.styleInput(search, fromDate, toDate, inactiveDays);
        WorkspaceShell.styleComboMatchInputRow(reportType);

        fromDate.setText(LocalDate.now().minusDays(30).toString());
        toDate.setText(LocalDate.now().toString());

        int r = 0;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        filterForm.add(WorkspaceShell.buildFormLabel("Report type"), gb);
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
        filterForm.add(WorkspaceShell.buildFormLabel("Search (optional)"), gb);
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
        filterForm.add(WorkspaceShell.buildFormLabel("From date (yyyy-MM-dd)"), gb);
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
        filterForm.add(WorkspaceShell.buildFormLabel("To date (yyyy-MM-dd)"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        filterForm.add(toDate, gb);

        JLabel inactiveLabel = WorkspaceShell.buildFormLabel("Inactive days (dead stock)");
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
        headingFilter.add(WorkspaceShell.buildSectionTitle("Generate Reports"), BorderLayout.NORTH);
        headingFilter.add(filterForm, BorderLayout.CENTER);
        DefaultTableModel salesTableModel = new DefaultTableModel(new String[]{
                "Item Code", "Item Name", "Amount", "Total Price", "Total Cost", "Gross Profit", "Reference", "User", "Date", "Note"
        }, 0);
        JTable salesTable = new JTable(salesTableModel);
        WorkspaceShell.installTableCopyMenu(salesTable);
        JScrollPane salesScroll = new JScrollPane(salesTable);
        salesScroll.setBorder(AppUI.newRoundedBorder(8));

        DefaultTableModel usersTableModel = new DefaultTableModel(new String[]{"Username", "Event Type", "Details", "Date"}, 0);
        JTable usersTable = new JTable(usersTableModel);
        WorkspaceShell.installTableCopyMenu(usersTable);
        JScrollPane usersScroll = new JScrollPane(usersTable);
        usersScroll.setBorder(AppUI.newRoundedBorder(8));

        DefaultTableModel analyticsTableModel = new DefaultTableModel(new String[]{
                "Item Code", "Item Name", "Stock", "Detail A", "Detail B", "Detail C"
        }, 0);
        JTable analyticsTable = new JTable(analyticsTableModel);
        WorkspaceShell.installTableCopyMenu(analyticsTable);
        JScrollPane analyticsScroll = new JScrollPane(analyticsTable);
        analyticsScroll.setBorder(AppUI.newRoundedBorder(8));

        JPanel contentCards = new JPanel(new CardLayout());
        AppUI.applyPanelBackground(contentCards);
        contentCards.add(salesScroll, "Sales");
        contentCards.add(usersScroll, "Users");
        contentCards.add(analyticsScroll, "Analytics");
        contentCards.setPreferredSize(new Dimension(900, 360));

        final ReportData[] latestReport = new ReportData[1];
        final String[] latestReportType = new String[]{"Sales"};

        JButton generate = new JButton("Generate Report");
        AppUI.stylePrimaryButton(generate);
        generate.setPreferredSize(new Dimension(190, 36));
        JButton export = new JButton("Export Report");
        WorkspaceShell.styleSecondaryButton(export);
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
                        WorkspaceShell.deferPackTableColumns(analyticsTable);
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
                        WorkspaceShell.deferPackTableColumns(analyticsTable);
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
                    WorkspaceShell.deferPackTableColumns(usersTable);
                } else if ("Movements".equals(selected)) {
                    ReportData movementData = buildMovementsReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(usersTableModel, movementData);
                    cardLayout.show(contentCards, "Users");
                    latestReport[0] = movementData;
                    latestReportType[0] = "movements";
                    WorkspaceShell.deferPackTableColumns(usersTable);
                } else if ("Sell-through".equals(selected)) {
                    ReportData data = buildSellThroughReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(analyticsTableModel, data);
                    cardLayout.show(contentCards, "Analytics");
                    latestReport[0] = data;
                    latestReportType[0] = "sell_through";
                    WorkspaceShell.deferPackTableColumns(analyticsTable);
                } else if ("P/L by item".equals(selected)) {
                    ReportData data = buildProfitLossByItemLifetimeReportData(connection, search.getText().trim());
                    populateReportTable(analyticsTableModel, data);
                    cardLayout.show(contentCards, "Analytics");
                    latestReport[0] = data;
                    latestReportType[0] = "pl_by_item";
                    WorkspaceShell.deferPackTableColumns(analyticsTable);
                } else if ("Bin utilization".equals(selected)) {
                    ReportData data = buildBinUtilizationReportData(connection, search.getText().trim());
                    populateReportTable(analyticsTableModel, data);
                    cardLayout.show(contentCards, "Analytics");
                    latestReport[0] = data;
                    latestReportType[0] = "bin_utilization";
                    WorkspaceShell.deferPackTableColumns(analyticsTable);
                } else {
                    ReportData salesData = buildSalesReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(salesTableModel, salesData);
                    cardLayout.show(contentCards, "Sales");
                    latestReport[0] = salesData;
                    latestReportType[0] = "sales";
                    WorkspaceShell.deferPackTableColumns(salesTable);
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

                String base = WorkspaceShell.sanitizeFileName(System.currentTimeMillis() + "_" + user.getUsername() + "_" + latestReportType[0] + "_from_" + from + "_to_" + to);
                Path csv = dir.resolve(base + ".csv");
                writeReportCsv(csv, latestReport[0]);

                JOptionPane.showMessageDialog(reportsPanel, "Export complete:\n" + csv.toString());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(reportsPanel, "Export failed: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        reportsPanel.add(headingFilter, BorderLayout.NORTH);
        reportsPanel.add(contentCards, BorderLayout.CENTER);
        reportsPanel.add(WorkspaceShell.buildActionBar(export, generate), BorderLayout.SOUTH);
        return reportsPanel;
    }

        static ReportData buildDeadStockReportData(Connection connection, String search, int inactiveDays)
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

        static ReportData buildItemMarginsReportData(Connection connection, String search) throws SQLException {
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
                            WorkspaceShell.formatUsdMoney(avg),
                            WorkspaceShell.formatUsdMoney(mp),
                            String.format(Locale.US, "%.1f%%", marginPct),
                            WorkspaceShell.formatUsdMoney(mp - avg)
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

        static ReportData buildSellThroughReportData(
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

        static ReportData buildProfitLossByItemLifetimeReportData(Connection connection, String search)
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
                            WorkspaceShell.formatUsdMoney(revenue),
                            WorkspaceShell.formatUsdMoney(cost),
                            WorkspaceShell.formatUsdMoney(profit)
                    });
                }
            }
        }
        data.columns = new String[]{"Item Code", "Item Name", "Units Sold", "Revenue", "Cost", "P/L"};
        data.summary.put("Total P/L", WorkspaceShell.formatUsdMoney(totalProfit));
        data.summary.put("Scope", "Lifetime sales grouped by item");
        return data;
    }

        static ReportData buildBinUtilizationReportData(Connection connection, String search) throws SQLException {
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
    static ReportData buildSalesReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
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
        data.summary.put("Total Revenue", WorkspaceShell.formatUsdMoney(totalRevenue));
        data.summary.put("Total Cost", WorkspaceShell.formatUsdMoney(totalCost));
        data.summary.put("Gross Profit", WorkspaceShell.formatUsdMoney(totalRevenue - totalCost));
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
    static ReportData buildUsersReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
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
    static ReportData buildMovementsReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
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

        static void populateReportTable(DefaultTableModel model, ReportData data) {
        model.setColumnCount(0);
        for (String col : data.columns) {
            model.addColumn(col);
        }
        model.setRowCount(0);
        for (Object[] row : data.rows) {
            model.addRow(row);
        }
    }

        /**
     * Writes report table data to CSV with quoted cells.
     *
     * @param path output CSV path
     * @param data report data to export
     * @throws IOException when writing fails
     */
    static void writeReportCsv(Path path, ReportData data) throws IOException {
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

        /** Mutable accumulator for CSV export rows and summary metadata. */
    static final class ReportData {
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
