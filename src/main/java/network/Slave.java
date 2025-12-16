package network;

import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Slave
{
    // Fields to store slave type (A or B), master's host/port, socket, and job queue
    private final String slaveType;
    private final String masterHost;
    private final int masterPort;
    private Socket socketToMaster;
    private final BlockingQueue<Job> jobQueue = new LinkedBlockingQueue<>();
    private PrintWriter outToMaster;
    private BufferedReader inFromMaster;

    // Constructor
    public Slave(String slaveType, String masterHost, int masterPort)
    {
        this.slaveType = slaveType.toUpperCase();  //making sure it's uppercase A or B
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    public static void main(String[] args)
    {
        // Check and parse command-line arguments
        // args should be: <A|B> <masterHost> <masterPort>
        if (args.length != 3)
        {
            System.out.println("Usage: java Slave <A|B> <masterHost> <masterPort>");
            return;
        }

        String slaveType = args[0];
        String masterHost = args[1];
        int masterPort = Integer.parseInt(args[2]);

        // Validate slave type
        if (!slaveType.equalsIgnoreCase("A") && !slaveType.equalsIgnoreCase("B"))
        {
            System.out.println("Error: Slave type must be A or B");
            return;
        }

        // Create a Slave instance and start it
        new Slave(slaveType, masterHost, masterPort).start();
    }

    public void start()
    {
        try
        {
            // 1. Connect to the master using a socket
            System.out.println("Slave-" + slaveType + ": Attempting to connect to master at " +
                    masterHost + ":" + masterPort);
            socketToMaster = new Socket(masterHost, masterPort);

            // 2. Setup input / output streams
            outToMaster = new PrintWriter(socketToMaster.getOutputStream(), true);
            inFromMaster = new BufferedReader(new InputStreamReader(socketToMaster.getInputStream()));

            // 3. Print confirmation of successful connection
            System.out.println("Slave-" + slaveType + ": Successfully connected to master");

            // 4. Announce slave type to master
            outToMaster.println("SLAVE;" + slaveType);
            System.out.println("Slave-" + slaveType + ": Announced type to master");

            // 5. Start a thread to listen for job assignments from the master
            Thread listenerThread = new Thread(this::listenForJobs, "Listener-Thread-Slave-" + slaveType);
            listenerThread.start();
            System.out.println("Slave-" + slaveType + ": Listener thread started");

            // 6. Process jobs sequentially (one at a time) from the queue
            // This ensures the master's load calculation remains accurate
            System.out.println("Slave-" + slaveType + ": Job processor thread started");

            while (true)
            {
                // This blocks until a job is available in the queue
                Job job = jobQueue.take();

                // Process job immediately in this thread (sequential processing)
                // This is crucial: we process ONE job at a time so the master's
                // load tracking (currentLoad) remains accurate
                processJob(job);
            }
        }
        catch (IOException e)
        {
            System.err.println("Slave-" + slaveType + ": Connection error - " + e.getMessage());
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            System.err.println("Slave-" + slaveType + ": Interrupted while waiting for job");
            e.printStackTrace();
        }
        catch (Exception e)
        {
            System.err.println("Slave-" + slaveType + ": Unexpected error");
            e.printStackTrace();
        }
    }

    // Thread method to listen for job assignments from the master
    public void listenForJobs()
    {
        try
        {
            System.out.println("Slave-" + slaveType + ": Ready to receive jobs from master");

            String line;

            // 1. Continuously read incoming lines
            while ((line = inFromMaster.readLine()) != null)
            {
                System.out.println("Slave-" + slaveType + ": Received message from master: " + line);

                // 2. For lines that start with "JOB", parse job type and job ID
                if (line.startsWith("JOB;"))
                {
                    // Expected format: "JOB;<type>;<jobId>"
                    String[] parts = line.split(";");

                    if (parts.length >= 3)
                    {
                        String jobType = parts[1];
                        String jobId = parts[2];

                        // 3. Create a new Job object and add it to the shared job queue
                        Job newJob = new Job(jobType, jobId);
                        jobQueue.add(newJob);

                        // 4. Print a message confirming the job was received
                        System.out.println("Slave-" + slaveType + ": Job " + jobId +
                                " (Type " + jobType + ") received and queued");
                    }
                    else
                    {
                        System.err.println("Slave-" + slaveType + ": Malformed job message: " + line);
                    }
                }
            }

            System.out.println("Slave-" + slaveType + ": Connection to master closed");

        }
        catch (IOException e)
        {
            System.err.println("Slave-" + slaveType + ": Error reading from master - " + e.getMessage());
            e.printStackTrace();
        }
        catch (Exception e)
        {
            System.err.println("Slave-" + slaveType + ": Unexpected error in listener thread");
            e.printStackTrace();
        }
    }

    // Method to process an individual job
    // IMPORTANT: This is now called sequentially, not in separate threads
    private void processJob(Job job)
    {
        try
        {
            System.out.println("Slave-" + slaveType + ": Starting to process Job " + job.jobId +
                    " (Type " + job.type + ")");

            // 1. Check if the job type matches the slave type
            boolean isOptimal = job.type.equalsIgnoreCase(slaveType);

            if (isOptimal)
            {
                // Optimal job: sleep for 2 seconds
                System.out.println("Slave-" + slaveType + ": Processing optimal job " + job.jobId +
                        " (2 seconds)");
                Thread.sleep(2000);
            }
            else
            {
                // Non-optimal job: sleep for 10 seconds
                System.out.println("Slave-" + slaveType + ": Processing non-optimal job " + job.jobId +
                        " (10 seconds)");
                Thread.sleep(10000);
            }

            // 2. After sleeping, send a completion message back to the master
            String completionMessage = "COMPLETE;" + job.jobId;
            outToMaster.println(completionMessage);

            // 3. Print a confirmation that the completion message was sent
            System.out.println("Slave-" + slaveType + ": Job " + job.jobId +
                    " completed and notified master");

        }
        catch (InterruptedException e)
        {
            System.err.println("Slave-" + slaveType + ": Job " + job.jobId + " was interrupted");
            e.printStackTrace();
        }
        catch (Exception e)
        {
            System.err.println("Slave-" + slaveType + ": Error processing job " + job.jobId);
            e.printStackTrace();
        }
    }
}