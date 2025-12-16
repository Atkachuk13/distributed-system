package network;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientInfo
{
    public String clientId;
    public Socket socketToClient;
    public PrintWriter out;
    public BufferedReader in;
}
