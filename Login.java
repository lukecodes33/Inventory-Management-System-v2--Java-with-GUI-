import javax.swing.*;
import java.sql.*;
import java.util.Map;
import java.io.File;

///TO DO
///
/// edit login and menus before implimenting functions
///

public class Login {

    /**
     * Main method that handles the login process and updates the login history.
     */

    public static void main(String[] args) {
        String userDatabasePath = "database/userDatabase.db";
        String loginsDatabasePath = "database/loginsDatabase.db"; // Path for the logins database

        try (Connection userConnection = DriverManager.getConnection("jdbc:sqlite:" + userDatabasePath);
             Connection loginsConnection = DriverManager.getConnection("jdbc:sqlite:" + loginsDatabasePath)) {

            boolean loginLoop = true;
            int attempts = 4;
            boolean isAdmin = false;

            while (attempts > 0 && loginLoop) {
                LoginPopUp loginPopUp = new LoginPopUp();
                Map<String, String> loginData = loginPopUp.createLoginPopUp();

                String username = loginData.get("username");
                String password = loginData.get("password");

                try {
                    // Check credentials
                    if (checkCredentials(userConnection, username, password)) {
                        isAdmin = checkAdminRights(userConnection, username); // Set the admin status based on check
                        JOptionPane.showMessageDialog(null, "Welcome Back " + username);

                        // Clear sensitive information
                        password = null; // Clear password variable
                        loginData.remove("password"); // Remove password from the Map
                        loginLoop = false; // Exit loop after successful login

                        // Update last login in user database
                        updateLastLogin(userConnection, username);

                        // Update Name and Time in logins database
                        updateLoginHistory(loginsConnection, username);

                        databaseCheck();

                        User user = new User(username, isAdmin);

                        // Go to main menu with user object, Login.java ends here
                        postLogin.mainMenu(user);

                    } else {
                        JOptionPane.showMessageDialog(null, "Invalid credentials. Attempts left: " + (attempts - 1));
                        attempts -= 1;
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "An unexpected error occurred. Please try again.");
                }
            }

            if (attempts == 0) {
                JOptionPane.showMessageDialog(null, "Max login attempts reached. Exiting...");
                System.exit(0);
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Unable to connect to the database. Please try again later.");
        }
    }

    /**
     * Method to check the user's credentials against the database.
     *
     * @param connection The connection to the user database.
     * @param username   The username entered by the user.
     * @param password   The password entered by the user.
     * @return True if the credentials are valid, false otherwise.
     * @throws SQLException If an SQL error occurs while checking credentials.
     */
    private static boolean checkCredentials(Connection connection, String username, String password) throws SQLException {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            ResultSet results = preparedStatement.executeQuery();

            if (results.next()) {
                String dbPassword = results.getString("password");
                return dbPassword.equals(password);
            } else {
                return false;
            }
        }
    }

    /**
     * Method to check if the user has admin rights.
     *
     * @param connection The connection to the user database.
     * @param username   The username of the user.
     * @return True if the user has admin rights, false otherwise.
     * @throws SQLException If an SQL error occurs while checking admin rights.
     */
    private static boolean checkAdminRights(Connection connection, String username) throws SQLException {
        String sql = "SELECT admin_rights FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            ResultSet results = preparedStatement.executeQuery();

            if (results.next()) {
                int adminRights = results.getInt("admin_rights"); // Assuming 'admin_rights' is stored as an integer (0 or 1)
                return adminRights == 1; // Return true if user has admin rights
            } else {
                return false;
            }
        }
    }

    /**
     * Method to update the last login time for the user in the user database.
     *
     * @param connection The connection to the user database.
     * @param username   The username of the user whose last login time is to be updated.
     * @throws SQLException If an SQL error occurs while updating the last login time.
     */
    private static void updateLastLogin(Connection connection, String username) throws SQLException {
        dateTime formattedDateTimeInstance = new dateTime();
        String formattedDateTime = formattedDateTimeInstance.formattedDateTime();

        String updateSql = "UPDATE users SET last_login = ? WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateSql)) {
            preparedStatement.setString(1, formattedDateTime);
            preparedStatement.setString(2, username);
            preparedStatement.executeUpdate();
        }
    }

    /**
     * Method to update the login history by adding the user's username and login time
     * to the logins database.
     *
     * @param loginsConnection The connection to the logins database.
     * @param username         The username of the user.
     * @throws SQLException If an SQL error occurs while updating the login history.
     */
    private static void updateLoginHistory(Connection loginsConnection, String username) throws SQLException {
        dateTime formattedDateTimeInstance = new dateTime();
        String formattedDateTime = formattedDateTimeInstance.formattedDateTime();

        String updateLoginsSql = "INSERT INTO Logins (Name, Time) VALUES (?, ?)"; // Assuming a table 'Logins'
        try (PreparedStatement preparedStatement = loginsConnection.prepareStatement(updateLoginsSql)) {
            preparedStatement.setString(1, username);  // Insert the username into the Name column
            preparedStatement.setString(2, formattedDateTime); // Insert the formatted DateTime into the Time column
            preparedStatement.executeUpdate();
        }
    }

    private static void databaseCheck() {
        // Booleans to store the existence of each database
        boolean itemDatabaseExists = false;
        boolean movementsExists = false;
        boolean pendingOrdersDatabaseExists = false;
        boolean salesDatabaseExists = false;

        // Define the paths to each file in the database folder
        String databaseFolderPath = "database/";

        File itemDatabaseFile = new File(databaseFolderPath, "itemDatabase.db");
        File movementsFile = new File(databaseFolderPath, "movements.db");
        File pendingOrdersDatabaseFile = new File(databaseFolderPath, "pendingOrdersDatabase.db");
        File salesDatabaseFile = new File(databaseFolderPath, "salesDatabase.db");

        // Update booleans based on file existence
        itemDatabaseExists = itemDatabaseFile.exists();
        movementsExists = movementsFile.exists();
        pendingOrdersDatabaseExists = pendingOrdersDatabaseFile.exists();
        salesDatabaseExists = salesDatabaseFile.exists();

        if (!itemDatabaseExists || !movementsExists || !pendingOrdersDatabaseExists || !salesDatabaseExists) {
            Object[] options = {"Create Files", "Cancel"};
            int option = JOptionPane.showOptionDialog(null,
                    "One or more database files are missing. If this is your first time using this program or you have removed a file, " +
                            "all missing files will be recreated. If this is not your first login and you have not removed them, restore database files from backup.",
                    "Missing Database Files",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null, // Icon
                    options, // Custom options
                    options[0]); // Default option

            if (option == 1) {
                System.exit(0);
            }

            if (option == 0) {

                if (!itemDatabaseExists) {
                    String url = "jdbc:sqlite:" + databaseFolderPath + "/itemDatabase.db";
                    String createTableSQL = """
                            CREATE TABLE IF NOT EXISTS Inventory (
                                "Item Code" TEXT,
                                "Item Name" TEXT,
                                "Stock" INTEGER,
                                "On Order" INTEGER,
                                "ReOrder Trigger" INTEGER,
                                "Purchase Price" REAL,
                                "Sale Price" REAL,
                                "Amount Sold" INTEGER,
                                "Profit" REAL
                                )""";

                    try (Connection conn = DriverManager.getConnection(url);
                         Statement stmt = conn.createStatement()) {
                        stmt.execute(createTableSQL);
                    } catch (SQLException e) {
                        JOptionPane.showMessageDialog(null, "There has been an error creating tables");
                        System.exit(0);
                    }

                }

                if (!movementsExists) {
                    String url = "jdbc:sqlite:" + databaseFolderPath + "/movements.db";
                    String createTableSQL = """
                            CREATE TABLE IF NOT EXISTS movements (
                                "Item" TEXT,
                                "Amount" INTEGER,
                                "Type" TEXT,
                                "User" TEXT,
                                "Date" TEXT
                                )""";

                    try (Connection conn = DriverManager.getConnection(url);
                         Statement stmt = conn.createStatement()) {
                        stmt.execute(createTableSQL);
                    } catch (SQLException e) {
                        JOptionPane.showMessageDialog(null, "There has been an error creating tables");
                        System.exit(0);
                    }
                }

                if (!pendingOrdersDatabaseExists) {
                    String url = "jdbc:sqlite:" + databaseFolderPath + "pendingOrdersDatabase.db";
                    String createTableSQL = """
                            CREATE TABLE IF NOT EXISTS pendingOrders (
                                "Item Code" TEXT,
                                "Amount" INTEGER,
                                "Reference" TEXT,
                                "User" TEXT,
                                "Date" TEXT
                                )""";

                    try (Connection conn = DriverManager.getConnection(url);
                         Statement stmt = conn.createStatement()) {
                        stmt.execute(createTableSQL);
                    } catch (SQLException e) {
                        JOptionPane.showMessageDialog(null, "There has been an error creating tables");
                        System.exit(0);
                    }
                }

                if (!salesDatabaseExists) {
                    String url = "jdbc:sqlite:" + databaseFolderPath + "salesDatabase.db";
                    String createTableSQL = """
                            CREATE TABLE IF NOT EXISTS sales (
                                "Item Code" TEXT,
                                "Amount" INTEGER,
                                "Total Price" INTEGER,
                                "Reference" TEXT,
                                "User" TEXT,
                                "Date" TEXT
                                )""";

                    try (Connection conn = DriverManager.getConnection(url);
                         Statement stmt = conn.createStatement()) {
                        stmt.execute(createTableSQL);
                    } catch (SQLException e) {
                        JOptionPane.showMessageDialog(null, "There has been an error creating tables");
                        System.exit(0);
                    }
                }
            }
        }
    }
}