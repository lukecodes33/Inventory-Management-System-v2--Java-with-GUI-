import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class menuFunctions {

    /**
     * Checks to ensure user has admin access.
     * Opens a graphical interface to add a new item to the inventory database, checking for duplicate entries.
     * <p>
     * This method creates a JFrame interface allowing a user to input details of a new inventory item, including
     * item code, item name, stock count, reorder trigger level, purchase price, and sales price. The method
     * checks for a duplicate item code in the database and displays an error if the item code already exists.
     * If the inputs are valid and the item is unique, it inserts the new item into the `Inventory` database and
     * logs the addition in the `movements` database.
     *
     * @param user The current user, whose username is logged in the `movements` database as the action's originator.
     * @throws SQLException if a database access error occurs
     */
    public void addItem(User user) throws SQLException {

        boolean adminRights = user.hasAdminRights();

        if(adminRights) {
            String itemDatabasePath = "database/itemDatabase.db";
            CountDownLatch latch = new CountDownLatch(1);

            try (Connection itemConnection = DriverManager.getConnection("jdbc:sqlite:" + itemDatabasePath)) {

                JFrame frame = new JFrame("Add Item");
                frame.setSize(400, 350);
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

                JLabel reOrderTriggerLabel = new JLabel("ReOrder Trigger:");
                reOrderTriggerLabel.setBounds(10, 130, 100, 25);
                frame.add(reOrderTriggerLabel);
                JTextField reOrderTriggerText = new JTextField(20);
                reOrderTriggerText.setBounds(120, 130, 250, 25);
                frame.add(reOrderTriggerText);

                JLabel purchasePriceLabel = new JLabel("Purchase Price:");
                purchasePriceLabel.setBounds(10, 170, 100, 25);
                frame.add(purchasePriceLabel);
                JTextField purchasePriceText = new JTextField(20);
                purchasePriceText.setBounds(120, 170, 250, 25);
                frame.add(purchasePriceText);

                JLabel salePriceLabel = new JLabel("Sales Price:");
                salePriceLabel.setBounds(10, 210, 100, 25);
                frame.add(salePriceLabel);
                JTextField salePriceText = new JTextField(20);
                salePriceText.setBounds(120, 210, 250, 25);
                frame.add(salePriceText);

                JButton addButton = new JButton("Add");
                addButton.setBounds(170, 260, 100, 25);
                frame.add(addButton);

                JButton cancelButton = new JButton("Cancel");
                cancelButton.setBounds(280, 260, 100, 25);
                frame.add(cancelButton);

                addButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {

                        int counter = 0;
                        boolean exists = false;

                        String itemCode = itemCodeText.getText();
                        if (itemCode == null || itemCode.trim().isEmpty()) {
                            JOptionPane.showMessageDialog(frame, "Item Code cannot be empty. Please enter a valid Item Code.", "Input Error", JOptionPane.ERROR_MESSAGE);
                        } else {
                            // Prepare the SQL query to check for itemCode existence
                            String query = "SELECT COUNT(*) AS count FROM inventory WHERE `Item Code` = ?";
                            try (PreparedStatement checkForDuplicate = itemConnection.prepareStatement(query)) {
                                checkForDuplicate.setString(1, itemCode);
                                ResultSet rs = checkForDuplicate.executeQuery();

                                try {
                                    if (rs.next()) {
                                        int count = rs.getInt("count");
                                        if (count > 0) {
                                            JOptionPane.showMessageDialog(null, "Item Code already exists", "Duplicate Entry", JOptionPane.WARNING_MESSAGE);
                                            exists = true;
                                        }
                                    }
                                } catch (SQLException ex) {
                                    throw new RuntimeException(ex);
                                }
                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
                            }
                        }


                        String itemName = itemNameText.getText();
                        if (itemName == null || itemName.trim().isEmpty()) {
                            JOptionPane.showMessageDialog(frame, "Item Name cannot be empty. Please enter a valid Item Name.", "Input Error", JOptionPane.ERROR_MESSAGE);
                        }

                        int stockCount = 0;
                        try {
                            stockCount = Integer.parseInt(itemStockCountText.getText());
                            counter += 1;
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(frame, "Please enter a valid Stock Count.", "Input Error", JOptionPane.ERROR_MESSAGE);
                        }

                        int reOrderTrigger = 0;
                        try {
                            reOrderTrigger = Integer.parseInt(reOrderTriggerText.getText());
                            counter += 1;
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(frame, "Please enter a valid Re Order Trigger.", "Input Error", JOptionPane.ERROR_MESSAGE);
                        }

                        double purchasePrice = 0;
                        try {
                            purchasePrice = Double.parseDouble(purchasePriceText.getText());
                            counter += 1;
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(frame, "Please enter a valid Purchase Price.", "Input Error", JOptionPane.ERROR_MESSAGE);
                        }

                        double salesPrice = 0;
                        try {
                            salesPrice = Double.parseDouble(salePriceText.getText());
                            counter += 1;
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(frame, "Please enter a valid Sales Price.", "Input Error", JOptionPane.ERROR_MESSAGE);
                        }

                        if (counter == 4 && itemCode != null && itemName != null && !exists) {
                            String insertQuery = "INSERT INTO Inventory (`Item Code`, `Item Name`, `Stock`, `On Order`, `ReOrder Trigger`, `Purchase Price`, `Sale Price`, `Amount Sold`, `Profit`, `Removed`) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                            try (PreparedStatement insertStmt = itemConnection.prepareStatement(insertQuery)) {
                                insertStmt.setString(1, itemCode);
                                insertStmt.setString(2, itemName);
                                insertStmt.setString(3, String.valueOf(stockCount));
                                insertStmt.setString(4, String.valueOf(0));
                                insertStmt.setString(5, String.valueOf(reOrderTrigger));
                                insertStmt.setString(6, String.valueOf(purchasePrice));
                                insertStmt.setString(7, String.valueOf(salesPrice));
                                insertStmt.setString(8, String.valueOf(0));
                                insertStmt.setString(9, String.valueOf(0));
                                insertStmt.setString(10, String.valueOf(0));
                                insertStmt.executeUpdate();

                                JOptionPane.showMessageDialog(null, "Item added successfully");


                                String movementsDatabase = "database/movements.db";
                                try (Connection movementsConnections = DriverManager.getConnection("jdbc:sqlite:" + movementsDatabase)) {

                                    String movementQuery = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) " +
                                            "VALUES (?, ?, ?, ?, ?)";

                                    try (PreparedStatement movementStatement = movementsConnections.prepareStatement(movementQuery)) {
                                        movementStatement.setString(1, itemCode);
                                        movementStatement.setString(2, String.valueOf(stockCount));
                                        movementStatement.setString(3, "ADD");
                                        movementStatement.setString(4, user.getUsername());
                                        movementStatement.setString(5, new dateTime().formattedDateTime());
                                        movementStatement.executeUpdate();
                                    }
                                }

                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
                            }

                            frame.dispose();
                            latch.countDown();

                        }
                    }
                });

                cancelButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        frame.dispose(); // Close the current frame
                        latch.countDown(); // Release the latch to allow the main loop to continue
                    }
                });

                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        System.exit(0);
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
        } else {
            JOptionPane.showMessageDialog(null, "You do not have access to this feature");
        }

    }


    /**
     * Displays all items in the inventory in a modal dialog.
     * The dialog contains a table showing the item details and provides
     * text fields for filtering the displayed items based on the selected columns.
     *
     * <p>This method connects to an SQLite database to retrieve item
     * information and populates a JTable with the retrieved data.</p>
     *
     * <p>The following columns are displayed in the table:</p>
     * <ul>
     *     <li>Item Code</li>
     *     <li>Item Name</li>
     *     <li>Stock</li>
     *     <li>On Order</li>
     *     <li>ReOrder Trigger</li>
     *     <li>Purchase Price ($)</li>
     *     <li>Sale Price ($)</li>
     * </ul>
     *
     * <p>The method also implements filtering capabilities using text fields
     * below the table. Users can filter the rows based on their input,
     * which is case insensitive.</p>
     *
     * @throws RuntimeException if a database access error occurs
     * while connecting to the SQLite database or executing the query.
     */


    public void viewAllItems() {
        String itemDatabasePath = "database/itemDatabase.db";

        JDialog dialog = new JDialog((JFrame) null, "Inventory Items", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(800, 400);
        dialog.setLocationRelativeTo(null);


        String[] columnNames = {"Item Code", "Item Name", "Stock", "On Order", "ReOrder Trigger", "Purchase Price($)", "Sale Price($)"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        JPanel filterPanel = new JPanel(new GridLayout(1, columnNames.length));

        JTextField[] filterFields = new JTextField[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            filterFields[i] = new JTextField();
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

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + itemDatabasePath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT `Item Code`, `Item Name`, `Stock`, `On Order`, `ReOrder Trigger`, `Purchase Price`, `Sale Price` FROM inventory")) {

            while (rs.next()) {
                Object[] rowData = {
                        rs.getString("Item Code"),
                        rs.getString("Item Name"),
                        rs.getInt("Stock"),
                        rs.getInt("On Order"),
                        rs.getInt("ReOrder Trigger"),
                        rs.getDouble("Purchase Price"),
                        rs.getDouble("Sale Price")
                };
                tableModel.addRow(rowData);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        // Add components to the dialog
        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }


    /**
     * Resets the password for the specified user.
     *
     * This method displays a GUI dialog that prompts the user to enter their current
     * password, a new password, and confirm the new password. It allows up to three
     * attempts to enter the current password correctly. If successful, the new password
     * is updated in the database.
     *
     * If the user cancels the operation, the Application will terminate without making any changes.
     * This is to prevent anyone trying to change a users password if it is signed in on an open machine.
     *
     * @param user The User object representing the user whose password is to be reset.
     * @throws RuntimeException If an SQL error occurs during the password update process.
     */

    public void resetPassword(User user) {
        final boolean[][] passwordLoopBreaker = {{false}};
        final int[] attempts = {3};

        while (!passwordLoopBreaker[0][0]) {
            // CountDownLatch to block until the user has logged in
            CountDownLatch latch = new CountDownLatch(1);

            JFrame frame = new JFrame("Password Reset");
            frame.setSize(380, 210);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(null);

            JLabel userLabel = new JLabel("Username:");
            userLabel.setBounds(10, 10, 160, 25);
            frame.add(userLabel);
            JLabel userText = new JLabel(user.getUsername());
            userText.setBounds(100, 10, 190, 25);
            frame.add(userText);

            JLabel currentPasswordLabel = new JLabel("Current Password:");
            currentPasswordLabel.setBounds(10, 40, 160, 25);
            frame.add(currentPasswordLabel);

            JPasswordField currentPasswordText = new JPasswordField(20);
            currentPasswordText.setBounds(165, 40, 190, 25);
            frame.add(currentPasswordText);

            JLabel newPasswordLabel = new JLabel("New Password:");
            newPasswordLabel.setBounds(10, 70, 160, 25);
            frame.add(newPasswordLabel);

            JPasswordField newPasswordText = new JPasswordField(20);
            newPasswordText.setBounds(165, 70, 190, 25);
            frame.add(newPasswordText);

            JLabel newPasswordConfirmationLabel = new JLabel("Confirm New Password:");
            newPasswordConfirmationLabel.setBounds(10, 100, 160, 25);
            frame.add(newPasswordConfirmationLabel);

            JPasswordField newPasswordConfirmationText = new JPasswordField(20);
            newPasswordConfirmationText.setBounds(165, 100, 190, 25);
            frame.add(newPasswordConfirmationText);

            JButton loginButton = new JButton("Submit");
            loginButton.setBounds(255, 135, 100, 25);
            frame.add(loginButton);

            loginButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {

                    Map<String, String> passwordMap = new HashMap<>();
                    passwordMap.put("current", String.valueOf(currentPasswordText.getPassword()));
                    passwordMap.put("new1", String.valueOf(newPasswordText.getPassword()));
                    passwordMap.put("new2", String.valueOf(newPasswordConfirmationText.getPassword()));

                    if (passwordMap.get("current").equals(user.getPassword())) {
                        if (passwordMap.get("new1").equals(passwordMap.get("new2"))) {
                            String updatedPassword = passwordMap.get("new1");

                            String userDatabasePath = "database/userDatabase.db";
                            try (Connection userConnection = DriverManager.getConnection("jdbc:sqlite:" + userDatabasePath)) {
                                String updateSql = "UPDATE users SET password = ?, first_login = 0 WHERE username = ?";
                                try (PreparedStatement preparedStatement = userConnection.prepareStatement(updateSql)) {
                                    preparedStatement.setString(1, updatedPassword);
                                    preparedStatement.setString(2, user.getUsername());
                                    preparedStatement.executeUpdate();

                                    System.out.println("Updating Password");
                                    JOptionPane.showMessageDialog(null, "Password reset. You will need to log back in to continue.");
                                    passwordMap.clear();
                                    System.exit(0);

                                } catch (SQLException ex) {
                                    throw new RuntimeException(ex);
                                }
                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
                            }
                        } else {
                            System.out.println("New passwords do not match");
                        }
                    } else {
                        attempts[0]--;
                        JOptionPane.showMessageDialog(null, "Incorrect Password. " + attempts[0] + " attempts remaining.");

                        if (attempts[0] == 0) {
                            System.exit(0);
                        }
                    }

                    frame.dispose();
                    latch.countDown();
                }
            });

            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
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
    }
}

