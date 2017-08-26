import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver{
	
	private static DatagramSocket receiverSocket;
	private static InetAddress senderIPAddress;
	private static int senderPort;
	private static String[] buffer;

	private static final int AVERAGE_DELAY = 100;
	private static ArrayList<String> linesToWrite = new ArrayList<String>();
	private static ArrayList<String> rcv_status = new ArrayList<String>();
	private static int receiver_port;
	private static String fileName;
	
	private static int MSS;
	private static int MWS;
	private static int Receiver_Seq; // receiver's sequence number
	private static int Sender_Seq; //Sender's sequnce number;
	private static int Receiver_SeqACK; // sequence ack num to reply sender
	private static int Sender_SeqAck; // sender's sequence ack num
	private static int size;
	private static int Sender_Seq_base;
	private static Random random = new Random();
	private static long start_time;
	private static long end_time;
	private static int AmountReceivedData;
	private static int NumSeg;
	private static int NumDupSeg;

	// convert datapacket to string
	private static String getData(DatagramPacket request) throws Exception{

	      return new String(request.getData(),"UTF-8");
	}
	
	// write received file
	public static void writeToFile(){

		try{      
			FileWriter fw = new FileWriter(fileName);
			PrintWriter pw = new PrintWriter(fw);
			for(int i = 0; i < size; i++){
	    		  pw.print(buffer[i]);
	    	  }
	    	  pw.close();
	    	  							
		}catch (IOException e){
			System.out.println("Error!");
		}
	}
	
	// write receiver log
	public static void write_logFile(){
		try{      
			FileWriter fw = new FileWriter("Receiver_Log.txt");
			PrintWriter pw = new PrintWriter(fw);
			for(int i = 0; i < rcv_status.size(); i++){
	    		  pw.println(rcv_status.get(i));
	    	  }
	    	  AmountReceivedData = size * MSS;
	    	  NumSeg = size;
	    	  pw.println("Amount of Data Received(in bytes) = " + AmountReceivedData);
	    	  pw.println("Number of Data Segments Received =  " + NumSeg);
	    	  pw.println("Number of duplicate segments received =  " + NumDupSeg);
	    	  pw.close();
	    	  rcv_status = null;								
		}catch (IOException e){
			System.out.println("Error!");
		}
	}
	
	// send and receive message
	public static String send(byte[] sendData) throws Exception{
		receiverSocket.send(new DatagramPacket(sendData,sendData.length,senderIPAddress,senderPort)); //catch Exception
		DatagramPacket reply = new DatagramPacket(new byte[1024],1024);
		receiverSocket.receive(reply);
		String replyMessage = getData(reply);
		return replyMessage;
	}
	
	/*
	 * use &# as separator between header and real data
 	 */
	public static String createSYNHeader(int Receiver_Seq,int Receiver_SeqACK){
		String header = Receiver_Seq + " "+ Receiver_SeqACK + " "+"SYN&#";
		return header;

	}

	public static String createHeader(int Receiver_Seq, int Receiver_SeqACK){
		String header = Receiver_Seq + " " + Receiver_SeqACK + " " +"&#";
		return header;
	}

	public static String createFINHeader(int Receiver_Seq, int Receiver_SeqACK){
		String header = Receiver_Seq + " " +Receiver_SeqACK  + " "+ "FIN&#";
		return header;
	}
	
	public static String getHeader(String message){
		String header = message.split("&#")[0];
		return header;
		
	}
	
	public static String getData(String message){
		String data = message.split("&#")[1];
		return data;
	}
	
	public static int check_state(String header){
		if (header.contains("SYN")){
			return 0;
		}else if(header.contains("FIN")){
			return 2;
		}else{
			return 1;
		}
	}
	
	//receive three way hand shake from sender
	public static boolean set_connection(String header)throws Exception{	
			String status = "";
			Sender_Seq = Integer.valueOf(header.split(" ")[0]);
			MWS = Integer.valueOf(header.split(" ")[2]);
			MSS = Integer.valueOf(header.split(" ")[3]);
			Receiver_Seq = random.nextInt(100);
			end_time = System.currentTimeMillis() - start_time;
			status = "rcv\t" + end_time + "\tS\t" + Sender_Seq + "\t0\t"+  0;
			rcv_status.add(status);
			
			System.out.println("Receiving the first connection header,  Sender_Seq= " + Sender_Seq);
			
			Receiver_SeqACK = Sender_Seq +1;
			String new_header = createSYNHeader(Receiver_Seq,Receiver_SeqACK);
			byte[] sendData =new_header.getBytes();
			
			end_time = System.currentTimeMillis() - start_time;
			status = "snd\t" + end_time + "\tSA\t" + Receiver_Seq + "\t0\t"+  Receiver_SeqACK;
			rcv_status.add(status);
			
			System.out.println("Sending the first connection header,  Receiver_Seq= " + Receiver_Seq + " Receiver_SeqACK=  "+ Receiver_SeqACK);
			String replyMessage = send(sendData);
			header = getHeader(replyMessage);
			
			
			Sender_Seq = Integer.valueOf(header.split(" ")[0]);
			Sender_SeqAck = Integer.valueOf(header.split(" ")[1]);
			System.out.println("Receiving the second connection header,  Sender_Seq= " + Sender_Seq + " Sender_SeqAck = "+ Sender_SeqAck);
			Sender_Seq_base = Sender_Seq;
			
			end_time = System.currentTimeMillis() - start_time;
			status = "rcv\t" + end_time + "\tA\t" + Sender_Seq + "\t0\t"+  Sender_SeqAck;
			rcv_status.add(status);
			return true;
	}
	

	//receive data from sender
	public static void receiveData() throws Exception{
		String status = "";
		NumDupSeg = 0;
		
		int Next_Seq_Num = Sender_Seq;
		
		DatagramPacket reply = new DatagramPacket(new byte[1024],1024);
		receiverSocket.receive(reply);
		String message = getData(reply);
		String header = getHeader(message);
		String data = getData(message);

		
		
		Sender_Seq = Integer.valueOf(header.split(" ")[0]);
		Sender_SeqAck = Integer.valueOf(header.split(" ")[1]);
		size = Integer.valueOf(header.split(" ")[2]);
		int j = 1;
		System.out.println(j +"th message: " + message);
		j += 1;
		buffer = new String[size];
		int buffer_size = 0;
		System.out.println("%%%%%%%%%%%%%%%%size =  " + size);
		
		while(true){	
			

			Sender_Seq = Integer.valueOf(header.split(" ")[0]);
			Sender_SeqAck = Integer.valueOf(header.split(" ")[1]);
			System.out.println("Receiving data,  Sender_Seq= " + Sender_Seq + " Sender_SeqAck = "+ Sender_SeqAck);
			end_time = System.currentTimeMillis() - start_time;
			status = "rcv\t" + end_time + "\tD\t" + Sender_Seq + "\t"+MSS+"\t"+  Sender_SeqAck;
			rcv_status.add(status);

			
			
			if(Sender_Seq == Next_Seq_Num){
				int base = 0;
				System.out.println("^^^^^^^^^^Sender_Seq_base = " + Sender_Seq_base);
				int w = (Sender_Seq-Sender_Seq_base)/MSS;
				System.out.println("(Sender_Seq-Sender_Seq_base)/MSS = " + w);
				if (buffer[(Sender_Seq-Sender_Seq_base)/MSS] == null){
					buffer[(Sender_Seq-Sender_Seq_base)/MSS] = data;
					buffer_size += 1;
					int x = (Sender_Seq-Sender_Seq_base)/MSS;
					System.out.println("@@@@@@@wirting data of index :" + x +" data_size = "+ buffer_size);
					}
				Receiver_SeqACK = Sender_Seq_base;
				System.out.println("!!!!!!!!sender_seq = " + Sender_Seq + " Next_seq_num = " + Next_Seq_Num + "data_size = " + buffer_size);
				while(base < size && buffer[base] != null){
					Receiver_SeqACK += MSS;
					base++;
				}

					header = createHeader(Receiver_Seq,Receiver_SeqACK);
					byte[] sendData = header.getBytes();
					receiverSocket.send(new DatagramPacket(sendData,sendData.length,senderIPAddress,senderPort));
					System.out.println("Sending ACK,  Receiver_Seq= " + Receiver_Seq + " Receiver_SeqACK=  "+ Receiver_SeqACK);
					Next_Seq_Num = Receiver_SeqACK;

					if (buffer_size >= size){
						header = createHeader(Receiver_Seq,Sender_Seq_base + size * MSS);
						sendData = header.getBytes();
						receiverSocket.send(new DatagramPacket(sendData,sendData.length,senderIPAddress,senderPort));
						break;
					}
				
				
				end_time = System.currentTimeMillis() - start_time;
				status = "snd\t" + end_time + "\tA\t" + Receiver_Seq + "\t0\t"+  Receiver_SeqACK;
				rcv_status.add(status);

				if (Receiver_SeqACK >= Sender_Seq_base + size * MSS){
					break;
					}
				
			}else if(Sender_Seq > Next_Seq_Num){

				Receiver_SeqACK = Next_Seq_Num;
				header = createHeader(Receiver_Seq,Receiver_SeqACK);
				byte[] sendData = header.getBytes();
				receiverSocket.send(new DatagramPacket(sendData,sendData.length,senderIPAddress,senderPort));
				System.out.println("received packet after the base packet, buffer the packet, Sending ACK,  Receiver_Seq= " + Receiver_Seq + " Receiver_SeqACK=  "+ Receiver_SeqACK);
				//buffer the data

				int index = (int) Math.ceil((Sender_Seq - Sender_Seq_base)/(MSS));
				if (buffer[index] == null){
					buffer[index] = data;
					buffer_size += 1;
					System.out.println("@@@@@@@wirting data of index :" + index + "data_size = " +buffer_size);
					if (buffer_size >= size){
						header = createHeader(Receiver_Seq,Sender_Seq_base + size * MSS);
						sendData = header.getBytes();
						receiverSocket.send(new DatagramPacket(sendData,sendData.length,senderIPAddress,senderPort));
						break;
					}
				}
				end_time = System.currentTimeMillis() - start_time;
				status = "snd\t" + end_time + "\tA\t" + Receiver_Seq + "\t0\t"+  Receiver_SeqACK;
				rcv_status.add(status);

				if (Receiver_SeqACK >= Sender_Seq_base + size * MSS){
					break;
				}
				
			} else if (Sender_Seq < Next_Seq_Num){
				NumDupSeg += 1;

				header = createHeader(Receiver_Seq,Next_Seq_Num);
				byte[] sendData = header.getBytes();
				receiverSocket.send(new DatagramPacket(sendData,sendData.length,senderIPAddress,senderPort));
				
				end_time = System.currentTimeMillis() - start_time;
				status = "snd\t" + end_time + "\tA\t" + Receiver_Seq + "\t0\t"+  Next_Seq_Num;
				rcv_status.add(status);
			}
			
			
			
			
			
			reply = new DatagramPacket(new byte[1024],1024);
			receiverSocket.receive(reply);
			message = getData(reply);
			System.out.println(j +"th message: " + message);
			header = getHeader(message);
			data = getData(message);
			

			j+= 1;



		}
		
		
	}
	
	//tear down the connection with sender
	public static void tearDown() throws Exception{
		writeToFile();
		String status = " ";
		DatagramPacket request = new DatagramPacket(new byte[1024],1024);
		receiverSocket.receive(request);
		String message = getData(request);
		String header = getHeader(message);
		
		Sender_Seq = Integer.valueOf(header.split(" ")[0]);
		Sender_SeqAck = Integer.valueOf(header.split(" ")[1]);
		
		end_time = System.currentTimeMillis() - start_time;
		status = "rcv\t" + end_time + "\tF\t" + Sender_Seq + "\t0\t"+  Sender_SeqAck;

		rcv_status.add(status);
		
		
		System.out.println("Receiving the FIN header,  Sender_Seq = "+Sender_Seq);
		Receiver_SeqACK = Sender_Seq +1;
		
		String new_header = createFINHeader( Receiver_Seq, Receiver_SeqACK);
		byte[] sendData = new_header.getBytes();
		receiverSocket.send(new DatagramPacket(sendData,sendData.length,senderIPAddress,senderPort));
		System.out.println("Sending the FIN header,  Receiver_Seq = "+Receiver_Seq + " Receiver_SeqACK = " + Receiver_SeqACK );
		
		end_time = System.currentTimeMillis() - start_time;
		status = "snd\t" + end_time + "\tFA\t" + Receiver_Seq + "\t0\t"+  Receiver_SeqACK;
		rcv_status.add(status);
		
		while(true){
		//receive ACK from sender, close connection
			request = new DatagramPacket(new byte[1024],1024);
			receiverSocket.receive(request);
			message = getData(request);
			String lastHeader = getHeader(message);

			Sender_Seq = Integer.valueOf(lastHeader.split(" ")[0]);
			Sender_SeqAck = Integer.valueOf(lastHeader.split(" ")[1]);

			System.out.println("Receiving the last FIN header,Sender_Seq = "+Sender_Seq+  " Sender_SeqAck = "+Sender_SeqAck);

			if(Sender_SeqAck == Receiver_Seq + 1){
				end_time = System.currentTimeMillis() - start_time;
				status = "rcv\t" + end_time + "\tA\t" + Sender_Seq + "\t0\t"+  Sender_SeqAck;
				rcv_status.add(status);
				break;
			}else{
				receiverSocket.send(new DatagramPacket(sendData,sendData.length,senderIPAddress,senderPort));
				end_time = System.currentTimeMillis() - start_time;
				status = "snd\t" + end_time + "\tFA\t" + Receiver_Seq + "\t0\t"+  Receiver_SeqACK;
				rcv_status.add(status);
			}
		}
		

	}
	
	
	
	public static void main(String[] args) throws Exception{
		receiver_port = Integer.valueOf(args[0]);
		fileName = args[1];
		receiverSocket = new DatagramSocket(receiver_port);
		
		while(true){
			System.out.println("Waiting for client to connect.....");
			DatagramPacket request = new DatagramPacket(new byte[1024],1024);
			receiverSocket.receive(request);
			String message = getData(request);
			
			
			//extract header from message
			String header = getHeader(message);
			
			
			if (check_state(header) == 0){
				start_time = System.currentTimeMillis();
				senderIPAddress = request.getAddress();
				senderPort = request.getPort();
				boolean connection_state = set_connection(header);
				if (connection_state == true){
					System.out.println("connection established!!!!!!");
					
					
					System.out.println("receiving file!!!!");
					receiveData();
					System.out.println("file received!!!!");
					
					System.out.println("tearing down connection!!!");
					
					tearDown();
					receiverSocket.close();
					System.out.println("connection closed!!!");
					write_logFile();
					break;
					
				}
			}else{
				continue;
			}
		}
		
   }
}
