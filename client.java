//Client.java
//
//Programmed by: Bogdan Dawabsheh
//
//
//Client side of chat application.
//Version: 2.0
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

    class Client{


		static int PORT = 59404;                              //Port used for communication
		static String SERV_ADDRESS = "localhost";             //Server IP
		static Socket clientSocket = null;                    //Client socket variable
		static Packet inPacket= null, outPacket = null;       //Input and output packet variables
		static String inString= "", outString = "";           //Input and output string variables
		static int packetID = 0;                              //packetID counter
		static String clientName = "";                        //Client name
		static DataInputStream din = null;                    //Data input stream from Server
		static DataOutputStream dout = null;                  //Data output stream to server
    static Encrypt encryption = new Encrypt();            //Encryption method.
    static int encrypt = 0;
		public static void main(String args[])throws Exception{

      //Attempt to establish connection to server
    	try{
			    clientSocket =new Socket(SERV_ADDRESS,PORT);
			    din=new DataInputStream(clientSocket.getInputStream());
			    dout=new DataOutputStream(clientSocket.getOutputStream());
		  } catch (Exception e){
			   System.out.println("Server is down"); //If server is down, close connection
         System.exit(0);  //exit
		  }

    //Inform user of connection to server. Ask for name
		System.out.println("Connected to server. Established Streams");
		System.out.println("Enter your name:");
    boolean checkName = false;
  	Scanner sc = new Scanner(System.in);
    //Check name for correct input. If false, loop
    while(!checkName){
  		clientName = sc.nextLine();
      if(!clientName.contains("WHO") && !clientName.contains("SERVER") && !clientName.contains("QQQ") && !clientName.contains("PMM") && !clientName.contains("ALL") && clientName.matches("[\\w]+"))
        checkName = true;
      else
        System.out.println("Ensure your name doesn't contain reserved words and is a single word");
    }

    //Create and send first packet with username to server
		outPacket = Packet.createFirstPacket(clientName);
		outString = Packet.ConvertToString(outPacket);
		dout.writeUTF(outString);

    //Establish thread for dealing with user input and sending messages to server
		Thread sendMessage = new Thread(new Runnable(){
			@Override
			public void run(){
        //LOOP until stopped
				while(true){

          //Get user input
					System.out.println("Type your input:");
					String userInput = sc.nextLine();

          //Check user input for correctness (Must be a word and atleast 3 characters)
					if(userInput.length() > 2 && userInput.matches("[\\w\\s]+"))
          {
            //Split user input into parts
						String[] parts = userInput.split(" ");

            //If first word is WHO, request WHO from Server
						if(parts[0].equals("WHO")){
							packetID++;  //Increment packetID
							outPacket = new Packet(clientName,"SERVER", "WHO", packetID, Packet.getChecksum("WHO"),0, "WHO"); //Create packet
							outString = Packet.ConvertToString(outPacket);   //Convert packet to string
							try{dout.writeUTF(outString);} catch(Exception e) {e.printStackTrace();} //Fire packet to server

          } else if(parts[0].equals("ENC")){
            if(encrypt == 0){
              encrypt = 1;
              System.out.println("ENCRYPTION TOGGLED ON");
            } else {
              encrypt = 0;
              System.out.println("ENCRYPTION TOGGLED OFF");
            }
            //If first word is QQQ, initialize disconnect procedure
          } else if (parts[0].equals("QQQ")){
							packetID++; //Increment packet number
							outPacket = new Packet(clientName, "SERVER", "DISCONNECT", packetID, Packet.getChecksum("DISCONNECT"),0, "DISCONNECT"); //Assemble Disconnect packet
							outString = Packet.ConvertToString(outPacket); //convert packet into a string
							try{dout.writeUTF(outString);} catch(Exception e) {e.printStackTrace();} //fire packet to server

							System.out.println("DISCONNECTED FROM SERVER"); //Inform user of disconnection
							System.out.println("BYE");
							System.exit(0); //exit

              //If first word is ALL, it is a general message meant for all users
						} else if (parts[0].equals("ALL")){
							packetID++; //increment packet number
							userInput = userInput.substring(3); //separate message from command
              if(encrypt == 1){
                userInput = encryption.encryptString(userInput);
              }
              outPacket = new Packet(clientName, "ALL", " ", packetID, Packet.getChecksum(userInput),encrypt, userInput); //Assemble message packet
							outString = Packet.ConvertToString(outPacket); //convert packet
							try{dout.writeUTF(outString);} catch(Exception e) {e.printStackTrace();} //first packet to serverSocket

              //if first word is PMM, it is a private message destined for 1 specific user
						} else if(parts[0].equals("PMM")){
							packetID++; //increment packet number
              if(parts.length > 2){ //check if there are atleast 3 parts (0 means Command, 1 means destination, 2 onward is the message)
                String destination = parts[1]; //Get the Destination

                //If destination doesn't violate input rules, continue
                if(!destination.contains("WHO") && !destination.contains("SERVER") && !destination.contains("QQQ") && !destination.contains("PMM") && !destination.contains("ALL") && destination.matches("[\\w]+"))
                {
                  String message = ""; //Assemble message
                  for(int i = 2; i < parts.length;i++){
                    message = message + parts[i];
                  }

                  //Assemble, convert and fire packet to server
                  if(encrypt == 1){
                    message = encryption.encryptString(message);
                  }
                  outPacket = new Packet(clientName, destination, " ", packetID, Packet.getChecksum(message),encrypt, message);
                  outString = Packet.ConvertToString(outPacket);
                  try{dout.writeUTF(outString);} catch(Exception e) {e.printStackTrace();}

                }else { //else inform user
                  System.out.println("Try again. Invalid PMM destination format.");
                }
              } else { //input violation
                System.out.println("Improper input, ensure your input is in format: PMM  else {<destination> <message>");
              }
            } else {//input violation
							System.out.println("Invalid input. See docs");
						}
					} else {//input violation
						System.out.println("Invalid input. See Docs");
					}
				}
			}
		});

    //Create a thread for reading messages from the server
		Thread readMessage = new Thread(new Runnable(){
			@Override
			public void run(){
        //Loop until stopped
				while(true){
					try{
						if(din.available() != 0) //If there is a message from the server
							inString = din.readUTF(); //read the packet
						if(inString.length() > 0){                                                            //if the packet is not empty
							inPacket = Packet.ConvertToPacket(inString);                                         //conver the packet to Packet format
              if(Packet.CheckChecksum(inPacket.checksum,inPacket.data)){                           //Check the checksum, if okay
                if(inPacket.verb.equals("RESEND")){                                                   //Check if Resend verb exists
                  System.out.println("SYSTEM: Network error? Resending previous packet");                 //if true, resend previous packet
                  outString = Packet.ConvertToString(outPacket);
                  try{dout.writeUTF(outString);} catch(Exception e) {e.printStackTrace();}
                } else {
                if(inPacket.encrypted == 1){
                  inPacket.data = encryption.decryptString(inPacket.data);
                }                                                                                  //else -> Output packet contents to user
  							System.out.println("FROM: " + inPacket.source + " TO: " + inPacket.destination + " ... " + inPacket.data);
  						  }
              } else {                                                                            //If checksum failed, ask Server for a packet resend.
                outPacket = new Packet(clientName, "SERVER", "RESEND", packetID, Packet.getChecksum("RESEND"),0,"RESEND");
              }
            }
					}catch(Exception e){
						e.printStackTrace();
					}

          //Clear input packets
					inString = "";
					inPacket = null;
				}
			}
		});

    //Start threads
		sendMessage.start();
		readMessage.start();
    	}
	}
