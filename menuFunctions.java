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
import java.util.UUID;

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
     * @param user       The current user, whose username is logged in the `movements` database as the action's originator.
     * @throws SQLException if a database access error occurs
     */
    public void addItem(User user, Connection connection) throws SQLException {

        boolean adminRights = user.hasAdminRights();

        if(adminRights) {
            CountDownLatch latch = new CountDownLatch(1);

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

            JLabel reOrderTriggerLabel = new JLabel("Re-Order Trigger:");
            reOrderTriggerLabel.setBounds(10, 130, 100, 25);
            frame.add(reOrderTriggerLabel);
            JTextField reOrderTriggerText = new JTextField(20);
            reOrderTriggerText.setBounds(120, 130, 250, 25);
            frame.add(reOrderTriggerText);

            JLabel purchasePriceLabel = new JLabel("Purchase Price: ($)");
            purchasePriceLabel.setBounds(10, 170, 100, 25);
            frame.add(purchasePriceLabel);
            JTextField purchasePriceText = new JTextField(20);
            purchasePriceText.setBounds(120, 170, 250, 25);
            frame.add(purchasePriceText);

            JLabel salePriceLabel = new JLabel("Sales Price: ($)");
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
                        try (PreparedStatement checkForDuplicate = connection.prepareStatement(query)) {
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
                        String insertQuery = "INSERT INTO Inventory (`Item Code`, `Item Name`, `Stock`, `On Order`, `On Dock`, `ReOrder Trigger`, `Purchase Price`, `Sale Price`, `Amount Sold`, `Profit`, `Written Off`) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                        try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                            insertStmt.setString(1, itemCode);
                            insertStmt.setString(2, itemName);
                            insertStmt.setString(3, String.valueOf(stockCount));
                            insertStmt.setString(4, String.valueOf(0));
                            insertStmt.setString(5, String.valueOf(0));
                            insertStmt.setString(6, String.valueOf(reOrderTrigger));
                            insertStmt.setString(7, String.valueOf(purchasePrice));
                            insertStmt.setString(8, String.valueOf(salesPrice));
                            insertStmt.setString(9, String.valueOf(0));
                            insertStmt.setString(10, String.valueOf(0));
                            insertStmt.setString(11, String.valueOf(0));
                            insertStmt.executeUpdate();

                            JOptionPane.showMessageDialog(null, "Item added successfully");

                            String movementQuery = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) " +
                                    "VALUES (?, ?, ?, ?, ?)";

                            try (PreparedStatement movementStatement = connection.prepareStatement(movementQuery)) {
                                movementStatement.setString(1, itemCode);
                                movementStatement.setString(2, String.valueOf(stockCount));
                                movementStatement.setString(3, "ADD");
                                movementStatement.setString(4, user.getUsername());
                                movementStatement.setString(5, new dateTime().formattedDateTime());
                                movementStatement.executeUpdate();
                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
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
                    try {
                        connection.close();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
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
    public void viewAllItems(Connection connection) throws SQLException {

        JDialog dialog = new JDialog((JFrame) null, "Inventory Items", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(800, 400);
        dialog.setLocationRelativeTo(null);


        String[] columnNames = {"Item Code", "Item Name", "Stock", "On Order", "On Dock", "Purchase Price($)", "Sale Price($)"};
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

         Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT `Item Code`, `Item Name`, `Stock`, `On Order`, `On Dock`, `Purchase Price`, `Sale Price` FROM inventory");
        {

            while (rs.next()) {
                Object[] rowData = {
                        rs.getString("Item Code"),
                        rs.getString("Item Name"),
                        rs.getInt("Stock"),
                        rs.getInt("On Order"),
                        rs.getInt("On Dock"),
                        rs.getDouble("Purchase Price"),
                        rs.getDouble("Sale Price")
                };
                tableModel.addRow(rowData);
            }
        }

        // Add components to the dialog
        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }


    /**
     * Resets the password for the specified user.
     * <p>
     * This method displays a GUI dialog that prompts the user to enter their current
     * password, a new password, and confirm the new password. It allows up to three
     * attempts to enter the current password correctly. If successful, the new password
     * is updated in the database.
     * <p>
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


    /**
     * Creates a new purchase order with specified items and quantities, and updates the relevant database tables.
     *
     * <p>This method performs the following actions:</p>
     * <ul>
     *     <li>Prompts the user for a reference number, generating a random reference if left blank.</li>
     *     <li>Iterates through item entries, verifying each item exists in the inventory before adding it to the order.</li>
     *     <li>Prompts the user for quantity per item and validates the input.</li>
     *     <li>Stores each item and quantity in a map for further processing.</li>
     *     <li>Inserts each item and its details into the pendingOrders table with a reference number, user, and date.</li>
     *     <li>Updates the On Order quantity in the inventory table for each item.</li>
     *     <li>Logs each item addition in the movements table, marking it as "ORDERED" with the user's details and date.</li>
     * </ul>
     *
     * <p>The following columns are updated in each table:</p>
     * <ul>
     *     <li><b>pendingOrders:</b> Item Code, Amount, Reference, User, Date</li>
     *     <li><b>inventory:</b> On Order (incremented by specified amount)</li>
     *     <li><b>movements:</b> Item Code, Amount, Type ("ORDERED"), User, Date</li>
     * </ul>
     *
     * <p>The method also checks for user permissions, restricting access to users without admin rights.</p>
     *
     * <p><b>Note:</b> This method expects an active database connection and a user object with username and admin rights details.</p>
     *
     * @param user      the User object representing the current user, including their admin rights
     * @param connection the active Connection object to the database for executing SQL operations
     * @throws RuntimeException if a database access error occurs while checking items or executing SQL queries
     */
    public void newPurchaseOrder(User user, Connection connection) {

        dateTime formattedDateTimeInstance = new dateTime();
        String formattedDateTime = formattedDateTimeInstance.formattedDateTime();

        boolean adminRights = user.hasAdminRights();

        if(adminRights) {
            String referenceNumber = JOptionPane.showInputDialog("Please enter your reference number (Leave this blank if you want like a random generated number):");

            if (referenceNumber == null) {
                return;
            }

            if (referenceNumber.trim().isEmpty()) {
                referenceNumber = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                JOptionPane.showMessageDialog(null, "No reference number provided. Generated Reference Number: " + referenceNumber);
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

                    try {
                        if (rs.next()) {
                            int count = rs.getInt("count");
                            if (count == 0) {
                                JOptionPane.showMessageDialog(null, "Item Code does not exist in system, add it before adding to purchase order (CAPS SENSITIVE)", "Item not found", JOptionPane.WARNING_MESSAGE);
                                exists = false;
                            }
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }

                if (exists) {
                    // Prompt for quantity
                    String quantityStr = JOptionPane.showInputDialog("Enter the quantity:");
                    if (quantityStr == null) {
                        break;
                    }

                    int quantity;
                    try {
                        quantity = Integer.parseInt(quantityStr);
                        if (quantity <= 0) {
                            JOptionPane.showMessageDialog(null, "Please enter a valid positive quantity.");
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(null, "Invalid quantity. Please enter a number.");
                        continue;
                    }

                    // Add item and quantity to the map
                    purchaseOrderItems.put(itemCode, quantity);


                    // Ask if the user wants to add another item
                    int response = JOptionPane.showConfirmDialog(null, "Would you like to add another item?", "Add Another Item", JOptionPane.YES_NO_OPTION);
                    if (response == JOptionPane.NO_OPTION) {
                        break;
                    }
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

            JOptionPane.showMessageDialog(null, "Purchase order created successfully with reference number: " + referenceNumber);


        } else {
            JOptionPane.showMessageDialog(null, "You do not have access to this feature");
        }

    }


    /**
     * Displays all items in the pendingOrders table in a modal dialog.
     * The dialog contains a table showing the item details and provides
     * text fields for filtering the displayed items based on the selected columns.
     *
     * <p>This method connects to an SQLite database to retrieve item
     * information and populates a JTable with the retrieved data.</p>
     *
     * <p>The following columns are displayed in the table:</p>
     * <ul>
     *     <li>Item Code/li>
     *     <li>Amount</li>
     *     <li>Reference</li>
     *     <li>Date</li>
     * </ul>
     *
     * <p>The method also implements filtering capabilities using text fields
     * below the table. Users can filter the rows based on their input,
     * which is case insensitive.</p>
     *
     * @throws RuntimeException if a database access error occurs
     * while connecting to the SQLite database or executing the query.
     */
    public void displayPendingOrders(Connection connection) throws SQLException {

        JDialog dialog = new JDialog((JFrame) null, "Pending Orders", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(null);

        // Define column names for the pendingOrders table
        String[] columnNames = {"Item Code", "Amount", "Reference", "Date"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);

        // Set up row sorter for filtering
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        JPanel filterPanel = new JPanel(new GridLayout(1, columnNames.length));

        // Create filter fields for each column
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

        // Retrieve data from pendingOrders table
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT `Item Code`, `Amount`, `Reference`, `Date` FROM pendingOrders");

        // Populate the table with data from the ResultSet
        while (rs.next()) {
            Object[] rowData = {
                    rs.getString("Item Code"),
                    rs.getInt("Amount"),
                    rs.getString("Reference"),
                    rs.getString("Date")
            };
            tableModel.addRow(rowData);
        }

        // Add components to the dialog
        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }


    /**
     * Processes the reception of items from a pending order. The user is prompted to enter a reference number,
     * and then the method performs the following:
     * - Retrieves pending items for the given reference number.
     * - Allows the user to input received amounts for each item.
     * - Updates the pendingOrders table to subtract the received amounts.
     * - Updates the Inventory table to reflect the changes in "On Order" and "On Dock" quantities.
     * - Logs the movement in the movements table.
     * - Deletes completed orders from the pendingOrders table where the amount is zero.
     *
     * @param user       The user performing the operation, used for logging movements.
     * @param connection The database connection to use for executing queries.
     * @throws SQLException if any database access error occurs.
     */
    public void receiveOrder(User user, Connection connection) throws SQLException {
        String referenceNumber = JOptionPane.showInputDialog("Enter reference number: ");

        if (referenceNumber == null) {
            return;

        }

        if (referenceNumber.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Reference number cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
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
                    JOptionPane.showMessageDialog(null, "No orders found for the given reference number.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                HashMap<String, Integer> receivedMap = new HashMap<>();

                for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                    CountDownLatch latch = new CountDownLatch(1);

                    String codeOrdered = entry.getKey();
                    int amountOrdered = entry.getValue();
                    String formattedString = String.format("%s - Amount on Order: %d           Received:", codeOrdered, amountOrdered);


                    JFrame frame = new JFrame(codeOrdered);
                    frame.setSize(350, 115);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.setLayout(null);

                    JLabel amountLabel = new JLabel(formattedString);
                    amountLabel.setBounds(10, 10, 250, 25);
                    frame.add(amountLabel);

                    JTextField amountReceived = new JTextField(20);
                    amountReceived.setBounds(250, 10, 80, 25);
                    frame.add(amountReceived);

                    JButton submitButton = new JButton("Submit");
                    submitButton.setBounds(250, 40, 80, 25);
                    frame.add(submitButton);

                    submitButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String receivedAmountString = amountReceived.getText();

                            try {
                                int receivedAmount = Integer.parseInt(receivedAmountString);

                                if (receivedAmount <= 0) {
                                    JOptionPane.showMessageDialog(frame, "Invalid input. Received amount must be a positive number.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                if (amountOrdered - receivedAmount < 0) {
                                    JOptionPane.showMessageDialog(frame, "Invalid input. Received amount cannot exceed the amount ordered.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }


                                receivedMap.put(codeOrdered, receivedAmount);

                                frame.dispose();
                                latch.countDown();

                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid integer.", "Input Error", JOptionPane.ERROR_MESSAGE);
                            }
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
                        preparedStatement.setInt(1, amountReceived); // Subtract amountReceived
                        preparedStatement.setString(2, referenceNumber); // Match reference number
                        preparedStatement.setString(3, codeReceived); // Match item code
                        preparedStatement.executeUpdate();
                    }

                    String updateOnOrder = "UPDATE Inventory SET `On Order` = `On Order` - ? WHERE `Item Code` = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnOrder)) {
                        preparedStatement.setInt(1, amountReceived); // Subtract amountReceived
                        preparedStatement.setString(2, codeReceived); // Match reference number
                        preparedStatement.executeUpdate();

                    }

                    String updateOnDock = "UPDATE Inventory SET `On Dock` = `On Dock` + ? WHERE `Item Code` = ?";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnDock)) {
                        preparedStatement.setInt(1, amountReceived); // Subtract amountReceived
                        preparedStatement.setString(2, codeReceived); // Match reference number
                        preparedStatement.executeUpdate();

                    }

                    String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) " +
                            "VALUES (?, ?, ?, ?, ?)";
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
                        deleteStatement.setString(1, referenceNumber); // Match reference number
                        deleteStatement.setString(2, codeReceived); // Match item code
                        deleteStatement.executeUpdate();
                    }

                    JOptionPane.showMessageDialog(null, "Items Received!");
                }

            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    /**
     * Handles the process of putting away stock from the "On Dock" section of the inventory into the main "Stock" section.
     *
     * This method performs the following operations:
     * 1. Prompts the user to enter an item code to put away stock.
     * 2. Checks the database for the specified item code in the "Inventory" table and retrieves the "On Dock" quantity.
     * 3. If no stock is found for the item code, displays a message to the user and terminates the process.
     * 4. For each item with stock on dock:
     *    - Displays a dialog to input the quantity to put away.
     *    - Validates the input to ensure it is a positive integer and does not exceed the available "On Dock" quantity.
     *    - Updates the database to:
     *      a. Deduct the specified quantity from "On Dock".
     *      b. Add the specified quantity to "Stock".
     *      c. Log the transaction in the "movements" table with the type "PUT-AWAY".
     * 5. Displays a confirmation message upon successful completion of the operation.
     *
     * @param user       The user performing the put-away operation. Used for logging purposes.
     * @param connection The database connection used to perform queries and updates.
     * @throws SQLException If there is an issue executing SQL queries or updates.
     */
    public void putAwayStock(User user, Connection connection) throws SQLException {
        String itemCode = JOptionPane.showInputDialog("Enter item code to put-away: ");

        if (itemCode == null) {
            return;

        }

        if (itemCode.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Reference number cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
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
                    System.out.println(resultMap);
                }

                if (resultMap.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No put away found for the given item code", "No Results", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                HashMap<String, Integer> putAwayMap = new HashMap<>();

                for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                    CountDownLatch latch = new CountDownLatch(1);

                    String itemOnDock = entry.getKey();
                    int onDockAmount = entry.getValue();
                    String formattedString = String.format("%s - Amount on dock: %d           Put-away:", itemOnDock, onDockAmount);


                    JFrame frame = new JFrame(itemOnDock);
                    frame.setSize(350, 115);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.setLayout(null);

                    JLabel amountOnDockLabel = new JLabel(formattedString);
                    amountOnDockLabel.setBounds(10, 10, 250, 25);
                    frame.add(amountOnDockLabel);

                    JTextField putAwayAmount = new JTextField(20);
                    putAwayAmount.setBounds(250, 10, 80, 25);
                    frame.add(putAwayAmount);

                    JButton submitButton = new JButton("Submit");
                    submitButton.setBounds(250, 40, 80, 25);
                    frame.add(submitButton);

                    submitButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String putAwayAmountString = putAwayAmount.getText();

                            try {
                                int putAwayAmount = Integer.parseInt(putAwayAmountString);

                                if (putAwayAmount <= 0) {
                                    JOptionPane.showMessageDialog(frame, "Invalid input. put Away amount must be a positive number.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                if (onDockAmount - putAwayAmount < 0) {
                                    JOptionPane.showMessageDialog(frame, "Invalid input. put away amount cannot exceed the amount on dock.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }


                                putAwayMap.put(itemCode, putAwayAmount);


                                frame.dispose();
                                latch.countDown();

                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid integer.", "Input Error", JOptionPane.ERROR_MESSAGE);
                            }
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

                    String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) " +
                            "VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement movementStatement = connection.prepareStatement(updateMovements)) {
                        movementStatement.setString(1, codeOnDock);
                        movementStatement.setString(2, String.valueOf(putAwayAmount));
                        movementStatement.setString(3, "PUT-AWAY");
                        movementStatement.setString(4, user.getUsername());
                        movementStatement.setString(5, new dateTime().formattedDateTime());
                        movementStatement.executeUpdate();
                    }

                    JOptionPane.showMessageDialog(null, "Put Away Successful");
                }

            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    /**
     * Displays a dialog showing items with low stock levels, based on the "ReOrder Trigger" value.
     *
     * This method performs the following operations:
     * 1. Queries the "Inventory" table to retrieve items where the "ReOrder Trigger" value is greater than or equal to the "Stock".
     * 2. Displays the results in a table with columns for "Item Code", "Item Name", "Stock", "On Dock", and "On Order".
     * 3. Provides a filter input for each column, allowing users to filter displayed data dynamically.
     * 4. Presents the data in a dialog window for review and analysis.
     *
     * @param connection The database connection used to execute the SQL query.
     * @throws SQLException If there is an issue executing the SQL query.
     */
    public void lowStockCheck(Connection connection) throws SQLException {

        JDialog dialog = new JDialog((JFrame) null, "Low Stock", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(null);

        String[] columnNames = {"Item Code", "Item Name", "Stock", "On Dock", "On Order"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);

        // Set up row sorter for filtering
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        JPanel filterPanel = new JPanel(new GridLayout(1, columnNames.length));

        // Create filter fields for each column
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


        String query = """
            SELECT `Item Code`,`Item Name`, `Stock`, `On Dock`, `On Order`
            FROM Inventory
            WHERE `ReOrder Trigger` >= `Stock`
            """;
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(query);


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

        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }





    /**
     * Adjusts the reorder trigger level for an inventory item, accessible only to admin users.
     * <p>
     * This method checks if the user has admin rights and provides a graphical interface to update the
     * "ReOrder Trigger" value of a specific item in the inventory. It retrieves the current value from the
     * `Inventory` database and displays it in a JFrame, allowing the user to input a new trigger value.
     * The method validates the input to ensure it is a non-negative integer, updates the value in the database,
     * and logs the action in the `movements` table.
     * <p>
     * If the specified item code does not exist in the database, an error message is displayed. The operation
     * ensures the use of parameterized queries to prevent SQL injection and maintain data integrity.
     *
     * @param user       The current user, whose admin rights are verified before granting access to this feature.
     *                   The user's username is also logged in the `movements` database.
     * @param connection A valid database connection used to interact with the `Inventory` and `movements` tables.
     * @throws SQLException if a database access error occurs
     *
     * Workflow:
     * - Validate that the user has admin rights.
     * - Prompt the user to input an item code and fetch its current "ReOrder Trigger" value.
     * - Display the current trigger value in a GUI and allow the user to input a new value.
     * - Validate the new value (must be a non-negative integer).
     * - Update the `ReOrder Trigger` value in the `Inventory` database.
     * - Log the update action in the `movements` database with the username, item code, and timestamp.
     * - Display success or error messages to the user as appropriate.
     *
     * Database Tables Involved:
     * - `Inventory`: Stores the "ReOrder Trigger" value for items.
     * - `movements`: Logs updates to the inventory for auditing purposes.
     *
     * Error Handling:
     * - Displays error messages for invalid input, non-existent item codes, or database access issues.
     * - Ensures robust handling of exceptions to prevent crashes.
     *
     * Security:
     * - Access restricted to admin users.
     * - Parameterized queries are used to prevent SQL injection attacks.
     */

    public void adjustReOrderTrigger(User user, Connection connection) throws SQLException {

        boolean adminRights = user.hasAdminRights();

        if (adminRights) {
            String itemCode = JOptionPane.showInputDialog("Please enter the item code you would like to update: ");

            if (itemCode == null) {
                return;

            }

            if (itemCode.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Reference number cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
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
                        JOptionPane.showMessageDialog(null, "Item not found.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    HashMap<String, Integer> updatedReOrderMap = new HashMap<>();

                    for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                        CountDownLatch latch = new CountDownLatch(1);

                        String oldValue = entry.getKey();
                        int newValue = entry.getValue();
                        String formattedText = String.format("%s - Current Trigger Value: %d           Updated:", oldValue, newValue);


                        JFrame frame = new JFrame(itemCode);
                        frame.setSize(350, 115);
                        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                        frame.setLayout(null);

                        JLabel amountLabel = new JLabel(formattedText);
                        amountLabel.setBounds(10, 10, 250, 25);
                        frame.add(amountLabel);

                        JTextField newTriggerValue = new JTextField(20);
                        newTriggerValue.setBounds(250, 10, 80, 25);
                        frame.add(newTriggerValue);

                        JButton submitButton = new JButton("Submit");
                        submitButton.setBounds(250, 40, 80, 25);
                        frame.add(submitButton);

                        submitButton.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                String newReOrderValue = newTriggerValue.getText();

                                try {
                                    int newTotal = Integer.parseInt(newReOrderValue);

                                    if (newTotal < 0) {
                                        JOptionPane.showMessageDialog(frame, "Invalid input. Re Order amount must be a positive number.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                        return;
                                    }

                                    updatedReOrderMap.put(itemCode, newTotal);

                                    frame.dispose();
                                    latch.countDown();

                                } catch (NumberFormatException ex) {
                                    JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid integer.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                }
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
                            preparedStatement.setInt(1, newTrigger); // Subtract amountReceived
                            preparedStatement.setString(2, item); // Match reference number
                            preparedStatement.executeUpdate();

                        }

                        String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) " +
                                "VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement movementStatement = connection.prepareStatement(updateMovements)) {
                            movementStatement.setString(1, item);
                            movementStatement.setString(2, " ");
                            movementStatement.setString(3, "UPDATED TRIGGER");
                            movementStatement.setString(4, user.getUsername());
                            movementStatement.setString(5, new dateTime().formattedDateTime());
                            movementStatement.executeUpdate();
                        }


                        JOptionPane.showMessageDialog(null, "Update Successful");
                    }

                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        else {
            JOptionPane.showMessageDialog(null, "You do not have access to this feature");
        }
    }



    /**
     * Processes the write-off of stock items in the inventory, restricted to admin users.
     * <p>
     * This method allows an admin user to write off a specified quantity of stock for an inventory item.
     * The method verifies user permissions, retrieves the current stock level from the `Inventory` database,
     * and displays a graphical interface for entering the quantity to write off. The write-off amount is validated
     * to ensure it is a non-negative integer. The stock, written-off quantity, and profit are updated in the
     * database accordingly, and the action is logged in the `movements` table.
     *
     * @param user       The current user, whose admin rights are verified and username logged in the `movements` table.
     * @param connection A valid database connection used to interact with the `Inventory` and `movements` tables.
     * @throws SQLException if a database access error occurs
     *
     * Workflow:
     * - Validate that the user has admin rights.
     * - Prompt the user to input an item code and fetch its current stock level.
     * - Display the current stock level in a GUI and allow the user to input the write-off quantity.
     * - Validate the write-off quantity (must be a non-negative integer).
     * - Update the `Written Off`, `Profit`, and `Stock` fields in the `Inventory` database for the specified item.
     * - Log the write-off action in the `movements` database with the username, item code, and timestamp.
     * - Provide success or error messages to the user as appropriate.
     *
     * Database Tables Involved:
     * - `Inventory`: Updates the stock, written-off quantity, and profit for the specified item.
     * - `movements`: Logs the write-off operation for auditing and tracking purposes.
     *
     * Error Handling:
     * - Displays error messages for invalid inputs, non-existent item codes, or database access issues.
     * - Ensures robust handling of exceptions to prevent crashes or data inconsistencies.
     *
     * Security:
     * - Access restricted to admin users.
     * - Parameterized queries are used to prevent SQL injection attacks.
     *
     * GUI:
     * - A JFrame interface is used to display the current stock level and accept the write-off quantity.
     * - Contains a text field for input and a button to submit the new value.
     * - Uses a CountDownLatch to ensure the GUI interaction completes before proceeding with updates.
     */
    public void writeOffStock(User user, Connection connection) throws SQLException {

        boolean adminRights = user.hasAdminRights();

        if (adminRights) {
            String itemCode = JOptionPane.showInputDialog("Please enter the item code you would like to write off: ");

            if (itemCode == null) {
                return;

            }

            if (itemCode.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Reference number cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
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
                        JOptionPane.showMessageDialog(null, "Item not found.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    HashMap<String, Integer> writeOffMap = new HashMap<>();

                    for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                        CountDownLatch latch = new CountDownLatch(1);

                        String stockCount = entry.getKey();
                        int writeOffAmount = entry.getValue();
                        String formattedText = String.format("%s - Current Stock: %d        Write Off:", stockCount, writeOffAmount);


                        JFrame frame = new JFrame(itemCode);
                        frame.setSize(350, 115);
                        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                        frame.setLayout(null);

                        JLabel currentAmount = new JLabel(formattedText);
                        currentAmount.setBounds(10, 10, 250, 25);
                        frame.add(currentAmount);

                        JTextField writeOff = new JTextField(20);
                        writeOff.setBounds(250, 10, 80, 25);
                        frame.add(writeOff);

                        JButton submitButton = new JButton("Submit");
                        submitButton.setBounds(250, 40, 80, 25);
                        frame.add(submitButton);

                        submitButton.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                String writeOffTotal = writeOff.getText();

                                try {
                                    int newTotal = Integer.parseInt(writeOffTotal);

                                    if (newTotal < 0) {
                                        JOptionPane.showMessageDialog(frame, "Invalid input. Write off amount must be a positive number.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                        return;
                                    }

                                    writeOffMap.put(itemCode, newTotal);

                                    frame.dispose();
                                    latch.countDown();

                                } catch (NumberFormatException ex) {
                                    JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid integer.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                }
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


                        String newWriteOff = "UPDATE Inventory SET `Written Off` = `Written Off` + ? WHERE `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(newWriteOff)) {
                            preparedStatement.setInt(1, amountToWriteOff); // Subtract amountReceived
                            preparedStatement.setString(2, item); // Match reference number
                            preparedStatement.executeUpdate();

                        }

                        String newProfit = "UPDATE Inventory SET Profit = Profit - `Purchase Price` * ? WHERE `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(newProfit)) {
                            preparedStatement.setInt(1, amountToWriteOff); // Match reference number
                            preparedStatement.setString(2, item); // Match reference number
                            preparedStatement.executeUpdate();

                        }

                        String newStockCount = "UPDATE Inventory SET Stock = Stock - ? WHERE `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(newStockCount)) {
                            preparedStatement.setInt(1, amountToWriteOff); // Subtract amountReceived
                            preparedStatement.setString(2, item); // Match reference number
                            preparedStatement.executeUpdate();

                        }

                        String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) " +
                                "VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement movementStatement = connection.prepareStatement(updateMovements)) {
                            movementStatement.setString(1, item);
                            movementStatement.setString(2, String.valueOf(amountToWriteOff));
                            movementStatement.setString(3, "WRITE OFF");
                            movementStatement.setString(4, user.getUsername());
                            movementStatement.setString(5, new dateTime().formattedDateTime());
                            movementStatement.executeUpdate();
                        }


                        JOptionPane.showMessageDialog(null, "Update Successful");
                    }

                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        else {
            JOptionPane.showMessageDialog(null, "You do not have access to this feature");
        }
    }


    /**
     * Generates a sale transaction, allowing a user to select items, specify quantities, and update the database.
     * <p>
     * This method enables the creation of a sale order by prompting the user to input item codes and quantities
     * for the sale. It validates stock availability and records the sale details in the `sales` table, adjusts
     * inventory levels, and logs the transaction in the `movements` table. A unique reference number is generated
     * for each sale transaction.
     *
     * @param user       The current user, whose username is logged as the originator of the transaction.
     * @param connection A valid database connection used to interact with the `inventory`, `sales`, and `movements` tables.
     *
     * Workflow:
     * - Generate a unique reference number for the sale.
     * - Prompt the user to input item codes and validate stock availability.
     * - Allow the user to input quantities for the sale, ensuring they do not exceed available stock.
     * - Retrieve item details such as name, purchase price, and sale price for each item.
     * - Record the sale details in the `sales` table, including the reference number and total price.
     * - Update the `inventory` table to adjust stock levels, amount sold, and profit.
     * - Log the sale transaction in the `movements` table with the item, amount, and timestamp.
     * - Display a confirmation message with the reference number upon successful completion.
     *
     * Database Tables Involved:
     * - `inventory`: Stores item stock levels, amount sold, and profit.
     * - `sales`: Records details of the sale, including item code, name, quantity, total price, reference number, user, and date.
     * - `movements`: Logs the sale for tracking and auditing purposes.
     *
     * Error Handling:
     * - Ensures all database operations are performed using parameterized queries to prevent SQL injection.
     * - Displays error messages for invalid inputs, stock unavailability, or database access issues.
     * - Gracefully handles interruptions or exceptions to prevent data inconsistencies.
     *
     * Security:
     * - Uses parameterized queries for all database interactions.
     * - Ensures proper handling of database resources with try-with-resources blocks.
     *
     * GUI:
     * - Uses dialog boxes (via `JOptionPane`) to interact with the user for item codes, quantities, and confirmation.
     * - Displays error messages for invalid inputs and successful messages for completed transactions.
     *
     * Note:
     * - This method assumes the `dateTime` class provides a `formattedDateTime` method that returns the current timestamp.
     * - Ensure the database connection is properly established before invoking this method.
     */
    public void generateSale(User user, Connection connection) {

        dateTime formattedDateTimeInstance = new dateTime();
        String formattedDateTime = formattedDateTimeInstance.formattedDateTime();

            String referenceNumber = UUID.randomUUID().toString().substring(0, 8).toUpperCase();;

            HashMap<String, Integer> saleItemMap = new HashMap<>();

            while (true) {
                boolean inStock = true;
                int availableStock = 0;

                String itemCode = JOptionPane.showInputDialog("Enter the item code:");

                if (itemCode == null) {
                    break;
                }

                String query = "SELECT `Stock` FROM inventory WHERE `Item Code` = ?";
                try (PreparedStatement checkForDuplicate = connection.prepareStatement(query)) {
                    checkForDuplicate.setString(1, itemCode);
                    ResultSet rs = checkForDuplicate.executeQuery();

                    try {
                        if (rs.next()) {
                            availableStock = rs.getInt("Stock");
                            if (availableStock <= 0) {
                                JOptionPane.showMessageDialog(null, "Item is out of stock", "Out of Stock", JOptionPane.WARNING_MESSAGE);
                                inStock = false;
                            }
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }

                if (inStock) {
                    // Prompt for quantity
                    String quantityStr = JOptionPane.showInputDialog("Enter the quantity:");
                    if (quantityStr == null) {
                        break;
                    }

                    int quantity;
                    try {
                        quantity = Integer.parseInt(quantityStr);
                        if (quantity <= 0) {
                            JOptionPane.showMessageDialog(null, "Please enter a valid positive quantity.");
                            continue;
                        }

                        if (quantity > availableStock) {
                            JOptionPane.showMessageDialog(null, "Do not have enough stock");
                            continue;
                        }

                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(null, "Invalid quantity. Please enter a number.");
                        continue;
                    }

                    // Add item and quantity to the map
                    saleItemMap.put(itemCode, quantity);


                    // Ask if the user wants to add another item
                    int response = JOptionPane.showConfirmDialog(null, "Would you like to add another item?", "Add Another Item", JOptionPane.YES_NO_OPTION);
                    if (response == JOptionPane.NO_OPTION) {
                        break;
                    }
                }
            }


            for (String itemCode : saleItemMap.keySet()) {
                int amount = saleItemMap.get(itemCode);


                double purchasePrice = 0.0;
                double salePrice = 0.0;
                String itemName = "";

                // Fetch Item Name, Purchase Price, and Sale Price in one query
                String fetchItemDetailsQuery = "SELECT `Item Name`, `Purchase Price`, `Sale Price` FROM inventory WHERE `Item Code` = ?";
                try (PreparedStatement fetchItemDetailsStmt = connection.prepareStatement(fetchItemDetailsQuery)) {
                    fetchItemDetailsStmt.setString(1, itemCode);
                    ResultSet rs = fetchItemDetailsStmt.executeQuery();
                    if (rs.next()) {
                        itemName = rs.getString("Item Name");
                        purchasePrice = rs.getDouble("Purchase Price");
                        salePrice = rs.getDouble("Sale Price");
                    } else {
                        JOptionPane.showMessageDialog(null, "Item not found in inventory.", "Error", JOptionPane.ERROR_MESSAGE);
                        continue;
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(null, "Database error when fetching item details: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }


                try {
                    String insertPendingOrderQuery = "INSERT INTO sales (`Item Code`, `Item Name`, `Amount`, `Total Price`, `Reference`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement insertPendingOrderStmt = connection.prepareStatement(insertPendingOrderQuery)) {
                        insertPendingOrderStmt.setString(1, itemCode);
                        insertPendingOrderStmt.setString(2, itemName);
                        insertPendingOrderStmt.setInt(3, amount);
                        insertPendingOrderStmt.setDouble(4, salePrice * amount);
                        insertPendingOrderStmt.setString(5, referenceNumber);
                        insertPendingOrderStmt.setString(6, user.getUsername());
                        insertPendingOrderStmt.setString(7, formattedDateTime);
                        insertPendingOrderStmt.executeUpdate();
                    }

                    String updateInventoryQuery = "UPDATE inventory SET `Stock` = `Stock` - ? WHERE `Item Code` = ?";
                    try (PreparedStatement updateInventoryStmt = connection.prepareStatement(updateInventoryQuery)) {
                        updateInventoryStmt.setInt(1, amount);
                        updateInventoryStmt.setString(2, itemCode);
                        updateInventoryStmt.executeUpdate();
                    }

                    String updateAmountSoldQuery = "UPDATE inventory SET `Amount Sold` = `Amount Sold` + ? WHERE `Item Code` = ?";
                    try (PreparedStatement updateInventoryStmt = connection.prepareStatement(updateAmountSoldQuery)) {
                        updateInventoryStmt.setInt(1, amount);
                        updateInventoryStmt.setString(2, itemCode);
                        updateInventoryStmt.executeUpdate();
                    }

                    String updateProfitQuery = "UPDATE inventory SET `Profit` = Profit + ? WHERE `Item Code` = ?";
                    try (PreparedStatement updateInventoryStmt = connection.prepareStatement(updateProfitQuery)) {
                        updateInventoryStmt.setDouble(1, (salePrice - purchasePrice) * amount);
                        updateInventoryStmt.setString(2, itemCode);
                        updateInventoryStmt.executeUpdate();
                    }

                    String insertMovementQuery = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement insertMovementStmt = connection.prepareStatement(insertMovementQuery)) {
                        insertMovementStmt.setString(1, itemCode);
                        insertMovementStmt.setInt(2, amount);
                        insertMovementStmt.setString(3, "SALE");
                        insertMovementStmt.setString(4, user.getUsername());
                        insertMovementStmt.setString(5, formattedDateTime);
                        insertMovementStmt.executeUpdate();
                    }

                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(null, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            JOptionPane.showMessageDialog(null, "Purchase order created successfully with reference number: " + referenceNumber);

    }



    /**
     * Displays a table of sales transactions with filtering options for each column.
     * <p>
     * This method fetches sales transaction data from the `sales` database table, joins it with the `inventory`
     * table to retrieve item names, and displays the data in a graphical interface. Users can filter the table
     * data dynamically using text fields for each column.
     *
     * @param connection A valid database connection used to retrieve data from the `sales` and `inventory` tables.
     * @throws SQLException if a database access error occurs
     *
     * Workflow:
     * - Create a modal dialog to display sales transactions in a table.
     * - Set up column headers and a table model to hold the data.
     * - Add a row sorter for filtering and create text fields for filtering each column.
     * - Execute an SQL query to fetch sales transaction data and populate the table.
     * - Display the dialog with the table and filtering options.
     *
     * Database Tables Involved:
     * - `sales`: Provides transaction details such as item code, amount, total price, reference, user, and date.
     * - `inventory`: Used to fetch item names corresponding to item codes in the `sales` table.
     *
     * GUI:
     * - Displays the sales transactions in a JTable within a modal JDialog.
     * - Includes a filter panel with text fields for each column, allowing dynamic filtering of rows.
     * - Supports case-insensitive filtering for user convenience.
     *
     * Error Handling:
     * - If a database error occurs during data retrieval, an error message is displayed, and the dialog closes.
     * - Ensures robust handling of SQL exceptions with try-with-resources for database queries.
     *
     * Security:
     * - Uses parameterized queries to prevent SQL injection (for future extensions involving user-provided input).
     *
     * Notes:
     * - The dialog is modal, ensuring the user must close it before returning to the main application.
     * - The method assumes the database schema aligns with the SQL query provided.
     * - This method is read-only and does not modify the database.
     */
    public void viewSalesTransactions(Connection connection) throws SQLException {
        JDialog dialog = new JDialog((JFrame) null, "Sales Transactions", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(800, 400);
        dialog.setLocationRelativeTo(null);

        // Column headings
        String[] columnNames = {"Item Code", "Item Name", "Amount", "Total Price", "Reference", "User", "Date"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);

        // Add a row sorter for filtering
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Create a filter panel
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

        // Query to fetch sales data
        String query = "SELECT sales.`Item Code`, " +
                "COALESCE(inventory.`Item Name`, 'N/A') AS `Item Name`, " +
                "sales.`Amount`, " +
                "sales.`Total Price`, " +
                "sales.`Reference`, " +
                "sales.`User`, " +
                "sales.`Date` " +
                "FROM sales " +
                "LEFT JOIN inventory ON sales.`Item Code` = inventory.`Item Code`";

        // Fetch and populate table data
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
                        rs.getString("Date")
                };
                tableModel.addRow(rowData);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error loading sales transactions: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            dialog.dispose();
            return;
        }

        // Add components to the dialog
        dialog.setLayout(new BorderLayout());
        dialog.add(filterPanel, BorderLayout.SOUTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }


    /**
     * Processes the return of items from a sales order and updates the inventory and sales records accordingly.
     * <p>
     * This method allows a user to return items associated with a specific sales reference number. The user is prompted
     * to input the sales reference number, and the method retrieves the relevant sales data. A graphical interface
     * is used to specify the quantity of each item being returned. The database is then updated to reflect the returned
     * items, including adjustments to sales records, inventory stock, and profit.
     *
     * @param user       The current user, whose username is logged in the `movements` table as the action's originator.
     * @param connection A valid database connection used to interact with the `sales`, `inventory`, and `movements` tables.
     * @throws SQLException if a database access error occurs
     *
     * Workflow:
     * - Prompt the user to input a sales reference number.
     * - Fetch the associated items and quantities from the `sales` table.
     * - Display a graphical interface for each item, allowing the user to input the quantity being returned.
     * - Validate the returned quantity to ensure it does not exceed the original sold amount.
     * - Update the `sales` table to reflect the reduced amount for each returned item.
     * - Adjust the `inventory` table to increase stock and decrement the `Amount Sold` and `Profit` values.
     * - Log the return operation in the `movements` table with the item code, returned quantity, and user details.
     * - Remove any completed sales records (where the amount sold becomes zero).
     *
     * Database Tables Involved:
     * - `sales`: Updates the `Amount` field to reflect the returned quantity and deletes completed orders.
     * - `inventory`: Updates stock, amount sold, and profit for returned items.
     * - `movements`: Logs the return transaction for tracking and auditing purposes.
     *
     * GUI:
     * - Displays a JFrame for each item, showing the current amount sold and allowing the user to input the return quantity.
     * - Includes input validation to prevent invalid or excessive return amounts.
     *
     * Error Handling:
     * - Displays error messages for invalid inputs, missing reference numbers, or database access issues.
     * - Ensures robust handling of SQL exceptions with try-with-resources for all database queries.
     *
     * Security:
     * - Uses parameterized queries to prevent SQL injection attacks.
     *
     * Notes:
     * - The method assumes the `dateTime` class provides a `formattedDateTime` method for generating timestamps.
     * - The method ensures the database remains consistent even in the event of partial failures.
     * - Users cannot return more items than were originally sold for the given reference number.
     */

    public void returnOrder(User user, Connection connection) throws SQLException {
        String referenceNumber = JOptionPane.showInputDialog("Enter transaction number: ");

        if (referenceNumber == null) {
            return;

        }

        if (referenceNumber.isEmpty()) {
            JOptionPane.showMessageDialog(null, "transaction number cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
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
                    JOptionPane.showMessageDialog(null, "No orders found for the given reference number.", "No Results", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                HashMap<String, Integer> receivedMap = new HashMap<>();

                for (Map.Entry<String, Integer> entry : resultMap.entrySet()) {
                    CountDownLatch latch = new CountDownLatch(1);

                    String codeOrdered = entry.getKey();
                    int amountOrdered = entry.getValue();
                    String formattedString = String.format("%s - Amount on Order: %d           returning:", codeOrdered, amountOrdered);


                    JFrame frame = new JFrame(codeOrdered);
                    frame.setSize(350, 115);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.setLayout(null);

                    JLabel amountLabel = new JLabel(formattedString);
                    amountLabel.setBounds(10, 10, 250, 25);
                    frame.add(amountLabel);

                    JTextField amountReceived = new JTextField(20);
                    amountReceived.setBounds(250, 10, 80, 25);
                    frame.add(amountReceived);

                    JButton submitButton = new JButton("Submit");
                    submitButton.setBounds(250, 40, 80, 25);
                    frame.add(submitButton);

                    submitButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            String receivedAmountString = amountReceived.getText();

                            try {
                                int returnedAmount = Integer.parseInt(receivedAmountString);


                                if (amountOrdered - returnedAmount < 0) {
                                    JOptionPane.showMessageDialog(frame, "Invalid input. returned amount cannot exceed the amount ordered.", "Input Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }


                                receivedMap.put(codeOrdered, returnedAmount);

                                frame.dispose();
                                latch.countDown();

                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid integer.", "Input Error", JOptionPane.ERROR_MESSAGE);
                            }
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

                    double purchasePrice = 0.0;
                    double salePrice = 0.0;

                    // Fetch Item Name, Purchase Price, and Sale Price in one query
                    String fetchItemDetailsQuery = "SELECT `Item Name`, `Purchase Price`, `Sale Price` FROM inventory WHERE `Item Code` = ?";
                    try (PreparedStatement fetchItemDetailsStmt = connection.prepareStatement(fetchItemDetailsQuery)) {
                        fetchItemDetailsStmt.setString(1, codeReceived);
                        ResultSet rs = fetchItemDetailsStmt.executeQuery();
                        if (rs.next()) {
                            purchasePrice = rs.getDouble("Purchase Price");
                            salePrice = rs.getDouble("Sale Price");
                        } else {
                            JOptionPane.showMessageDialog(null, "Item not found in inventory.", "Error", JOptionPane.ERROR_MESSAGE);
                            continue;
                        }
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(null, "Database error when fetching item details: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    if (amountReceived > 0) {
                        String updateReceivingLog = "UPDATE sales SET Amount = Amount - ? WHERE `Reference` = ? AND `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(updateReceivingLog)) {
                            preparedStatement.setInt(1, amountReceived); // Subtract amountReceived
                            preparedStatement.setString(2, referenceNumber); // Match reference number
                            preparedStatement.setString(3, codeReceived); // Match item code
                            preparedStatement.executeUpdate();
                        }

                        String updateOnOrder = "UPDATE Inventory SET `Amount Sold` = `Amount Sold` - ? WHERE `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnOrder)) {
                            preparedStatement.setInt(1, amountReceived); // Subtract amountReceived
                            preparedStatement.setString(2, codeReceived); // Match reference number
                            preparedStatement.executeUpdate();

                        }

                        String updateOnDock = "UPDATE Inventory SET `Stock` = `Stock` + ? WHERE `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(updateOnDock)) {
                            preparedStatement.setInt(1, amountReceived); // Subtract amountReceived
                            preparedStatement.setString(2, codeReceived); // Match reference number
                            preparedStatement.executeUpdate();

                        }

                        String updateProfit = "UPDATE Inventory SET `Profit` = `Profit` - ? WHERE `Item Code` = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(updateProfit)) {
                            preparedStatement.setDouble(1, (salePrice - purchasePrice) * amountReceived); // Subtract amountReceived
                            preparedStatement.setString(2, codeReceived); // Match reference number
                            preparedStatement.executeUpdate();

                        }

                        String updateMovements = "INSERT INTO movements (`Item`, `Amount`, `Type`, `User`, `Date`) " +
                                "VALUES (?, ?, ?, ?, ?)";
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
                        deleteStatement.setString(1, referenceNumber); // Match reference number
                        deleteStatement.setString(2, codeReceived); // Match item code
                        deleteStatement.executeUpdate();
                    }
                }

                JOptionPane.showMessageDialog(null, "Items Received!");
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

