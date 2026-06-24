import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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

        JPanel panel = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel header = new JLabel("Sign In", SwingConstants.LEFT);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(header, gbc);

        JLabel userLabel = new JLabel("Username", SwingConstants.RIGHT);
        userLabel.setForeground(AppUI.TEXT_MUTED);
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        panel.add(userLabel, gbc);

        JTextField userText = new JTextField(20);
        AppUI.applyInputField(userText);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 1.0;
        panel.add(userText, gbc);

        JLabel passwordLabel = new JLabel("Password", SwingConstants.RIGHT);
        passwordLabel.setForeground(AppUI.TEXT_MUTED);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        panel.add(passwordLabel, gbc);

        JPasswordField passwordText = new JPasswordField(20);
        AppUI.applyPasswordField(passwordText);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 1.0;
        panel.add(passwordText, gbc);

        JButton loginButton = new JButton("Sign In");
        AppUI.stylePrimaryButton(loginButton);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(16, 0, 0, 0);
        panel.add(loginButton, gbc);

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
