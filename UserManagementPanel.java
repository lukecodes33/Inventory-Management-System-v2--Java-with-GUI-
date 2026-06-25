import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;


/** Extracted from WorkspaceShell. */
public final class UserManagementPanel {
    private UserManagementPanel() {}

        /**
     * Builds a combined admin user-management panel with add/delete workflows.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @return user management panel
     */
    public static JPanel build(User user, Connection connection) {
        WorkspaceShell.ensureAdmin(user, "User Management");
        JPanel root = WorkspaceShell.buildSectionPanel();
        root.add(WorkspaceShell.adminToolsSectionTitle("User management"));
        root.add(Box.createVerticalStrut(10));

        root.add(WorkspaceShell.adminToolsSectionTitle("Add user"));
        root.add(Box.createVerticalStrut(6));

        JPanel addForm = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(addForm);
        GridBagConstraints gab = new GridBagConstraints();
        gab.insets = new Insets(4, 0, 4, 10);

        JTextField addUsername = new JTextField();
        JPasswordField addPassword = new JPasswordField();
        JPasswordField addConfirm = new JPasswordField();
        WorkspaceShell.styleInputCompact(addUsername);
        WorkspaceShell.stylePasswordInputCompact(addPassword, addConfirm);

        JCheckBox addAdminRights = new JCheckBox("Grant administrator rights");
        AppUI.applyPanelBackground(addAdminRights);

        JPanel usernameAdminRow = new JPanel(new BorderLayout(8, 0));
        AppUI.applyPanelBackground(usernameAdminRow);
        usernameAdminRow.add(addUsername, BorderLayout.CENTER);
        usernameAdminRow.add(addAdminRights, BorderLayout.EAST);

        int row = 0;
        gab.gridx = 0;
        gab.gridy = row;
        gab.anchor = GridBagConstraints.LINE_END;
        gab.fill = GridBagConstraints.NONE;
        gab.weightx = 0;
        addForm.add(new JLabel("Username *"), gab);
        gab.gridx = 1;
        gab.anchor = GridBagConstraints.LINE_START;
        gab.fill = GridBagConstraints.HORIZONTAL;
        gab.weightx = 1;
        addForm.add(usernameAdminRow, gab);
        row++;
        gab.gridx = 0;
        gab.gridy = row;
        gab.anchor = GridBagConstraints.LINE_END;
        gab.fill = GridBagConstraints.NONE;
        gab.weightx = 0;
        addForm.add(new JLabel("Password *"), gab);
        gab.gridx = 1;
        gab.anchor = GridBagConstraints.LINE_START;
        gab.fill = GridBagConstraints.HORIZONTAL;
        gab.weightx = 1;
        addForm.add(addPassword, gab);
        row++;
        gab.gridx = 0;
        gab.gridy = row;
        gab.anchor = GridBagConstraints.LINE_END;
        gab.fill = GridBagConstraints.NONE;
        gab.weightx = 0;
        addForm.add(new JLabel("Confirm password *"), gab);
        gab.gridx = 1;
        gab.anchor = GridBagConstraints.LINE_START;
        gab.fill = GridBagConstraints.HORIZONTAL;
        gab.weightx = 1;
        addForm.add(addConfirm, gab);

        addForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(addForm);

        JButton createUser = new JButton("Create User");
        AppUI.stylePrimaryButton(createUser);
        JPanel addActions = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(addActions);
        addActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        addActions.add(createUser, BorderLayout.EAST);

        createUser.addActionListener(e -> {
            String enteredUsername = addUsername.getText().trim();
            char[] enteredPassword = addPassword.getPassword();
            char[] enteredConfirm = addConfirm.getPassword();
            try {
                if (enteredUsername.isEmpty()) {
                    JOptionPane.showMessageDialog(root, "Username is required.");
                    return;
                }
                if (!enteredUsername.matches("[A-Za-z0-9._-]{3,32}")) {
                    JOptionPane.showMessageDialog(root, "Username must be 3-32 chars and only use letters, numbers, ., _, or -.");
                    return;
                }
                if (!Arrays.equals(enteredPassword, enteredConfirm)) {
                    JOptionPane.showMessageDialog(root, "Password and confirm password do not match.");
                    return;
                }
                String policyError = AccountActions.validatePasswordPolicy(enteredPassword);
                if (policyError != null) {
                    JOptionPane.showMessageDialog(root, policyError);
                    return;
                }
                try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) AS count FROM users WHERE username = ?")) {
                    check.setString(1, enteredUsername);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt("count") > 0) {
                            JOptionPane.showMessageDialog(root, "That username already exists.");
                            return;
                        }
                    }
                }
                String hash = SecurityUtils.hashPassword(enteredPassword);
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO users (first_name, last_name, username, password, admin_rights, first_login) VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, null);
                    insert.setString(2, null);
                    insert.setString(3, enteredUsername);
                    insert.setString(4, hash);
                    insert.setInt(5, addAdminRights.isSelected() ? 1 : 0);
                    insert.setInt(6, 1);
                    insert.executeUpdate();
                }
                DatabaseManager.logSecurityEvent(
                        connection,
                        user.getUsername(),
                        "USER_CREATED",
                        "Created user '" + enteredUsername + "' (admin=" + (addAdminRights.isSelected() ? "1" : "0") + ")"
                );
                JOptionPane.showMessageDialog(root, "User created successfully. They will reset password on first sign-in.");
                addUsername.setText("");
                addPassword.setText("");
                addConfirm.setText("");
                addAdminRights.setSelected(false);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(root, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(enteredPassword, '\0');
                Arrays.fill(enteredConfirm, '\0');
            }
        });

        root.add(addActions);

        root.add(Box.createVerticalStrut(18));
        root.add(WorkspaceShell.adminToolsSectionTitle("Delete user"));
        root.add(Box.createVerticalStrut(6));

        JPanel deleteForm = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(deleteForm);
        GridBagConstraints gdb = new GridBagConstraints();
        gdb.insets = new Insets(4, 0, 4, 10);

        JTextField deleteUsername = new JTextField();
        JTextField deleteReason = new JTextField();
        WorkspaceShell.styleInputCompact(deleteUsername, deleteReason);

        int drow = 0;
        gdb.gridx = 0;
        gdb.gridy = drow;
        gdb.anchor = GridBagConstraints.LINE_END;
        gdb.fill = GridBagConstraints.NONE;
        gdb.weightx = 0;
        deleteForm.add(new JLabel("Username to delete *"), gdb);
        gdb.gridx = 1;
        gdb.anchor = GridBagConstraints.LINE_START;
        gdb.fill = GridBagConstraints.HORIZONTAL;
        gdb.weightx = 1;
        deleteForm.add(deleteUsername, gdb);
        drow++;
        gdb.gridx = 0;
        gdb.gridy = drow;
        gdb.anchor = GridBagConstraints.LINE_END;
        gdb.fill = GridBagConstraints.NONE;
        gdb.weightx = 0;
        deleteForm.add(new JLabel("Deletion reason *"), gdb);
        gdb.gridx = 1;
        gdb.anchor = GridBagConstraints.LINE_START;
        gdb.fill = GridBagConstraints.HORIZONTAL;
        gdb.weightx = 1;
        deleteForm.add(deleteReason, gdb);

        deleteForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(deleteForm);
        root.add(Box.createVerticalStrut(8));

        JButton deleteUser = new JButton("Delete User");
        AppUI.stylePrimaryButton(deleteUser);
        JPanel deleteActions = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(deleteActions);
        deleteActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteActions.add(deleteUser, BorderLayout.EAST);

        deleteUser.addActionListener(e -> {
            String targetUsername = deleteUsername.getText().trim();
            String reasonText = deleteReason.getText().trim();
            if (targetUsername.isEmpty()) {
                JOptionPane.showMessageDialog(root, "Username is required.");
                return;
            }
            if (reasonText.isEmpty()) {
                JOptionPane.showMessageDialog(root, "Deletion reason is required.");
                return;
            }
            if (targetUsername.equalsIgnoreCase(user.getUsername())) {
                JOptionPane.showMessageDialog(root, "You cannot delete your own account while signed in.");
                return;
            }
            try (PreparedStatement countPs = connection.prepareStatement("SELECT COUNT(*) AS n FROM users");
                 ResultSet crs = countPs.executeQuery()) {
                if (crs.next() && crs.getInt("n") <= 1) {
                    JOptionPane.showMessageDialog(root, "There is only one user account. Create another user before deleting this one.");
                    return;
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(root, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(
                    root,
                    "Delete user '" + targetUsername + "'?\nReason: " + reasonText,
                    "Confirm Account Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM users WHERE username = ?")) {
                deleteStmt.setString(1, targetUsername);
                int deleted = deleteStmt.executeUpdate();
                if (deleted == 0) {
                    JOptionPane.showMessageDialog(root, "No user found with that username.");
                    return;
                }
                DatabaseManager.logSecurityEvent(
                        connection,
                        user.getUsername(),
                        "USER_DELETED",
                        "Deleted user '" + targetUsername + "' | reason: " + reasonText
                );
                JOptionPane.showMessageDialog(root, "User deleted successfully.");
                deleteUsername.setText("");
                deleteReason.setText("");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(root, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        root.add(deleteActions);

        root.setAlignmentX(Component.LEFT_ALIGNMENT);
        return root;
    }
}
