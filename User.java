/**
 * Represents a user in the system with a username and admin rights.
 *
 * This class encapsulates the details of a user, including their username
 * and whether they have administrative rights. The username is immutable
 * once assigned, and admin rights are determined at the time of object creation.
 */

public class User {
    private final String username;
    private final boolean adminRights;
    private final String password;

    /**
     * Constructor to create a new User instance.
     *
     * @param username   The username of the user. It cannot be null.
     * @param adminRights A boolean indicating whether the user has admin rights.
     */

    public User(String username, boolean adminRights, String password) {
        this.username = username;
        this.adminRights = adminRights;
        this.password = password;
    }

    /**
     * Gets the username of the user.
     *
     * @return The username associated with this user.
     */

    public String getUsername() {
        return username;
    }

    /**
     * Checks if the user has administrative rights.
     *
     * @return true if the user has admin rights; false otherwise.
     */

    public boolean hasAdminRights() {
        return adminRights;
    }

    /**
     * Gets the password of the user.
     *
     * @return The password associated with this user.
     */

    public String getPassword() {
        return password;
    }
}
