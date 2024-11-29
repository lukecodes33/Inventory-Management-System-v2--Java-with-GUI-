import javax.swing.*;
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
        // Create a panel to hold the message and title
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Use vertical box layout

        // Create and center the title label
        JLabel titleLabel = new JLabel("Inventory Management System");
        titleLabel.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f)); // Optionally, increase font size

        // Create and center the message label
        JLabel messageLabel = new JLabel("User: " + user.getUsername());
        messageLabel.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        // Add the labels to the panel
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(10)); // Add some space between title and message
        panel.add(messageLabel);

        // Create options for the dialog
        String[] options = {
                "Add Item",
                "View Items",
                "Create Purchase Order",
                "View Pending Orders",
                "Receive Order",
                "Put-away Stock",
                "Low Stock Check",
                "Change Re Order Triggers",
                "Write Off Stock",
                "Process Sale",
                "View Transaction",
                "Return Item",
                "Reset Password",
                "Log Out"
        };

        // Show option dialog with buttons
        int choice = JOptionPane.showOptionDialog(
                null,
                panel, // Use the custom panel instead of a simple string
                null, // No custom title for the dialog, the title will be set by JOptionPane
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0] // Default selection
        );

        return choice + 1; // Return 1-based index (Cancel will return 0)
    }

}
