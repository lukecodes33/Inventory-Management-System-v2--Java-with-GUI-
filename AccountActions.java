import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Account administration: password reset UX, interactive backup/restore/prune, and related file operations.
 */
public class AccountActions {

    /** Outcome of an interactive password reset dialog. */
    public enum PasswordResetOutcome {
        SUCCESS,
        CANCELLED,
        FAILED_ATTEMPTS
    }

    /**
     * Validates password against minimum complexity policy (shared with workspace forms).
     *
     * @return error message if invalid, or null if valid
     */
    public static String validatePasswordPolicy(char[] candidatePassword) {
        if (candidatePassword == null || candidatePassword.length < 12) {
            return "Password must be at least 12 characters.";
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : candidatePassword) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                hasSpecial = true;
            }
        }

        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            return "Password must include uppercase, lowercase, number, and symbol.";
        }
        return null;
    }

    /**
     * Modal password change dialog for the given user. Does not terminate the JVM.
     *
     * @param parent optional owner window; may be null at first-login
     * @param user   user whose password is updated
     * @return outcome for callers (e.g. first-login loop or workspace re-auth)
     */
    public PasswordResetOutcome showPasswordResetDialog(Window parent, User user) {
        final PasswordResetOutcome[] result = {PasswordResetOutcome.CANCELLED};
        final int[] attemptsRemaining = {3};

        JDialog dialog = new JDialog(parent instanceof java.awt.Frame ? (java.awt.Frame) parent : null);
        dialog.setTitle("Password Reset");
        dialog.setModal(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("Reset Password");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        form.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.gridx = 0;
        form.add(new JLabel("Username"), gbc);
        gbc.gridx = 1;
        form.add(new JLabel(user.getUsername()), gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        form.add(new JLabel("Current Password"), gbc);
        JPasswordField currentPasswordText = new JPasswordField(20);
        currentPasswordText.setBorder(AppUI.newRoundedBorder(8));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        form.add(currentPasswordText, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.weightx = 0;
        form.add(new JLabel("New Password"), gbc);
        JPasswordField newPasswordText = new JPasswordField(20);
        newPasswordText.setBorder(AppUI.newRoundedBorder(8));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        form.add(newPasswordText, gbc);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.weightx = 0;
        form.add(new JLabel("Confirm New Password"), gbc);
        JPasswordField newPasswordConfirmationText = new JPasswordField(20);
        newPasswordConfirmationText.setBorder(AppUI.newRoundedBorder(8));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        form.add(newPasswordConfirmationText, gbc);

        JLabel checklistTitle = new JLabel("Password requirements");
        checklistTitle.setFont(checklistTitle.getFont().deriveFont(Font.BOLD, 12f));
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        form.add(checklistTitle, gbc);

        JLabel reqLength = new JLabel();
        JLabel reqUpper = new JLabel();
        JLabel reqLower = new JLabel();
        JLabel reqDigit = new JLabel();
        JLabel reqSymbol = new JLabel();
        gbc.gridwidth = 2;
        gbc.gridy = 6;
        form.add(reqLength, gbc);
        gbc.gridy = 7;
        form.add(reqUpper, gbc);
        gbc.gridy = 8;
        form.add(reqLower, gbc);
        gbc.gridy = 9;
        form.add(reqDigit, gbc);
        gbc.gridy = 10;
        form.add(reqSymbol, gbc);

        Runnable refreshChecklist = () -> {
            char[] np = newPasswordText.getPassword();
            try {
                updateRequirementLabels(np, reqLength, reqUpper, reqLower, reqDigit, reqSymbol);
            } finally {
                Arrays.fill(np, '\0');
            }
        };

        DocumentListener checklistListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshChecklist.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshChecklist.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshChecklist.run();
            }
        };
        newPasswordText.getDocument().addDocumentListener(checklistListener);
        newPasswordConfirmationText.getDocument().addDocumentListener(checklistListener);
        refreshChecklist.run();

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBorder(AppUI.newRoundedBorder(8));
        cancelButton.setFocusPainted(false);
        JButton submitButton = new JButton("Submit");
        AppUI.stylePrimaryButton(submitButton);
        cancelButton.addActionListener(e -> {
            result[0] = PasswordResetOutcome.CANCELLED;
            dialog.dispose();
        });
        submitButton.addActionListener(e -> handlePasswordSubmit(
                user,
                dialog,
                currentPasswordText,
                newPasswordText,
                newPasswordConfirmationText,
                attemptsRemaining,
                result
        ));

        JPanel buttonRow = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(buttonRow);
        GridBagConstraints bg = new GridBagConstraints();
        bg.insets = new Insets(12, 8, 0, 8);
        bg.anchor = GridBagConstraints.EAST;

        bg.gridx = 0;
        buttonRow.add(cancelButton, bg);
        bg.gridx = 1;
        buttonRow.add(submitButton, bg);

        gbc.gridy = 11;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(12, 8, 8, 8);
        form.add(buttonRow, gbc);

        dialog.add(form, BorderLayout.CENTER);

        dialog.pack();
        Dimension pref = dialog.getPreferredSize();
        // Between the original fixed 540×460 and the oversized 600px floor: ~half the extra height (~+70px).
        int w = Math.max(480, pref.width);
        int h = Math.max(528, pref.height);
        dialog.setSize(w, h);
        dialog.setMinimumSize(new Dimension(480, h));

        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }

    /** Updates checklist labels next to “New Password” showing which policy rules still fail. */
    private static void updateRequirementLabels(
            char[] newPass,
            JLabel reqLength,
            JLabel reqUpper,
            JLabel reqLower,
            JLabel reqDigit,
            JLabel reqSymbol
    ) {
        boolean len = newPass != null && newPass.length >= 12;
        boolean up = false;
        boolean lo = false;
        boolean dig = false;
        boolean sym = false;
        if (newPass != null) {
            for (char c : newPass) {
                if (Character.isUpperCase(c)) {
                    up = true;
                } else if (Character.isLowerCase(c)) {
                    lo = true;
                } else if (Character.isDigit(c)) {
                    dig = true;
                } else if (!Character.isWhitespace(c)) {
                    sym = true;
                }
            }
        }
        reqLength.setText(formatReq("At least 12 characters", len));
        reqUpper.setText(formatReq("One uppercase letter", up));
        reqLower.setText(formatReq("One lowercase letter", lo));
        reqDigit.setText(formatReq("One digit", dig));
        reqSymbol.setText(formatReq("One symbol (non-space)", sym));
    }

    /** Renders ✓ vs ○ prefix for password checklist rows. */
    private static String formatReq(String text, boolean ok) {
        return (ok ? "✓ " : "○ ") + text;
    }

    /**
     * Validates current password + policy, persists hash update, clears {@code first_login}, updates {@code result} holder.
     *
     * @param attemptsRemaining decreasing counter for wrong current-password attempts
     */
    private void handlePasswordSubmit(
            User user,
            JDialog dialog,
            JPasswordField currentPasswordText,
            JPasswordField newPasswordText,
            JPasswordField newPasswordConfirmationText,
            int[] attemptsRemaining,
            PasswordResetOutcome[] result
    ) {
        char[] currentPassword = currentPasswordText.getPassword();
        char[] newPassword1 = newPasswordText.getPassword();
        char[] newPassword2 = newPasswordConfirmationText.getPassword();

        try (Connection userConnection = DatabaseManager.getConnection()) {
            String currentPasswordQuery = "SELECT password FROM users WHERE username = ?";
            try (PreparedStatement currentPasswordStmt = userConnection.prepareStatement(currentPasswordQuery)) {
                currentPasswordStmt.setString(1, user.getUsername());
                ResultSet resultSet = currentPasswordStmt.executeQuery();

                if (!resultSet.next()) {
                    JOptionPane.showMessageDialog(dialog, "User record not found.");
                    return;
                }

                String storedHash = resultSet.getString("password");
                boolean currentPasswordIsValid = SecurityUtils.verifyPassword(currentPassword, storedHash);

                if (!currentPasswordIsValid) {
                    attemptsRemaining[0]--;
                    JOptionPane.showMessageDialog(dialog,
                            "Current password is incorrect. " + attemptsRemaining[0] + " attempts remaining.");
                    if (attemptsRemaining[0] <= 0) {
                        result[0] = PasswordResetOutcome.FAILED_ATTEMPTS;
                        dialog.dispose();
                    }
                    return;
                }

                if (!Arrays.equals(newPassword1, newPassword2)) {
                    JOptionPane.showMessageDialog(dialog, "New password entries do not match.");
                    return;
                }

                String passwordPolicyError = validatePasswordPolicy(newPassword1);
                if (passwordPolicyError != null) {
                    JOptionPane.showMessageDialog(dialog, passwordPolicyError);
                    return;
                }

                if (SecurityUtils.verifyPassword(newPassword1, storedHash)) {
                    JOptionPane.showMessageDialog(dialog, "New password must be different from your current password.");
                    return;
                }

                String updatedPasswordHash = SecurityUtils.hashPassword(newPassword1);
                String updateSql = "UPDATE users SET password = ?, first_login = 0 WHERE username = ?";
                try (PreparedStatement preparedStatement = userConnection.prepareStatement(updateSql)) {
                    preparedStatement.setString(1, updatedPasswordHash);
                    preparedStatement.setString(2, user.getUsername());
                    preparedStatement.executeUpdate();
                    DatabaseManager.logSecurityEvent(userConnection, user.getUsername(), "PASSWORD_RESET", "User reset password from GUI flow");
                    result[0] = PasswordResetOutcome.SUCCESS;
                    JOptionPane.showMessageDialog(dialog, "Update successful.");
                    dialog.dispose();
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Database error: " + ex.getMessage());
        } finally {
            Arrays.fill(currentPassword, '\0');
            Arrays.fill(newPassword1, '\0');
            Arrays.fill(newPassword2, '\0');
        }
    }

    /** Lists backup folder names under {@code database_backups} (newest name first). */
    public List<String> listDatabaseBackupFolderNames() throws IOException {
        Path backupDirectory = Paths.get("database_backups");
        if (!Files.isDirectory(backupDirectory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(backupDirectory)) {
            List<String> names = new ArrayList<>();
            stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("database-"))
                    .forEach(names::add);
            names.sort(Comparator.reverseOrder());
            return names;
        }
    }

    private static final DateTimeFormatter BACKUP_FOLDER_TS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final String BACKUP_FOLDER_PREFIX = "database-";

    /**
     * Days since the newest folder under {@code database_backups}, or {@code -1} when none exist or names are unreadable.
     */
    public int daysSinceLatestBackup() throws IOException {
        Path backupDirectory = Paths.get("database_backups");
        if (!Files.isDirectory(backupDirectory)) {
            return -1;
        }
        LocalDateTime newest = null;
        try (Stream<Path> stream = Files.list(backupDirectory)) {
            for (Path p : stream.filter(Files::isDirectory).toList()) {
                String name = p.getFileName().toString();
                if (!name.startsWith(BACKUP_FOLDER_PREFIX)) {
                    continue;
                }
                String ts = name.substring(BACKUP_FOLDER_PREFIX.length());
                try {
                    LocalDateTime parsed = LocalDateTime.parse(ts, BACKUP_FOLDER_TS);
                    if (newest == null || parsed.isAfter(newest)) {
                        newest = parsed;
                    }
                } catch (Exception ignored) {
                    // Skip folders that do not match the timestamp suffix.
                }
            }
        }
        if (newest == null) {
            return -1;
        }
        return (int) ChronoUnit.DAYS.between(newest.toLocalDate(), LocalDateTime.now().toLocalDate());
    }

    /** Opens the backup root folder in the system file manager. */
    public void openBackupsFolder(User user, Window parent) throws IOException {
        requireAdminRights(user);
        Path backupDirectory = Paths.get("database_backups");
        Files.createDirectories(backupDirectory);
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(parent, "Desktop open is not supported on this platform.");
            return;
        }
        Desktop.getDesktop().open(backupDirectory.toFile());
    }

    /**
     * Creates a timestamped copy of the {@code database} folder under {@code database_backups}.
     * Creates the backup directory if it does not exist.
     */
    public void backUpDatabase(User user, Window parent) throws SQLException {
        requireAdminRights(user);
        Path sourceDirectory = Paths.get("database");
        Path backupDirectory = Paths.get("database_backups");

        try {
            Files.createDirectories(backupDirectory);
        } catch (IOException e) {
            logBackupFailure(user, "Could not create backup directory: " + e.getMessage());
            JOptionPane.showMessageDialog(parent, "Could not create backup folder: " + e.getMessage(), "Backup Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!Files.isDirectory(sourceDirectory)) {
            JOptionPane.showMessageDialog(parent, sourceDirectory + " was not found.", "Database Folder Missing", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Path destinationDirectory = backupDirectory.resolve(sourceDirectory.getFileName() + "-" + dateTime.nowDisplayString());

        try {
            Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetDir = destinationDirectory.resolve(sourceDirectory.relativize(dir));
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path targetFile = destinationDirectory.resolve(sourceDirectory.relativize(file));
                    Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });

            try (Connection c = DatabaseManager.getConnection()) {
                DatabaseManager.logSecurityEvent(c, user.getUsername(), "DATABASE_BACKUP_SUCCESS", destinationDirectory.toAbsolutePath().toString());
            }
            JOptionPane.showMessageDialog(parent, "Backup created successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            logBackupFailure(user, e.getMessage());
            JOptionPane.showMessageDialog(parent, "Backup failed. Please try again.", "Error", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Records a DATABASE_BACKUP_FAILED security audit row with {@code detail}. */
    private void logBackupFailure(User user, String detail) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            DatabaseManager.logSecurityEvent(c, user.getUsername(), "DATABASE_BACKUP_FAILED", detail != null ? detail : "unknown error");
        }
    }

    /**
     * Replaces the contents of {@code database} with a selected backup folder.
     * Warns the user; may fail if database files are locked.
     */
    public void restoreDatabaseFromBackup(User user, Window parent, String backupFolderName) throws SQLException, IOException {
        requireAdminRights(user);
        if (backupFolderName == null || backupFolderName.isBlank()) {
            JOptionPane.showMessageDialog(parent, "Select a backup folder first.", "Restore", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path backupRoot = Paths.get("database_backups");
        Path selected = backupRoot.resolve(backupFolderName).normalize();
        if (!selected.startsWith(backupRoot) || !Files.isDirectory(selected)) {
            JOptionPane.showMessageDialog(parent, "Invalid backup selection.", "Restore", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(parent,
                "This will replace all files in the live database folder with the selected backup.\n"
                        + "Other instances of this application should be closed.\n"
                        + "You will need to restart the application afterward.\n\n"
                        + "Continue?",
                "Confirm Restore",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        Path dbDir = Paths.get("database");
        if (!Files.isDirectory(dbDir)) {
            Files.createDirectories(dbDir);
        }

        try {
            clearDirectoryContents(dbDir);
            copyDirectoryTree(selected, dbDir);
            try (Connection c = DatabaseManager.getConnection()) {
                DatabaseManager.logSecurityEvent(c, user.getUsername(), "DATABASE_RESTORE_SUCCESS", selected.toAbsolutePath().toString());
            }
            JOptionPane.showMessageDialog(parent,
                    "Restore completed. Close this application completely and start it again so the database is reloaded.",
                    "Restore Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            try (Connection c = DatabaseManager.getConnection()) {
                DatabaseManager.logSecurityEvent(c, user.getUsername(), "DATABASE_RESTORE_FAILED", e.getMessage());
            }
            JOptionPane.showMessageDialog(parent, "Restore failed: " + e.getMessage(), "Restore Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Deletes backup folders under {@code database_backups} whose last-modified time is older than the cutoff.
     */
    public void pruneDatabaseBackups(User user, Window parent, int daysToKeep) throws SQLException, IOException {
        requireAdminRights(user);
        if (daysToKeep < 0) {
            JOptionPane.showMessageDialog(parent, "Days to keep must be zero or positive.", "Prune Backups", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path backupRoot = Paths.get("database_backups");
        if (!Files.isDirectory(backupRoot)) {
            JOptionPane.showMessageDialog(parent, "No backup folder exists yet.", "Prune Backups", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        long cutoffMillis = System.currentTimeMillis() - (long) daysToKeep * 86_400_000L;
        List<Path> toDelete = new ArrayList<>();
        try (Stream<Path> stream = Files.list(backupRoot)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("database-"))
                    .forEach(p -> {
                        try {
                            if (Files.getLastModifiedTime(p).toMillis() < cutoffMillis) {
                                toDelete.add(p);
                            }
                        } catch (IOException ignored) {
                            // skip
                        }
                    });
        }

        if (toDelete.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No backups older than " + daysToKeep + " days were found.", "Prune Backups", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(parent,
                "Delete " + toDelete.size() + " backup folder(s) older than " + daysToKeep + " days?",
                "Confirm Prune",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        int removed = 0;
        List<String> failures = new ArrayList<>();
        for (Path p : toDelete) {
            try {
                deleteDirectoryRecursive(p);
                removed++;
            } catch (IOException ex) {
                failures.add(p.getFileName() + ": " + ex.getMessage());
            }
        }

        try (Connection c = DatabaseManager.getConnection()) {
            DatabaseManager.logSecurityEvent(c, user.getUsername(), "DATABASE_BACKUP_PRUNE",
                    "removed=" + removed + " failures=" + failures.size());
        }

        String msg = "Removed " + removed + " backup folder(s).";
        if (!failures.isEmpty()) {
            msg += "\nSome folders could not be deleted:\n" + String.join("\n", failures.subList(0, Math.min(5, failures.size())));
        }
        JOptionPane.showMessageDialog(parent, msg, "Prune Backups", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Throws {@link SecurityException} when {@code user} is null or not an administrator. */
    private static void requireAdminRights(User user) {
        if (user == null || !user.hasAdminRights()) {
            throw new SecurityException("Administrator rights are required for this action.");
        }
    }

    /** Deletes every child path under {@code dir} recursively (typically before restore). */
    private static void clearDirectoryContents(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : children.toList()) {
                deleteDirectoryRecursive(child);
            }
        }
    }

    /** Recursive delete for folders or files (visitor-based). */
    private static void deleteDirectoryRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Mirrors {@code source} tree into {@code targetRoot}, creating directories/files as needed. */
    private static void copyDirectoryTree(Path source, Path targetRoot) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path dest = targetRoot.resolve(source.relativize(dir));
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = targetRoot.resolve(source.relativize(file));
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
