package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class Master
{
    int clientPort;
    int slavePort;

    // Thread pool for managing concurrent connections
    private final ExecutorService threadPool;

    // Shared object for slave registry (thread-safe access)
    private final HashMap<String, SlaveInfo> sharedObjSlaveRegistry;

    // Shared object for completed jobs queue
    private final BlockingQueue<JobCompletion> sharedObjCompletedJobs;

    // TODO: Add more shared objects as needed
    // private BlockingQueue<Job> sharedObjJobQueue;
    // private HashMap<Integer, Integer> jobToClientMapping;
    // etc.

    // slave class for now, will be deleted later
    private class SlaveInfo
    {
        String slaveId;
        char slaveType;
        Socket commSockToSlave;
        int currentLoad;
        ObjectOutputStream out;
        ObjectInputStream in;
    }

    public Master(int clientPort, int slavePort)
    {
        this.clientPort = clientPort;
        this.slavePort = slavePort;

        // Initialize thread pool - creates threads as needed
        this.threadPool = Executors.newCachedThreadPool();

        // Initialize shared objects
        this.sharedObjSlaveRegistry = new HashMap<>();
        this.sharedObjCompletedJobs = new LinkedBlockingQueue<>();

        System.out.println("Master initialized on client port " + clientPort +
                " and slave port " + slavePort);
    }

    private void acceptSlaveConnections()
    {
        try
        {
            // Create ServerSocket on slave port to listen for slave connections
            ServerSocket slaveServerSocket = new ServerSocket(slavePort); // Use slavePort variable
            System.out.println("Master: Listening for slave connections on port " + slavePort);

            // Loop continuously to accept multiple slave connections
            while (true)
            {
                // Accept incoming slave connection - this blocks until a slave connects
                Socket commSockToSlave = slaveServerSocket.accept();
                System.out.println("Master: New slave connection accepted from " +
                        commSockToSlave.getInetAddress());

                // Start new thread to handle slave registration
                // This allows master to continue accepting other connections
                threadPool.execute(() -> registerSlave(commSockToSlave));
            }
        } catch (IOException e)
        {
            System.err.println("Master: Error accepting slave connections - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerSlave(Socket commSockToSlave)
    {
        try
        {
            // Create input and output streams for communication with this slave
            ObjectOutputStream out = new ObjectOutputStream(commSockToSlave.getOutputStream());
            out.flush(); // Important: flush the header
            ObjectInputStream in = new ObjectInputStream(commSockToSlave.getInputStream());

            // Read slave registration message
            // Expected format: First send slave ID (String), then slave type (Character 'A' or 'B')
            String slaveId = (String) in.readObject();
            Character slaveType = (Character) in.readObject();

            System.out.println("Master: Registering slave - ID: " + slaveId + ", Type: " + slaveType);

            // Validate slave type
            if (slaveType != 'A' && slaveType != 'B')
            {
                System.err.println("Master: Invalid slave type received: " + slaveType);
                commSockToSlave.close();
                return;
            }

            // Create SlaveInfo object and populate fields
            SlaveInfo slaveInfo = new SlaveInfo();
            slaveInfo.slaveId = slaveId;
            slaveInfo.slaveType = slaveType;
            slaveInfo.commSockToSlave = commSockToSlave;
            slaveInfo.currentLoad = 0; // Initially no jobs assigned
            slaveInfo.out = out;
            slaveInfo.in = in;

            // Add slave to sharedObjSlaveRegistry (thread-safe)
            // Use synchronized block or ConcurrentHashMap
            synchronized (sharedObjSlaveRegistry)
            {
                sharedObjSlaveRegistry.put(slaveId, slaveInfo);
            }

            // Send acknowledgment back to slave
            out.writeObject("REGISTERED");
            out.flush();

            System.out.println("Master: Slave " + slaveId + " (Type " + slaveType +
                    ") successfully registered");

            // Start reader thread for this slave to listen for job completions
            threadPool.execute(() -> readFromSlave(slaveId, slaveInfo));

        } catch (IOException | ClassNotFoundException e)
        {
            System.err.println("Master: Error during slave registration - " + e.getMessage());
            e.printStackTrace();
            try
            {
                commSockToSlave.close();
            } catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    private void readFromSlave(String slaveId, SlaveInfo slaveInfo)
    {
        try
        {
            ObjectInputStream in = slaveInfo.in;

            System.out.println("Master: Started listening for messages from slave " + slaveId);

            // Loop continuously to read messages from this slave
            while (true)
            {
                // Read job completion message from slave
                // Expected format: String "JOB_COMPLETE" followed by Integer jobId
                String messageType = (String) in.readObject();

                if (messageType.equals("JOB_COMPLETE"))
                {
                    Integer jobId = (Integer) in.readObject();

                    // Log receipt of completion
                    System.out.println("Master: Received job completion from slave " + slaveId +
                            " for job " + jobId);

                    // Update slave's current load (decrement) - thread-safe
                    synchronized (sharedObjSlaveRegistry)
                    {
                        slaveInfo.currentLoad--;
                        if (slaveInfo.currentLoad < 0)
                        {
                            slaveInfo.currentLoad = 0; // Safety check
                        }
                    }

                    System.out.println("Master: Slave " + slaveId + " load updated. Current load: " +
                            slaveInfo.currentLoad);

                    // Create completion info object
                    JobCompletion completion = new JobCompletion(jobId, slaveId);

                    // Add completion info to sharedObjCompletedJobs queue (thread-safe)
                    sharedObjCompletedJobs.put(completion); // BlockingQueue is thread-safe

                    System.out.println("Master: Job " + jobId + " completion queued for client notification");

                } else if (messageType.equals("HEARTBEAT"))
                {
                    // Optional: handle heartbeat messages to verify slave is alive
                    System.out.println("Master: Received heartbeat from slave " + slaveId);

                } else
                {
                    System.err.println("Master: Unknown message type from slave " + slaveId +
                            ": " + messageType);
                }
            }

        } catch (EOFException e)
        {
            // Slave disconnected
            System.err.println("Master: Slave " + slaveId + " disconnected");
            handleSlaveDisconnection(slaveId);

        } catch (IOException | ClassNotFoundException e)
        {
            System.err.println("Master: Error reading from slave " + slaveId + " - " + e.getMessage());
            e.printStackTrace();
            handleSlaveDisconnection(slaveId);

        } catch (InterruptedException e)
        {
            System.err.println("Master: Interrupted while queuing completion for slave " + slaveId);
            Thread.currentThread().interrupt();
        }
    }

    private void handleSlaveDisconnection(String slaveId)
    {
        // Remove slave from registry
        synchronized (sharedObjSlaveRegistry)
        {
            SlaveInfo removed = sharedObjSlaveRegistry.remove(slaveId);
            if (removed != null)
            {
                System.out.println("Master: Removed slave " + slaveId + " from registry");

                // Close socket
                try
                {
                    removed.commSockToSlave.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        // TODO: Handle any jobs that were assigned to this slave
        // For now, just log the issue
        System.err.println("Master: WARNING - Jobs assigned to slave " + slaveId +
                " may need to be reassigned");
    }

    public static void main(String[] args)
    {
        // Create master with default ports
        Master master = new Master(5000, 7700);

        // Start accepting slave connections
        master.acceptSlaveConnections();
    }
}