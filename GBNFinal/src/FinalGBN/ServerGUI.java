package FinalGBN;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class ServerGUI extends JFrame implements ActionListener {
    private JTextArea consoleArea;

    public ServerGUI() {
        setTitle("FinalGBN.Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 150);
        setLayout(new GridLayout(2, 1));

        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(consoleArea);
        add(new JLabel("Server Output:"));
        add(scrollPane);

        JButton startButton = new JButton("Start Server");
        startButton.addActionListener(this);
        add(startButton);

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Thread serverThread = new Thread(() -> {
            try {
                Server.main(new String[0]); // Call the main method of Server
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        serverThread.start();
    }

    public static void main(String[] args) {
        new ServerGUI();
    }
}
