package network;

import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Slave
{
    // fields to store slave type (A or B), master's host/port, socket, and job queue
    private final String slaveType;
    private final String masterHost;
    private final int masterPort;
    private Socket socketToMaster;
    private final BlockingQueue<Job> jobQueue = new LinkedBlockingQueue<>();
    private PrintWriter outToMaster;
    private BufferedReader inFromMaster;

    // constructor
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

        // validate slave type
        if (!slaveType.equalsIgnoreCase("A") && !slaveType.equalsIgnoreCase("B"))
        {
            System.out.println("Error: Slave type must be A or B");
            return;
        }

        // create a Slave instance and start it
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

            // 6. Continuously take jobs from the queue and process them in separate threads
            while (true)
            {
                // this blocks until a job is available in the queue
                Job job = jobQueue.take();

                // Process each job in separate thread to allow concurrent job processing
                Thread jobThread = new Thread(() -> processJob(job),
                        "Job-Processor-Thread-" + job.jobId);
                jobThread.start();
            }
        } catch (IOException e)
        {
            System.err.println("Slave-" + slaveType + ": Connection error - " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e)
        {
            System.err.println("Slave-" + slaveType + ": Interrupted while waiting for job");
            e.printStackTrace();
        } catch (Exception e)
        {
            System.err.println("Slave-" + slaveType + ": Unexpected error");
            e.printStackTrace();
        }
    }

    // thread method to listen for job assignments from the master
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
                        int jobId = Integer.parseInt(parts[2]);

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

        } catch (IOException e)
        {
            System.err.println("Slave-" + slaveType + ": Error reading from master - " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e)
        {
            System.err.println("Slave-" + slaveType + ": Unexpected error in listener thread");
            e.printStackTrace();
        }
    }

    // method to process an individual job
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
                System.out.println("Slave-" + slaveType + ": Processing optimal job " + job.jobId);
                Thread.sleep(2000);
            } else
            {
                // non-optimal job: sleep for 10 seconds
                System.out.println("Slave-" + slaveType + ": Processing non-optimal job " + job.jobId);
                Thread.sleep(10000);
            }

            // 2. After sleeping, send a completion message back to the master
            String completionMessage = "COMPLETE;" + job.jobId;
            outToMaster.println(completionMessage);

            // 3. Print a confirmation that the completion message was sent
            System.out.println("Slave-" + slaveType + ": Job " + job.jobId + " completed and notified master");

        } catch (InterruptedException e)
        {
            System.err.println("Slave-" + slaveType + ": Job " + job.jobId + " was interrupted");
            e.printStackTrace();
        } catch (Exception e)
        {
            System.err.println("Slave-" + slaveType + ": Error processing job " + job.jobId);
            e.printStackTrace();
        }
    }

    // inner class representing a job
    private static class Job
    {
        String type;
        int jobId;

        // constructor to initialize job type and ID
        Job(String type, int jobId) {
            this.type = type;
            this.jobId = jobId;
        }
    }
}