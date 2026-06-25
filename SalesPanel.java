import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableModel;

public final class SalesPanel {
    private SalesPanel() {}

    /** Model column indices for the sales transaction table (see {@link #buildSalesPanel}). */
    static final int SALES_VIEW_COL_ITEM_CODE = 0;

        static final int SALES_VIEW_COL_AMOUNT = 3;

        static final int SALES_VIEW_COL_REFERENCE = 5;

    /** Internal SQLite row id — present in the model but removed from the visible table. */
    static final int SALES_MODEL_COL_ROWID = 9;

    /**
     * Sales history with Excel-style column filters and return processing for the selected line
     * (same database rules as the former Return Item screen: restock when condition is {@code New}, otherwise reversal only).
     */
    public static JPanel build(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
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
        WorkspaceShell.styleInput(returnQty);
        WorkspaceShell.styleComboMatchInputRow(returnCondition, damagedReason);
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
        JPanel intro = WorkspaceShell.buildSectionPanel();
        selectionSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        intro.add(selectionSummary);
        returnSouth.add(intro, BorderLayout.NORTH);
        returnSouth.add(returnForm, BorderLayout.CENTER);
        JPanel returnFooter = WorkspaceShell.buildActionBar(null, processReturnBtn);
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
                String locSummary = ("—".equals(loc) || loc.isBlank()) ? "no bin on record" : WorkspaceShell.abbreviateForTableCell(loc, 28);
                selectionSummary.setText(String.format(Locale.US,
                        "Selected: Reference %s · %s (%s) · %s — line quantity remaining: %s",
                        ref, code, WorkspaceShell.abbreviateForTableCell(nm, 40), locSummary, amtStr));
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
                    WorkspaceShell.showView(workspaceContainer, "View Sales Transaction", build(user, connection, workspaceContainer));
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(processReturnBtn,
                            "Return saved but the view could not refresh: " + ex.getMessage());
                }
                WorkspaceShell.requestMetricsRefresh();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(processReturnBtn, "Enter a valid return quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(processReturnBtn, "Database error: " + ex.getMessage());
            }
        });

        return WorkspaceShell.buildFilterableTablePanel(
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
    static void processSaleReturn(
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
                WorkspaceShell.incrementInventoryStorageQty(connection, itemCode, restockBin, returnQty);
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
}
