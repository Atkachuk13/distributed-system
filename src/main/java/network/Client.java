package network;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client
{
    private final String host = "localhost";
    private final int port = 6000;
    private Socket socketToMaster;

    // Unique ID for this client instance
    private String clientId;

    // Shared memory for outgoing messages (thread-safe queue)
    private final BlockingQueue<String> outgoingQueue = new LinkedBlockingQueue<>();

    public static void main(String[] args)
    {
        new Client().start();
    }

    public void start()
    {
        try
        {
            System.out.println("Client starting...");
            socketToMaster = new Socket(host, port);
            System.out.println("Connected to master at " + host + ":" + port);
            System.out.println();

            // Start threads for:
            //  1) user input
            //  2) sending messages to master
            //  3) listening for messages from master
            startUserInputThread();
            startSenderThread();
            startListenerThread();

        } catch (Exception e)
        {
            System.err.println("Error starting client:");
            e.printStackTrace();
        }
    }

    /**
     * Thread that interacts with the USER:
     * - asks for client ID (once)
     * - repeatedly asks for job type and job ID
     * - builds a SUBMIT message and puts it into the outgoing queue
     */
    private void startUserInputThread()
    {
        Thread t = new Thread(() ->
        {
            Scanner scanner = new Scanner(System.in);

            // Ask once for a client ID so the master can distinguish clients
            System.out.print("Enter your client ID (e.g., client1, client2): ");
            clientId = scanner.nextLine().trim();
            if (clientId.isEmpty())
            {
                clientId = "client-" + System.currentTimeMillis();
                System.out.println("No ID entered. Using generated ID: " + clientId);
            } else
            {
                System.out.println("Client ID set to: " + clientId);
            }

            // Main loop: read jobs from the user
            while (true)
            {
                System.out.print("Enter job type (A/B): ");
                String type = scanner.nextLine().trim().toUpperCase();

                System.out.print("Enter job ID: ");
                String id = scanner.nextLine().trim();

                // Message format: SUBMIT;clientId;type;jobId
                String msg = "SUBMIT;" + clientId + ";" + type + ";" + id;

                // Put message into shared queue for the sender thread
                outgoingQueue.add(msg);

                System.out.println("Client [" + clientId + "]: queued job " + id + " (type " + type + ")");
            }
        }, "UserInputThread");

        t.start();
    }

    /**
     * Thread that sends messages from the outgoingMessages queue to the master.
     * This uses the socket's OUTPUT stream.
     */
    private void startSenderThread()
    {
        Thread t = new Thread(() ->
        {
            try
            {
                PrintWriter out = new PrintWriter(socketToMaster.getOutputStream(), true);

                while (true)
                {
                    // Take next message from queue (blocks until available)
                    String msg = outgoingQueue.take();

                    // Send to master
                    out.println(msg);
                    System.out.println("Client [" + clientId + "] submitted to Master: " + msg);
                }
            } catch (Exception e)
            {
                System.err.println("Sender thread encountered an error:");
                e.printStackTrace();
            }
        }, "SenderThread");

        t.start();
    }

    /**
     * Thread that listens for responses from the master.
     * This uses the socket's INPUT stream.
     * Expected format (example): DONE;clientId;jobId
     */
    private void startListenerThread()
    {
        Thread t = new Thread(() ->
        {
            try
            {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socketToMaster.getInputStream())
                );
                String line;

                while ((line = in.readLine()) != null)
                {
                    System.out.println("Master response to Client [" + clientId + "]: " + line);

                    // Example expected format: DONE;clientId;jobId
                    if (line.startsWith("DONE;"))
                    {
                        String[] parts = line.split(";");
                        if (parts.length >= 3)
                        {
                            String doneClientId = parts[1];
                            String jobId = parts[2];

                            // Only announce completion if this message is for THIS client
                            if (doneClientId.equals(clientId))
                            {
                                System.out.println("Client [" + clientId + "]: Job " + jobId + " has been completed!");
                            } else
                            {
                                // helpful for debugging:
                                System.out.println("Client [" + clientId + "]: Ignored DONE for "
                                        + doneClientId + " (job " + jobId + ")");
                            }
                        }
                    }
                }

            } catch (Exception e) {
                System.err.println("Listener thread encountered an error:");
                e.printStackTrace();
            }
        }, "ListenerThread");

        t.start();
    }
}
