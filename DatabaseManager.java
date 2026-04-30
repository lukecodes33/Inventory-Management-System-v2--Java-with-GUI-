import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * SQLite enterprise database path, connection factory, schema creation, legacy migration, and security audit logging.
 */
public final class DatabaseManager {
    private static final Path DATABASE_DIR = Paths.get("database");
    private static final String ENTERPRISE_DB_FILE = "ims_enterprise.sqlite";
    private static volatile boolean driverLoaded = false;

    private DatabaseManager() {
    }

    /**
     * Returns the path to the primary enterprise database file.
     *
     * @return database file path
     */
    public static String getDatabasePath() {
        return DATABASE_DIR.resolve(ENTERPRISE_DB_FILE).toString();
    }

    /**
     * Opens a configured SQLite connection with application pragmas applied.
     *
     * @return open configured connection
     * @throws SQLException when connection cannot be opened
     */
    public static Connection getConnection() throws SQLException {
        ensureDriverLoaded();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + getDatabasePath());
        applyPragmas(connection);
        return connection;
    }

    /** Loads the SQLite JDBC driver once for the process lifetime. */
    private static void ensureDriverLoaded() throws SQLException {
        if (driverLoaded) {
            return;
        }

        synchronized (DatabaseManager.class) {
            if (driverLoaded) {
                return;
            }
            try {
                Class.forName("org.sqlite.JDBC");
                driverLoaded = true;
            } catch (ClassNotFoundException e) {
                throw new SQLException(
                        "SQLite JDBC driver not found. Ensure lib/sqlite-jdbc-3.46.1.3.jar is on the runtime classpath.",
                        e
                );
            }
        }
    }

    /**
     * Creates and upgrades schema, indexes, migration state, and default admin.
     *
     * @throws SQLException when initialization fails
     */
    public static void initializeEnterpriseDatabase() throws SQLException {
        try {
            Files.createDirectories(DATABASE_DIR);
        } catch (IOException e) {
            throw new SQLException("Unable to create database directory", e);
        }

        try (Connection connection = getConnection()) {
            createEnterpriseTables(connection);
            ensureSchemaEvolution(connection);
            createEnterpriseIndexes(connection);
            migrateLegacyDatabasesIfNeeded(connection);
            seedDefaultAdminIfNeeded(connection);
        }
    }

    /** Applies SQLite pragmas used for reliability, performance, and security. */
    private static void applyPragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA secure_delete = ON");
        }
    }

    /** Creates all enterprise tables when they do not already exist. */
    private static void createEnterpriseTables(Connection connection) throws SQLException {
        String[] statements = {
                """
                CREATE TABLE IF NOT EXISTS app_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY,
                    first_name TEXT,
                    last_name TEXT,
                    username TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    admin_rights INTEGER NOT NULL DEFAULT 0,
                    last_login TEXT,
                    first_login INTEGER NOT NULL DEFAULT 1,
                    failed_attempts INTEGER NOT NULL DEFAULT 0,
                    locked_until TEXT
                )""",
                """
                CREATE TABLE IF NOT EXISTS Logins (
                    Name TEXT NOT NULL,
                    Time TEXT NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS Inventory (
                    "Item Code" TEXT PRIMARY KEY,
                    "Item Name" TEXT NOT NULL,
                    "Stock" INTEGER NOT NULL DEFAULT 0,
                    "On Dock" INTEGER NOT NULL DEFAULT 0,
                    "On Order" INTEGER NOT NULL DEFAULT 0,
                    "ReOrder Trigger" INTEGER NOT NULL DEFAULT 0,
                    "Supplier" TEXT,
                    "Lead Time" INTEGER,
                    "Notes" TEXT,
                    "Market Price" REAL
                )""",
                """
                CREATE TABLE IF NOT EXISTS movements (
                    "Item" TEXT NOT NULL,
                    "Amount" INTEGER NOT NULL,
                    "Type" TEXT NOT NULL,
                    "Reason" TEXT,
                    "User" TEXT NOT NULL,
                    "Date" TEXT NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS pendingOrders (
                    "Item Code" TEXT NOT NULL,
                    "Amount" INTEGER NOT NULL,
                    "Purchase Price" REAL NOT NULL DEFAULT 0,
                    "Reference" TEXT NOT NULL,
                    "User" TEXT NOT NULL,
                    "Date" TEXT NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS sales (
                    "Item Code" TEXT NOT NULL,
                    "Item Name" TEXT NOT NULL,
                    "Amount" INTEGER NOT NULL,
                    "Total Price" REAL NOT NULL,
                    "Total Cost" REAL NOT NULL DEFAULT 0,
                    "Reference" TEXT NOT NULL,
                    "User" TEXT NOT NULL,
                    "Date" TEXT NOT NULL,
                    "DateISO" TEXT,
                    "Note" TEXT
                )""",
                """
                CREATE TABLE IF NOT EXISTS inventory_cost_layers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_code TEXT NOT NULL,
                    reference TEXT,
                    unit_cost REAL NOT NULL,
                    qty_received INTEGER NOT NULL,
                    qty_remaining INTEGER NOT NULL,
                    created_at TEXT NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS security_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT,
                    event_type TEXT NOT NULL,
                    details TEXT,
                    created_at TEXT NOT NULL
                )"""
        };

        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    /** Creates indexes used by common query paths and reports. */
    private static void createEnterpriseIndexes(Connection connection) throws SQLException {
        String[] statements = {
                "CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)",
                "CREATE INDEX IF NOT EXISTS idx_logins_name_time ON Logins(Name, Time)",
                "CREATE INDEX IF NOT EXISTS idx_movements_item_date ON movements(\"Item\", \"Date\")",
                "CREATE INDEX IF NOT EXISTS idx_pending_orders_reference ON pendingOrders(\"Reference\")",
                "CREATE INDEX IF NOT EXISTS idx_sales_reference ON sales(\"Reference\")",
                "CREATE INDEX IF NOT EXISTS idx_sales_date_iso ON sales(\"DateISO\")",
                "CREATE INDEX IF NOT EXISTS idx_inventory_cost_layers_item_created ON inventory_cost_layers(item_code, created_at, id)",
                "CREATE INDEX IF NOT EXISTS idx_security_audit_created_at ON security_audit(created_at)"
        };

        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    /** Runs one-time legacy database migration into the enterprise schema. */
    private static void migrateLegacyDatabasesIfNeeded(Connection enterpriseConnection) throws SQLException {
        if (isLegacyMigrationCompleted(enterpriseConnection)) {
            return;
        }

        migrateUsersFromLegacy(enterpriseConnection);
        migrateLoginsFromLegacy(enterpriseConnection);
        migrateInventoryFromLegacy(enterpriseConnection);
        migrateMovementsFromLegacy(enterpriseConnection);
        migratePendingOrdersFromLegacy(enterpriseConnection);
        migrateSalesFromLegacy(enterpriseConnection);
        markLegacyMigrationCompleted(enterpriseConnection);
    }

    /** Checks whether legacy migration has already completed. */
    private static boolean isLegacyMigrationCompleted(Connection connection) throws SQLException {
        String query = "SELECT value FROM app_metadata WHERE key = 'legacy_migration_completed'";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            return resultSet.next() && "1".equals(resultSet.getString("value"));
        }
    }

    /** Marks legacy migration as completed in metadata. */
    private static void markLegacyMigrationCompleted(Connection connection) throws SQLException {
        String sql = "INSERT OR REPLACE INTO app_metadata (key, value) VALUES ('legacy_migration_completed', '1')";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /** Applies additive schema changes required by newer application versions. */
    private static void ensureSchemaEvolution(Connection connection) throws SQLException {
        if (!columnExists(connection, "users", "failed_attempts")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE users ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0");
            }
        }
        if (!columnExists(connection, "users", "locked_until")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE users ADD COLUMN locked_until TEXT");
            }
        }
        if (!columnExists(connection, "pendingOrders", "Purchase Price")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE pendingOrders ADD COLUMN \"Purchase Price\" REAL NOT NULL DEFAULT 0");
            }
        }
        if (!columnExists(connection, "movements", "Reason")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE movements ADD COLUMN \"Reason\" TEXT");
            }
        }
        if (!columnExists(connection, "Inventory", "Supplier")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE Inventory ADD COLUMN \"Supplier\" TEXT");
            }
        }
        if (!columnExists(connection, "Inventory", "Lead Time")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE Inventory ADD COLUMN \"Lead Time\" INTEGER");
            }
        }
        if (!columnExists(connection, "Inventory", "Notes")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE Inventory ADD COLUMN \"Notes\" TEXT");
            }
        }
        if (!columnExists(connection, "Inventory", "Market Price")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE Inventory ADD COLUMN \"Market Price\" REAL");
            }
        }
        if (columnExists(connection, "Inventory", "Sale Price")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE Inventory DROP COLUMN \"Sale Price\"");
            }
        }
        if (columnExists(connection, "Inventory", "Purchase Price")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE Inventory DROP COLUMN \"Purchase Price\"");
            }
        }
        if (!columnExists(connection, "sales", "Total Cost")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE sales ADD COLUMN \"Total Cost\" REAL NOT NULL DEFAULT 0");
            }
        }
        if (!columnExists(connection, "sales", "DateISO")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE sales ADD COLUMN \"DateISO\" TEXT");
            }
        }
        if (!columnExists(connection, "sales", "Note")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE sales ADD COLUMN \"Note\" TEXT");
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE sales SET \"DateISO\" = (substr(\"Date\", 7, 4) || '-' || substr(\"Date\", 4, 2) || '-' || substr(\"Date\", 1, 2) || substr(\"Date\", 11)) " +
                            "WHERE \"DateISO\" IS NULL AND length(\"Date\") >= 19"
            );
        }
        if (!tableExists(connection, "inventory_cost_layers")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS inventory_cost_layers (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            item_code TEXT NOT NULL,
                            reference TEXT,
                            unit_cost REAL NOT NULL,
                            qty_received INTEGER NOT NULL,
                            qty_remaining INTEGER NOT NULL,
                            created_at TEXT NOT NULL
                        )
                        """);
            }
        }
    }

    /** Migrates legacy user records into the enterprise users table. */
    private static void migrateUsersFromLegacy(Connection enterpriseConnection) throws SQLException {
        Path legacyPath = DATABASE_DIR.resolve("userDatabase.db");
        if (!Files.exists(legacyPath)) {
            return;
        }

        try (Connection legacyConnection = DriverManager.getConnection("jdbc:sqlite:" + legacyPath)) {
            if (!tableExists(legacyConnection, "users")) {
                return;
            }
            String selectSql = "SELECT id, first_name, last_name, username, password, admin_rights, last_login, first_login FROM users";
            String insertSql = "INSERT OR IGNORE INTO users (id, first_name, last_name, username, password, admin_rights, last_login, first_login) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (Statement selectStatement = legacyConnection.createStatement();
                 ResultSet rs = selectStatement.executeQuery(selectSql);
                 PreparedStatement insertStatement = enterpriseConnection.prepareStatement(insertSql)) {
                while (rs.next()) {
                    insertStatement.setObject(1, rs.getObject("id"));
                    insertStatement.setString(2, rs.getString("first_name"));
                    insertStatement.setString(3, rs.getString("last_name"));
                    insertStatement.setString(4, rs.getString("username"));
                    insertStatement.setString(5, rs.getString("password"));
                    insertStatement.setInt(6, rs.getInt("admin_rights"));
                    insertStatement.setString(7, rs.getString("last_login"));
                    insertStatement.setString(8, rs.getString("first_login"));
                    insertStatement.executeUpdate();
                }
            }
        }
    }

    /** Migrates legacy login history into the enterprise logins table. */
    private static void migrateLoginsFromLegacy(Connection enterpriseConnection) throws SQLException {
        Path legacyPath = DATABASE_DIR.resolve("loginsDatabase.db");
        if (!Files.exists(legacyPath)) {
            return;
        }

        try (Connection legacyConnection = DriverManager.getConnection("jdbc:sqlite:" + legacyPath)) {
            if (!tableExists(legacyConnection, "Logins")) {
                return;
            }
            copySimpleTable(
                    legacyConnection,
                    enterpriseConnection,
                    "SELECT Name, Time FROM Logins",
                    "INSERT INTO Logins (Name, Time) VALUES (?, ?)"
            );
        }
    }

    /** Migrates legacy inventory data into the enterprise inventory table. */
    private static void migrateInventoryFromLegacy(Connection enterpriseConnection) throws SQLException {
        Path legacyPath = DATABASE_DIR.resolve("inventoryManagementDatabase.db");
        if (!Files.exists(legacyPath)) {
            return;
        }

        try (Connection legacyConnection = DriverManager.getConnection("jdbc:sqlite:" + legacyPath)) {
            if (!tableExists(legacyConnection, "Inventory")) {
                return;
            }
            String selectSql = "SELECT \"Item Code\", \"Item Name\", \"Stock\", \"On Dock\", \"On Order\", \"ReOrder Trigger\" FROM Inventory";
            String insertSql = "INSERT OR IGNORE INTO Inventory (\"Item Code\", \"Item Name\", \"Stock\", \"On Dock\", \"On Order\", \"ReOrder Trigger\", \"Supplier\", \"Lead Time\", \"Notes\", \"Market Price\") VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL)";
            copyLegacyInventoryWithoutPricingColumns(legacyConnection, enterpriseConnection, selectSql, insertSql);
        }
    }

    /** Migrates legacy inventory movement records. */
    private static void migrateMovementsFromLegacy(Connection enterpriseConnection) throws SQLException {
        Path legacyPath = DATABASE_DIR.resolve("inventoryManagementDatabase.db");
        if (!Files.exists(legacyPath)) {
            return;
        }

        try (Connection legacyConnection = DriverManager.getConnection("jdbc:sqlite:" + legacyPath)) {
            if (!tableExists(legacyConnection, "movements")) {
                return;
            }
            copySimpleTable(
                    legacyConnection,
                    enterpriseConnection,
                    "SELECT \"Item\", \"Amount\", \"Type\", \"User\", \"Date\" FROM movements",
                    "INSERT INTO movements (\"Item\", \"Amount\", \"Type\", \"User\", \"Date\") VALUES (?, ?, ?, ?, ?)"
            );
        }
    }

    /** Migrates legacy pending purchase order lines. */
    private static void migratePendingOrdersFromLegacy(Connection enterpriseConnection) throws SQLException {
        Path legacyPath = DATABASE_DIR.resolve("inventoryManagementDatabase.db");
        if (!Files.exists(legacyPath)) {
            return;
        }

        try (Connection legacyConnection = DriverManager.getConnection("jdbc:sqlite:" + legacyPath)) {
            if (!tableExists(legacyConnection, "pendingOrders")) {
                return;
            }
            copySimpleTable(
                    legacyConnection,
                    enterpriseConnection,
                    "SELECT \"Item Code\", \"Amount\", \"Reference\", \"User\", \"Date\" FROM pendingOrders",
                    "INSERT INTO pendingOrders (\"Item Code\", \"Amount\", \"Reference\", \"User\", \"Date\") VALUES (?, ?, ?, ?, ?)"
            );
        }
    }

    /** Migrates legacy sales and backfills derived enterprise fields. */
    private static void migrateSalesFromLegacy(Connection enterpriseConnection) throws SQLException {
        Path legacyPath = DATABASE_DIR.resolve("inventoryManagementDatabase.db");
        if (!Files.exists(legacyPath)) {
            return;
        }

        try (Connection legacyConnection = DriverManager.getConnection("jdbc:sqlite:" + legacyPath)) {
            if (!tableExists(legacyConnection, "sales")) {
                return;
            }
            String selectSql = "SELECT \"Item Code\", \"Item Name\", \"Amount\", \"Total Price\", \"Reference\", \"User\", \"Date\" FROM sales";
            String insertSql = "INSERT INTO sales (\"Item Code\", \"Item Name\", \"Amount\", \"Total Price\", \"Total Cost\", \"Reference\", \"User\", \"Date\", \"DateISO\", \"Note\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            copyTableWithSevenColumns(legacyConnection, enterpriseConnection, selectSql, insertSql);
        }
    }

    /** Copies rows from a source query into a target insert statement. */
    private static void copySimpleTable(
            Connection source,
            Connection target,
            String selectSql,
            String insertSql
    ) throws SQLException {
        try (Statement selectStatement = source.createStatement();
             ResultSet resultSet = selectStatement.executeQuery(selectSql);
             PreparedStatement insertStatement = target.prepareStatement(insertSql)) {
            while (resultSet.next()) {
                int columnCount = resultSet.getMetaData().getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    insertStatement.setObject(i, resultSet.getObject(i));
                }
                insertStatement.executeUpdate();
            }
        }
    }

    /** Returns true when the specified table exists in the current database. */
    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Returns true when the specified column exists on a table. */
    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String pragma = "PRAGMA table_info(" + tableName + ")";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(pragma)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Copies legacy inventory rows (first six columns) into enterprise Inventory including nullable Supplier/Lead Time. */
    private static void copyLegacyInventoryWithoutPricingColumns(
            Connection source,
            Connection target,
            String selectSql,
            String insertSql
    ) throws SQLException {
        try (Statement selectStatement = source.createStatement();
             ResultSet resultSet = selectStatement.executeQuery(selectSql);
             PreparedStatement insertStatement = target.prepareStatement(insertSql)) {
            while (resultSet.next()) {
                for (int i = 1; i <= 6; i++) {
                    insertStatement.setObject(i, resultSet.getObject(i));
                }
                insertStatement.executeUpdate();
            }
        }
    }

    /** Copies legacy sales shape into the enterprise sales table format. */
    private static void copyTableWithSevenColumns(
            Connection source,
            Connection target,
            String selectSql,
            String insertSql
    ) throws SQLException {
        try (Statement selectStatement = source.createStatement();
             ResultSet resultSet = selectStatement.executeQuery(selectSql);
             PreparedStatement insertStatement = target.prepareStatement(insertSql)) {
            while (resultSet.next()) {
                insertStatement.setObject(1, resultSet.getObject(1));
                insertStatement.setObject(2, resultSet.getObject(2));
                insertStatement.setObject(3, resultSet.getObject(3));
                insertStatement.setObject(4, resultSet.getObject(4));
                insertStatement.setDouble(5, 0.0);
                insertStatement.setObject(6, resultSet.getObject(5));
                insertStatement.setObject(7, resultSet.getObject(6));
                String displayDate = resultSet.getString(7);
                insertStatement.setString(8, displayDate);
                if (displayDate != null && displayDate.length() >= 19) {
                    String iso = displayDate.substring(6, 10) + "-" + displayDate.substring(3, 5) + "-" + displayDate.substring(0, 2) + displayDate.substring(10);
                    insertStatement.setString(9, iso);
                } else {
                    insertStatement.setString(9, null);
                }
                insertStatement.setNull(10, Types.VARCHAR);
                insertStatement.executeUpdate();
            }
        }
    }

    /**
     * Writes a security-relevant event to the audit log.
     *
     * @param connection active database connection
     * @param username username tied to the event
     * @param eventType event category label
     * @param details human-readable details
     * @throws SQLException when insert fails
     */
    public static void logSecurityEvent(Connection connection, String username, String eventType, String details) throws SQLException {
        String sql = "INSERT INTO security_audit (username, event_type, details, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, eventType);
            statement.setString(3, details);
            statement.setString(4, new Timestamp(System.currentTimeMillis()).toString());
            statement.executeUpdate();
        }
    }

    /**
     * Destructive factory reset: clears transactional tables, keeps only the {@code Admin} user with password
     * {@code firstLogin} and {@code first_login=1}, removes every other login, and deletes regular files under
     * {@code item_images/} (does not remove {@code company.txt} or {@code workspace_welcome.png} in the project root).
     *
     * @param connection open JDBC connection; auto-commit is restored after this method returns
     * @throws SQLException when a statement fails
     * @throws IOException   when an on-disk item image cannot be deleted
     */
    public static void resetEnterpriseDataToDayOne(Connection connection) throws SQLException, IOException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM inventory_cost_layers");
                statement.executeUpdate("DELETE FROM sales");
                statement.executeUpdate("DELETE FROM pendingOrders");
                statement.executeUpdate("DELETE FROM movements");
                statement.executeUpdate("DELETE FROM Inventory");
                statement.executeUpdate("DELETE FROM Logins");
                statement.executeUpdate("DELETE FROM security_audit");
                statement.executeUpdate("DELETE FROM users WHERE LOWER(TRIM(username)) <> 'admin'");
            }
            String adminHash = SecurityUtils.hashPassword("firstLogin".toCharArray());
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE users SET first_name = ?, last_name = ?, password = ?, admin_rights = 1, "
                            + "last_login = NULL, first_login = 1, failed_attempts = 0, locked_until = NULL "
                            + "WHERE LOWER(TRIM(username)) = 'admin'"
            )) {
                update.setString(1, "System");
                update.setString(2, "Administrator");
                update.setString(3, adminHash);
                int updated = update.executeUpdate();
                if (updated == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO users (first_name, last_name, username, password, admin_rights, first_login, "
                                    + "failed_attempts, locked_until, last_login) VALUES (?, ?, ?, ?, 1, 1, 0, NULL, NULL)"
                    )) {
                        insert.setString(1, "System");
                        insert.setString(2, "Administrator");
                        insert.setString(3, "Admin");
                        insert.setString(4, adminHash);
                        insert.executeUpdate();
                    }
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN ('inventory_cost_layers', 'security_audit')");
            } catch (SQLException ignored) {
                // sqlite_sequence may be absent on a pristine file; ignore.
            }
            connection.commit();
            clearItemImagesDirectoryFilesOnly();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /** Deletes regular files directly under {@code item_images/}; leaves the directory and project-root assets intact. */
    private static void clearItemImagesDirectoryFilesOnly() throws IOException {
        Path dir = Paths.get("item_images");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    Files.deleteIfExists(entry);
                }
            }
        }
    }

    /** Seeds the default administrator account on an empty users table. */
    private static void seedDefaultAdminIfNeeded(Connection connection) throws SQLException {
        String checkSql = "SELECT COUNT(*) AS count FROM users";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(checkSql)) {
            if (resultSet.next() && resultSet.getInt("count") > 0) {
                return;
            }
        }

        String insertSql = "INSERT INTO users (first_name, last_name, username, password, admin_rights, first_login) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, "System");
            statement.setString(2, "Administrator");
            statement.setString(3, "Admin");
            statement.setString(4, SecurityUtils.hashPassword("firstLogin".toCharArray()));
            statement.setInt(5, 1);
            statement.setInt(6, 1);
            statement.executeUpdate();
        }
    }
}
