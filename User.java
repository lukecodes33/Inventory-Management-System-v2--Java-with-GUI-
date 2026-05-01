/**
 * Lightweight session credential model: immutable username paired with elevated-role flag sourced from SQLite.
 *
 * <p>Both fields derive from persisted {@code users} rows—mutations require reloading from the workspace shell.</p>
 */
public class User {
    private final String username;
    private final boolean adminRights;

    /**
     * Builds the DTO mirrored from {@code users.admin_rights}.
     *
     * @param username    non-null sign-in principal
     * @param adminRights {@code true} when {@code admin_rights == 1} in the backing row
     */
    public User(String username, boolean adminRights) {
        this.username = username;
        this.adminRights = adminRights;
    }

    /** @return session username */
    public String getUsername() {
        return username;
    }

    /**
     * @return {@code true} when the operator has destructive/admin-only routes enabled inside {@link WorkspaceShell}
     */
    public boolean hasAdminRights() {
        return adminRights;
    }
}
