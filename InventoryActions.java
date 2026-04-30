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
import java.util.concurrent.CountDownLatch;

/**
 * Legacy modal inventory maintenance: add item, stock tables, adjustments, write-offs, and low-stock reporting.
 * Prefer {@link WorkspaceShell} for integrated admin flows where available.
 */
public class InventoryActions {
    /** Runs legacy popup-based add-item workflow. */
    public void addItem(User user, Connection connection) throws SQLException {
        if (!user.hasAdminRights()) {
            JOptionPane.showMessageDialog(null, "You do not have permission to use this feature.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        JFrame frame = new JFrame("Add Item");
        frame.setSize(400, 310);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(null);

        JLabel itemCodeLabel = new JLabel("Item Code:");
        itemCodeLabel.setBounds(10, 10, 100, 25);
        frame.add(itemCodeLabel);
        JTextField itemCodeText = new JTextField(20);
        itemCodeText.setBounds(120, 10, 250, 25);
        frame.add(itemCodeText);

        JLabel itemNameLabel = new JLabel("Item Name:");
        itemNameLabel.setBounds(10, 50, 100, 25);
        frame.add(itemNameLabel);
        JTextField itemNameText = new JTextField(20);
        itemNameText.setBounds(120, 50, 250, 25);
        frame.add(itemNameText);

        JLabel itemStockCountLabel = new JLabel("Stock Count:");
        itemStockCountLabel.setBounds(10, 90, 100, 25);
        frame.add(itemStockCountLabel);
        JTextField itemStockCountText = new JTextField(20);
        itemStockCountText.setBounds(120, 90, 250, 25);
        frame.add(itemStockCountText);

        JLabel reOrderTriggerLabel = new JLabel("Re-Order Trigger:");
        reOrderTriggerLabel.setBounds(10, 130, 100, 25);
        frame.add(reOrderTriggerLabel);
        JTextField reOrderTriggerText = new JTextField(20);
        reOrderTriggerText.setBounds(120, 130, 250, 25);
        frame.add(reOrderTriggerText);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBounds(170, 220, 100, 25);
        frame.add(cancelButton);

        JButton addButton = new JButton("Add");
        addButton.setBounds(280, 220, 100, 25);
        AppUI.stylePrimaryButton(addButton);
        frame.add(addButton);
        AppUI.styleWindow(frame);

        addButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int counter = 0;
                boolean exists = false;
                String itemCode = itemCodeText.getText();

                if (itemCode == null || itemCode.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Item code is required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String query = "SELECT COUNT(*) AS count FROM inventory WHERE `Item Code` = ?";
                try (PreparedStatement checkForDuplicate = connection.prepareStatement(query)) {
                    checkForDuplicate.setString(1, itemCode);
                    ResultSet rs = checkForDuplicate.executeQuery();
                    if (rs.next() && rs.getInt("count") > 0) {
                        JOptionPane.showMessageDialog(null, "That item code already exists.", "Duplicate Entry", JOptionPane.WARNING_MESSAGE);
                        exists = true;
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }

                String itemName = itemNameText.getText();
                if (itemName == null || itemName.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Item name is required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                int stockCount;
                int reOrderTrigger;
                try { stockCount = Integer.parseInt(itemStockCountText.getText()); counter++; } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(frame, "Enter a valid stock quantity.", "Input Error", JOptionPane.ERROR_MESSAGE); return; }
                try { reOrderTrigger = Integer.parseInt(reOrderTriggerText.getText()); counter++; } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(frame, "Enter a valid reorder trigger.", "Input Error", JOptionPane.ERROR_MESSAGE); return; }

                if (counter == 2 && !exists) {
                    String insertQuery = "INSERT INTO Inventory (`Item Code`, `Item Name`, `Stock`, `On Order`, `On Dock`, `ReOrder Trigger`, `Notes`, `Market Price`) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)";
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                        insertStmt.setString(1, itemCode);
                        insertStmt.setString(2, itemName);
                        insertStmt.setString(3, String.valueOf(stockCount));
                        insertStmt.setString(4, String.valueOf(0));
                        insertStmt.setString(5, String.valueOf(0));
                        insertStmt.setString(6, String.valueOf(reOrderTrigger));
                        insertStmt.executeUpdate();

                        if (stockCount > 0) {
                            try (PreparedStatement addLayer = connection.prepareStatement(
                                    "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) VALUES (?, ?, ?, ?, ?, ?)"
                            )) {
                                addLayer.setString(1, itemCode);
                                addLayer.setString(2, "INITIAL_STOCK");
                                addLayer.setDouble(3, 0);
                                addLayer.setInt(4, stockCount);
                                addLayer.setInt(5, stockCount);
                                addLayer.setString(6, new dateTime().formattedDateTime());
                                addLayer.executeUpdate();
                            }
                        }

                        JOptionPane.showMessageDialog(null, "Item added successfully. Enter sale price at checkout.");

                        String movementQuery = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement movementStatement = connection.prepareStatement(movementQuery)) {
                            movementStatement.setString(1, itemCode);
                            movementStatement.setString(2, String.valueOf(stockCount));
                            movementStatement.setString(3, "ADD");
                            movementStatement.setString(4, user.getUsername());
                            movementStatement.setString(5, new dateTime().formattedDateTime());
                            movementStatement.executeUpdate();
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }

                    frame.dispose();
                    latch.countDown();
                }
            }
        });

        cancelButton.addActionListener(e -> {
            frame.dispose();
            latch.countDown();
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Displays legacy popup inventory table with per-column filters. */
    public void viewAllItems(Connection connection) throws SQLException {
        JDialog dialog = new JDialog((JFrame) null, "Inventory Items", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(900, 460);
        dialog.setLocationRelativeTo(null);

        String[] columnNames = {"Item Code", "Item Name", "Stock", "On Order", "On Dock"};
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
             ResultSet rs = stmt.executeQuery("SELECT `Item Code`, `Item Name`, `Stock`, `On Order`, `On Dock` FROM inventory")) {
            while (rs.next()) {
                Object[] rowData = {
                        rs.getString("Item Code"),
                        rs.getString("Item Name"),
                        rs.getInt("Stock"),
                        rs.getInt("On Order"),
                        rs.getInt("On Dock")
                };
                tableModel.addRow(rowData);
            }
        }

        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    /** Displays legacy popup low-stock table using reorder threshold. */
    public void lowStockCheck(Connection connection) throws SQLException {
        JDialog dialog = new JDialog((JFrame) null, "Low Stock", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(760, 460);
        dialog.setLocationRelativeTo(null);

        String[] columnNames = {"Item Code", "Item Name", "Stock", "On Dock", "On Order"};
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

        String query = """
            SELECT `Item Code`,`Item Name`, `Stock`, `On Dock`, `On Order`
            FROM Inventory
            WHERE `ReOrder Trigger` >= `Stock`
            """;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Object[] rowData = {
                        rs.getString("Item Code"),
                        rs.getString("Item Name"),
                        rs.getInt("Stock"),
                        rs.getInt("On Dock"),
                        rs.getInt("On Order")
                };
                tableModel.addRow(rowData);
            }
        }

        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    /** Runs legacy popup workflow to update reorder trigger values. */
    public void adjustReOrderTrigger(User user, Connection connection) throws SQLException {
        if (!user.hasAdminRights()) {
            JOptionPane.showMessageDialog(null, "You do not have permission to use this feature.");
            return;
        }

        String itemCode = JOptionPane.showInputDialog("Please enter the item code you would like to update: ");
        if (itemCode == null) {
            return;
        }
        if (itemCode.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Item code is required.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        HashMap<String, Integer> resultMap = new HashMap<>();
        String query = "SELECT `ReOrder Trigger` FROM Inventory WHERE `Item Code` = ?";

        try (PreparedStatement checkOrder = connection.prepareStatement(query)) {
            checkOrder.setString(1, itemCode);

            try (ResultSet rs = checkOrder.executeQuery()) {
                while (rs.next()) {
                    int reOrderTrigger = rs.getInt("ReOrder Trigger");
                    resultMap.put(itemCode, reOrderTrigger);
                }

                if (resultMap.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No matching item was found.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                HashMap<String, Integer> updatedReOrderMap = new HashMap<>();

                for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                    CountDownLatch latch = new CountDownLatch(1);

                    String oldValue = entry.getKey();
                    int newValue = entry.getValue();
                    String formattedText = String.format("%s - Current Trigger Value: %d           Updated:", oldValue, newValue);

                    JFrame frame = new JFrame(itemCode);
                    frame.setSize(420, 140);
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    frame.setLayout(null);

                    JLabel amountLabel = new JLabel(formattedText);
                    amountLabel.setBounds(10, 10, 280, 25);
                    frame.add(amountLabel);

                    JTextField newTriggerValue = new JTextField(20);
                    newTriggerValue.setBounds(290, 10, 100, 25);
                    frame.add(newTriggerValue);

                    JButton submitButton = new JButton("Submit");
                    submitButton.setBounds(290, 45, 100, 25);
                    AppUI.stylePrimaryButton(submitButton);
                    frame.add(submitButton);
                    AppUI.styleWindow(frame);

                    submitButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String newReOrderValue = newTriggerValue.getText();

                            try {
                                int newTotal = Integer.parseInt(newReOrderValue);
                                if (newTotal < 0) {
                                    JOptionPane.showMessageDialog(frame, "Reorder trigger must be zero or greater.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }
                                updatedReOrderMap.put(itemCode, newTotal);
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

                for (Map.Entry<String, Integer> entry : updatedReOrderMap.entrySet()) {
                    String item = entry.getKey();
                    int newTrigger = entry.getValue();

                    String updateOnDock = "UPDATE Inventory SET `ReOrder Trigger` = ? WHERE `Item Code` = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnDock)) {
                        preparedStatement.setInt(1, newTrigger);
                        preparedStatement.setString(2, item);
                        preparedStatement.executeUpdate();
                    }

                    String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement movementStatement = connection.prepareStatement(updateMovements)) {
                        movementStatement.setString(1, item);
                        movementStatement.setString(2, " ");
                        movementStatement.setString(3, "UPDATED TRIGGER");
                        movementStatement.setString(4, user.getUsername());
                        movementStatement.setString(5, new dateTime().formattedDateTime());
                        movementStatement.executeUpdate();
                    }

                    JOptionPane.showMessageDialog(null, "Reorder trigger updated successfully.");
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Runs legacy popup stock write-off workflow. */
    public void writeOffStock(User user, Connection connection) throws SQLException {
        if (!user.hasAdminRights()) {
            JOptionPane.showMessageDialog(null, "You do not have permission to use this feature.");
            return;
        }

        String itemCode = JOptionPane.showInputDialog("Please enter the item code you would like to write off: ");
        if (itemCode == null) {
            return;
        }
        if (itemCode.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Item code is required.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        HashMap<String, Integer> resultMap = new HashMap<>();
        String query = "SELECT Stock FROM Inventory WHERE `Item Code` = ?";

        try (PreparedStatement checkOrder = connection.prepareStatement(query)) {
            checkOrder.setString(1, itemCode);

            try (ResultSet rs = checkOrder.executeQuery()) {
                while (rs.next()) {
                    int currentStock = rs.getInt("Stock");
                    resultMap.put(itemCode, currentStock);
                }

                if (resultMap.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No matching item was found.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                HashMap<String, Integer> writeOffMap = new HashMap<>();

                for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                    CountDownLatch latch = new CountDownLatch(1);

                    String stockCount = entry.getKey();
                    int writeOffAmount = entry.getValue();
                    String formattedText = String.format("%s - Current Stock: %d        Write Off:", stockCount, writeOffAmount);

                    JFrame frame = new JFrame(itemCode);
                    frame.setSize(420, 140);
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    frame.setLayout(null);

                    JLabel currentAmount = new JLabel(formattedText);
                    currentAmount.setBounds(10, 10, 280, 25);
                    frame.add(currentAmount);

                    JTextField writeOff = new JTextField(20);
                    writeOff.setBounds(290, 10, 100, 25);
                    frame.add(writeOff);

                    JButton submitButton = new JButton("Submit");
                    submitButton.setBounds(290, 45, 100, 25);
                    AppUI.stylePrimaryButton(submitButton);
                    frame.add(submitButton);
                    AppUI.styleWindow(frame);

                    submitButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String writeOffTotal = writeOff.getText();

                            try {
                                int newTotal = Integer.parseInt(writeOffTotal);
                                if (newTotal < 0) {
                                    JOptionPane.showMessageDialog(frame, "Write-off quantity must be zero or greater.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }
                                if (newTotal > writeOffAmount) {
                                    JOptionPane.showMessageDialog(frame, "Write-off quantity cannot exceed current stock.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                writeOffMap.put(itemCode, newTotal);
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

                for (Map.Entry<String, Integer> entry : writeOffMap.entrySet()) {
                    String item = entry.getKey();
                    int amountToWriteOff = entry.getValue();

                    String newStockCount = "UPDATE Inventory SET Stock = Stock - ? WHERE `Item Code` = ? AND Stock >= ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(newStockCount)) {
                        preparedStatement.setInt(1, amountToWriteOff);
                        preparedStatement.setString(2, item);
                        preparedStatement.setInt(3, amountToWriteOff);
                        int affectedRows = preparedStatement.executeUpdate();
                        if (affectedRows == 0) {
                            JOptionPane.showMessageDialog(null, "Write-off failed because stock is lower than the requested quantity.", "Stock Update Error", JOptionPane.ERROR_MESSAGE);
                            continue;
                        }
                    }

                    String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement movementStatement = connection.prepareStatement(updateMovements)) {
                        movementStatement.setString(1, item);
                        movementStatement.setString(2, String.valueOf(amountToWriteOff));
                        movementStatement.setString(3, "WRITE OFF");
                        movementStatement.setString(4, user.getUsername());
                        movementStatement.setString(5, new dateTime().formattedDateTime());
                        movementStatement.executeUpdate();
                    }

                    JOptionPane.showMessageDialog(null, "Stock write-off completed successfully.");
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
