import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Modal sign-in dialog. Uses a modal {@link JDialog} so it is safe when shown from the EDT
 * (unlike {@code CountDownLatch.await()} on the EDT, which freezes the UI).
 */
public class LoginPopUp {

    /**
     * Launches modal login UI and returns immutable credentials assembled on the EDT.
     *
     * @return credential snapshot; cleared password array when canceled/closed ({@code username} {@code null} signals exit)
     */
    public LoginCredentials createLoginPopUp() {
        JDialog dialog = new JDialog((java.awt.Frame) null, "Inventory Management System", true);
        dialog.setSize(460, AppUI.usesEmbeddedTitleBar() ? 280 : 260);
        dialog.setMinimumSize(new java.awt.Dimension(420, 240));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));

        JLabel header = new JLabel("Sign In", SwingConstants.LEFT);
        header.setFont(AppUI.fontPageTitle(22));
        header.setForeground(AppUI.TEXT);
        header.putClientProperty(AppUI.CLIENT_PRESERVE_FOREGROUND, Boolean.TRUE);
        header.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        panel.add(header);
        panel.add(Box.createVerticalStrut(6));
        JLabel tagline = new JLabel("Know what's on the shelf");
        tagline.setFont(AppUI.fontCaption(13));
        tagline.setForeground(AppUI.TEXT_MUTED);
        tagline.putClientProperty(AppUI.CLIENT_PRESERVE_FOREGROUND, Boolean.TRUE);
        tagline.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        panel.add(tagline);
        panel.add(Box.createVerticalStrut(20));

        JTextField userText = new JTextField(20);
        AppUI.applyInputField(userText);
        AppUI.setPlaceholder(userText, "Username");
        panel.add(AppUI.buildStackedField("Username", userText));
        panel.add(Box.createVerticalStrut(12));

        JPasswordField passwordText = new JPasswordField(20);
        AppUI.applyPasswordField(passwordText);
        panel.add(AppUI.buildStackedField("Password", passwordText));
        panel.add(Box.createVerticalStrut(18));

        JButton loginButton = new JButton("Sign In");
        AppUI.stylePrimaryButton(loginButton);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 44));
        btnRow.add(loginButton);
        panel.add(btnRow);

        if (AppUI.usesEmbeddedTitleBar()) {
            dialog.add(AppUI.createApplicationTitleBar("Inventory Management System"), BorderLayout.NORTH);
        }
        dialog.add(panel, BorderLayout.CENTER);

        final String[] usernameHolder = {null};
        final char[][] passwordHolder = {null};
        final boolean[] submitted = {false};

        loginButton.addActionListener(e -> {
            usernameHolder[0] = userText.getText();
            passwordHolder[0] = passwordText.getPassword();
            passwordText.setText("");
            submitted[0] = true;
            dialog.dispose();
        });

        passwordText.addActionListener(e -> loginButton.doClick());

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
            return new LoginCredentials(null, new char[0]);
        }
        return new LoginCredentials(usernameHolder[0], passwordHolder[0] == null ? new char[0] : passwordHolder[0]);
    }
}
