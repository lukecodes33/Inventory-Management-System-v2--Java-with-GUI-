import javax.swing.*;
import java.io.File;
import java.sql.*;
import java.util.Map;

//Write off lets value go below 0
//Process sale window creates purchase order code on close
//input as headers
//configure the menu to have sections with related buttons
//Create POWER BI similar dashboard to display data from database
//encryption needed for passwords
//Create backup and restore from backup
//Admin rights = no, display less fields rather than denying access
// 3NF database and Postgresql

/**
 * Main method that handles the login process and updates the login history.
 */

public class Login {

    public static void main(String[] args) {
        String userDatabasePath = "database/userDatabase.db";
        String loginsDatabasePath = "database/loginsDatabase.db";

        try (Connection userConnection = DriverManager.getConnection("jdbc:sqlite:" + userDatabasePath);
             Connection loginsConnection = DriverManager.getConnection("jdbc:sqlite:" + loginsDatabasePath)) {

            boolean loginLoop = true;
            int attempts = 4;

            while (attempts > 0 && loginLoop) {
                LoginPopUp loginPopUp = new LoginPopUp();
                Map<String, String> loginData = loginPopUp.createLoginPopUp();

                String username = loginData.get("username");
                String password = loginData.get("password");

                try {

                    //Checks to see if a match is found, if there is then checks for their admin level
                    if (checkCredentials(userConnection, username, password)) {
                        boolean isAdmin = checkAdminRights(userConnection, username);
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
     * to the login's database.
     *
     * @param loginsConnection The connection to the login's database.
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
     * Creates the database file and all necessary tables if they do not exist.
     * This method opens a connection to the database, executes SQL statements to create each table,
     * and then closes the connection.
     */
    private static void databaseCheck() {

        String databaseFolderPath = "database/";
        File databaseExists = new File(databaseFolderPath, "inventoryManagementDatabase.db");

        // Updates booleans if database is found so program knows which to make and which exist.
        boolean database = databaseExists.exists();

        if (!database) {
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

                {
                    String url = "jdbc:sqlite:" + databaseFolderPath + "/inventoryManagementDatabase.db";

                    String[] tableCreationStatements = {
                            """
                CREATE TABLE IF NOT EXISTS Inventory (
                    "Item Code" TEXT,
                    "Item Name" TEXT,
                    "Stock" INTEGER,
                    "On Dock" INTEGER,
                    "On Order" INTEGER,
                    "ReOrder Trigger" INTEGER,
                    "Purchase Price" REAL,
                    "Sale Price" REAL,
                    "Amount Sold" INTEGER,
                    "Profit" REAL,
                    "Written Off" INTEGER
                )""",
                            """
                CREATE TABLE IF NOT EXISTS movements (
                    "Item" TEXT,
                    "Amount" INTEGER,
                    "Type" TEXT,
                    "User" TEXT,
                    "Date" TEXT
                )""",
                            """
                CREATE TABLE IF NOT EXISTS pendingOrders (
                    "Item Code" TEXT,
                    "Amount" INTEGER,
                    "Reference" TEXT,
                    "User" TEXT,
                    "Date" TEXT
                )""",
                            """
                CREATE TABLE IF NOT EXISTS sales (
                    "Item Code" TEXT,
                    "Amount" INTEGER,
                    "Total Price" INTEGER,
                    "Reference" TEXT,
                    "User" TEXT,
                    "Date" TEXT
                )"""
                    };

                    // Create the database and tables in one try-with-resources block
                    try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
                        for (String sql : tableCreationStatements) {
                            stmt.execute(sql);
                        }
                        JOptionPane.showMessageDialog(null, "Database and tables created successfully.");
                    } catch (SQLException e) {
                        JOptionPane.showMessageDialog(null, "There has been an error creating tables: " + e.getMessage());
                        System.exit(0);
                    }
                }
            }
        }
    }
}