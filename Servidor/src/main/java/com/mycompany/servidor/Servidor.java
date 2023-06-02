/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package com.mycompany.servidor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.*;

/**
 *
 * @author goncalo farias
 */
class Server {

    private ServerSocket serverSocket;
    private Map<String, ClientHandler> connectedClients;

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            connectedClients = new HashMap<>();
            System.out.println("Servidor comecou na porta " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Crie uma nova instância de ClientHandler em uma nova thread
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                Thread clientThread = new Thread(clientHandler);
                clientThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void addUser(String email, ClientHandler clientHandler) {
        connectedClients.put(email, clientHandler);
    }

    public synchronized void removeUser(String email) {
        connectedClients.remove(email);
    }

    public synchronized void sendMessage(String senderEmail, String recipientEmail, String message) {
        ClientHandler recipient = connectedClients.get(recipientEmail);
        if (recipient != null) {
            recipient.sendMessage(senderEmail, message);
        }
    }

    public synchronized Map<String, ClientHandler> getConnectedClients() {
        return connectedClients;
    }

    public synchronized List<String> getConnectedClientsList() {
        List<String> clientList = new ArrayList<>();
        for (String email : connectedClients.keySet()) {
            clientList.add(email.trim());
        }
        return clientList;
    }
}

class ClientHandler implements Runnable {

    private Socket clientSocket;
    private Server server;
    private BufferedReader in;
    private PrintWriter out;
    private String userEmail;

    public ClientHandler(Socket clientSocket, Server server) {
        this.clientSocket = clientSocket;
        this.server = server;
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Solicitar o email ao cliente
            userEmail = in.readLine();
            if (userEmail != null) {
                // Verificar se é um novo usuário
                if (!server.getConnectedClients().containsKey(userEmail)) {
                    out.println("new_user");
                    String firstName = in.readLine();
                    String lastName = in.readLine();
                    System.out.println("Novo user registado: " + firstName + " " + lastName + " (" + userEmail + ")");
                }
                server.addUser(userEmail, this);
                System.out.println("Usuário conectado: " + userEmail);
            }

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                if (clientMessage.startsWith("/msg")) {
                    String[] parts = clientMessage.split(" ", 3);
                    if (parts.length == 3) {
                        String recipientEmail = parts[1];
                        String message = parts[2];
                        
                        if(recipientEmail.equals("LP")) {
                            // Envia mensagem a todos os users disponiveis
                            for(ClientHandler cliente : server.getConnectedClients().values())
                                cliente.sendMessage(userEmail, message);
                        } else {
                            server.sendMessage(userEmail, recipientEmail, message);
                        }
                    }
                } else if (clientMessage.equals("/clientes")) {
                    List<String> connectedClients = server.getConnectedClientsList();
                    String clientList = String.join("\n", connectedClients);
                    out.println(clientList);
                } else {
                    System.out.println("Mensagem do user " + userEmail + ": " + clientMessage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.removeUser(userEmail);
            System.out.println("User desconectado: " + userEmail);
            try {
                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String senderEmail, String message) {
        out.println(senderEmail + ": " + message);
    }
}

public class Servidor {

    public static void main(String[] args) {
        int port = 2346;
        Server server = new Server(port);
        server.start();
    }
}
