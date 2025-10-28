/*
slave program (with command line argument A or B)
    Connect to master on given host/port with commSock
    While running:
        Wait for job assignment from master
        For each received job:
            If job type matches slave type:
                Print "Sleeping for 2 seconds for optimal job type, be back soon."
                Sleep(2s)
            Else:
                Print "Sleeping for 10 seconds for non-optimal job type, be back soon."
                Sleep(10s)
            After sleeping:
                Send completion message to master with job ID
                Print confirmation of completion sent
 */

package network;

import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Slave
{

    // Fields to store slave type (A or B), master's host/port, socket, and job queue
    private final String slaveType;
    private final String masterHost;
    private final int masterPort;
    private Socket commSockMaster;
    private final BlockingQueue<Job> sharedObjectJobQueue = new LinkedBlockingQueue<>();

    // Constructor
    public Slave(String slaveType, String masterHost, int masterPort)
    {
        this.slaveType = slaveType;
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

        // Create a Slave instance and start it
        new Slave(slaveType, masterHost, masterPort).start();
    }

    public void start()
    {
        try
        {
            // 1. Connect to the master using a socket
            // 2. Print confirmation of successful connection
            // 3. Start a thread to listen for job assignments from the master
            // 4. Continuously take jobs from the queue and process them in separate threads

        } catch (Exception e)
        {
            // Handle errors in connection or runtime
            e.printStackTrace();
        }
    }

    // Thread method to listen for job assignments from the master
    public void listenForJobs()
    {
        try
        {
            // 1. Create an input stream to read messages from the master
            // 2. Continuously read incoming lines
            // 3. For lines that start with "JOB", parse job type and job ID
            // 4. Create a new Job object and add it to the shared job queue
            // 5. Print a message confirming the job was received

        } catch (Exception e)
        {
            // Handle errors while receiving or parsing job messages
            e.printStackTrace();
        }
    }

    // Method to process an individual job
    private void processJob(Job job)
    {
        try
        {
            // 1. Check if the job type matches the slave type
            //     - If yes: print a message and sleep for 2 seconds
            //     - If no: print a message and sleep for 10 seconds
            // 2. After sleeping, send a completion message ("COMPLETE <jobId>") back to the master
            // 3. Print a confirmation that the completion message was sent

        } catch (Exception e)
        {
            // Handle any errors that occur during job processing
            e.printStackTrace();
        }
    }

    // Inner class representing a job
    private static class Job
    {
        String type;
        int jobId;

        // Constructor to initialize job type and ID
        Job(String type, int jobId) {
            this.type = type;
            this.jobId = jobId;
        }
    }
}
