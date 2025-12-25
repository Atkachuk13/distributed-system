/*
 * WE DID THE EXTRA CREDIT IMPLEMENTATION
 * This system supports dynamic slaves and clients joining at any time.
 */

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

    private final ConcurrentHashMap<String, SlaveInfo> slaveRegistry;
    private final BlockingQueue<JobCompletion> completedJobsQueue;

    private final ConcurrentHashMap<String, ClientInfo> clientRegistry;
    private final BlockingQueue<JobSubmission> jobSubmissionQueue;

    // jobId -> clientId
    private final ConcurrentHashMap<String, String> jobToClientMapping;

    public Master(int clientPort, int slavePort)
    {
        this.clientPort = clientPort;
        this.slavePort = slavePort;

        // Initialize thread pool - creates threads as needed
        this.threadPool = Executors.newCachedThreadPool();

        // Initialize shared objects with ConcurrentHashMap
        this.slaveRegistry = new ConcurrentHashMap<>();
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
        } catch (IOException e)
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
        } catch (IOException e)
        {
            System.err.println("Master: Error accepting client connections");
            e.printStackTrace();
        }
    }

    private void handleSlaveConnection(Socket slaveSocket)
    {
        try
        {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(slaveSocket.getInputStream()));

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
                } else
                {
                    System.err.println("Master: Invalid slave identification format");
                    slaveSocket.close();
                }
            } else
            {
                System.err.println("Master: Expected SLAVE message, got: " + firstMessage);
                slaveSocket.close();
            }
        } catch (IOException e)
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
                // extract clientId from first message: SUBMIT;clientId;type;jobId
                String[] parts = firstMessage.split(";");
                if (parts.length < 4)
                {
                    System.err.println("Master: Invalid client message format: " + firstMessage);
                    clientSocket.close();
                    return;
                }

                String clientId = parts[1];

                System.out.println("Master: Registering client - ID: " + clientId);

                ClientInfo clientInfo = new ClientInfo();
                clientInfo.clientId = clientId;
                clientInfo.socketToClient = clientSocket;
                clientInfo.out = out;
                clientInfo.in = in;

                clientRegistry.put(clientId, clientInfo);

                System.out.println("Master: Client " + clientId + " successfully registered");

                // first job submission
                processClientMessage(firstMessage, clientId);

                // continue reading messages from this client
                readFromClient(clientId, clientInfo);
            } else
            {
                System.err.println("Master: Expected SUBMIT message from client, got: " + firstMessage);
                clientSocket.close();
            }
        } catch (IOException e)
        {
            System.err.println("Master: Error handling client connection - " + e.getMessage());
            e.printStackTrace();
        }
    }

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

            slaveRegistry.put(slaveId, slaveInfo);

            System.out.println("Master: Slave " + slaveId + " successfully registered");

            // start reader thread
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

                        // update slave load
                        Integer processingTime;
                        synchronized (slaveInfo)
                        {
                            processingTime = slaveInfo.activeJobs.remove(jobIdStr);
                            if (processingTime != null)
                            {
                                slaveInfo.currentLoad -= processingTime;
                                if (slaveInfo.currentLoad < 0)
                                {
                                    slaveInfo.currentLoad = 0;
                                }
                            }
                        }

                        if (processingTime != null)
                        {
                            System.out.println("Master: Slave " + slaveId + " load decreased by " +
                                    processingTime + " seconds, current load: " + slaveInfo.currentLoad);
                        } else
                        {
                            System.err.println("Master: WARNING - Job " + jobIdStr +
                                    " completed but no processing time was tracked");
                        }

                        JobCompletion completion = new JobCompletion(jobIdStr, slaveId);
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
        // remove slave from registry
        SlaveInfo removed = slaveRegistry.remove(slaveId);
        if (removed != null)
        {
            System.out.println("Master: Removed slave " + slaveId + " from registry");

            // handle job reassignment before closing the socket
            reassignJobsFromDisconnectedSlave(removed);

            try
            {
                removed.socketToSlave.close();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void reassignJobsFromDisconnectedSlave(SlaveInfo disconnectedSlave)
    {
        // get all active jobs that were assigned to this slave
        ConcurrentHashMap<String, Integer> activeJobs = disconnectedSlave.activeJobs;

        if (activeJobs.isEmpty())
        {
            System.out.println("Master: No active jobs to reassign from slave " +
                    disconnectedSlave.slaveId);
            return;
        }

        System.out.println("Master: Reassigning " + activeJobs.size() +
                " jobs from disconnected slave " + disconnectedSlave.slaveId);

        // iterate through all jobs that were on this slave
        for (String jobId : activeJobs.keySet())
        {
            // find the client who submitted this job
            String clientId = jobToClientMapping.get(jobId);

            if (clientId != null)
            {
                System.out.println("Master: Reassigning job " + jobId +
                        " (originally from client " + clientId + ")");

                Integer processingTime = activeJobs.get(jobId);
                String jobType;

                // 2 seconds = optimal match, 10 seconds = non-optimal match
                if (processingTime == 2)
                {
                    // optimal match
                    jobType = String.valueOf(disconnectedSlave.slaveType);
                } else if (processingTime == 10)
                {
                    // non-optimal
                    jobType = disconnectedSlave.slaveType == 'A' ? "B" : "A";
                } else
                {
                    // unknown processing time, default to trying both
                    System.err.println("Master: WARNING - Unknown processing time " +
                            processingTime + " for job " + jobId + ", defaulting to type A");
                    jobType = "A";
                }

                // new JobSubmission for reassignment
                JobSubmission reassignment = new JobSubmission();
                reassignment.clientId = clientId;
                reassignment.jobType = jobType;
                reassignment.jobId = jobId;

                // add back to the job queue for reassignment
                try
                {
                    jobSubmissionQueue.put(reassignment);
                    System.out.println("Master: Job " + jobId + " (Type " + jobType +
                            ") queued for reassignment");
                } catch (InterruptedException e)
                {
                    System.err.println("Master: Failed to reassign job " + jobId);
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            } else
            {
                System.err.println("Master: WARNING - Cannot reassign job " + jobId +
                        ": client mapping not found");
            }
        }

        System.out.println("Master: Finished reassigning jobs from slave " +
                disconnectedSlave.slaveId);
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
                // format: SUBMIT;clientId;type;jobId
                String[] parts = message.split(";");
                if (parts.length >= 4)
                {
                    String jobType = parts[2];
                    String jobId = parts[3];

                    // validate job type
                    if (jobType.isEmpty() || (!jobType.equals("A") && !jobType.equals("B")))
                    {
                        System.err.println("Master: Invalid job type '" + jobType + "' from client " + clientId);
                        return;
                    }

                    // validate job ID
                    if (jobId.isEmpty())
                    {
                        System.err.println("Master: Empty job ID from client " + clientId);
                        return;
                    }

                    System.out.println("Master: Received job " + jobId + " (Type " + jobType +
                            ") from client " + clientId);

                    // track which client submitted this job
                    String existing = jobToClientMapping.putIfAbsent(jobId, clientId);
                    if (existing != null)
                    {
                        System.err.println(
                                "Master: Duplicate job ID " + jobId +
                                        " already submitted by client " + existing +
                                        " — rejecting new submission from " + clientId
                        );

                        notifyClientError(clientId, "DUPLICATE_JOB_ID", jobId);
                        return;
                    }

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
        ClientInfo removed = clientRegistry.remove(clientId);
        if (removed != null)
        {
            System.out.println("Master: Removed client " + clientId + " from registry");
            try
            {
                removed.socketToClient.close();
            } catch (IOException e)
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

                    // select optimal slave based on load and job type
                    SlaveInfo selectedSlave = selectOptimalSlave(job.jobType);

                    if (selectedSlave != null)
                    {
                        assignJobToSlave(selectedSlave, job);
                    } else
                    {
                        System.err.println(
                                "Master: No slaves available for job " + job.jobId + " — re-queuing"
                        );
                        jobSubmissionQueue.put(job);
                        Thread.sleep(200); // small delay to avoid busy loop
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
        if (slaveRegistry.isEmpty())
        {
            System.err.println("Master: No slaves available! Job will wait for slaves to connect.");
            return null;
        }

        SlaveInfo bestSlave = null;
        int minCompletionTime = Integer.MAX_VALUE;

        for (SlaveInfo slave : slaveRegistry.values())
        {
            int loadSnapshot;

            synchronized (slave)
            {
                loadSnapshot = slave.currentLoad;
            }

            boolean isOptimal = String.valueOf(slave.slaveType).equals(jobType);
            int processingTime = isOptimal ? 2 : 10;
            int completionTime = loadSnapshot + processingTime;

            if (completionTime < minCompletionTime)
            {
                minCompletionTime = completionTime;
                bestSlave = slave;
            }
        }

        if (bestSlave != null)
        {
            int bestLoadSnapshot;
            synchronized (bestSlave)
            {
                bestLoadSnapshot = bestSlave.currentLoad;
            }

            boolean isOptimal = String.valueOf(bestSlave.slaveType).equals(jobType);

            System.out.println("Master: Selected slave " + bestSlave.slaveId +
                    " (Type " + bestSlave.slaveType + ") for job type " + jobType +
                    " - current load: " + bestLoadSnapshot +
                    " seconds, " + (isOptimal ? "optimal" : "non-optimal") +
                    " match, will complete in " + minCompletionTime + " seconds");
        }

        return bestSlave;
    }


    private void assignJobToSlave(SlaveInfo slave, JobSubmission job)
    {
        boolean isOptimal = String.valueOf(slave.slaveType).equals(job.jobType);
        int processingTime = isOptimal ? 2 : 10;

        String jobMessage = "JOB;" + job.jobType + ";" + job.jobId;

        synchronized (slave)
        {
            slave.currentLoad += processingTime;
            slave.activeJobs.put(job.jobId, processingTime);
            slave.out.println(jobMessage);
        }

        System.out.println("Master: Updated slave " + slave.slaveId + " load: +" +
                processingTime + " seconds, new total load: " + slave.currentLoad + " seconds");

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

                    // find which client submitted this job
                    String clientId = jobToClientMapping.remove(completion.jobId);

                    if (clientId != null)
                    {
                        notifyClientOfCompletion(clientId, completion.jobId);
                    } else
                    {
                        System.err.println("Master: No client found for completed job " +
                                completion.jobId);
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
        ClientInfo client = clientRegistry.get(clientId);

        if (client != null)
        {
            String completionMessage = "DONE;" + clientId + ";" + jobId;

            synchronized (client)
            {
                client.out.println(completionMessage);
            }

            System.out.println("Master: Notified client " + clientId +
                    " that job " + jobId + " is complete");
        } else
        {
            System.err.println("Master: Client " + clientId +
                    " not found for job completion notification");
        }
    }

    private void notifyClientError(String clientId, String errorCode, String jobId)
    {
        ClientInfo client = clientRegistry.get(clientId);
        if (client == null)
        {
            System.err.println("Master: Cannot send error to client " + clientId + " (not registered)");
            return;
        }

        String msg = "ERROR;" + errorCode + ";" + jobId;

        synchronized (client)
        {
            client.out.println(msg);
        }

        System.out.println("Master: Sent error to client " + clientId + ": " + msg);
    }

    public static void main(String[] args)
    {
        // master with separate ports for clients and slaves
        // clients connect on port 6000
        // slaves connect on port 6001
        Master master = new Master(6000, 6001);

        // background threads
        master.startJobAssignmentThread();
        master.startCompletionNotificationThread();

        // accepting connections
        master.acceptConnections();
    }
}