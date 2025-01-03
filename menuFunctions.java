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
                    JOptionPane.showMessageDialog(null, "No orders found for the given item code", "No Results", JOptionPane.INFORMATION_MESSAGE);
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

}

