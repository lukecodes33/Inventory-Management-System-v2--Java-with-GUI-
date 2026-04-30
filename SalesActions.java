import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Legacy modal sales flows: multi-step {@link #generateSale}, filtered sales grid, and return-by-reference UI.
 * Prefer {@link WorkspaceShell} process-sale for new sessions; this class remains for sidebar menu parity.
 */
public class SalesActions {

    private static final int MAX_SALE_TRANSACTION_NOTE_LENGTH = 2000;

    /**
     * Trims optional checkout notes and caps length for {@code sales.Note} storage.
     *
     * @param raw user-entered note text, may be null
     * @return trimmed non-empty string or {@code null} when absent
     */
    private static String normalizedSaleTransactionNoteForDb(String raw) {
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

    /** One SKU line in the legacy sale dialog (quantity and unit sell price). */
    private static final class SaleDraftLine {
        final int quantity;
        final double unitSalePrice;

        SaleDraftLine(int quantity, double unitSalePrice) {
            this.quantity = quantity;
            this.unitSalePrice = unitSalePrice;
        }
    }

    /**
     * Converts {@code dd-MM-yyyy HH:mm:ss} display timestamps to ISO-like {@code yyyy-MM-dd HH:mm:ss} for {@code DateISO}.
     *
     * @param displayDateTime value from {@link dateTime#formattedDateTime()}
     * @return ISO-style prefix or {@code null} when input is too short
     */
    private static String toIsoDateTime(String displayDateTime) {
        if (displayDateTime == null || displayDateTime.length() < 19) {
            return null;
        }
        return displayDateTime.substring(6, 10) + "-"
                + displayDateTime.substring(3, 5) + "-"
                + displayDateTime.substring(0, 2)
                + displayDateTime.substring(10);
    }

    /**
     * Legacy JOptionPane-driven sale: prompts for lines, writes {@code sales} with FIFO cost via
     * {@link InventoryFifo#fifoCostWithLatestLayerFallback}, adjusts stock, logs movements.
     */
    public void generateSale(User user, Connection connection) {
        dateTime formattedDateTimeInstance = new dateTime();
        String formattedDateTime = formattedDateTimeInstance.formattedDateTime();

        LinkedHashMap<String, SaleDraftLine> saleItemMap = new LinkedHashMap<>();

        while (true) {
            boolean inStock = true;
            int availableStock = 0;
            boolean itemExists = false;

            String itemCode = JOptionPane.showInputDialog("Enter the item code:");
            if (itemCode == null) {
                break;
            }

            String query = "SELECT `Stock` FROM inventory WHERE `Item Code` = ?";
            try (PreparedStatement checkForDuplicate = connection.prepareStatement(query)) {
                checkForDuplicate.setString(1, itemCode);
                ResultSet rs = checkForDuplicate.executeQuery();

                if (rs.next()) {
                    itemExists = true;
                    availableStock = rs.getInt("Stock");
                    if (availableStock <= 0) {
                        JOptionPane.showMessageDialog(null, "This item is currently out of stock.", "Out of Stock", JOptionPane.WARNING_MESSAGE);
                        inStock = false;
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }

            if (!itemExists) {
                JOptionPane.showMessageDialog(null, "That item code was not found.", "Item Not Found", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            if (inStock) {
                String quantityStr = JOptionPane.showInputDialog("Enter the quantity:");
                if (quantityStr == null) {
                    break;
                }

                int quantity;
                try {
                    quantity = Integer.parseInt(quantityStr);
                    if (quantity <= 0) {
                        JOptionPane.showMessageDialog(null, "Quantity must be greater than zero.");
                        continue;
                    }

                    if (quantity > availableStock) {
                        JOptionPane.showMessageDialog(null, "There is not enough stock available.");
                        continue;
                    }

                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, "Enter a valid quantity.");
                    continue;
                }

                String priceStr = JOptionPane.showInputDialog("Enter unit sale price for " + itemCode.trim() + ":");
                if (priceStr == null) {
                    return;
                }
                double unitPrice;
                try {
                    unitPrice = Double.parseDouble(priceStr.trim());
                    if (unitPrice < 0) {
                        JOptionPane.showMessageDialog(null, "Unit sale price cannot be negative.");
                        continue;
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(null, "Enter a valid unit sale price.");
                    continue;
                }

                saleItemMap.put(itemCode.trim(), new SaleDraftLine(quantity, unitPrice));

                int response = JOptionPane.showConfirmDialog(null, "Would you like to add another item?", "Add Another Item", JOptionPane.YES_NO_OPTION);
                if (response == JOptionPane.NO_OPTION) {
                    break;
                }
            }
        }

        // User cancelled/closed flow before adding any items.
        if (saleItemMap.isEmpty()) {
            return;
        }

        String rawOptionalNote = JOptionPane.showInputDialog(null, "Optional note for this entire sale (leave blank for none):");
        if (rawOptionalNote != null && rawOptionalNote.length() > MAX_SALE_TRANSACTION_NOTE_LENGTH) {
            JOptionPane.showMessageDialog(null,
                    "Transaction note must be at most " + MAX_SALE_TRANSACTION_NOTE_LENGTH + " characters.",
                    "Input Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String saleNoteForDb = normalizedSaleTransactionNoteForDb(rawOptionalNote);

        String referenceNumber = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String dateIso = toIsoDateTime(formattedDateTime);

        boolean originalAutoCommit = true;
        boolean saleCompleted = false;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            for (Map.Entry<String, SaleDraftLine> entry : saleItemMap.entrySet()) {
                String itemCode = entry.getKey();
                SaleDraftLine line = entry.getValue();
                int amount = line.quantity;
                double unitSale = line.unitSalePrice;

                String itemName;
                try (PreparedStatement fetchItemDetailsStmt = connection.prepareStatement(
                        "SELECT `Item Name` FROM inventory WHERE `Item Code` = ?")) {
                    fetchItemDetailsStmt.setString(1, itemCode);
                    try (ResultSet rs = fetchItemDetailsStmt.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            JOptionPane.showMessageDialog(null, "Item not found in inventory: " + itemCode, "Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        itemName = rs.getString("Item Name");
                    }
                }

                double fifoCost = InventoryFifo.fifoCostWithLatestLayerFallback(connection, itemCode, amount);

                try (PreparedStatement insertSale = connection.prepareStatement(
                        "INSERT INTO sales (`Item Code`, `Item Name`, `Amount`, `Total Price`, `Total Cost`, `Reference`, `User`, `Date`, `DateISO`, `Note`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    insertSale.setString(1, itemCode);
                    insertSale.setString(2, itemName);
                    insertSale.setInt(3, amount);
                    insertSale.setDouble(4, unitSale * amount);
                    insertSale.setDouble(5, fifoCost);
                    insertSale.setString(6, referenceNumber);
                    insertSale.setString(7, user.getUsername());
                    insertSale.setString(8, formattedDateTime);
                    insertSale.setString(9, dateIso);
                    insertSale.setString(10, saleNoteForDb);
                    insertSale.executeUpdate();
                }

                try (PreparedStatement updateInventoryStmt = connection.prepareStatement(
                        "UPDATE inventory SET `Stock` = `Stock` - ? WHERE `Item Code` = ? AND `Stock` >= ?")) {
                    updateInventoryStmt.setInt(1, amount);
                    updateInventoryStmt.setString(2, itemCode);
                    updateInventoryStmt.setInt(3, amount);
                    if (updateInventoryStmt.executeUpdate() == 0) {
                        connection.rollback();
                        JOptionPane.showMessageDialog(null, "Insufficient stock for item: " + itemCode, "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                try (PreparedStatement insertMovementStmt = connection.prepareStatement(
                        "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    insertMovementStmt.setString(1, itemCode);
                    insertMovementStmt.setInt(2, amount);
                    insertMovementStmt.setString(3, "SALE");
                    insertMovementStmt.setString(4, "CUSTOMER_SALE");
                    insertMovementStmt.setString(5, user.getUsername());
                    insertMovementStmt.setString(6, formattedDateTime);
                    insertMovementStmt.executeUpdate();
                }
            }
            connection.commit();
            saleCompleted = true;
        } catch (SQLException ex) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // ignore
            }
            JOptionPane.showMessageDialog(null, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
                // ignore
            }
        }

        if (saleCompleted) {
            JOptionPane.showMessageDialog(null, "Sale completed successfully. Reference: " + referenceNumber);
        }
    }

    /**
     * Opens a modal table of {@code sales} joined to {@code inventory} for names, with per-column regex filters.
     */
    public void viewSalesTransactions(Connection connection) throws SQLException {
        JDialog dialog = new JDialog((JFrame) null, "Sales Transactions", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(900, 460);
        dialog.setLocationRelativeTo(null);

        String[] columnNames = {"Item Code", "Item Name", "Amount", "Total Price", "Reference", "User", "Date", "Note"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        JPanel filterPanel = new JPanel(new GridLayout(1, columnNames.length));
        AppUI.applyPanelBackground(filterPanel);
        JTextField[] filterFields = new JTextField[columnNames.length];

        for (int i = 0; i < columnNames.length; i++) {
            filterFields[i] = new JTextField();
            filterFields[i].setBorder(AppUI.newRoundedBorder(8));
            final int columnIndex = i;
            filterFields[i].addCaretListener(e -> {
                String filterText = filterFields[columnIndex].getText();
                if (filterText.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + filterText, columnIndex));
                }
            });
            filterPanel.add(filterFields[i]);
        }

        String query = "SELECT sales.`Item Code`, " +
                "COALESCE(inventory.`Item Name`, 'N/A') AS `Item Name`, " +
                "sales.`Amount`, " +
                "sales.`Total Price`, " +
                "sales.`Reference`, " +
                "sales.`User`, " +
                "sales.`Date`, " +
                "COALESCE(sales.`Note`, '') AS `Note` " +
                "FROM sales " +
                "LEFT JOIN inventory ON sales.`Item Code` = inventory.`Item Code`";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Object[] rowData = {
                        rs.getString("Item Code"),
                        rs.getString("Item Name"),
                        rs.getInt("Amount"),
                        rs.getDouble("Total Price"),
                        rs.getString("Reference"),
                        rs.getString("User"),
                        rs.getString("Date"),
                        rs.getString("Note")
                };
                tableModel.addRow(rowData);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error loading sales transactions: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            dialog.dispose();
            return;
        }

        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    /**
     * Legacy return flow: given a sale reference, reduces line amounts and restores stock (partial returns supported).
     */
    public void returnOrder(User user, Connection connection) throws SQLException {
        String referenceNumber = JOptionPane.showInputDialog("Enter sales transaction reference:");

        if (referenceNumber == null) {
            return;
        }

        if (referenceNumber.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Transaction reference is required.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        HashMap<String, Integer> resultMap = new HashMap<>();
        String query = "SELECT `Item Code`, Amount FROM sales WHERE `Reference` = ?";

        try (PreparedStatement checkOrder = connection.prepareStatement(query)) {
            checkOrder.setString(1, referenceNumber);

            try (ResultSet rs2 = checkOrder.executeQuery()) {
                while (rs2.next()) {
                    String itemCode = rs2.getString("Item Code");
                    int amount = rs2.getInt("Amount");
                    resultMap.put(itemCode, amount);
                }

                if (resultMap.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No sales records were found for that reference.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                HashMap<String, Integer> receivedMap = new HashMap<>();

                for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                    CountDownLatch latch = new CountDownLatch(1);

                    String codeOrdered = entry.getKey();
                    int amountOrdered = entry.getValue();
                    String formattedString = String.format("%s - Amount on Order: %d           returning:", codeOrdered, amountOrdered);

                    JFrame frame = new JFrame(codeOrdered);
                    frame.setSize(420, 140);
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    frame.setLayout(null);

                    javax.swing.JLabel amountLabel = new javax.swing.JLabel(formattedString);
                    amountLabel.setBounds(10, 10, 280, 25);
                    frame.add(amountLabel);

                    JTextField amountReceived = new JTextField(20);
                    amountReceived.setBounds(290, 10, 100, 25);
                    frame.add(amountReceived);

                    javax.swing.JButton submitButton = new javax.swing.JButton("Submit");
                    submitButton.setBounds(290, 45, 100, 25);
                    AppUI.stylePrimaryButton(submitButton);
                    frame.add(submitButton);
                    AppUI.styleWindow(frame);

                    submitButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String receivedAmountString = amountReceived.getText();

                            try {
                                int returnedAmount = Integer.parseInt(receivedAmountString);

                                if (amountOrdered - returnedAmount < 0) {
                                    JOptionPane.showMessageDialog(frame, "Returned quantity cannot exceed the sold amount.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                receivedMap.put(codeOrdered, returnedAmount);
                                frame.dispose();
                                latch.countDown();

                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(frame, "Enter a valid whole number.", "Input Error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    });

                    frame.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent e) {
                            latch.countDown();
                        }
                    });
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);

                    try {
                        latch.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }

                for (Map.Entry<String, Integer> entry : receivedMap.entrySet()) {

                    String codeReceived = entry.getKey();
                    int amountReceived = entry.getValue();

                    if (amountReceived > 0) {
                        String updateReceivingLog = "UPDATE sales SET Amount = Amount - ? WHERE `Reference` = ? AND `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(updateReceivingLog)) {
                            preparedStatement.setInt(1, amountReceived);
                            preparedStatement.setString(2, referenceNumber);
                            preparedStatement.setString(3, codeReceived);
                            preparedStatement.executeUpdate();
                        }

                        String updateOnDock = "UPDATE Inventory SET `Stock` = `Stock` + ? WHERE `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnDock)) {
                            preparedStatement.setInt(1, amountReceived);
                            preparedStatement.setString(2, codeReceived);
                            preparedStatement.executeUpdate();
                        }

                        String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement movementStatement = connection.prepareStatement(updateMovements)) {
                            movementStatement.setString(1, codeReceived);
                            movementStatement.setString(2, String.valueOf(amountReceived));
                            movementStatement.setString(3, "RETURN");
                            movementStatement.setString(4, user.getUsername());
                            movementStatement.setString(5, new dateTime().formattedDateTime());
                            movementStatement.executeUpdate();
                        }
                    }

                    String deleteCompletedOrder = "DELETE FROM sales WHERE `Reference` = ? AND `Item Code` = ? AND Amount = 0";
                    try (PreparedStatement deleteStatement = connection.prepareStatement(deleteCompletedOrder)) {
                        deleteStatement.setString(1, referenceNumber);
                        deleteStatement.setString(2, codeReceived);
                        deleteStatement.executeUpdate();
                    }
                }

                JOptionPane.showMessageDialog(null, "Return completed successfully.");
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
