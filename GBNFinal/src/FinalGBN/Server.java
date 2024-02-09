package FinalGBN;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static FinalGBN.Setup.recvByteMethod;

public class Server {
	private static int port = 7070;
	private static int Length = 1024;

	private static int sendWindows;

	private static int recvWindows = 10;


	private static InetAddress sendAddress;

	private static int sendPort;

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

	public static void send(DatagramSocket socket, byte[] sendBytes, InetAddress address, int targetPort) throws IOException {
		//input from the user part to ask for window size which will also be our recvWindow
		System.out.println("Please enter the window size that you would like to receive.");
		System.out.println("1 = SAW and for GBN enter the desired size");
		Scanner size = new Scanner(System.in);
		sendWindows = size.nextInt();

		//Random number stuff
		Random rand = new Random();
		 int upperbound = 100;
		System.out.println("Please enter the percentage loss of packets between 0 and 99");
		Scanner sc = new Scanner(System.in);
		int packetLoss = sc.nextInt();


		int numberPack = sendBytes.length / Length;
		int lastSize = sendBytes.length % Length;
		Map<Integer, Integer> ackMap = new ConcurrentHashMap<>();
		int baseSeq = 0;
		int nextSeq = 0;
		byte[] bytes;
		ACKpacket recvACK = new ACKpacket(socket, ackMap);
		recvACK.start();

		//starts our timer
		long startTime = System.nanoTime();

		while (nextSeq <= numberPack || ackMap.size() != 0) {
			int oldBase = baseSeq;
			int oldSize = ackMap.size();
			for (int i = 0; i < oldSize; i++) {
				Integer now = (oldBase + i) % 256;
				if (ackMap.get(now) == null)
					System.out.println("its null: " + now);
				if (ackMap.get(now) == -1)
					break;
				baseSeq = (baseSeq + 1) % 256;
				ackMap.remove(now);
			}
			int canSend = sendWindows - ackMap.size();
			int ackNumber = (baseSeq + ackMap.size()) % 256;
			for (int i = 0; i < canSend; i++) {
				if (nextSeq > numberPack)
					break;
				int sequence = (ackNumber + i) % 256;
				int sendLength = nextSeq < numberPack ? Length : lastSize;
				int end = nextSeq < numberPack ? 1 : 0;

				//header stuff
				bytes = new byte[sendLength + 4];
				bytes[0] = (byte) ((sendLength >> 8) & 0xff);
				bytes[1] = (byte) (sendLength & 0xff);
				bytes[2] = (byte) sequence;
				bytes[sendLength + 3] = (byte) end;
				for (int j = 3; j < sendLength + 3; j++) {
					bytes[j] = sendBytes[nextSeq * Length + j - 3];
				}
				DatagramPacket datagramPacket = new DatagramPacket(bytes, sendLength + 4, address, targetPort);

				// this says if a rand num is less than a our packet loss we drop the packet
				//if (rand.nextInt(upperbound) < packetLoss) {
				//  System.out.println("Packet: " + sequence + " lost");
				//}
				//else
				{
					System.out.println("Packet: " + sequence + " sent");
					socket.send(datagramPacket);
				}
				nextSeq++;
				ackMap.put(sequence, -1);
				new TimeOut(ackMap, socket, datagramPacket, sequence).start();
			}
		}
		// this gives us the entire amount of time it takes to send the file
		long endTime = System.nanoTime();
		long duration = (endTime - startTime) / 1000000;
		System.out.println("The total amount of time for the file to send: " + duration + " ms");
		return;
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

	public static void send(DatagramSocket socket, byte[] sendBytes) throws IOException { //send method
		send(socket, sendBytes, sendAddress, sendPort);
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
		try (DatagramSocket socket = new DatagramSocket(port)) {
			File recvFile = new File("C:\\Users\\yjhsq\\OneDrive\\Desktop\\GBNFinal\\src\\COSC635_P2_DataRecieved.txt");
			try (FileOutputStream fileOutputStream = new FileOutputStream(recvFile)) {
				System.out.println("The Server is running and ready to receive the file from the client");
				System.out.println("Please run the Client file");
				socket.setSoTimeout(1000);
				recvByteMethod(fileOutputStream, socket);
				System.out.println("The file was Successfully received from the client");
			}
		} catch (BindException e) {
			System.out.println("Port " + port + " is already in use. Please check if another instance of the server is running.");
		}
		System.out.println("The file was Successfully put in the txt file");
	}


}