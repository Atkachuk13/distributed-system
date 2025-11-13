package network;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {

    private final String host = "localhost";
    private final int port = 5000;
    private Socket masterSocket;

    // Shared memory for outgoing messages
    private BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();

    public static void main(String[] args) {
        new Client().start();
    }

    public void start() {
        try {
            System.out.println("Client starting...");
            masterSocket = new Socket(host, port);
            System.out.println("Connected to master!");

            startUserInputThread();
            startSenderThread();
            startListenerThread();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startUserInputThread() {
        Thread t = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("Enter job type (A/B): ");
                String type = scanner.nextLine();

                System.out.print("Enter job ID: ");
                String id = scanner.nextLine();

                String msg = "SUBMIT;" + type + ";" + id;
                outgoingMessages.add(msg);
                System.out.println("Client: queued job " + id + " (" + type + ")");
            }
        });
        t.start();
    }

    private void startSenderThread() {
        Thread t = new Thread(() -> {
            try {
                PrintWriter out = new PrintWriter(masterSocket.getOutputStream(), true);

                while (true) {
                    String msg = outgoingMessages.take();
                    out.println(msg);
                    System.out.println("Client → Master: " + msg);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.start();
    }

    private void startListenerThread() {
        Thread t = new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(masterSocket.getInputStream()));
                String line;

                while ((line = in.readLine()) != null) {
                    System.out.println("Master → Client: " + line);

                    if (line.startsWith("DONE;")) {
                        String jobId = line.split(";")[1];
                        System.out.println("Client: Job " + jobId + " is complete!");
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.start();
    }
}
