import javax.swing.*;
import java.sql.*;
import java.util.Map;

public class Login {

    /**
     * Main method that handles the login process and updates the login history.
     *
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
     * @param username The username entered by the user.
     * @param password The password entered by the user.
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
     * @param username The username of the user.
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
     * @param username The username of the user whose last login time is to be updated.
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
     * @param username The username of the user.
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
}
