package network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class Master
{
    int clientPort;
    int slavePort;

    // thread pool for managing concurrent connections
    private final ExecutorService threadPool;

<<<<<<< HEAD
    // Shared object for slave registry (thread-safe access)
    private final ConcurrentHashMap<String, SlaveInfo> slaveRegistry;
=======
    // shared object for slave registry (thread-safe access)
    private final HashMap<String, SlaveInfo> slaveRegistry;
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0

    // shared object for completed jobs queue
    private final BlockingQueue<JobCompletion> completedJobsQueue;

    private final ConcurrentHashMap<String, ClientInfo> clientRegistry;
    private final BlockingQueue<JobSubmission> jobSubmissionQueue;

    // jobId -> clientId
    private final ConcurrentHashMap<String, String> jobToClientMapping;

    public Master(int clientPort, int slavePort)
    {
        this.clientPort = clientPort;
        this.slavePort = slavePort;

        // initialize thread pool
        this.threadPool = Executors.newCachedThreadPool();

<<<<<<< HEAD
        // Initialize shared objects with ConcurrentHashMap
        this.slaveRegistry = new ConcurrentHashMap<>();
=======
        // initialize shared objects
        this.slaveRegistry = new HashMap<>();
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
        this.completedJobsQueue = new LinkedBlockingQueue<>();
        this.clientRegistry = new ConcurrentHashMap<>();
        this.jobSubmissionQueue = new LinkedBlockingQueue<>();
        this.jobToClientMapping = new ConcurrentHashMap<>();

        System.out.println("Master initialized on client port " + clientPort +
                " and slave port " + slavePort);
    }

    private void acceptConnections()
    {
        threadPool.execute(() -> acceptSlaveConnections());
        threadPool.execute(() -> acceptClientConnections());
    }

    private void acceptSlaveConnections()
    {
        try (ServerSocket ss = new ServerSocket(slavePort))
        {
            System.out.println("Master: Listening for slave connections on port " + slavePort);
            while (true)
            {
                Socket s = ss.accept();
                System.out.println("Master: New connection on slave port from " + s.getInetAddress());
                threadPool.execute(() -> handleSlaveConnection(s));
            }
        }
        catch (IOException e)
        {
            System.err.println("Master: Error accepting slave connections");
            e.printStackTrace();
        }
    }

    private void acceptClientConnections()
    {
        try (ServerSocket ss = new ServerSocket(clientPort))
        {
            System.out.println("Master: Listening for client connections on port " + clientPort);
            while (true)
            {
                Socket s = ss.accept();
                System.out.println("Master: New connection on client port from " + s.getInetAddress());
                threadPool.execute(() -> handleClientConnection(s));
            }
        }
        catch (IOException e)
        {
            System.err.println("Master: Error accepting client connections");
            e.printStackTrace();
        }
    }

<<<<<<< HEAD
    private void handleSlaveConnection(Socket slaveSocket)
    {
        try
        {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(slaveSocket.getInputStream()));

            // Read first message to get slave type
            String firstMessage = in.readLine();

            if (firstMessage == null)
            {
                System.err.println("Master: Slave connection closed before identification");
                slaveSocket.close();
                return;
            }

            System.out.println("Master: Received slave identification: " + firstMessage);

            if (firstMessage.startsWith("SLAVE;"))
            {
                String[] parts = firstMessage.split(";");
                if (parts.length >= 2)
                {
                    String slaveType = parts[1];
                    registerSlave(slaveSocket, slaveType, in);
                }
                else
                {
                    System.err.println("Master: Invalid slave identification format");
                    slaveSocket.close();
                }
            }
            else
            {
                System.err.println("Master: Expected SLAVE message, got: " + firstMessage);
                slaveSocket.close();
            }
        }
        catch (IOException e)
        {
            System.err.println("Master: Error handling slave connection - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleClientConnection(Socket clientSocket)
    {
        try
        {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Read first message to get client ID
            String firstMessage = in.readLine();

            if (firstMessage == null)
            {
                System.err.println("Master: Client connection closed before identification");
                clientSocket.close();
                return;
            }

            System.out.println("Master: Received from client: " + firstMessage);

            if (firstMessage.startsWith("SUBMIT;"))
            {
                // Extract clientId from first message: SUBMIT;clientId;type;jobId
                String[] parts = firstMessage.split(";");
                if (parts.length < 4)
                {
                    System.err.println("Master: Invalid client message format: " + firstMessage);
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
                clientRegistry.put(clientId, clientInfo);

                System.out.println("Master: Client " + clientId + " successfully registered");

                // Process the first job submission
                processClientMessage(firstMessage, clientId);

                // Continue reading messages from this client
                readFromClient(clientId, clientInfo);
            }
            else
            {
                System.err.println("Master: Expected SUBMIT message from client, got: " + firstMessage);
                clientSocket.close();
            }
        }
        catch (IOException e)
        {
            System.err.println("Master: Error handling client connection - " + e.getMessage());
            e.printStackTrace();
        }
    }

=======
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
    private void registerSlave(Socket socketToSlave, String slaveType, BufferedReader in)
    {
        try
        {
            PrintWriter out = new PrintWriter(socketToSlave.getOutputStream(), true);

            String slaveId = "Slave-" + slaveType + "-" + System.currentTimeMillis();

            System.out.println("Master: Registering slave - ID: " + slaveId + ", Type: " + slaveType);

            // validate slave type
            if (!slaveType.equals("A") && !slaveType.equals("B"))
            {
                System.err.println("Master: Invalid slave type: " + slaveType);
                socketToSlave.close();
                return;
            }

            // create SlaveInfo object
            SlaveInfo slaveInfo = new SlaveInfo();
            slaveInfo.slaveId = slaveId;
            slaveInfo.slaveType = slaveType.charAt(0);
            slaveInfo.socketToSlave = socketToSlave;
            slaveInfo.currentLoad = 0;
            slaveInfo.out = out;
            slaveInfo.in = in;

<<<<<<< HEAD
            // Add to registry (ConcurrentHashMap, no synchronization needed)
            slaveRegistry.put(slaveId, slaveInfo);
=======
            // add to registry
            synchronized (slaveRegistry)
            {
                slaveRegistry.put(slaveId, slaveInfo);
            }
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0

            System.out.println("Master: Slave " + slaveId + " successfully registered");

            // start reader thread
            threadPool.execute(() -> readFromSlave(slaveId, slaveInfo));

        }
        catch (IOException e)
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

                        // Update slave load using exact job time that was tracked
                        Integer processingTime = slaveInfo.activeJobs.remove(jobIdStr);
                        if (processingTime != null)
                        {
                            slaveInfo.currentLoad -= processingTime;

                            // Ensure load doesn't go negative (safety check)
                            if (slaveInfo.currentLoad < 0)
                            {
                                slaveInfo.currentLoad = 0;
                            }

                            System.out.println("Master: Slave " + slaveId + " load decreased by " +
                                    processingTime + " seconds, current load: " + slaveInfo.currentLoad);
                        }
                        else
                        {
                            System.err.println("Master: WARNING - Job " + jobIdStr +
                                    " completed but no processing time was tracked");
                        }

                        // Add to completion queue
                        JobCompletion completion = new JobCompletion(jobIdStr, slaveId);
                        completedJobsQueue.put(completion);
                    }
                }
            }

            System.err.println("Master: Slave " + slaveId + " disconnected");
            handleSlaveDisconnection(slaveId);

        }
        catch (IOException | InterruptedException e)
        {
            System.err.println("Master: Error reading from slave " + slaveId);
            e.printStackTrace();
            handleSlaveDisconnection(slaveId);
        }
    }

    private void handleSlaveDisconnection(String slaveId)
    {
<<<<<<< HEAD
        // Remove slave from registry
        SlaveInfo removed = slaveRegistry.remove(slaveId);
        if (removed != null)
=======
        // remove slave from registry
        synchronized (slaveRegistry)
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
        {
            System.out.println("Master: Removed slave " + slaveId + " from registry");

<<<<<<< HEAD
            // Close socket
            try
            {
                removed.socketToSlave.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
=======
                // close socket
                try
                {
                    removed.socketToSlave.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
            }
        }

        // TODO: Handle any jobs that were assigned to this slave
        // for now, just log the issue
        System.err.println("Master: WARNING - Jobs assigned to slave " + slaveId +
                " may need to be reassigned");
    }

<<<<<<< HEAD
=======
    private void handleClientConnection(Socket clientSocket, String firstMessage, BufferedReader in)
    {
        try
        {
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            // extract clientId from first message: SUBMIT;clientId;type;jobId
            String[] parts = firstMessage.split(";");
            if (parts.length < 2)
            {
                System.err.println("Master: Invalid client message format");
                clientSocket.close();
                return;
            }

            String clientId = parts[1];

            System.out.println("Master: Registering client - ID: " + clientId);

            // create ClientInfo object
            ClientInfo clientInfo = new ClientInfo();
            clientInfo.clientId = clientId;
            clientInfo.socketToClient = clientSocket;
            clientInfo.out = out;
            clientInfo.in = in;

            // add to registry
            synchronized (clientRegistry)
            {
                clientRegistry.put(clientId, clientInfo);
            }

            System.out.println("Master: Client " + clientId + " successfully registered");

            // process the first job submission
            processClientMessage(firstMessage, clientId);

            // start reader thread to listen for more job submissions
            threadPool.execute(() -> readFromClient(clientId, clientInfo));

        } catch (IOException e)
        {
            System.err.println("Master: Error during client registration - " + e.getMessage());
            e.printStackTrace();
        }
    }

>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
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

        }
        catch (IOException e)
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
                // format: SUBMIT;clientId;type;jobId
                String[] parts = message.split(";");
                if (parts.length >= 4)
                {
                    String jobType = parts[2];
                    String jobId = parts[3];

                    // Validate job type
                    if (jobType.isEmpty() || (!jobType.equals("A") && !jobType.equals("B")))
                    {
                        System.err.println("Master: Invalid job type '" + jobType + "' from client " + clientId);
                        return;
                    }

                    // Validate job ID
                    if (jobId.isEmpty())
                    {
                        System.err.println("Master: Empty job ID from client " + clientId);
                        return;
                    }

                    System.out.println("Master: Received job " + jobId + " (Type " + jobType +
                            ") from client " + clientId);

<<<<<<< HEAD
                    // Track which client submitted this job
                    jobToClientMapping.put(jobId, clientId);
=======
                    // track which client submitted this job
                    synchronized (jobToClientMapping)
                    {
                        jobToClientMapping.put(jobId, clientId);
                    }
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0

                    // create JobSubmission and add to queue
                    JobSubmission submission = new JobSubmission();
                    submission.clientId = clientId;
                    submission.jobType = jobType;
                    submission.jobId = jobId;

                    jobSubmissionQueue.put(submission);

                    System.out.println("Master: Job " + jobId + " added to assignment queue");
                }
            }
        }
        catch (InterruptedException e)
        {
            System.err.println("Master: Error queuing job from client " + clientId);
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

    private void handleClientDisconnection(String clientId)
    {
        ClientInfo removed = clientRegistry.remove(clientId);
        if (removed != null)
        {
            System.out.println("Master: Removed client " + clientId + " from registry");
            try
            {
                removed.socketToClient.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void startJobAssignmentThread()
    {
        threadPool.execute(() ->
        {
            try
            {
                System.out.println("Master: Job assignment thread started");

                while (true)
                {
                    // take job from queue (blocks until available)
                    JobSubmission job = jobSubmissionQueue.take();

                    System.out.println("Master: Processing job " + job.jobId +
                            " (Type " + job.jobType + ") from client " + job.clientId);

<<<<<<< HEAD
                    // Select optimal slave based on load and job type
=======
                    // select optimal slave
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
                    SlaveInfo selectedSlave = selectOptimalSlave(job.jobType);

                    if (selectedSlave != null)
                    {
                        assignJobToSlave(selectedSlave, job);
                    } else
                    {
                        System.err.println("Master: No slaves available for job " + job.jobId);
                    }
                }
            }
            catch (InterruptedException e)
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

        for (SlaveInfo slave : slaveRegistry.values())
        {
<<<<<<< HEAD
            // Calculate completion time for this slave
            boolean isOptimal = String.valueOf(slave.slaveType).equals(jobType);
            int processingTime = isOptimal ? 2 : 10;
            int completionTime = slave.currentLoad + processingTime;
=======
            for (SlaveInfo slave : slaveRegistry.values())
            {
                // calculate completion time for this slave
                boolean isOptimal = String.valueOf(slave.slaveType).equals(jobType);
                int processingTime = isOptimal ? 2 : 10;
                int completionTime = slave.currentLoad + processingTime;
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0

            if (completionTime < minCompletionTime)
            {
                minCompletionTime = completionTime;
                bestSlave = slave;
            }
        }

        if (bestSlave != null)
        {
            boolean isOptimal = String.valueOf(bestSlave.slaveType).equals(jobType);
            System.out.println("Master: Selected slave " + bestSlave.slaveId +
                    " (Type " + bestSlave.slaveType + ") for job type " + jobType +
                    " - current load: " + bestSlave.currentLoad +
                    " seconds, " + (isOptimal ? "optimal" : "non-optimal") +
                    " match, will complete in " + minCompletionTime + " seconds");
        }

        return bestSlave;
    }

    private void assignJobToSlave(SlaveInfo slave, JobSubmission job)
    {
        boolean isOptimal = String.valueOf(slave.slaveType).equals(job.jobType);
        int processingTime = isOptimal ? 2 : 10;

        // Update slave load and track the exact processing time for this job
        slave.currentLoad += processingTime;
        slave.activeJobs.put(job.jobId, processingTime);

        System.out.println("Master: Updated slave " + slave.slaveId + " load: +" +
                processingTime + " seconds, new total load: " + slave.currentLoad + " seconds");

        // send job to slave: JOB;type;jobId
        String jobMessage = "JOB;" + job.jobType + ";" + job.jobId;
        slave.out.println(jobMessage);

        System.out.println("Master: Assigned job " + job.jobId + " to slave " +
                slave.slaveId + " (Type " + slave.slaveType + ", " +
                (isOptimal ? "optimal" : "non-optimal") + " match)");
    }

    private void startCompletionNotificationThread()
    {
        threadPool.execute(() ->
        {
            try
            {
                System.out.println("Master: Completion notification thread started");

                while (true)
                {
                    // take completion from queue
                    JobCompletion completion = completedJobsQueue.take();

                    System.out.println("Master: Processing completion notification for job " +
                            completion.jobId);

<<<<<<< HEAD
                    // Find which client submitted this job
                    String clientId = jobToClientMapping.remove(completion.jobId);
=======
                    // find which client submitted this job
                    String clientId;
                    synchronized (jobToClientMapping)
                    {
                        clientId = jobToClientMapping.remove(completion.jobId);
                    }
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0

                    if (clientId != null)
                    {
<<<<<<< bugs
                        notifyClientOfCompletion(clientId, completion.jobId);
                    }
                    else
=======
                        notifyClientOfCompletion(clientId, String.valueOf(completion.jobId));
                    } else
>>>>>>> main
                    {
                        System.err.println("Master: No client found for completed job " +
                                completion.jobId);
                    }
                }
            }
            catch (InterruptedException e)
            {
                System.err.println("Master: Completion notification thread interrupted");
                Thread.currentThread().interrupt();
            }
        });
    }

    private void notifyClientOfCompletion(String clientId, String jobId)
    {
        ClientInfo client = clientRegistry.get(clientId);

        if (client != null)
        {
            // send completion message: DONE;clientId;jobId
            String completionMessage = "DONE;" + clientId + ";" + jobId;
            client.out.println(completionMessage);

            System.out.println("Master: Notified client " + clientId +
                    " that job " + jobId + " is complete");
        } else
        {
<<<<<<< HEAD
            System.err.println("Master: Client " + clientId +
                    " not found for job completion notification");
=======
            System.err.println("Master: Client " + clientId + " not found for job completion notification");
        }
    }

    private void handleNewConnection(Socket newConnection)
    {
        try
        {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(newConnection.getInputStream()));

            // read first message to determine the connection type
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
                // handle slave connection
                String[] parts = firstMessage.split(";");
                if (parts.length >= 2)
                {
                    String slaveType = parts[1];
                    registerSlave(newConnection, slaveType, in);
                }
            } else if (firstMessage.startsWith("SUBMIT;"))
            {
                // handle client connection
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
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
        }
    }

    public static void main(String[] args)
    {
<<<<<<< HEAD
        // Create master with separate ports for clients and slaves
        // Clients connect on port 6000, slaves connect on port 6001
=======
<<<<<<< bugs
        // Create master with default ports
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
        Master master = new Master(6000, 6001);
=======
        // master with default ports
        Master master = new Master(6000, 6000);
>>>>>>> main

        // start background threads
        master.startJobAssignmentThread();
        master.startCompletionNotificationThread();

<<<<<<< HEAD
        // Start accepting connections (separate ports for clients and slaves)
=======
        // accepting connections
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
        master.acceptConnections();
    }
}