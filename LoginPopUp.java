import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * A class that represents a login pop-up for an inventory management system.
 * This pop-up collects the username and password from the user and returns
 * the credentials upon successful login.
 */

public class LoginPopUp {

    /**
     * Creates and displays the login pop-up window.
     *
     * @return a Map containing the entered username and password, where
     *         the key "username" maps to the username string and the key
     *         "password" maps to the password string.
     */

    public Map<String, String> createLoginPopUp() {

        // CountDownLatch to block until the user has logged in
        CountDownLatch latch = new CountDownLatch(1);


        JFrame frame = new JFrame("Inventory Management System");
        frame.setSize(350, 150);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(null);

        JLabel userLabel = new JLabel("Username:");
        userLabel.setBounds(10, 10, 80, 25);
        frame.add(userLabel);
        JTextField userText = new JTextField(20);
        userText.setBounds(100, 10, 220, 25);
        frame.add(userText);


        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setBounds(10, 40, 80, 25);
        frame.add(passwordLabel);
        JPasswordField passwordText = new JPasswordField(20);
        passwordText.setBounds(100, 40, 220, 25);
        frame.add(passwordText);

        JButton loginButton = new JButton("Login");
        loginButton.setBounds(220, 80, 100, 25);
        frame.add(loginButton);

        // Map to store login data
        Map<String, String> loginData = new HashMap<>();


        loginButton.addActionListener(new ActionListener() {
            @Override

            //Stores username as a string and password as an array before converting to string, i would like to find another
            //way to do this so that the password is more secure but while it is being built this will do for now
            public void actionPerformed(ActionEvent e) {
                String username = userText.getText();
                char[] password = passwordText.getPassword();
                String passwordString = new String(password);

                loginData.put("username", username);
                loginData.put("password", passwordString);

                passwordText.setText("");

                frame.dispose();
                latch.countDown();
            }
        });

        frame.setLocationRelativeTo(null);


        frame.setVisible(true);

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return loginData;
    }
}
