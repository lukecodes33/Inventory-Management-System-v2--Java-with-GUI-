import javax.swing.*;

public class postLogin {

    static void mainMenu(User user) {
        System.out.println("Welcome, " + user.getUsername() + "!");

        // Loop to keep showing the menu until the user decides to exit
        while (true) {
            int choice = showMainMenu(user);

            // If the user pressed cancel or closed the dialog, exit the loop
            if (choice == 0) { // 0 indicates the dialog was closed or Cancel was pressed
                System.out.println("Exiting the menu.");
                break;
            }

            // Process the user's choice
            switch (choice) {



                case 1:
                    System.out.println("You selected Item Management.");
                    // Add logic for Item Management
                    int itemManagementChoice = showItemManagementMenu(user);
                    switch (itemManagementChoice) {

                        case 1:
                            System.out.println("You selected Item Management.");
                            // Add logic for Item Management
                            break;
                        case 2:
                            System.out.println("You selected Stock Order Management.");
                            // Add logic for Stock Order Management
                            break;
                        case 3:
                            System.out.println("You selected Inventory Management.");
                            // Add logic for Inventory Management
                            break;
                        case 4:
                            System.out.println("You selected Sales Management.");
                            // Add logic for Sales Management
                            break;
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                    break;



                case 2:
                    System.out.println("You selected Stock Order Management.");
                    // Add logic for Stock Order Management
                    int stockOrderChoice = showStockOrderMenu(user);
                    switch (stockOrderChoice) {

                        case 1:
                            System.out.println("You selected Item Management.");
                            // Add logic for Item Management
                            break;
                        case 2:
                            System.out.println("You selected Stock Order Management.");
                            // Add logic for Stock Order Management
                            break;
                        case 3:
                            System.out.println("You selected Inventory Management.");
                            // Add logic for Inventory Management
                            break;
                        case 4:
                            System.out.println("You selected Sales Management.");
                            // Add logic for Sales Management
                            break;
                        case 5:
                            System.out.println("You selected s Management.");
                            // Add logic for Sales Management
                            break;
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                    break;






                case 3:
                    System.out.println("You selected Inventory Management.");
                    // Add logic for Inventory Management
                    int inventoryManagementChoice = showInventoryManagementMenu(user);
                    switch (inventoryManagementChoice) {

                        case 1:
                            System.out.println("You selected Item Management.");
                            // Add logic for Item Management
                            break;
                        case 2:
                            System.out.println("You selected Stock Order Management.");
                            // Add logic for Stock Order Management
                            break;
                        case 3:
                            System.out.println("You selected Inventory Management.");
                            // Add logic for Inventory Management
                            break;
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                    break;





                case 4:
                    System.out.println("You selected Sales Management.");
                    // Add logic for Sales Management
                    int salesManagementChoice = showSalesManagement(user);
                    switch (salesManagementChoice) {

                        case 1:
                            System.out.println("You selected Item Management.");
                            // Add logic for Item Management
                            break;
                        case 2:
                            System.out.println("You selected Stock Order Management.");
                            // Add logic for Stock Order Management
                            break;
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                    break;







                default:
                    System.out.println("Invalid choice. Please try again.");
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
                "Item Management",
                "Stock Order Management",
                "Inventory Management",
                "Sales Management"
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

    private static int showItemManagementMenu(User user) {
        // Create a panel to hold the message and title
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Use vertical box layout

        // Create and center the title label
        JLabel titleLabel = new JLabel("Item Management");
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
                "Remove Item",
                "Search Item By Code",
                "Search Item By Name",
                "Search All Items"
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

    private static int showStockOrderMenu(User user) {
        // Create a panel to hold the message and title
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Use vertical box layout

        // Create and center the title label
        JLabel titleLabel = new JLabel("Stock Order Management");
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
                "Create Pending Order",
                "View Pending Orders",
                "Receive Order",
                "Cancel Order",
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

    private static int showInventoryManagementMenu(User user) {
        // Create a panel to hold the message and title
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Use vertical box layout

        // Create and center the title label
        JLabel titleLabel = new JLabel("Inventory Management");
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
                "Low Stock Check",
                "Reset Re Order Amounts",
                "Write Off Stock",
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

    private static int showSalesManagement(User user) {
        // Create a panel to hold the message and title
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Use vertical box layout

        // Create and center the title label
        JLabel titleLabel = new JLabel("Sales Management");
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
                "Process Sale",
                "View Transaction",
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
