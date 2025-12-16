package network;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SlaveInfo
{
    public String slaveId;
    public char slaveType;
    public Socket socketToSlave;
    public int currentLoad;
    public PrintWriter out;
    public BufferedReader in;
}
