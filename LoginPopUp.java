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
     * Shows a modal login dialog and returns submitted credentials.
     * If the user closes the window without signing in, username is {@code null}.
     */
    public LoginCredentials createLoginPopUp() {
        JDialog dialog = new JDialog((java.awt.Frame) null, "Inventory Management System", true);
        dialog.setSize(420, 230);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel header = new JLabel("Sign In", SwingConstants.LEFT);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(header, gbc);

        JLabel userLabel = new JLabel("Username");
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(userLabel, gbc);

        JTextField userText = new JTextField(20);
        userText.setBorder(AppUI.newRoundedBorder(8));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(userText, gbc);

        JLabel passwordLabel = new JLabel("Password");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(passwordLabel, gbc);

        JPasswordField passwordText = new JPasswordField(20);
        passwordText.setBorder(AppUI.newRoundedBorder(8));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(passwordText, gbc);

        JButton loginButton = new JButton("Sign In");
        AppUI.stylePrimaryButton(loginButton);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(loginButton, gbc);
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

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                submitted[0] = false;
            }
        });

        AppUI.styleWindow(dialog);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        if (!submitted[0]) {
            return new LoginCredentials(null, new char[0]);
        }
        return new LoginCredentials(usernameHolder[0], passwordHolder[0] == null ? new char[0] : passwordHolder[0]);
    }
}
