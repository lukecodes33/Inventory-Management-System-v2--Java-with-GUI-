import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;

/**
 * First-run wizard when the {@code users} table is empty: capture display name, derive a unique login username,
 * and set an initial password for the first administrator account.
 */
public final class FirstAdministratorSetupDialog {

    public record Outcome(String firstName, String lastName, String username, char[] password) {
    }

    private FirstAdministratorSetupDialog() {
    }

    /**
     * Shows a modal setup dialog on the EDT.
     *
     * @param connection open JDBC connection (used only to reserve a unique username)
     * @return submitted outcome, or {@code null} when the dialog is dismissed without creating an account
     */
    public static Outcome show(Connection connection) {
        JDialog dialog = new JDialog((java.awt.Frame) null, "Welcome — Create administrator", true);
        dialog.setSize(500, AppUI.usesEmbeddedTitleBar() ? 420 : 400);
        dialog.setMinimumSize(new java.awt.Dimension(460, 380));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel header = new JLabel("Set up your workspace", SwingConstants.LEFT);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(header, gbc);

        JLabel sub = new JLabel(
                "<html><body style='width:400px;color:#a1a1a1;'>No user accounts exist yet. Enter your name and a strong password.<br>"
                        + "You will be the first administrator.</body></html>"
        );
        gbc.gridy = 1;
        panel.add(sub, gbc);

        JLabel nameLabel = new JLabel("Your name", SwingConstants.RIGHT);
        nameLabel.setForeground(AppUI.TEXT_MUTED);
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        panel.add(nameLabel, gbc);

        JTextField nameField = new JTextField(24);
        AppUI.applyInputField(nameField);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 1.0;
        panel.add(nameField, gbc);

        JLabel usernameHint = new JLabel(" ");
        usernameHint.setFont(usernameHint.getFont().deriveFont(Font.PLAIN, 11f));
        usernameHint.setForeground(AppUI.TEXT_MUTED);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(usernameHint, gbc);

        Runnable refreshUsernamePreview = () -> {
            try {
                String trimmed = nameField.getText().trim();
                if (trimmed.isEmpty()) {
                    usernameHint.setText(" ");
                    return;
                }
                String derived = deriveUniqueUsername(connection, trimmed);
                usernameHint.setText("You will sign in with username: " + derived);
            } catch (SQLException ex) {
                usernameHint.setText("(Could not preview username)");
            }
        };
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshUsernamePreview.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshUsernamePreview.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshUsernamePreview.run();
            }
        });

        JLabel passwordLabel = new JLabel("Password", SwingConstants.RIGHT);
        passwordLabel.setForeground(AppUI.TEXT_MUTED);
        gbc.gridwidth = 1;
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        panel.add(passwordLabel, gbc);

        JPasswordField passwordField = new JPasswordField(24);
        AppUI.applyPasswordField(passwordField);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 1.0;
        panel.add(passwordField, gbc);

        JLabel confirmLabel = new JLabel("Confirm password", SwingConstants.RIGHT);
        confirmLabel.setForeground(AppUI.TEXT_MUTED);
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        panel.add(confirmLabel, gbc);

        JPasswordField confirmField = new JPasswordField(24);
        AppUI.applyPasswordField(confirmField);
        gbc.gridx = 1;
        panel.add(confirmField, gbc);

        JButton createButton = new JButton("Create administrator account");
        AppUI.stylePrimaryButton(createButton);
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(16, 0, 0, 0);
        panel.add(createButton, gbc);

        dialog.add(panel, BorderLayout.CENTER);
        if (AppUI.usesEmbeddedTitleBar()) {
            dialog.add(AppUI.createApplicationTitleBar("Welcome — Create administrator"), BorderLayout.NORTH);
        }

        final Outcome[] holder = {null};
        final boolean[] submitted = {false};

        createButton.addActionListener(e -> {
            String fullName = nameField.getText().trim();
            if (fullName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter your name.");
                return;
            }

            String first;
            String last;
            int sp = fullName.indexOf(' ');
            if (sp < 0) {
                first = fullName;
                last = "";
            } else {
                first = fullName.substring(0, sp);
                last = fullName.substring(sp + 1).trim();
            }

            char[] pw = passwordField.getPassword();
            char[] confirm = confirmField.getPassword();
            try {
                if (!Arrays.equals(pw, confirm)) {
                    JOptionPane.showMessageDialog(dialog, "Password and confirm password do not match.");
                    return;
                }
                String policyError = AccountActions.validatePasswordPolicy(pw);
                if (policyError != null) {
                    JOptionPane.showMessageDialog(dialog, policyError);
                    return;
                }

                String username;
                try {
                    username = deriveUniqueUsername(connection, fullName);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(dialog, "Could not assign a username: " + ex.getMessage());
                    return;
                }

                passwordField.setText("");
                confirmField.setText("");
                holder[0] = new Outcome(first, last, username, pw);
                submitted[0] = true;
                Arrays.fill(confirm, '\0');
                dialog.dispose();
            } finally {
                if (!submitted[0]) {
                    Arrays.fill(pw, '\0');
                    Arrays.fill(confirm, '\0');
                }
            }
        });

        confirmField.addActionListener(e -> createButton.doClick());

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                submitted[0] = false;
            }
        });

        AppUI.applyWindowChrome(dialog);
        AppUI.styleWindow(dialog);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        if (!submitted[0]) {
            if (holder[0] != null && holder[0].password() != null) {
                Arrays.fill(holder[0].password(), '\0');
            }
            return null;
        }
        return holder[0];
    }

    static String deriveUniqueUsername(Connection connection, String displayName) throws SQLException {
        String slug = displayName.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        slug = slug.replaceAll("[^a-z0-9._-]", "");
        if (slug.isEmpty()) {
            slug = "owner";
        }
        while (slug.length() < 3) {
            slug = slug + "0";
        }
        if (slug.length() > 32) {
            slug = slug.substring(0, 32);
        }

        String candidate = slug;
        int n = 2;
        while (usernameExists(connection, candidate)) {
            String suffix = String.valueOf(n++);
            int maxBase = Math.max(1, 32 - suffix.length());
            String base = slug.length() <= maxBase ? slug : slug.substring(0, maxBase);
            candidate = base + suffix;
            if (candidate.length() > 32) {
                candidate = candidate.substring(0, 32);
            }
        }
        return candidate;
    }

    private static boolean usernameExists(Connection connection, String username) throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(?))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("count") > 0;
            }
        }
    }
}
