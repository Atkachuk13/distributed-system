package network;

import java.io.*;
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
    private final HashMap<String, SlaveInfo> slaveRegistry;

    // Shared object for completed jobs queue
    private final BlockingQueue<JobCompletion> completedJobsQueue;

    private final HashMap<String, ClientInfo> clientRegistry;
    private final BlockingQueue<JobSubmission> jobSubmissionQueue;

    // jobId -> clientId
    private final HashMap<String, String> jobToClientMapping;

    // slave class for now, will be deleted later
    // DELETE THESE INNER CLASSES?????
    // CONNECT THEM !!!!!!
    private static class SlaveInfo
    {
        String slaveId;
        char slaveType;
        Socket socketToSlave;
        int currentLoad;
        PrintWriter out;
        BufferedReader in;
    }

    private static class ClientInfo
    {
        String clientId;
        Socket socketToClient;
        PrintWriter out;
        BufferedReader in;
    }

    private static class JobSubmission
    {
        String clientId;
        String jobType;
        String jobId;
    }

    public Master(int clientPort, int slavePort)
    {
        this.clientPort = clientPort;
        this.slavePort = slavePort;

        // Initialize thread pool - creates threads as needed
        this.threadPool = Executors.newCachedThreadPool();

        // Initialize shared objects
        this.slaveRegistry = new HashMap<>();
        this.completedJobsQueue = new LinkedBlockingQueue<>();
        this.clientRegistry = new HashMap<>();
        this.jobSubmissionQueue = new LinkedBlockingQueue<>();
        this.jobToClientMapping = new HashMap<>();

        System.out.println("Master initialized on client port " + clientPort +
                " and slave port " + slavePort);
    }

    private void acceptConnections()
    {
        try
        {
            // Create ServerSocket on slave port to listen for slave connections
            // Loop continuously to accept multiple slave connections
            try (ServerSocket serverSocket = new ServerSocket(5000))
            {
                System.out.println("Master: Listening for slave connections on port 5000");

                while (true)
                {
                    Socket newConnection = serverSocket.accept();
                    System.out.println("Master: New slave connection accepted from " +
                            newConnection.getInetAddress());
                    threadPool.execute(() -> handleNewConnection(newConnection));
                }
            }
        } catch (IOException e)
        {
            System.err.println("Master: Error accepting slave connections - " + e.getMessage());
            e.printStackTrace();
        }
    }

   private void registerSlave(Socket socketToSlave, String slaveType, BufferedReader in )
    {
        try
        {
            PrintWriter out = new PrintWriter(socketToSlave.getOutputStream(), true);

            String slaveId = "Slave-" + slaveType + "-" + System.currentTimeMillis();

            System.out.println("Master: Registering slave - ID: " + slaveId + ", Type: " + slaveType);

            // Validate slave type
            if (!slaveType.equals("A") && !slaveType.equals("B"))
            {
                System.err.println("Master: Invalid slave type: " + slaveType);
                socketToSlave.close();
                return;
            }

            // Create SlaveInfo object
            SlaveInfo slaveInfo = new SlaveInfo();
            slaveInfo.slaveId = slaveId;
            slaveInfo.slaveType = slaveType.charAt(0);
            slaveInfo.socketToSlave = socketToSlave;
            slaveInfo.currentLoad = 0;
            slaveInfo.out = out;
            slaveInfo.in = in;

            // Add to registry
            synchronized (slaveRegistry)
            {
                slaveRegistry.put(slaveId, slaveInfo);
            }

            System.out.println("Master: Slave " + slaveId + " successfully registered");

            // Start reader thread
            threadPool.execute(() -> readFromSlave(slaveId, slaveInfo));

        } catch (IOException e)
        {
            System.err.println("Master: Error during slave registration - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void readFromSlave(String slaveId, SlaveInfo slaveInfo)
    {
        try
        {
            BufferedReader in = slaveInfo.in;
            System.out.println("Master: Listening for messages from slave " + slaveId);

            String line;
            while ((line = in.readLine()) != null)
            {
                System.out.println("Master: Received from slave " + slaveId + ": " + line);

                if (line.startsWith("COMPLETE;"))
                {
                    String[] parts = line.split(";");
                    if (parts.length >= 2)
                    {
                        String jobIdStr = parts[1];

                        System.out.println("Master: Job " + jobIdStr + " completed by slave " + slaveId);

                        // Update slave load
                        synchronized (slaveRegistry)
                        {
                            // Determine job processing time based on match
                            // Need to track this when assigning jobs
                            slaveInfo.currentLoad -= 2; // Placeholder - need proper tracking
                            if (slaveInfo.currentLoad < 0) slaveInfo.currentLoad = 0;
                        }

                        System.out.println("Master: Slave " + slaveId + " current load: " +
                                slaveInfo.currentLoad);

                        // Add to completion queue
                        JobCompletion completion = new JobCompletion(Integer.parseInt(jobIdStr), slaveId);
                        completedJobsQueue.put(completion);
                    }
                }
            }

            System.err.println("Master: Slave " + slaveId + " disconnected");
            handleSlaveDisconnection(slaveId);

        } catch (IOException | InterruptedException e)
        {
            System.err.println("Master: Error reading from slave " + slaveId);
            e.printStackTrace();
            handleSlaveDisconnection(slaveId);
        }
    }

    private void handleSlaveDisconnection(String slaveId)
    {
        // Remove slave from registry
        synchronized (slaveRegistry)
        {
            SlaveInfo removed = slaveRegistry.remove(slaveId);
            if (removed != null)
            {
                System.out.println("Master: Removed slave " + slaveId + " from registry");

                // Close socket
                try
                {
                    removed.socketToSlave.close();
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

    private void handleClientConnection(Socket clientSocket, String firstMessage, BufferedReader in)
    {
        try
        {
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Extract clientId from first message: SUBMIT;clientId;type;jobId
            String[] parts = firstMessage.split(";");
            if (parts.length < 2)
            {
                System.err.println("Master: Invalid client message format");
                clientSocket.close();
                return;
            }

            String clientId = parts[1];

            System.out.println("Master: Registering client - ID: " + clientId);

            // Create ClientInfo object
            ClientInfo clientInfo = new ClientInfo();
            clientInfo.clientId = clientId;
            clientInfo.socketToClient = clientSocket;
            clientInfo.out = out;
            clientInfo.in = in;

            // Add to registry
            synchronized (clientRegistry)
            {
                clientRegistry.put(clientId, clientInfo);
            }

            System.out.println("Master: Client " + clientId + " successfully registered");

            // Process the first job submission
            processClientMessage(firstMessage, clientId);

            // Start reader thread to listen for more job submissions
            threadPool.execute(() -> readFromClient(clientId, clientInfo));

        } catch (IOException e)
        {
            System.err.println("Master: Error during client registration - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void readFromClient(String clientId, ClientInfo clientInfo)
    {
        try
        {
            BufferedReader in = clientInfo.in;
            System.out.println("Master: Listening for job submissions from client " + clientId);

            String line;
            while ((line = in.readLine()) != null)
            {
                System.out.println("Master: Received from client " + clientId + ": " + line);
                processClientMessage(line, clientId);
            }

            System.err.println("Master: Client " + clientId + " disconnected");
            handleClientDisconnection(clientId);

        } catch (IOException e)
        {
            System.err.println("Master: Error reading from client " + clientId);
            e.printStackTrace();
            handleClientDisconnection(clientId);
        }
    }

    private void processClientMessage(String message, String clientId)
    {
        try
        {
            if (message.startsWith("SUBMIT;"))
            {
                // Format: SUBMIT;clientId;type;jobId
                String[] parts = message.split(";");
                if (parts.length >= 4)
                {
                    String jobType = parts[2];
                    String jobId = parts[3];

                    System.out.println("Master: Received job " + jobId + " (Type " + jobType +
                            ") from client " + clientId);

                    // Track which client submitted this job
                    synchronized (jobToClientMapping)
                    {
                        jobToClientMapping.put(jobId, clientId);
                    }

                    // Create JobSubmission and add to queue
                    JobSubmission submission = new JobSubmission();
                    submission.clientId = clientId;
                    submission.jobType = jobType;
                    submission.jobId = jobId;

                    jobSubmissionQueue.put(submission);

                    System.out.println("Master: Job " + jobId + " added to assignment queue");
                }
            }
        } catch (InterruptedException e)
        {
            System.err.println("Master: Error queuing job from client " + clientId);
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

    private void handleClientDisconnection(String clientId)
    {
        synchronized (clientRegistry)
        {
            ClientInfo removed = clientRegistry.remove(clientId);
            if (removed != null)
            {
                System.out.println("Master: Removed client " + clientId + " from registry");
                try
                {
                    removed.socketToClient.close();
                } catch (IOException e)
                {
                    e.printStackTrace();                }
            }
        }
    }

    private void startJobAssignmentThread()
    {
        threadPool.execute(() -> {
            try
            {
                System.out.println("Master: Job assignment thread started");

                while (true)
                {
                    // Take job from queue (blocks until available)
                    JobSubmission job = jobSubmissionQueue.take();

                    System.out.println("Master: Processing job " + job.jobId +
                            " (Type " + job.jobType + ")");

                    // Select optimal slave
                    SlaveInfo selectedSlave = selectOptimalSlave(job.jobType);

                    if (selectedSlave != null)
                    {
                        assignJobToSlave(selectedSlave, job);
                    }
                    else
                    {
                        System.err.println("Master: No slaves available for job " + job.jobId);
                    }
                }
            } catch (InterruptedException e)
            {
                System.err.println("Master: Job assignment thread interrupted");
                Thread.currentThread().interrupt();
            }
        });
    }

    private SlaveInfo selectOptimalSlave(String jobType)
    {
        SlaveInfo bestSlave = null;
        int minCompletionTime = Integer.MAX_VALUE;

        synchronized (slaveRegistry)
        {
            for (SlaveInfo slave : slaveRegistry.values())
            {
                // Calculate completion time for this slave
                boolean isOptimal = String.valueOf(slave.slaveType).equals(jobType);
                int processingTime = isOptimal ? 2 : 10;
                int completionTime = slave.currentLoad + processingTime;

                if (completionTime < minCompletionTime)
                {
                    minCompletionTime = completionTime;
                    bestSlave = slave;
                }
            }
        }

        if (bestSlave != null)
        {
            System.out.println("Master: Selected slave " + bestSlave.slaveId +
                    " for job type " + jobType + " (current load: " + bestSlave.currentLoad +
                    " seconds, will complete in " + minCompletionTime + " seconds)");
        }

        return bestSlave;
    }

    private void assignJobToSlave(SlaveInfo slave, JobSubmission job)
    {
        boolean isOptimal = String.valueOf(slave.slaveType).equals(job.jobType);
        int processingTime = isOptimal ? 2 : 10;

        // Update slave load
        synchronized (slaveRegistry)
        {
            slave.currentLoad += processingTime;
        }

        // Send job to slave: JOB;type;jobId
        String jobMessage = "JOB;" + job.jobType + ";" + job.jobId;
        slave.out.println(jobMessage);

        System.out.println("Master: Assigned job " + job.jobId + " to slave " +
                slave.slaveId + " (Type " + slave.slaveType + ", " +
                (isOptimal ? "optimal" : "non-optimal") + " match, +" + processingTime +
                " seconds load)");
    }

    private void startCompletionNotificationThread()
    {
        threadPool.execute(() -> {
            try
            {
                System.out.println("Master: Completion notification thread started");

                while (true)
                {
                    // Take completion from queue
                    JobCompletion completion = completedJobsQueue.take();

                    System.out.println("Master: Processing completion for job " + completion.jobId);

                    // Find which client submitted this job
                    String clientId;
                    synchronized (jobToClientMapping)
                    {
                        clientId = jobToClientMapping.remove(String.valueOf(completion.jobId));
                    }

                    if (clientId != null)
                    {
                        notifyClientOfCompletion(clientId, String.valueOf(completion.jobId));
                    }
                    else
                    {
                        System.err.println("Master: No client found for completed job " + completion.jobId);
                    }
                }
            } catch (InterruptedException e)
            {
                System.err.println("Master: Completion notification thread interrupted");
                Thread.currentThread().interrupt();
            }
        });
    }

    private void notifyClientOfCompletion(String clientId, String jobId)
    {
        ClientInfo client;
        synchronized (clientRegistry)
        {
            client = clientRegistry.get(clientId);
        }

        if (client != null)
        {
            // Send completion message: DONE;clientId;jobId
            String completionMessage = "DONE;" + clientId + ";" + jobId;
            client.out.println(completionMessage);

            System.out.println("Master: Notified client " + clientId +
                    " that job " + jobId + " is complete");
        }
        else
        {
            System.err.println("Master: Client " + clientId + " not found for job completion notification");
        }
    }

    private void handleNewConnection(Socket newConnection)
    {
        try
        {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(newConnection.getInputStream()));

            // Read first message to determine the connection type
            String firstMessage = in.readLine();

            if (firstMessage == null)
            {
                System.err.println("Master: Connection closed before identification");
                newConnection.close();
                return;
            }

            System.out.println("Master: Received identification: " + firstMessage);

            if (firstMessage.startsWith("SLAVE;"))
            {
                // Handle slave connection
                String[] parts = firstMessage.split(";");
                if (parts.length >= 2)
                {
                    String slaveType = parts[1];
                    registerSlave(newConnection, slaveType, in);
                }
            } else if (firstMessage.startsWith("SUBMIT;"))
            {
                // Handle client connection
                handleClientConnection(newConnection, firstMessage, in);
            } else
            {
                System.err.println("Master: Unknown connection type: " + firstMessage);
                newConnection.close();
            }
        } catch (IOException e)
        {
            System.err.println("Master: Error handling connection -" + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        // Create master with default ports
        Master master = new Master(5000, 5000);

        // Start background threads
        master.startJobAssignmentThread();
        master.startCompletionNotificationThread();

        // Start accepting connections
        master.acceptConnections();
    }
}