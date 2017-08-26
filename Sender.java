import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;



public class Sender{	
	private static DatagramSocket senderSocket;
	private static InetAddress receiverIPAddress;
	private static String receiver_host_ip;
	private static int receiver_port; 
	private static String fileName;
	private static int MWS; // maximum window size
	private static int MSS; // Maximum Segment Size
	private static int timeout;
	private static Double pdrop;
	private static int seed;

	private static BufferedReader reader;
	private static ArrayList<String> linesToSend;
	private static ArrayList<String> sender_status;
	private static int Sender_Seq; // sender's sequence number
	private static int Reicever_Seq; //receiver's sequnce number;
	private static int Receiver_SeqACK; // sequence ack number replied from receiver
	private static int Sender_SeqACK; // ack sending to receiver
	private static Random random;
	private static long start_time;
	private static long end_time;

	private static int num_timeout;
	private static int num_drop;
	private static int num_retransmitted;
	private static int num_DupACK;
	private static int numSeg; 
	private static boolean retransmit_flag;

	// write sender log
	public static void write_logFile() throws Exception{
		try{      
			FileWriter fw = new FileWriter("Sender_log.txt");
			PrintWriter pw = new PrintWriter(fw);
			for(int i = 0; i < sender_status.size(); i++){
	    		  pw.println(sender_status.get(i));
	    	  }
	    	  int AmountData = linesToSend.size()*MSS;
	    	  pw.println("Amount of Data Transferred(in bytes) = "+ AmountData);
	    	  pw.println("Number of Data Segments Sent(excluding retransmissions) = " + numSeg);
	    	  pw.println("Number of Packets Dropped = "+ num_drop);
	    	  pw.println("Number of Retransmitted Segments = "+ num_retransmitted);
	    	  pw.println("Number of Duplicate Acknowledgements received = "+ num_DupACK);
	    	  pw.close();
	    	  sender_status = null;								
		}catch (IOException e){
			System.out.println("Error!");
		}
	}

	//read file from disk
	public static void readFile() throws IOException,FileNotFoundException{
		linesToSend = new ArrayList<String>();
		reader = new BufferedReader(new FileReader(fileName));
		String line = reader.readLine();
		String file = "";
		
		while(line != null){
			file += line +"\n";
			line = reader.readLine();
		}
		reader.close();
		
		while(file.length() > MSS) {
			line = file.substring(0, MSS);
			linesToSend.add(line);
			file = file.substring(MSS, file.length());
		}
	
		linesToSend.add(file);
			
	}
	
	// convert data packet to string
	private static String getData(DatagramPacket request) throws Exception{
	      return new String(request.getData(),"UTF-8");
	}

	

	//send data and get reply
	public static String send(byte[] sendData) throws Exception{
		senderSocket.send(new DatagramPacket(sendData,sendData.length,receiverIPAddress,receiver_port)); //catch Exception
		DatagramPacket reply = new DatagramPacket(new byte[1024],1024);
		senderSocket.receive(reply);
		String replyMessage = getData(reply);
		return replyMessage;
	}
	
	

	/*
	 * use &# as separator between header and real data
 	 */
	public static String createSYNHeader(int Sender_Seq,int Sender_ACK,int MWS,int MSS){
		String header =  Sender_Seq + " "+Sender_ACK+" " + MWS +" "+MSS+" "+"SYN&#";
		return header;

	}

	public static String createHeader(int Sender_Seq, int Sender_seqAck){
		String header = Sender_Seq + " " + Sender_seqAck + " " +linesToSend.size()+" "+"&#";
		return header;
	}

	public static String createFINHeader(int Sender_Seq,int Sender_SeqACK){
		String header = Sender_Seq + " " +Sender_SeqACK  + " "+ "FIN&#";
		return header;
	}
	
	public static String getHeader(String message){
		String header = message.split("&#")[0];
		return header;
		
	}
	
	public static String getData(String message){
		String header = message.split("&#")[1];
		return header;
	}
	
	// connection initiation
	public static void three_way_handshake() throws Exception{
		// set up variables 
		Reicever_Seq = 0;
		Receiver_SeqACK =0;
		Sender_SeqACK = 0;
		Sender_Seq = random.nextInt(100);
		//make header
		String header = createSYNHeader(Sender_Seq,Sender_SeqACK,MWS,MSS);

		byte[] sendData = header.getBytes();
		// send first SYN, seq
		end_time = System.currentTimeMillis() - start_time;
		String status = "snd\t" + end_time + "\tS\t" + Sender_Seq + "\t0\t"+ "0";
		sender_status.add(status);

		String replyMessage = send(sendData);
		System.out.println("Sending  SYN ---- Sender_Seq= "+ Sender_Seq);
		
		// receiving SYN,seq,ack and check if replied message satisfied the result
		while(true){
				
			Reicever_Seq = Integer.valueOf(replyMessage.split(" ")[0]);//replySeq = server_sequence number

			Receiver_SeqACK = Integer.parseInt(replyMessage.split(" ")[1]);


			if( Receiver_SeqACK == Sender_Seq+1){
				// send next SYN, seq, ack
				System.out.println("received first Header reply,  Reicever_Seq= "+Reicever_Seq+ " Receiver_SeqACK = "+Receiver_SeqACK);
				end_time = System.currentTimeMillis() - start_time;
				status = "rcv\t" + end_time + "\tSA\t" + Reicever_Seq + "\t0\t"+  Receiver_SeqACK;
				sender_status.add(status);
				break;
			}else{
				replyMessage = send(sendData);
				System.out.println("Resending the first header");
			}
		}
		
		//last send and start to send file
		
		Sender_Seq = Sender_Seq +1;
		Sender_SeqACK = Reicever_Seq +1;

		String header2 = createSYNHeader(Sender_Seq,Sender_SeqACK,MWS,MSS);
		sendData = header2.getBytes();

		senderSocket.send(new DatagramPacket(sendData,sendData.length,receiverIPAddress,receiver_port));
		System.out.println("Sending the last header,  Sender_Seq = "+Sender_Seq + " Sender_SeqACK = "+ Sender_SeqACK);
		end_time = System.currentTimeMillis() - start_time;
		status = "snd\t" + end_time + "\tA\t" + Sender_Seq + "\t0\t"+  Sender_SeqACK;
		sender_status.add(status);
	}
	
	// sending file
	public static void sendFile() throws Exception{
		int window_start = 0;
		int window_end = 0;
		int count = 0;
		num_timeout = 0;
		num_drop = 0;
		num_retransmitted = 0;
		numSeg = 0;
		retransmit_flag = true;

		String status = "";
		readFile();
		int sender_seq_base = Sender_Seq;
		System.out.println("linesToSend size = "+ linesToSend.size());
		System.out.println("Sender_Seq = " + Sender_Seq + " Receiver_SeqACK = " + Receiver_SeqACK);
		
		// mimic the window, sending packets
		while(window_start < linesToSend.size()){
			
			while(window_end-window_start < MWS  && window_end < linesToSend.size()){
				numSeg += 1;
				String header = createHeader(Sender_Seq,Sender_SeqACK);
				String data = linesToSend.get(window_end);
				String message = header + data;
				if (random.nextDouble() < pdrop) {
					num_drop += 1;
            		System.out.println("packet not sent.");
            		end_time = System.currentTimeMillis() - start_time;
					status = "drop\t" + end_time + "\tD\t" + Sender_Seq + "\t"+ MSS +"\t"+  Sender_SeqACK;
					sender_status.add(status);
					Sender_Seq += MSS;
					window_end += 1;
            		continue;       
         		}

				
				System.out.println(message);
				byte[] sendData = message.getBytes();
				senderSocket.send(new DatagramPacket(sendData,sendData.length,receiverIPAddress,receiver_port));
				end_time = System.currentTimeMillis() - start_time;
				status = "snd\t" + end_time + "\tD\t" + Sender_Seq +"\t" + MSS + "\t" +Sender_SeqACK;
				sender_status.add(status);		
				Sender_Seq += MSS;
				window_end += 1;
				System.out.println("######## window_start = " + window_start + " window_end =  " + window_end);
				System.out.println("Sending ---Sender_Seq = " + Sender_Seq + " Receiver_SeqACK = " + Receiver_SeqACK);
				

				
			}
			// set time out
			senderSocket.setSoTimeout(timeout);


			try{
				//receive ack from receiver
				DatagramPacket reply = new DatagramPacket(new byte[1024],1024);
				senderSocket.receive(reply);
		
				String messageACK = getData(reply);
				String headerACK = getHeader(messageACK);

				Reicever_Seq= Integer.valueOf(headerACK.split(" ")[0]);
				Receiver_SeqACK = Integer.valueOf(headerACK.split(" ")[1]);
				System.out.println("Receiving --- Reicever_Seq = " + Reicever_Seq + " Receiver_SeqACK = " + Receiver_SeqACK);
				
				end_time = System.currentTimeMillis() - start_time;
				status = "rcv\t" + end_time + "\tA\t" + Reicever_Seq + "\t0\t" +  Receiver_SeqACK;
				sender_status.add(status);

				// receive ack greater than send base, move window to its right position
				if(Receiver_SeqACK - sender_seq_base > 0){
					System.out.println("!@#need to jump num = " + (int) (Receiver_SeqACK-sender_seq_base)/MSS );
					window_start += (int) Math.ceil((Receiver_SeqACK-sender_seq_base)/MSS);
					sender_seq_base = Receiver_SeqACK;
					
					System.out.println("window_start = "+ window_start  + " window_end = " + window_end);
					retransmit_flag = true;
					count = 0;
			
					
				} else { // receive ack less than send base
					num_DupACK += 1;
					System.out.println("received ack less than seq");
					// fast retransmittion
					if (count < 2){
						count += 1;
					} else {
						if(retransmit_flag == true){
							num_retransmitted += 1;
							count = 0;
							//resend
							String header = createHeader(sender_seq_base,Sender_SeqACK);
							String data = linesToSend.get(window_start);
							String message = header + data;
							byte[] sendData = message.getBytes();
							senderSocket.send(new DatagramPacket(sendData,sendData.length,receiverIPAddress,receiver_port));
							System.out.println("Sending ---Sender_Seq = " + sender_seq_base + " Receiver_SeqACK = " + Receiver_SeqACK);
							System.out.println(message);

							end_time = System.currentTimeMillis() - start_time;
							status = "snd\t" + end_time + "\tD\t" + sender_seq_base + "\t"+ MSS +"\t"+  Sender_SeqACK;
							sender_status.add(status);
							retransmit_flag = false;
						}
						
						count += 1;

					} 
				}
	
			}catch(Exception e){// time out, retransmit the first packet
				System.out.println("time out.");
				num_timeout += 1;
				num_retransmitted += 1;
				String header = createHeader(sender_seq_base,Sender_SeqACK);
				String data = linesToSend.get(window_start);
				String message = header + data;
				int Sender_Retrans_Seq = sender_seq_base;
				System.out.println(message);
				byte[] sendData = message.getBytes();
				senderSocket.send(new DatagramPacket(sendData,sendData.length,receiverIPAddress,receiver_port));
				end_time = System.currentTimeMillis() - start_time;
				status = "snd\t" + end_time + "\tD\t" + Sender_Retrans_Seq +"\t" + MSS + "\t" +Sender_SeqACK;
				sender_status.add(status);

				System.out.println("Sender_Seq = " + Sender_Retrans_Seq);
				count = 0;
				
			}
		}	
	}
	
	// tear down process
	public static void closeConnection() throws Exception{
		
		String status = "";
		
		String Header1 = createFINHeader(Sender_Seq, Sender_SeqACK);

		System.out.println("Sending the FIN header,  Sender_Seq = "+Sender_Seq );

		end_time = System.currentTimeMillis() - start_time;
		status = "snd\t" + end_time + "\tF\t" + Sender_Seq + "\t0\t"+ Sender_SeqACK;
		sender_status.add(status);

		byte[] sendData = Header1.getBytes();

		String replyMessage = send(sendData);
		
		String header = getHeader(replyMessage);
		
		
		Reicever_Seq = Integer.parseInt(header.split(" ")[0]);
		Receiver_SeqACK = Integer.parseInt(header.split(" ")[1]);

		end_time = System.currentTimeMillis() - start_time;
		status = "rcv\t" + end_time + "\tFA\t" + Reicever_Seq + "\t0\t"+ Receiver_SeqACK;
		sender_status.add(status);



		System.out.println("Receiving the FIN header, Reicever_Seq = "+Reicever_Seq +" Receiver_SeqACK = " + Receiver_SeqACK);

		Sender_SeqACK = Reicever_Seq +1;
		Sender_Seq = Sender_Seq +1;
		String Header2 = createFINHeader(Sender_Seq,Sender_SeqACK);
		sendData = Header2.getBytes();
		senderSocket.send(new DatagramPacket(sendData,sendData.length,receiverIPAddress,receiver_port));		
		
		System.out.println("Sending the last FIN header,  Sender_Seq = "+Sender_Seq +  "Sender_SeqACK = " + Sender_SeqACK);

		end_time = System.currentTimeMillis() - start_time;
		status = "snd\t" + end_time + "\tA\t" + Sender_Seq + "\t0\t"+ Sender_SeqACK;
		sender_status.add(status);


		//close the socket
		senderSocket.close();
		
	}
	
	public static void main(String args[]) throws Exception{

		receiver_host_ip = args[0];
		receiver_port = Integer.valueOf(args[1]);
		fileName =args[2];
		MWS = Integer.valueOf(args[3]);// maximum window size
		MSS = Integer.valueOf(args[4]);
		timeout =Integer.valueOf(args[5]);
		pdrop = Double.valueOf(args[6]);
		seed = Integer.valueOf(args[7]);
		
		MWS = MWS/MSS;
		random = new Random(seed);
		//create a socket for sender
		senderSocket = new DatagramSocket();
		receiverIPAddress = InetAddress.getByName(receiver_host_ip);
		
		System.out.println("setting up connection!!!");

		start_time = System.currentTimeMillis();
		sender_status = new ArrayList<String>();
		three_way_handshake();
		System.out.println("Connection established!!!!");
		
		System.out.println("Sending File!!!!!");
		sendFile();
		System.out.println("File sent!!");
		
		System.out.println("tearing down connection!!!");
		closeConnection();
		System.out.println("connection closed!!!");

		write_logFile();

	}
	
}
