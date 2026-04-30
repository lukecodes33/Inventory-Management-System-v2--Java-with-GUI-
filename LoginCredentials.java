/**
 * Immutable username and password snapshot from the login dialog (password as {@code char[]} for clearing).
 */
public final class LoginCredentials {
    private final String username;
    private final char[] password;

    /** Creates immutable login credential holder for a single sign-in attempt. */
    public LoginCredentials(String username, char[] password) {
        this.username = username;
        this.password = password;
    }

    /** Returns username entered in the login form. */
    public String getUsername() {
        return username;
    }

    /** Returns password char array entered in the login form. */
    public char[] getPassword() {
        return password;
    }
}

