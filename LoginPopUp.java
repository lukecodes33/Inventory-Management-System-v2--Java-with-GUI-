import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class LoginPopUp {

    // Method to create and display the login pop-up
    public Map<String, String> createLoginPopUp() {

        // Create a CountDownLatch to block until the user has logged in
        CountDownLatch latch = new CountDownLatch(1);

        // Create a JFrame (main window)
        JFrame frame = new JFrame("Login Form");
        frame.setSize(300, 150);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(null);

        // Create JLabel and JTextField for username
        JLabel userLabel = new JLabel("Username:");
        userLabel.setBounds(10, 10, 80, 25);
        frame.add(userLabel);

        JTextField userText = new JTextField(20);
        userText.setBounds(100, 10, 160, 25);
        frame.add(userText);

        // Create JLabel and JPasswordField for password
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setBounds(10, 40, 80, 25);
        frame.add(passwordLabel);

        JPasswordField passwordText = new JPasswordField(20);
        passwordText.setBounds(100, 40, 160, 25);
        frame.add(passwordText);

        // Create a login button
        JButton loginButton = new JButton("Login");
        loginButton.setBounds(100, 80, 160, 25);
        frame.add(loginButton);

        // Create a map to hold login data
        Map<String, String> loginData = new HashMap<>();

        // Action listener for the login button
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = userText.getText();
                // Get password securely as char array
                char[] password = passwordText.getPassword();

                // Convert password to a String for demonstration (not recommended in production)
                String passwordString = new String(password);

                // Put username and password into the map
                loginData.put("username", username);
                loginData.put("password", passwordString);

                // Clear password from memory
                passwordText.setText("");

                frame.dispose(); // Close the login frame
                latch.countDown(); // Signal that login is complete
            }
        });

        frame.setLocationRelativeTo(null);

        // Show the frame
        frame.setVisible(true);

        try {
            latch.await(); // Wait for the login to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
        }

        return loginData; // Return the map with username and password
    }
}
