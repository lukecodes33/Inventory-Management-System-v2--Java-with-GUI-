import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JLabel;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Legacy modal flows for purchase orders, receiving, put-away, and dock-to-stock moves (JOptionPane / JDialog).
 * The primary workspace implementations live in {@link WorkspaceShell}; this class backs older menu actions.
 */
public class OrderActions {
    /** Runs legacy popup-based purchase order creation flow. */
    public void newPurchaseOrder(User user, Connection connection) {
        dateTime formattedDateTimeInstance = new dateTime();
        String formattedDateTime = formattedDateTimeInstance.formattedDateTime();

        if (!user.hasAdminRights()) {
            JOptionPane.showMessageDialog(null, "You do not have permission to use this feature.");
            return;
        }

        String referenceNumber = JOptionPane.showInputDialog("Enter a purchase order reference (leave blank to auto-generate):");
        if (referenceNumber == null) {
            return;
        }

        if (referenceNumber.trim().isEmpty()) {
            referenceNumber = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            JOptionPane.showMessageDialog(null, "No reference provided. Generated reference: " + referenceNumber);
        }

        HashMap<String, Integer> purchaseOrderItems = new HashMap<>();

        while (true) {
            boolean exists = true;
            String itemCode = JOptionPane.showInputDialog("Enter the item code:");
            if (itemCode == null) {
                break;
            }

            String query = "SELECT COUNT(*) AS count FROM inventory WHERE `Item Code` = ?";
            try (PreparedStatement checkForDuplicate = connection.prepareStatement(query)) {
                checkForDuplicate.setString(1, itemCode);
                ResultSet rs = checkForDuplicate.executeQuery();
                if (rs.next() && rs.getInt("count") == 0) {
                    JOptionPane.showMessageDialog(null, "That item code was not found. Add the item before creating this order.", "Item Not Found", JOptionPane.WARNING_MESSAGE);
                    exists = false;
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }

            if (!exists) {
                continue;
            }

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
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Enter a valid quantity.");
                continue;
            }

            purchaseOrderItems.put(itemCode, quantity);
            int response = JOptionPane.showConfirmDialog(null, "Would you like to add another item?", "Add Another Item", JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.NO_OPTION) {
                break;
            }
        }

        for (String itemCode : purchaseOrderItems.keySet()) {
            int amount = purchaseOrderItems.get(itemCode);

            try {
                String insertPendingOrderQuery = "INSERT INTO pendingOrders (`Item Code`, `Amount`, `Reference`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement insertPendingOrderStmt = connection.prepareStatement(insertPendingOrderQuery)) {
                    insertPendingOrderStmt.setString(1, itemCode);
                    insertPendingOrderStmt.setInt(2, amount);
                    insertPendingOrderStmt.setString(3, referenceNumber);
                    insertPendingOrderStmt.setString(4, user.getUsername());
                    insertPendingOrderStmt.setString(5, formattedDateTime);
                    insertPendingOrderStmt.executeUpdate();
                }

                String updateInventoryQuery = "UPDATE inventory SET `On Order` = `On Order` + ? WHERE `Item Code` = ?";
                try (PreparedStatement updateInventoryStmt = connection.prepareStatement(updateInventoryQuery)) {
                    updateInventoryStmt.setInt(1, amount);
                    updateInventoryStmt.setString(2, itemCode);
                    updateInventoryStmt.executeUpdate();
                }

                String insertMovementQuery = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement insertMovementStmt = connection.prepareStatement(insertMovementQuery)) {
                    insertMovementStmt.setString(1, itemCode);
                    insertMovementStmt.setInt(2, amount);
                    insertMovementStmt.setString(3, "ORDERED");
                    insertMovementStmt.setString(4, user.getUsername());
                    insertMovementStmt.setString(5, formattedDateTime);
                    insertMovementStmt.executeUpdate();
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(null, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        JOptionPane.showMessageDialog(null, "Purchase order created successfully. Reference: " + referenceNumber);
    }

    /** Displays legacy popup pending-order table with per-column filters. */
    public void displayPendingOrders(Connection connection) throws SQLException {
        JDialog dialog = new JDialog((JFrame) null, "Pending Orders", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(760, 460);
        dialog.setLocationRelativeTo(null);

        String[] columnNames = {"Item Code", "Amount", "Reference", "Date"};
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

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT `Item Code`, `Amount`, `Reference`, `Date` FROM pendingOrders")) {
            while (rs.next()) {
                Object[] rowData = {
                        rs.getString("Item Code"),
                        rs.getInt("Amount"),
                        rs.getString("Reference"),
                        rs.getString("Date")
                };
                tableModel.addRow(rowData);
            }
        }

        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    /** Runs legacy popup receive-order workflow for a PO reference. */
    public void receiveOrder(User user, Connection connection) throws SQLException {
        String referenceNumber = JOptionPane.showInputDialog("Enter purchase order reference:");

        if (referenceNumber == null) {
            return;
        }

        if (referenceNumber.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Purchase order reference is required.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        HashMap<String, Integer> resultMap = new HashMap<>();
        String query = "SELECT `Item Code`, Amount FROM pendingOrders WHERE `Reference` = ?";

        try (PreparedStatement checkOrder = connection.prepareStatement(query)) {
            checkOrder.setString(1, referenceNumber);

            try (ResultSet rs = checkOrder.executeQuery()) {
                while (rs.next()) {
                    String itemCode = rs.getString("Item Code");
                    int amount = rs.getInt("Amount");
                    resultMap.put(itemCode, amount);
                }

                if (resultMap.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No orders were found for that reference.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                HashMap<String, Integer> receivedMap = new HashMap<>();

                for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                    CountDownLatch latch = new CountDownLatch(1);

                    String codeOrdered = entry.getKey();
                    int amountOrdered = entry.getValue();
                    String formattedString = String.format("Item: %s | Ordered: %d | Received:", codeOrdered, amountOrdered);

                    JFrame frame = new JFrame(codeOrdered);
                    frame.setSize(420, 140);
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    frame.setLayout(null);

                    JLabel amountLabel = new JLabel(formattedString);
                    amountLabel.setBounds(10, 10, 280, 25);
                    frame.add(amountLabel);

                    JTextField amountReceived = new JTextField(20);
                    amountReceived.setBounds(290, 10, 100, 25);
                    frame.add(amountReceived);

                    JButton submitButton = new JButton("Submit");
                    submitButton.setBounds(290, 45, 100, 25);
                    AppUI.stylePrimaryButton(submitButton);
                    frame.add(submitButton);

                    AppUI.styleWindow(frame);
                    submitButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String receivedAmountString = amountReceived.getText();

                            try {
                                int receivedAmount = Integer.parseInt(receivedAmountString);

                                if (receivedAmount <= 0) {
                                    JOptionPane.showMessageDialog(frame, "Received quantity must be greater than zero.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                if (amountOrdered - receivedAmount < 0) {
                                    JOptionPane.showMessageDialog(frame, "Received quantity cannot exceed the ordered amount.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                receivedMap.put(codeOrdered, receivedAmount);
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

                    String updateReceivingLog = "UPDATE pendingOrders SET Amount = Amount - ? WHERE `Reference` = ? AND `Item Code` = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(updateReceivingLog)) {
                        preparedStatement.setInt(1, amountReceived);
                        preparedStatement.setString(2, referenceNumber);
                        preparedStatement.setString(3, codeReceived);
                        preparedStatement.executeUpdate();
                    }

                    String updateOnOrder = "UPDATE Inventory SET `On Order` = `On Order` - ? WHERE `Item Code` = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnOrder)) {
                        preparedStatement.setInt(1, amountReceived);
                        preparedStatement.setString(2, codeReceived);
                        preparedStatement.executeUpdate();
                    }

                    String updateOnDock = "UPDATE Inventory SET `On Dock` = `On Dock` + ? WHERE `Item Code` = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnDock)) {
                        preparedStatement.setInt(1, amountReceived);
                        preparedStatement.setString(2, codeReceived);
                        preparedStatement.executeUpdate();
                    }

                    String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement movementStatement = connection.prepareStatement(updateMovements)) {
                        movementStatement.setString(1, codeReceived);
                        movementStatement.setString(2, String.valueOf(amountReceived));
                        movementStatement.setString(3, "RECEIVED");
                        movementStatement.setString(4, user.getUsername());
                        movementStatement.setString(5, new dateTime().formattedDateTime());
                        movementStatement.executeUpdate();
                    }

                    String deleteCompletedOrder = "DELETE FROM pendingOrders WHERE `Reference` = ? AND `Item Code` = ? AND Amount = 0";
                    try (PreparedStatement deleteStatement = connection.prepareStatement(deleteCompletedOrder)) {
                        deleteStatement.setString(1, referenceNumber);
                        deleteStatement.setString(2, codeReceived);
                        deleteStatement.executeUpdate();
                    }

                    JOptionPane.showMessageDialog(null, "Items received and inventory updated successfully.");
                }

            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Runs legacy popup put-away workflow from on-dock to stock. */
    public void putAwayStock(User user, Connection connection) throws SQLException {
        String itemCode = JOptionPane.showInputDialog("Enter item code to put away:");

        if (itemCode == null) {
            return;
        }

        if (itemCode.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Item code is required.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        HashMap<String, Integer> resultMap = new HashMap<>();
        String query = "SELECT `On Dock` FROM Inventory WHERE `Item Code` = ?";

        try (PreparedStatement checkOrder = connection.prepareStatement(query)) {
            checkOrder.setString(1, itemCode);

            try (ResultSet rs = checkOrder.executeQuery()) {
                while (rs.next()) {
                    int onDock = rs.getInt("On Dock");
                    resultMap.put(itemCode, onDock);
                }

                if (resultMap.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No dock stock was found for that item code.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                HashMap<String, Integer> putAwayMap = new HashMap<>();

                for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                    CountDownLatch latch = new CountDownLatch(1);

                    String itemOnDock = entry.getKey();
                    int onDockAmount = entry.getValue();
                    String formattedString = String.format("%s - Amount on dock: %d           Put-away:", itemOnDock, onDockAmount);

                    JFrame frame = new JFrame(itemOnDock);
                    frame.setSize(420, 140);
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    frame.setLayout(null);

                    JLabel amountOnDockLabel = new JLabel(formattedString);
                    amountOnDockLabel.setBounds(10, 10, 280, 25);
                    frame.add(amountOnDockLabel);

                    JTextField putAwayAmount = new JTextField(20);
                    putAwayAmount.setBounds(290, 10, 100, 25);
                    frame.add(putAwayAmount);

                    JButton submitButton = new JButton("Submit");
                    submitButton.setBounds(290, 45, 100, 25);
                    AppUI.stylePrimaryButton(submitButton);
                    frame.add(submitButton);

                    AppUI.styleWindow(frame);
                    submitButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String putAwayAmountString = putAwayAmount.getText();

                            try {
                                int parsedPutAwayAmount = Integer.parseInt(putAwayAmountString);

                                if (parsedPutAwayAmount <= 0) {
                                    JOptionPane.showMessageDialog(frame, "Put-away quantity must be greater than zero.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                if (onDockAmount - parsedPutAwayAmount < 0) {
                                    JOptionPane.showMessageDialog(frame, "Put-away quantity cannot exceed the amount on dock.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                putAwayMap.put(itemCode, parsedPutAwayAmount);
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
                for (Map.Entry<String, Integer> entry : putAwayMap.entrySet()) {

                    String codeOnDock = entry.getKey();
                    int putAwayAmount = entry.getValue();

                    String updateOnOrder = "UPDATE Inventory SET `On Dock` = `On Dock` - ? WHERE `Item Code` = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnOrder)) {
                        preparedStatement.setInt(1, putAwayAmount);
                        preparedStatement.setString(2, codeOnDock);
                        preparedStatement.executeUpdate();
                    }

                    String updateOnDock = "UPDATE Inventory SET `Stock` = `Stock` + ? WHERE `Item Code` = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnDock)) {
                        preparedStatement.setInt(1, putAwayAmount);
                        preparedStatement.setString(2, codeOnDock);
                        preparedStatement.executeUpdate();
                    }

                    String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement movementStatement = connection.prepareStatement(updateMovements)) {
                        movementStatement.setString(1, codeOnDock);
                        movementStatement.setString(2, String.valueOf(putAwayAmount));
                        movementStatement.setString(3, "PUT-AWAY");
                        movementStatement.setString(4, user.getUsername());
                        movementStatement.setString(5, new dateTime().formattedDateTime());
                        movementStatement.executeUpdate();
                    }

                    JOptionPane.showMessageDialog(null, "Put-away completed successfully.");
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
