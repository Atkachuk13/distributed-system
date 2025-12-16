package network;

// helper class for master
public class JobCompletion
{
    String jobId;
    String slaveId;
    long completionTime;

    public JobCompletion(String jobId, String slaveId)
    {
        this.jobId = jobId;
        this.slaveId = slaveId;
        this.completionTime = System.currentTimeMillis();
    }
}