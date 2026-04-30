import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.sql.*;
import java.util.Arrays;
import java.time.Instant;
import java.time.Duration;

/**
 * Application entry, enterprise DB bootstrap, session-based login loop, and authentication side effects
 * (lockout, login history, password migrations).
 */
public class Login {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(15);

    /** Application entry point for startup, authentication, and session launch. */
    public static void main(String[] args) {
        AppUI.initialize();
        try {
            databaseCheck();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "Unable to initialize the enterprise database.\n\nDetails: " + e.getMessage()
            );
            return;
        }

        SwingUtilities.invokeLater(Login::runSessionCycle);
    }

    /**
     * Modal login loop: validates credentials, enforces first-login password reset, opens {@link postLogin#mainMenu},
     * and re-runs itself when the workspace closes so operators can log in again or switch users.
     */
    static void runSessionCycle() {
        try (Connection connection = DatabaseManager.getConnection()) {
            boolean loginLoop = true;
            int attempts = 4;

            while (attempts > 0 && loginLoop) {
                LoginPopUp loginPopUp = new LoginPopUp();
                LoginCredentials loginData = loginPopUp.createLoginPopUp();

                String username = loginData.getUsername();
                char[] passwordChars = loginData.getPassword() != null ? loginData.getPassword() : new char[0];

                if (username == null || username.trim().isEmpty()) {
                    System.exit(0);
                }

                try {
                    AuthResult authResult = checkCredentials(connection, username, passwordChars);
                    if (authResult == AuthResult.SUCCESS) {
                        boolean isAdmin = checkAdminRights(connection, username);
                        JOptionPane.showMessageDialog(null, "Welcome, " + username + ".");

                        updateLastLogin(connection, username);
                        updateLoginHistory(connection, username);

                        Arrays.fill(passwordChars, '\0');
                        loginLoop = false;

                        User user = new User(username, isAdmin);

                        if (firstLogin(connection, username)) {
                            JOptionPane.showMessageDialog(null, "You must update your password before continuing.");
                            AccountActions accountActions = new AccountActions();
                            while (firstLogin(connection, username)) {
                                AccountActions.PasswordResetOutcome outcome = accountActions.showPasswordResetDialog(null, user);
                                if (outcome == AccountActions.PasswordResetOutcome.SUCCESS) {
                                    break;
                                }
                                if (outcome == AccountActions.PasswordResetOutcome.CANCELLED) {
                                    JOptionPane.showMessageDialog(null, "You must update your password before continuing.");
                                } else {
                                    JOptionPane.showMessageDialog(null, "Too many incorrect password attempts. Please try again.");
                                }
                            }
                        }

                        postLogin.mainMenu(user, Login::runSessionCycle);
                        return;

                    } else if (authResult == AuthResult.LOCKED) {
                        JOptionPane.showMessageDialog(null, "Account is temporarily locked. Please try again later.");
                        attempts -= 1;
                    } else {
                        JOptionPane.showMessageDialog(null, "Invalid username or password. Attempts remaining: " + (attempts - 1));
                        attempts -= 1;
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "An unexpected error occurred. Please try again.");
                } finally {
                    Arrays.fill(passwordChars, '\0');
                }
            }

            if (attempts == 0) {
                JOptionPane.showMessageDialog(null, "Maximum login attempts reached. The application will now close.");
                System.exit(0);
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Unable to connect to the database. Please try again later.");
        }
    }


    /**
     * Validates username and password against {@code users}, applies lockout rules, migrates legacy plaintext
     * hashes on successful verify, and records security audit events.
     *
     * @param connection open JDBC connection to the enterprise database
     * @param username   trimmed username from the login dialog
     * @param password   password characters from the login dialog (caller clears the array afterward)
     * @return authentication outcome (success, invalid password, or locked account)
     * @throws SQLException when database access fails
     */
    private static AuthResult checkCredentials(Connection connection, String username, char[] password) throws SQLException {
        String sql = "SELECT password, failed_attempts, locked_until FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            ResultSet results = preparedStatement.executeQuery();

            if (results.next()) {
                String dbPassword = results.getString("password");
                String lockedUntilRaw = results.getString("locked_until");

                if (isAccountLocked(lockedUntilRaw)) {
                    DatabaseManager.logSecurityEvent(connection, username, "LOGIN_BLOCKED_LOCKED", "Account locked until " + lockedUntilRaw);
                    return AuthResult.LOCKED;
                }

                boolean isValid = SecurityUtils.verifyPassword(password, dbPassword);

                // Seamlessly migrate legacy plaintext passwords to secure hashes.
                if (isValid && SecurityUtils.isLegacyPlaintextPassword(dbPassword)) {
                    String hashedPassword = SecurityUtils.hashPassword(password);
                    String updateSql = "UPDATE users SET password = ? WHERE username = ?";
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                        updateStatement.setString(1, hashedPassword);
                        updateStatement.setString(2, username);
                        updateStatement.executeUpdate();
                    }
                }

                if (isValid) {
                    resetFailedAttempts(connection, username);
                    DatabaseManager.logSecurityEvent(connection, username, "LOGIN_SUCCESS", "Successful login");
                    return AuthResult.SUCCESS;
                }

                registerFailedAttempt(connection, username, results.getInt("failed_attempts") + 1);
                DatabaseManager.logSecurityEvent(connection, username, "LOGIN_FAILED", "Invalid password");
                return AuthResult.INVALID;
            } else {
                DatabaseManager.logSecurityEvent(connection, username, "LOGIN_FAILED_UNKNOWN_USER", "Unknown username attempted login");
                return AuthResult.INVALID;
            }
        }
    }

    /** Returns true when locked_until is set to a future timestamp. */
    private static boolean isAccountLocked(String lockedUntilRaw) {
        if (lockedUntilRaw == null || lockedUntilRaw.isBlank()) {
            return false;
        }
        try {
            Instant lockedUntil = Timestamp.valueOf(lockedUntilRaw).toInstant();
            return Instant.now().isBefore(lockedUntil);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Increments failed attempts and applies lockout window when threshold is reached. */
    private static void registerFailedAttempt(Connection connection, String username, int failedAttempts) throws SQLException {
        boolean shouldLock = failedAttempts >= MAX_FAILED_ATTEMPTS;
        String lockUntil = shouldLock
                ? Timestamp.from(Instant.now().plus(ACCOUNT_LOCK_DURATION)).toString()
                : null;

        String sql = "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shouldLock ? 0 : failedAttempts);
            statement.setString(2, lockUntil);
            statement.setString(3, username);
            statement.executeUpdate();
        }
    }

    /** Clears failed-attempt and lockout state after successful authentication. */
    private static void resetFailedAttempts(Connection connection, String username) throws SQLException {
        String sql = "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }

    /** Outcome of a single credential check against {@code users}. */
    private enum AuthResult {
        /** Password verified and lockout cleared. */
        SUCCESS,
        /** Unknown user or bad password. */
        INVALID,
        /** {@code locked_until} is still in the future. */
        LOCKED
    }


    /**
     * Reads {@code admin_rights} for the user (1 means administrator).
     *
     * @param connection enterprise JDBC connection
     * @param username   user row to inspect
     * @return {@code true} when {@code admin_rights} is 1
     * @throws SQLException when the query fails
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
     * Reads {@code first_login} for the user (1 means the password reset gate is still required).
     *
     * @param connection enterprise JDBC connection
     * @param username   user row to inspect
     * @return {@code true} when {@code first_login} is 1
     * @throws SQLException when the query fails
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
     * Persists {@code users.last_login} using the same display format as {@link dateTime#formattedDateTime()}.
     *
     * @param connection enterprise JDBC connection
     * @param username   row to update
     * @throws SQLException when the update fails
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
     * Appends a row to {@code Logins} with the username and current formatted timestamp.
     *
     * @param loginsConnection enterprise JDBC connection (same file as other tables)
     * @param username         user who just authenticated
     * @throws SQLException when the insert fails
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


    /** Initializes database schema and migration checks at startup. */
    private static void databaseCheck() throws SQLException {
        DatabaseManager.initializeEnterpriseDatabase();
    }
}