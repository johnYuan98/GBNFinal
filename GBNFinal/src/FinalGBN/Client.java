package FinalGBN;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

//import static FinalGBN.Setup.*;

//Calls the methods from the class

public class Client {

    private static String host = "localhost";
    private static int ownPort = 8080;
    private static int targetPort = 7070;
    private static int Length = 1024;
    private static int sendWindows;
    private static int recvWindows = sendWindows;
    private static InetAddress sendAddress;
    private static int sendPort;

    public static void setSendWindows(int windows) {
        sendWindows = windows;
    }

    public static void setOwnPort(int port) {
        ownPort = port;
    }

    public static void setHost(String hostname) {
        host = hostname;
    }

    public static void setTargetPort(int port) {
        targetPort = port;
    }

    // Public getter methods
    public static int getOwnPort() {
        return ownPort;
    }

    public static String getHost() {
        return host;
    }

    public static int getTargetPort() {
        return targetPort;
    }

    public interface ClientCallback {
        void updateStatus(String message);
    }

    public static ByteArrayOutputStream getByteArray(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = fileInputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result;
    }

    public static void send(DatagramSocket socket, byte[] sendBytes, InetAddress address, int targetPort, int sendWindows, int packetLoss, ClientCallback callback) throws IOException {
        int numberPack = sendBytes.length / Length;
        int lastSize = sendBytes.length % Length;
        Map<Integer, Integer> ackMap = new ConcurrentHashMap<>();
        int baseSeq = 0;
        int nextSeq = 0;
        byte[] bytes;
        ACKpacket recvACK = new ACKpacket(socket, ackMap);
        recvACK.start();

        long startTime = System.nanoTime();

        while (nextSeq <= numberPack || !ackMap.isEmpty()) {
            int oldBase = baseSeq;
            int oldSize = ackMap.size();
            for (int i = 0; i < oldSize; i++) {
                Integer now = (oldBase + i) % 256;
                if (ackMap.get(now) == null || ackMap.get(now) == -1) {
                    break;
                }
                baseSeq = (baseSeq + 1) % 256;
                ackMap.remove(now);
            }
            int canSend = sendWindows - ackMap.size();
            int ackNumber = (baseSeq + ackMap.size()) % 256;
            for (int i = 0; i < canSend; i++) {
                if (nextSeq > numberPack) {
                    break;
                }
                int sequence = (ackNumber + i) % 256;
                int sendLength = nextSeq < numberPack ? Length : lastSize;
                int end = nextSeq < numberPack ? 1 : 0;

                bytes = new byte[sendLength + 4];
                bytes[0] = (byte) ((sendLength >> 8) & 0xff);
                bytes[1] = (byte) (sendLength & 0xff);
                bytes[2] = (byte) sequence;
                bytes[sendLength + 3] = (byte) end;
                for (int j = 3; j < sendLength + 3; j++) {
                    bytes[j] = sendBytes[nextSeq * Length + j - 3];
                }
                DatagramPacket datagramPacket = new DatagramPacket(bytes, sendLength + 4, address, targetPort);

                Random rand = new Random();
                if (rand.nextInt(100) < packetLoss) {
                    callback.updateStatus("Packet: " + sequence + " lost");
                } else {
                    callback.updateStatus("Packet: " + sequence + " sent");
                    socket.send(datagramPacket);
                }
                nextSeq++;
                ackMap.put(sequence, -1);
                new TimeOut(ackMap, socket, datagramPacket, sequence).start();
            }
        }

        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        callback.updateStatus("The total amount of time for the file to send: " + duration + " ms");
    }

    public static byte[] receive(DatagramSocket socket) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] recvBytes = new byte[1028];
        int length;
        int baseSeq = 0;
        Map<Integer, byte[]> cache = new HashMap<>();
        int end = 1;
        long startTime = System.nanoTime();
        while (true) {
            DatagramPacket packet = new DatagramPacket(recvBytes, recvBytes.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {

                break;
            }
            //our send info but for receive
            length = recvBytes[0] & 0xff;
            length = length << 8 | recvBytes[1] & 0xff;
            int sequence = recvBytes[2] & 0xff;
            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            sendAddress = address;
            sendPort = port;
            byte[] recvAck = new byte[2];
            recvAck[0] = recvBytes[2];
            recvAck[1] = recvBytes[length + 3];
            byte[] newArray = Arrays.copyOfRange(recvBytes, 3, 3 + length);
            int old1 = (baseSeq - recvWindows + 256) % 256;
            int old2 = (baseSeq - 1 + 256) % 256;
            int new1 = baseSeq;
            int new2 = (baseSeq + recvWindows - 1) % 256;
            if (((old1 > old2) && (sequence >= old1 || sequence <= old2)) || (sequence >= old1 && sequence <= old2)) {
                DatagramPacket ackPocket = new DatagramPacket(recvAck, recvAck.length, address, port);  //sends the ack packet
                socket.send(ackPocket);
                System.out.println("Packet" + sequence + " received");
                continue;
            }
            if (((new1 > new2) && (sequence >= new1 || sequence <= new2)) || (sequence >= new1 && sequence <= new2)) {
                if (sequence == baseSeq) {
                    byteArrayOutputStream.write(newArray, 0, newArray.length);
                    baseSeq = (baseSeq + 1) % 256;
                    while (cache.get(baseSeq) != null) {
                        byteArrayOutputStream.write(cache.get(baseSeq), 0, cache.get(baseSeq).length);
                        cache.remove(baseSeq);
                        baseSeq = (baseSeq + 1) % 256;
                    }
                } else {
                    cache.put(sequence, newArray);
                }
                DatagramPacket ackPocket = new DatagramPacket(recvAck, recvAck.length, address, port);  //sends ack
                if (Math.random() > 0)
                    socket.send(ackPocket);
                System.out.println("packet" + sequence + " received");
            }
            if ((recvBytes[length + 3] & 0xff) == 0)
                end = 0;

        }


        return byteArrayOutputStream.toByteArray();
    }



    static void recvByteMethod(FileOutputStream fileOutputStream, DatagramSocket socket) throws IOException {
        byte[] recvBytes;
        while (true) { //allows for packets to be received
            recvBytes = receive(socket);
            if (recvBytes.length != 0) {
                System.out.println(recvBytes.length);
                fileOutputStream.write(recvBytes);
                break;
            }
        }

    }




    public static void main(String[] args) throws IOException {
        File sendFile = new File("C:\\Users\\yjhsq\\OneDrive\\Desktop\\GBNFinal\\src\\COSC635_P2_DataSent.txt");
        byte[] sendBytes = getByteArray(sendFile).toByteArray();
        DatagramSocket socket = new DatagramSocket(ownPort);
        InetAddress address = InetAddress.getByName(host);

        // Update to match the new method signature
        // Example: send(socket, sendBytes, address, targetPort, 10 /* window size */, 20 /* packet loss */, null /* callback */);
        // Adjust the window size and packet loss as per your requirements
    }

}