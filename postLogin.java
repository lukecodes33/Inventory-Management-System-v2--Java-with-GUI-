import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class postLogin {

    static void mainMenu(User user) throws SQLException {

        String inventoryManagementPath = "database/inventoryManagementDatabase.db";
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + inventoryManagementPath)) {

            while (true) {

                int choice = showMainMenu(user);

                if (choice == 0) {
                    break;
                }

                switch (choice) {

                    case 1:

                    if (user.hasAdminRights()) {
                        menuFunctions addItem = new menuFunctions();
                        addItem.addItem(user, connection);
                        break;
                    }

                    else {
                        menuFunctions searchItems = new menuFunctions();
                        searchItems.viewAllItems(connection);
                        break;
                    }

                    case 2:

                    if (user.hasAdminRights()) {
                        menuFunctions searchItems = new menuFunctions();
                        searchItems.viewAllItems(connection);
                        break;
                    }

                    else {
                        menuFunctions showPendingOrders = new menuFunctions();
                        showPendingOrders.displayPendingOrders(connection);
                        break;
                    }

                    case 3:

                    if (user.hasAdminRights()) {
                        menuFunctions newPurchaseOrder = new menuFunctions();
                        newPurchaseOrder.newPurchaseOrder(user, connection);
                        break;
                    }

                    else {
                        menuFunctions receiveOrder = new menuFunctions();
                        receiveOrder.receiveOrder(user, connection);
                        break;
                    }

                    case 4:
                    
                    if (user.hasAdminRights()) {
                        menuFunctions showPendingOrders = new menuFunctions();
                        showPendingOrders.displayPendingOrders(connection);
                        break;
                    }

                    else {
                        menuFunctions putAwayStock = new menuFunctions();
                        putAwayStock.putAwayStock(user, connection);
                        break;
                    }

                    case 5:
                    if (user.hasAdminRights()) {
                        menuFunctions receiveOrder = new menuFunctions();
                        receiveOrder.receiveOrder(user, connection);
                        break;
                    }

                    else {
                        menuFunctions lowStockCheck = new menuFunctions();
                        lowStockCheck.lowStockCheck(connection);
                        break;
                    }

                    case 6:
                    if (user.hasAdminRights()) {
                        menuFunctions putAwayStock = new menuFunctions();
                        putAwayStock.putAwayStock(user, connection);
                        break;
                    }

                    else {
                        menuFunctions generateSale = new menuFunctions();
                        generateSale.generateSale(user, connection);
                        break;
                    }

                    case 7:
                    if (user.hasAdminRights()) {
                        menuFunctions lowStockCheck = new menuFunctions();
                        lowStockCheck.lowStockCheck(connection);
                        break;
                    }

                    else {
                        menuFunctions viewSalesTransactions = new menuFunctions();
                        viewSalesTransactions.viewSalesTransactions(connection);
                        break;

                    }

                    case 8:
                    if (user.hasAdminRights()){
                        menuFunctions adjustReOrderTrigger = new menuFunctions();
                        adjustReOrderTrigger.adjustReOrderTrigger(user, connection);
                        break;
                    }
                    else {
                        menuFunctions returnOrder = new menuFunctions();
                        returnOrder.returnOrder(user, connection);
                        break;
                    }

                    case 9:
                    if (user.hasAdminRights()){
                        menuFunctions writeOffStock = new menuFunctions();
                        writeOffStock.writeOffStock(user, connection);
                        break;
                    }
                    else {
                        menuFunctions passwordReset = new menuFunctions();
                        passwordReset.resetPassword(user);
                        break;

                    }

                    case 10:
                    if (user.hasAdminRights()) {
                        menuFunctions generateSale = new menuFunctions();
                        generateSale.generateSale(user, connection);
                        break;
                    }

                    else {
                        JOptionPane.showMessageDialog(null, "Goodbye " + user.getUsername());
                        System.exit(0);
                        connection.close();
                    }

                    case 11:
                    if (user.hasAdminRights()) {
                        menuFunctions viewSalesTransactions = new menuFunctions();
                        viewSalesTransactions.viewSalesTransactions(connection);
                        break;
                    }

                    case 12:
                    if (user.hasAdminRights()) {
                        menuFunctions returnOrder = new menuFunctions();
                        returnOrder.returnOrder(user, connection);
                        break;
                    }


                    case 13:
                    if (user.hasAdminRights()) {
                        menuFunctions passwordReset = new menuFunctions();
                        passwordReset.resetPassword(user);
                        break;
                    }

                    case 14:

                    if (user.hasAdminRights()) {
                        menuFunctions backup = new menuFunctions();
                        backup.backUpDatabase();
                        break;
                    }

                    case 15:

                    if (user.hasAdminRights()) {
                        JOptionPane.showMessageDialog(null, "Goodbye " + user.getUsername());
                        System.exit(0);
                        connection.close();
                    }

                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            }
        }
    }

    private static int showMainMenu(User user) {
        // Create a panel with a vertical layout
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Create title label
        JLabel titleLabel = new JLabel("Inventory Management System", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));

        // User label
        JLabel messageLabel = new JLabel("User: " + user.getUsername(), SwingConstants.CENTER);

        // Centering labels
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add labels
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(10)); 
        panel.add(messageLabel);
        panel.add(Box.createVerticalStrut(10));

        // List created of menu items, will display one or the other depending on if user has admin rights or not.
        String[] adminOptions = {
            "Add Item", "View Items", "Create Purchase Order", "View Pending Orders",
            "Receive Order", "Put-away Stock", "Low Stock Check", "Change Re Order Triggers",
            "Write Off Stock", "Process Sale", "View Transaction", "Return Item",
            "Reset Password", "Create Local Backup", "Log Out"
        };

        String[] noAdminOptions = {
            "View Items", "View Pending Orders",
            "Receive Order", "Put-away Stock", "Low Stock Check",
            "Process Sale", "View Transaction", "Return Item",
            "Reset Password", "Log Out"
        };

        String[] options;
        if (user.hasAdminRights()) {
            options = adminOptions;
        } else {
            options = noAdminOptions;
        }


        // Panel to hold buttons in a grid layout (4 per row)
        JPanel buttonPanel = new JPanel(new GridLayout(0, 4, 10, 10)); // 4 columns, auto rows

        JButton[] buttons = new JButton[options.length];
        int[] choice = {-1}; // Store user's selection

        for (int i = 0; i < options.length; i++) {
            buttons[i] = new JButton(options[i]);
            final int index = i + 1; // Store 1-based index
            buttons[i].addActionListener(e -> {
                choice[0] = index;
                SwingUtilities.getWindowAncestor(panel).dispose(); // Close the dialog
            });
            buttonPanel.add(buttons[i]);
        }

        panel.add(buttonPanel);

        // Create a resizable dialog
        JDialog dialog = new JDialog((Frame) null, "Menu", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(panel);
        dialog.pack();
        dialog.setSize(600, 400); // Initial size
        dialog.setLocationRelativeTo(null); // Center on screen

        // Handle window close (X button)
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                choice[0] = 0; // Indicate cancellation (same as pressing "Cancel")
                dialog.dispose();
            }
        });

        dialog.setVisible(true);

        return choice[0]; // Return the user's selection (1-based index or 0 for exit)
    }
}
