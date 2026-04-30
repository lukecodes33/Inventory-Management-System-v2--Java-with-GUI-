import java.sql.Connection;
import java.sql.SQLException;

/**
 * Bridges authentication to the main workspace: opens {@link WorkspaceShell} after sign-in.
 */
public final class postLogin {

    private postLogin() {
    }

    /**
     * Opens the workspace for the signed-in user and wires the session lifecycle callback.
     *
     * @param user             authenticated user (username and admin flag)
     * @param whenWindowClosed invoked on the EDT after the workspace frame is disposed (e.g. show login again)
     * @throws SQLException when a JDBC connection cannot be opened
     */
    static void mainMenu(User user, Runnable whenWindowClosed) throws SQLException {
        Connection connection = DatabaseManager.getConnection();
        AccountActions accountActions = new AccountActions();
        WorkspaceShell.open(user, connection, accountActions, whenWindowClosed);
    }
}
