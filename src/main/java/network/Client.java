package network;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client
{
    private final String host;
    private final int port;
    private Socket socketToMaster;

    // Unique ID for this client instance
    private String clientId;

    // Shared memory for outgoing messages (thread-safe queue)
    private final BlockingQueue<String> outgoingQueue = new LinkedBlockingQueue<>();

    // Single lock for ALL console output (prevents prompt/output interleaving)
    private final Object consoleLock = new Object();

    // Constructor with configurable host and port
    public Client(String host, int port)
    {
        this.host = host;
        this.port = port;
    }

    // Default constructor for backward compatibility
    public Client()
    {
        this("localhost", 6000); // Default to client port
    }

    public static void main(String[] args)
    {
        // Allow specifying host and port via command line
        // Usage: java Client [host] [port]
        String host = "localhost";
        int port = 6000;

        if (args.length >= 1)
        {
            host = args[0];
        }
        if (args.length >= 2)
        {
            try
            {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e)
            {
                System.err.println("Invalid port number: " + args[1]);
                System.err.println("Usage: java Client [host] [port]");
                return;
            }
        }

        new Client(host, port).start();
    }

    public void start()
    {
        try
        {
            synchronized (consoleLock)
            {
                System.out.println("Client starting...");
                System.out.println("Connecting to master at " + host + ":" + port);
            }

            socketToMaster = new Socket(host, port);

            synchronized (consoleLock)
            {
                System.out.println("Successfully connected to master!");
                System.out.println();
            }

            // Start threads for:
            //  1) user input
            //  2) sending messages to master
            //  3) listening for messages from master
            startUserInputThread();
            startSenderThread();
            startListenerThread();

        } catch (Exception e)
        {
            synchronized (consoleLock)
            {
                System.err.println("Error starting client:");
                e.printStackTrace();
            }
        }
    }

    /**
     * Thread that interacts with the USER:
     * - asks for client ID (once)
     * - repeatedly asks for job type and job ID with validation
     * - builds a SUBMIT message and puts it into the outgoing queue
     */
    private void startUserInputThread()
    {
        Thread t = new Thread(() ->
        {
            Scanner scanner = new Scanner(System.in);

            synchronized (consoleLock)
            {
                System.out.print("Enter your client ID (e.g., client1, client2): ");
            }
            clientId = scanner.nextLine().trim();

            if (clientId.isEmpty())
            {
                clientId = "client-" + System.currentTimeMillis();

                System.out.println("No ID entered. Using generated ID: " + clientId);
            } else
            {
                synchronized (consoleLock)
                {
                    System.out.println("Client ID set to: " + clientId);
                }
            }

            synchronized (consoleLock)
            {
                System.out.println();
            }

            // Main loop: read jobs from the user with proper validation
            while (true)
            {
                try
                {
                    // Get and validate job type
                    String type = "";
                    while (!type.equals("A") && !type.equals("B"))
                    {
                        synchronized (consoleLock)
                        {
                            System.out.print("Enter job type (A or B): ");
                        }
                        type = scanner.nextLine().trim().toUpperCase();

                        if (!type.equals("A") && !type.equals("B"))
                        {
                            synchronized (consoleLock)
                            {
                                System.out.println("Invalid job type. Please enter A or B.");
                            }
                        }
                    }

                    // Get and validate job ID
                    String id = "";
                    while (id.isEmpty())
                    {
                        synchronized (consoleLock)
                        {
                            System.out.print("Enter job ID: ");
                        }
                        id = scanner.nextLine().trim();

                        if (id.isEmpty())
                        {
                            synchronized (consoleLock)
                            {
                                System.out.println("Job ID cannot be empty. Please enter a valid job ID.");
                            }
                        }
                    }

                    // Message format: SUBMIT;clientId;type;jobId
                    String msg = "SUBMIT;" + clientId + ";" + type + ";" + id;

                    // Put message into shared queue for the sender thread
                    outgoingQueue.add(msg);

                    System.out.println("Client [" + clientId + "]: Queued job " + id + " (type " + type + ")");
                    System.out.println(); // Add blank line for readability

                } catch (Exception e)
                {
                    synchronized (consoleLock)
                    {
                        System.err.println("Error reading input: " + e.getMessage());
                    }
                }
            }
        }, "UserInputThread");

        t.setDaemon(false); // Keep JVM running
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

                    synchronized (consoleLock)
                    {
                        System.out.println("\nClient [" + clientId + "]: Submitted to master: " + msg);
                    }
                }
            } catch (Exception e)
            {
                synchronized (consoleLock)
                {
                    System.err.println("Sender thread encountered an error:");
                    e.printStackTrace();
                }
            }
        }, "SenderThread");

        t.setDaemon(true); // Don't prevent JVM shutdown
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
                    synchronized (consoleLock)
                    {
                        System.out.println("\n--- Master Response ---");
                        System.out.println("Client [" + clientId + "]: Received from master: " + line);
                    }

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
                                System.out.println("Client [" + clientId + "]: ✓ Job " + jobId +
                                        " has been COMPLETED!");
                            } else
                            {
                                // This shouldn't normally happen with separate client connections
                                synchronized (consoleLock)
                                {
                                    System.out.println("Client [" + clientId + "]: (Note: Received completion " +
                                            "for " + doneClientId + "'s job " + jobId + ")");
                                }
                            }
                        }
                    }
                }

                System.err.println("Client [" + clientId + "]: Connection to master closed");

            } catch (Exception e)
            {
                synchronized (consoleLock)
                {
                    System.err.println("Listener thread encountered an error:");
                    e.printStackTrace();
                }
            }
        }, "ListenerThread");

        t.

                setDaemon(true); // Don't prevent JVM shutdown
        t.

                start();
    }
}
