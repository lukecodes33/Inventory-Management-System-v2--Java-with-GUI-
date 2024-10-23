public class User {
    private final String username;
    private final boolean adminRights;

    // Constructor
    public User(String username, boolean adminRights) {
        this.username = username;
        this.adminRights = adminRights;
    }

    // Getter for username
    public String getUsername() {
        return username;
    }

    // Getter for admin rights
    public boolean hasAdminRights() {
        return adminRights;
    }
}
