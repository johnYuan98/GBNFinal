package FinalGBN;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ClientGUI extends JFrame implements ActionListener {
    private JTextField sendWindowsField, packetLossField;
    private JTextArea consoleArea;

    public ClientGUI() {
        setTitle("FinalGBN.Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 300);
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new GridLayout(4, 2));

        sendWindowsField = new JTextField();
        packetLossField = new JTextField();
        consoleArea = new JTextArea();
        consoleArea.setEditable(false);

        inputPanel.add(new JLabel("Window size (1 for SAW, 2+ for GBN):"));
        inputPanel.add(sendWindowsField);
        inputPanel.add(new JLabel("Packet Loss (%)"));
        inputPanel.add(packetLossField);

        JButton startButton = new JButton("Start Client");
        startButton.addActionListener(this);
        inputPanel.add(startButton);

        add(inputPanel, BorderLayout.NORTH);

        consoleArea = new JTextArea(10, 30);
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(consoleArea);
        add(scrollPane, BorderLayout.CENTER);

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    int sendWindows = Integer.parseInt(sendWindowsField.getText());
                    int packetLoss = Integer.parseInt(packetLossField.getText()); // Assuming a method for packet loss

                    DatagramSocket socket = new DatagramSocket(Client.getOwnPort());
                    InetAddress address = InetAddress.getByName(Client.getHost());
                    byte[] sendBytes = Client.getByteArray(new File("C:\\Users\\yjhsq\\OneDrive\\Desktop\\GBNFinal\\src\\COSC635_P2_DataSent.txt")).toByteArray();

                    Client.send(socket, sendBytes, address, Client.getTargetPort(), sendWindows, packetLoss, this::publish);
                } catch (NumberFormatException ex) {
                    publish("Invalid input format. Please enter numeric values.\n");
                } catch (IOException ex) {
                    publish("IO Exception: " + ex.getMessage() + "\n");
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    consoleArea.append(message + "\n");
                }
            }

            @Override
            protected void done() {
                consoleArea.append("Operation completed.\n");
            }
        };
        worker.execute();
    }


    public static void main(String[] args) {
        new ClientGUI();
    }
}
