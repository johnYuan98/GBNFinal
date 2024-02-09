
package FinalGBN;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;

public class ACKpacket extends Thread {
	 private DatagramSocket socket;

	    private Map<Integer, Integer> ackMap;

	    public ACKpacket(DatagramSocket socket, Map<Integer, Integer> ackMap) {
	        this.socket = socket;
	        this.ackMap = ackMap;
	    }

	    @Override
	    public void run() {
	        int end = 1;
	        while (true) {

	            if (end == 0 && ackMap.size() == 0)
	                break;
	            byte[] ackByte = new byte[2];
	            DatagramPacket packet = new DatagramPacket(ackByte, ackByte.length);
	            try {
	                socket.receive(packet);
	            } catch (IOException e) {
	            }
	            
	            
	            int ack = 0xff & ackByte[0];
	            if ((0xff & ackByte[1]) == 0)
	                end = 0;
	            if (ackMap.get(ack) != null) {
	                ackMap.put(ack, 1);
	            }
	            System.out.println("Received acknowledgment : " + ack);
	            if (end == 0 && ackMap.isEmpty()) //stops everyting if null or aborted
	                break;
	        }
	    }
}
