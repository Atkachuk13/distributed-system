package network;

// helper class for master
public class JobCompletion
{
    int jobId;
    String slaveId;
    long completionTime;

    public JobCompletion(int jobId, String slaveId)
    {
        this.jobId = jobId;
        this.slaveId = slaveId;
        this.completionTime = System.currentTimeMillis();
    }
}