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
public final class ResetPasswordPanel {
    private ResetPasswordPanel() {}

        /** Opens the shared password-reset dialog; on success closes the workspace so the user can sign in again. */
    public static JPanel build(User user, JFrame frame, AccountActions accountActions) {
        JPanel panel = WorkspaceShell.buildFormPanel("Reset Password");
        JPanel content = WorkspaceShell.buildSectionPanel();
        content.add(WorkspaceShell.buildSectionText("Update your password. After a successful change you will return to the sign-in screen."));
        content.add(Box.createVerticalStrut(14));
        JButton openDialog = new JButton("Change password...");
        AppUI.stylePrimaryButton(openDialog);
        openDialog.setAlignmentX(Component.LEFT_ALIGNMENT);
        openDialog.addActionListener(e -> {
            AccountActions.PasswordResetOutcome outcome = accountActions.showPasswordResetDialog(frame, user);
            if (outcome == AccountActions.PasswordResetOutcome.SUCCESS) {
                frame.dispose();
            }
        });
        content.add(openDialog);
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

        /** Compact strip for Administration Tools page (avoid nested full-page headings). */
    public static JPanel buildInlineForAdminTools(User user, JFrame frame, AccountActions accountActions) {
        JPanel block = WorkspaceShell.buildSectionPanel();
        block.add(WorkspaceShell.adminToolsSectionTitle("Reset password"));
        block.add(Box.createVerticalStrut(6));
        JLabel hint = WorkspaceShell.buildSectionText("Updates your signed-in password. After a successful change you return to the sign-in screen.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(hint);
        block.add(Box.createVerticalStrut(10));
        JButton openDialog = new JButton("Change password…");
        AppUI.stylePrimaryButton(openDialog);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        AppUI.applyPanelBackground(row);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(openDialog);
        openDialog.addActionListener(e -> {
            AccountActions.PasswordResetOutcome outcome = accountActions.showPasswordResetDialog(frame, user);
            if (outcome == AccountActions.PasswordResetOutcome.SUCCESS) {
                frame.dispose();
            }
        });
        block.add(row);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        return block;
    }
}
