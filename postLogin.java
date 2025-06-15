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

                        menuFunctions addItem = new menuFunctions();
                        addItem.addItem(user, connection);
                        break;

                    case 2:

                        menuFunctions searchItems = new menuFunctions();
                        searchItems.viewAllItems(connection);
                        break;

                    case 3:

                        menuFunctions newPurchaseOrder = new menuFunctions();
                        newPurchaseOrder.newPurchaseOrder(user, connection);
                        break;

                    case 4:

                        menuFunctions showPendingOrders = new menuFunctions();
                        showPendingOrders.displayPendingOrders(connection);
                        break;

                    case 5:

                        menuFunctions receiveOrder = new menuFunctions();
                        receiveOrder.receiveOrder(user, connection);
                        break;

                    case 6:

                        menuFunctions putAwayStock = new menuFunctions();
                        putAwayStock.putAwayStock(user, connection);
                        break;

                    case 7:

                        menuFunctions lowStockCheck = new menuFunctions();
                        lowStockCheck.lowStockCheck(connection);
                        break;

                    case 8:

                        menuFunctions adjustReOrderTrigger = new menuFunctions();
                        adjustReOrderTrigger.adjustReOrderTrigger(user, connection);
                        break;

                    case 9:

                        menuFunctions writeOffStock = new menuFunctions();
                        writeOffStock.writeOffStock(user, connection);
                        break;

                    case 10:

                        menuFunctions generateSale = new menuFunctions();
                        generateSale.generateSale(user, connection);
                        break;

                    case 11:

                        menuFunctions viewSalesTransactions = new menuFunctions();
                        viewSalesTransactions.viewSalesTransactions(connection);
                        break;

                    case 12:

                        menuFunctions returnOrder = new menuFunctions();
                        returnOrder.returnOrder(user, connection);
                        break;


                    case 13:
                        menuFunctions passwordReset = new menuFunctions();
                        passwordReset.resetPassword(user);
                        break;

                    case 14:
                        JOptionPane.showMessageDialog(null, "Goodbye " + user.getUsername());
                        System.exit(0);
                        connection.close();

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
        panel.add(Box.createVerticalStrut(10)); // Add spacing
        panel.add(messageLabel);
        panel.add(Box.createVerticalStrut(10));

        // Button options
        String[] options = {
                "Add Item", "View Items", "Create Purchase Order", "View Pending Orders",
                "Receive Order", "Put-away Stock", "Low Stock Check", "Change Re Order Triggers",
                "Write Off Stock", "Process Sale", "View Transaction", "Return Item",
                "Reset Password", "Log Out"
        };

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
