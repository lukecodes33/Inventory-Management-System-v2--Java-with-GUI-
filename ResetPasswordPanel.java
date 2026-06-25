import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

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
