package network;

import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Slave
{
    private final String slaveType;
    private final String masterHost;
    private final int masterPort;
    private Socket socketToMaster;
    private final BlockingQueue<Job> jobQueue = new LinkedBlockingQueue<>();
    private PrintWriter outToMaster;
    private BufferedReader inFromMaster;

    public Slave(String slaveType, String masterHost, int masterPort)
    {
        this.slaveType = slaveType.toUpperCase();
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    public static void main(String[] args)
    {
        // check and parse command-line arguments
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
            // 1. connect to the master using a socket
            System.out.println("Slave-" + slaveType + ": Attempting to connect to master at " +
                    masterHost + ":" + masterPort);
            socketToMaster = new Socket(masterHost, masterPort);

            // 2. setup input / output streams
            outToMaster = new PrintWriter(socketToMaster.getOutputStream(), true);
            inFromMaster = new BufferedReader(new InputStreamReader(socketToMaster.getInputStream()));

            // 3. print confirmation of successful connection
            System.out.println("Slave-" + slaveType + ": Successfully connected to master");

            // 4. announce slave type to master
            outToMaster.println("SLAVE;" + slaveType);
            System.out.println("Slave-" + slaveType + ": Announced type to master");

            // 5. start a thread to listen for job assignments from the master
            Thread listenerThread = new Thread(this::listenForJobsFromMaster, "Listener-Thread-Slave-" + slaveType);
            listenerThread.start();
            System.out.println("Slave-" + slaveType + ": Listener thread started");

<<<<<<< HEAD
            // 6. Process jobs sequentially (one at a time) from the queue
            // This ensures the master's load calculation remains accurate
            System.out.println("Slave-" + slaveType + ": Job processor thread started");

=======
            // 6. continuously take jobs from the queue and process them in separate threads
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
            while (true)
            {
                // This blocks until a job is available in the queue
                Job job = jobQueue.take();

<<<<<<< HEAD
                // Process job immediately in this thread (sequential processing)
                // This is crucial: we process ONE job at a time so the master's
                // load tracking (currentLoad) remains accurate
                processJob(job);
=======
                // process each job in separate thread to allow concurrent job processing
                Thread jobThread = new Thread(() -> processJob(job),
                        "Job-Processor-Thread-" + job.jobId);
                jobThread.start();
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
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

    // listen for job assignments from the master
    public void listenForJobsFromMaster()
    {
        try
        {
            System.out.println("Slave-" + slaveType + ": Ready to receive jobs from master");

            String line;

            // 1. continuously read incoming lines
            while ((line = inFromMaster.readLine()) != null)
            {
                System.out.println("Slave-" + slaveType + ": Received message from master: " + line);

                // 2. for lines that start with "JOB", parse job type and job ID
                if (line.startsWith("JOB;"))
                {
                    // expected format: "JOB;<type>;<jobId>"
                    String[] parts = line.split(";");

                    if (parts.length >= 3)
                    {
                        String jobType = parts[1];
                        String jobId = parts[2];

                        // 3. create a new Job object and add it to the shared job queue
                        Job newJob = new Job(jobType, jobId);
                        jobQueue.add(newJob);

                        // 4. print a message confirming the job was received
                        System.out.println("Slave-" + slaveType + ": Job " + jobId +
                                " (Type " + jobType + ") received and queued");
                    } else
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

<<<<<<< HEAD
    // Method to process an individual job
    // IMPORTANT: This is now called sequentially, not in separate threads
=======
    // process an individual job
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
    private void processJob(Job job)
    {
        try
        {
            System.out.println("Slave-" + slaveType + ": Starting to process Job " + job.jobId +
                    " (Type " + job.type + ")");

            // 1. check if the job type matches the slave type
            boolean isOptimal = job.type.equalsIgnoreCase(slaveType);

            if (isOptimal)
            {
<<<<<<< HEAD
                // Optimal job: sleep for 2 seconds
                System.out.println("Slave-" + slaveType + ": Processing optimal job " + job.jobId +
                        " (2 seconds)");
=======
                // optimal job: sleep for 2 seconds
                System.out.println("Slave-" + slaveType + ": Processing optimal job " + job.jobId);
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0
                Thread.sleep(2000);
            }
            else
            {
                // Non-optimal job: sleep for 10 seconds
                System.out.println("Slave-" + slaveType + ": Processing non-optimal job " + job.jobId +
                        " (10 seconds)");
                Thread.sleep(10000);
            }

            // 2. after sleeping, send a completion message back to the master
            String completionMessage = "COMPLETE;" + job.jobId;
            outToMaster.println(completionMessage);

<<<<<<< HEAD
            // 3. Print a confirmation that the completion message was sent
            System.out.println("Slave-" + slaveType + ": Job " + job.jobId +
                    " completed and notified master");
=======
            // 3. print a confirmation that the completion message was sent
            System.out.println("Slave-" + slaveType + ": Job " + job.jobId + " completed and notified master");
>>>>>>> 665d6b12c798d1b53eebcd20f1cb6e77abc438f0

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