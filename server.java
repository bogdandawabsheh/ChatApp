//Server.java
//
//Programmed by: Bogdan Dawabsheh
//
//
//Server side of chat application.
//Version: 1.5
//
// Version Changes:
// -- Added Encryption to DATA part of packet
//
//=====================================================================

	import java.io.*;
	import java.text.*;
	import java.util.*;
	import java.net.*;
	import java.security.MessageDigest;
	import javax.xml.bind.DatatypeConverter;
	import java.security.*;

		class Server{

		//Static variables used between different classes in the program.
		static int PORT = 59404; 																											//Access port for message communication
		static int maxClients = 50; 																									//Number of max clients the server can take
		static ConnectedClient[] connectedClients = new ConnectedClient[maxClients]; //Connected client list
		static int numClients = 0; 																										//Num of current clients connected

		public static void main(String args[])throws Exception{
			int connectedClientNum = 0;
			//Connect to the socket at the specified port
			ServerSocket serverSideSocket=new ServerSocket(PORT);

			while (true){
				//Server socket
				Socket serverSocket = null;

				//Error handling
				try
				{
					//Accept incoming connection
					serverSocket=serverSideSocket.accept();
					connectedClientNum++; //Increase the number of clients
					//Print out
					System.out.println("Client #" + connectedClientNum + " connected");

						//Create input and output streams for the specified client
    				DataInputStream din=new DataInputStream(serverSocket.getInputStream());
    				DataOutputStream dout=new DataOutputStream(serverSocket.getOutputStream());

					//Developer message to show status
					System.out.println("This server is splitting into two to handle a new client :o");

					//Split the application into two threads -> 1. Listens for oncomming connections
																				// 						2. handles connected client.
					Thread clientThread = new ClientHandler(serverSocket, din, dout);

					clientThread.start(); //Start new client handling thread.

				} catch (Exception e){
					//If error occurs, safely close the port and print the error message
					serverSocket.close();
					e.printStackTrace();
				}
			}
    	}
	}

	//Client handling method.
	//If a new client connects, a new thread is created that handles each client at socket.
	class ClientHandler extends Thread{

		final DataInputStream din;				//D
		final DataOutputStream dout;
		final Socket socket;

		//Constructor class that assigns socket information, datainput stream nand dataoutput stream
		public ClientHandler(Socket s, DataInputStream dinput, DataOutputStream doutput){
			socket = s;
			din = dinput;
			dout = doutput;
		}

		//Main client handling logic method
		@Override
		public void run(){
			String inString = "";									//Incomming message holder variable
			String outString = "";								//output message holder variable
			Packet inPacket =  null;							//Input packet holder variable
			Packet outPacket = null;							//output packet holder variable
			int packetNum = 0;										//Packet number variable

			//Loop indefinetely
			while(true){
				try{
					if(din.available() != 0)	//If there is an input, read input
						inString = din.readUTF();
					if(inString.length() > 0){
						inPacket = Packet.ConvertToPacket(inString); //Conver input string to packet format

						//******Print destination for easier developer management
						System.out.println("Destination: " + inPacket.destination + "DATA: " + inPacket.data);
						//*************

						//CORRUPTING PACKETS INTENTIONALLY
						Random rnd = new Random(); //Create a random number generator
						double randomNum = rnd.nextDouble(); //Create a number between 0.0 and 1.0
						if(randomNum > 0.8){	//if the number is greater than 0.8, corrupt the checksum and inform developer
							inPacket.checksum = "CORRUPTING";
							System.out.println("Packet from "+ inPacket.source+" to " + inPacket.destination + " with potential verb " + inPacket.verb+" was corrupted");
						}
						//========================================

						//Check the packet checksum
						if(Packet.CheckChecksum(inPacket.checksum,inPacket.data)){ //if correct, continue.
						if (inPacket.destination.equals("SERVER")) //if the packet destination is the server
						{
							if (inPacket.verb.equals("WHO")) //if the verb associated with the packet is WHO
							{
								//Server received WHO request
								//increment the packet number, create a new packet with the list of active users
								//and send back to client
								String whoOutput = "Connected clients: ";
								for (int i = 0; i < Server.numClients; i++)//Loop through the connected client list and log.
								{
									whoOutput = whoOutput + Server.connectedClients[i].name + " ";
								}

								packetNum++; //increment packet number and send packet
								outPacket = new Packet("SERVER", inPacket.source, "RETURN", packetNum, Packet.getChecksum(whoOutput),0, whoOutput);
								outString = Packet.ConvertToString(outPacket);
								dout.writeUTF(outString);

							//If the server receives a resend request, resend the latest packet.
							} else if (inPacket.verb.equals("RESEND")){
								System.out.println("SYSTEM: Resending packet to client due to checksum error: " + outPacket.destination);
								dout.writeUTF(outString);
							}


							//Server received FIRST connection request
							//Save client information.
							//Pass message to other clients that a client has connected
							else if (inPacket.verb.equals("FIRST"))
							{
								System.out.println("RECEIVED FIRST SERVER CONNECTION. Adding client to server list");
								packetNum++;
								//Save the new client information (name, id and datastreams)
								Server.connectedClients[Server.numClients] = new ConnectedClient(inPacket.source,Server.numClients+1,din,dout);
								Server.numClients++;
								//Construct a packet and send to all other connected clients that a user has connected
								outPacket = new Packet("SERVER", "ALL", "", packetNum, Packet.getChecksum(inPacket.source + " has Joined"),0, inPacket.source + " has Joined");
								for (int i = 0; i < Server.numClients; i++)
									passMessage(outPacket, Server.connectedClients[i].outputStream);
							}
							//Server receives disconnect connection request
							//Remove client information from active users list
							else if (inPacket.verb.equals("DISCONNECT"))
							{
								System.out.println("Disconnecting client");

								//Go into the connected clients list -> and remove the disconnected client from active user list
								for (int i = 0; i < Server.numClients; i++)
								{
									if (Server.connectedClients[i].name.equals(inPacket.source))
									{
										//case 1: The client is at the end of the list -> snip off and decrement counter
										if (i + 1 >= Server.numClients)
										{
											Server.connectedClients[i].closeConnection();
											Server.connectedClients[i] = null;
											Server.numClients--;
										}
										else //case 2: The client is in the middle or start of list -> go around the client.
										{
											Server.connectedClients[i].closeConnection();
											Server.connectedClients[i] = null;
											for (int p = i; p < Server.numClients - 1; p++)
											{
												Server.connectedClients[p] = Server.connectedClients[p + 1];
											}
											Server.numClients--;
										}
										break;
									}
								}

								//Construct a packet and send to all other connected clients that a user has disconnected
								outPacket = new Packet("SERVER", "ALL", "", packetNum, Packet.getChecksum(inPacket.source + " has left us"),0, inPacket.source + " has left us");
								for (int i = 0; i < Server.numClients; i++)
									passMessage(outPacket, Server.connectedClients[i].outputStream);
							}
						}
						//Server receives packet for ALL users
						//Rebrodcast the packet to all other users.
						else if (inPacket.destination.equals("ALL"))
						{
							for (int i = 0; i < Server.numClients; i++)
								passMessage(inPacket, Server.connectedClients[i].outputStream);
						}
						else //Else, it is a private message
						//Send the packet to a specified client.
						{
							for (int i = 0; i < Server.numClients; i++)
							{
								if (inPacket.destination.equals(Server.connectedClients[i].name))
								{
									passMessage(inPacket, Server.connectedClients[i].outputStream);
									break;
								}
							}
						}
					} else { //Checksum failed ELSE statement
						packetNum++;
						requestResend(dout,packetNum); //Ask for a resend
					}
					}

				inString = ""; //Clean the input packets
				inPacket = null;


				}catch(IOException e){
					e.printStackTrace(); //If error occurs, print and stop
					break;
				}
			}

		}

		//Pass packet to specified destination
		private void passMessage(Packet packet, DataOutputStream dout)
		{
			//convert packet
			String outString = Packet.ConvertToString(packet);
			//fire packet to destination
			try{dout.writeUTF(outString);}  catch (Exception e) {e.printStackTrace();}
		}

		//Request a latest packet resend from a specified destination
		private void requestResend(DataOutputStream dout, int packetNum){
			//create a new packet containing RESEND request and pass it to selected client
			passMessage(new Packet("SERVER","","RESEND",packetNum,Packet.getChecksum("RESEND"),0,"RESEND"),dout);
		}
	}

//PACKET class
//contains methods for packet assembly, conversion as well as a default packet constructor.
//See documentation for more details
class Packet
{
	public String source;							//Contains the Source of the packet
	public String destination;				//contains the destination of the packet
	public String checksum;						//contains the checksum of the packet
	public String version = "3.0";		//contains the packet version
	public int encrypted = 0;
	public String verb;								//contains extra piece of information (command)
	public int packetID;							//packetID
	public String data;								//Data (messages)

	//default empty constructor
	public Packet()
	{
		source = destination = checksum = version = verb = data = "";
		packetID = 0;
	}

	//constructor utilizing every aspect except for checksum and version, which are prewritten
	public Packet(String source, String destination, String verb, int packetID, String checksum, int encrypted, String data)
	{
		this.source = source;
		this.destination = destination;
		this.verb = verb;
		this.packetID = packetID;
		this.checksum = checksum;
		this.encrypted = encrypted;
		this.data = data;
	}

	//Creates and returns first connection packet
	public static Packet createFirstPacket(String source)
	{

		return new Packet(source, "SERVER", "FIRST", 1, Packet.getChecksum("FIRST"),0,"FIRST");
	}

	//Converts the packet into a string format using "____" as a whitespace replacement
	public static String ConvertToString(Packet packetToTransform){
		return packetToTransform.source + "____" + packetToTransform.destination + "____" + packetToTransform.verb + "____" + packetToTransform.packetID + "____" + packetToTransform.checksum + "____" + packetToTransform.encrypted + "____" + packetToTransform.data;
	}

	//Convert the string into a packet
	public static Packet ConvertToPacket(String input){
		Packet dummyPacket;
		String[] split = input.split("____");
			dummyPacket = new Packet(split[0], split[1], split[2], Integer.parseInt(split[3]), split[4], Integer.parseInt(split[5]), split[6]);

		return dummyPacket;
	}

	//MD5 Checksum creation
	private static String createMD5Checksum(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hashedMessage = digest.digest(data.getBytes("UTF-8")); //Use the local library to transform the string into a byte array
            return DatatypeConverter.printHexBinary(hashedMessage).toUpperCase(); //convert the array into a hash and put everything into UPPER case

				}catch(Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

		//Check if the checksum matches the checksum supplied
		public static boolean CheckChecksum(String checksum, String message){
			String createdChecksum = createMD5Checksum(message); //get the checksum for the string
			return checksum.equals(createdChecksum); //Compare checksums and return results
		}

		//Get the checksum for a string
		public static String getChecksum(String message){
			return createMD5Checksum(message);
		}
}

	//Connected client class
	//Replacement for a struct or a Tuple class in C# that was present in version 1.0
	//Allows for creation of an array that contains connected client information.
	//See main method static variables :)
	class ConnectedClient{
		public String name;					//name of client
		public int id;							//clientID (not used)
		public DataInputStream inputStream;			//input data stream
		public DataOutputStream outputStream;		//output data stream

		//default null constructor
		public ConnectedClient(){
			name = "";
			inputStream = null;
			outputStream = null;
		}

		//Constructor with input
		public ConnectedClient(String name,int id, DataInputStream din, DataOutputStream dos){
			this.name = name;
			this.id = id;
			this.inputStream = din;
			this.outputStream = dos;
		}

		//close data stream connections at client level.
		public void closeConnection(){
			try{
			inputStream.close();
			outputStream.close();
			} catch (Exception e){
				System.out.println("Closed connection to client");;
			}
		}
	}

	class Encrypt{
		public Encrypt(){};

		//Based on:
		//https://introcs.cs.princeton.edu/java/31datatype/Rot13.java.html
		public static String encryptString(String data){
			String encryptedString = "";
			for (int i = 0; i < data.length(); i++) {
					char c = data.charAt(i);
            if       (c >= 'a' && c <= 'm') c += 13;
            else if  (c >= 'A' && c <= 'M') c += 13;
            else if  (c >= 'n' && c <= 'z') c -= 13;
            else if  (c >= 'N' && c <= 'Z') c -= 13;
						encryptedString = encryptedString + c;
				}
			return encryptedString;
		}

		//Based on:
		//https://introcs.cs.princeton.edu/java/31datatype/Rot13.java.html
		public static String decryptString(String data){
			String decryptedString = "";
			for (int i = 0; i < data.length(); i++) {
					char c = data.charAt(i);
            if       (c >= 'a' && c <= 'm') c += 13;
            else if  (c >= 'A' && c <= 'M') c += 13;
            else if  (c >= 'n' && c <= 'z') c -= 13;
            else if  (c >= 'N' && c <= 'Z') c -= 13;
					decryptedString = decryptedString + c;
				}
			return decryptedString;
	}
}
