import javax.swing.*;
import java.sql.*;
import java.util.Map;
import java.io.File;

/// TODO along the way:
///
/// addITem applciation closes on window close, currently have set to system exit on close but
/// would like it to just return to old window and remove back button
///
/// Add hashing and salting to password
/// add "Docked" column to item database,
// process will go Create Order - Recieve order (move to dock) - putaway (move to available stock)
///
///Converge all databases into one (excluding login and user)
///
public class Login {

    /**
     * Main method that handles the login process and updates the login history.
     */

    public static void main(String[] args) {
        String userDatabasePath = "database/userDatabase.db";
        String loginsDatabasePath = "database/loginsDatabase.db";

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

                    //Checks to see if a match is found, if there is then checks for their admin level
                    if (checkCredentials(userConnection, username, password)) {
                        isAdmin = checkAdminRights(userConnection, username);
                        JOptionPane.showMessageDialog(null, "Welcome " + username);

                        // Update relevant databases and create user object, parse user through application to postLogin
                        updateLastLogin(userConnection, username);
                        updateLoginHistory(loginsConnection, username);
                        databaseCheck();
                        User user = new User(username, isAdmin, password);

                        // Clear passwords from map and null value the string to be removed in rubbish removal
                        // purely to get password storage out of memory
                        password = null;
                        loginData.remove("password");
                        loginLoop = false;

                        boolean firstLogin = firstLogin(userConnection, username);

                        if (firstLogin) {
                            JOptionPane.showMessageDialog(null, "Password update is required");
                            menuFunctions resetPassword = new menuFunctions();
                            resetPassword.resetPassword(user);
                        }

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
                //admin rights are stored as 0 and 1 in the database. 1 = yes, 0 = no. Returns true or false on expression.
                int adminRights = results.getInt("admin_rights");
                return adminRights == 1;
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
     * @return True if the user is on first login, false otherwise.
     * @throws SQLException If an SQL error occurs while checking first login integer.
     */
    private static boolean firstLogin(Connection connection, String username) throws SQLException {
        String sql = "SELECT first_login FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            ResultSet results = preparedStatement.executeQuery();

            if (results.next()) {
                //first login is stored as 0 and 1 in the database. 1 = yes, 0 = no. Returns true or false on expression.
                int firstLogin = results.getInt("first_login");
                return firstLogin == 1;
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
     *
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

        String updateLoginsSql = "INSERT INTO Logins (Name, Time) VALUES (?, ?)";
        try (PreparedStatement preparedStatement = loginsConnection.prepareStatement(updateLoginsSql)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, formattedDateTime);
            preparedStatement.executeUpdate();
        }
    }

    /**
     * Checks if any of the required database files are missing.
     * If one or more database files (itemDatabase, movements, pendingOrdersDatabase, salesDatabase)
     * do not exist, a confirmation dialog is displayed to the user.
     *
     * The dialog informs the user that:
     * - If this is the first time using the program or if a file has been removed,
     *   all missing files will be recreated.
     * - If the user has not removed the files and this is not the first login,
     *   they should restore the database files from a backup.
     *
     * The user is given two options:
     * - "Create Files": If selected, the program will proceed to create the missing database files.
     * - "Cancel": If selected, the program will terminate.
     *
     * Note: This check helps ensure that the necessary database structure is present
     * for the application to function correctly.
     */

    private static void databaseCheck() {

        boolean itemDatabaseExists = false;
        boolean movementsExists = false;
        boolean pendingOrdersDatabaseExists = false;
        boolean salesDatabaseExists = false;


        String databaseFolderPath = "database/";
        File itemDatabaseFile = new File(databaseFolderPath, "itemDatabase.db");
        File movementsFile = new File(databaseFolderPath, "movements.db");
        File pendingOrdersDatabaseFile = new File(databaseFolderPath, "pendingOrdersDatabase.db");
        File salesDatabaseFile = new File(databaseFolderPath, "salesDatabase.db");

        // Updates booleans if database is found so program knows which to make and which exist.
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
                    null,
                    options,
                    options[0]);

            if (option == 1) {
                System.exit(0);
            }

            if (option == 0) {

                //Creates missing databases with set table and columns.
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