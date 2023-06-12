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
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.*;
import java.util.regex.Pattern;

/**
 *
 * @author goncalo farias
 */
class Server {

    private ServerSocket serverSocket;
    private Map<String, ClientHandler> connectedClients;
    private Map<String, List<String>> conversas; // Vai guardar as conversas

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            connectedClients = new HashMap<>();
            conversas = new HashMap<>();
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
        String keyConversa = obterKeyConversas(senderEmail, recipientEmail);
        List<String> conversa = conversas.computeIfAbsent(keyConversa, k -> new ArrayList<>());
        conversa.add(senderEmail + ": " + message);

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

    private String obterKeyConversas(String user1, String user2) {
        // Ordena os emails para garantir a consistência da chave
        String firstUser = (user1.compareTo(user2) < 0) ? user1 : user2;
        String secondUser = (user1.compareTo(user2) < 0) ? user2 : user1;
        return firstUser + "-" + secondUser;
    }

    public synchronized List<String> obterHistoricoConversas(String user1, String user2) {
        // Obtém a lista de mensagens daquela conversa
        String keyConversa = obterKeyConversas(user1, user2);
        List<String> conversa = conversas.get(keyConversa);
        if (conversa == null) {
            conversa = new ArrayList<>();
        }

        // Retorna as últimas 5 mensagens para as outras mensagens
        int startIndex = Math.max(conversa.size() - 5, 0);
        return new ArrayList<>(conversa.subList(startIndex, conversa.size()));

    }

    // Obtém o historico de LP
    public void obterHistoricoConversasLP(String senderEmail, String message) {
        List<String> conversaLP = conversas.computeIfAbsent("LP", k -> new ArrayList<>());
        conversaLP.add(senderEmail + ": " + message);
        int maxMessagesLP = 10;
        if (conversaLP.size() > maxMessagesLP) {
            conversaLP.subList(0, conversaLP.size() - maxMessagesLP).clear();
        }
    }

    // Getter para a lista com as mensagens no historico de LP
    public List<String> getterHistoricoLP() {
        List<String> conversaLP = conversas.get("LP");
        if (conversaLP != null) {
            int startIndex = Math.max(conversaLP.size() - 10, 0);
            return new ArrayList<>(conversaLP.subList(startIndex, conversaLP.size()));
        }
        return new ArrayList<>();
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
            if (userEmail != null && isValidEmail(userEmail)) {
                // Verificar se é um novo usuário
                if (!server.getConnectedClients().containsKey(userEmail)) {
                    out.println("new_user");
                    String firstName = in.readLine();
                    String lastName = in.readLine();
                    System.out.println("Novo user registado: " + firstName + " " + lastName + " (" + userEmail + ")");
                    sendMenu(); // Mostra o menu de comandos após registo
                }
                server.addUser(userEmail, this);
                System.out.println("User conectado: " + userEmail);
            } else {
                System.out.println("Email invalido: " + userEmail);
                // Encerrar a conexão com o cliente inválido, se necessário
                in.close();
                out.close();
                clientSocket.close();
                return;
            }

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                if (clientMessage.startsWith("/msg")) {
                    String[] parts = clientMessage.split(" ", 3);
                    if (parts.length == 3) {
                        String recipientEmail = parts[1];
                        String message = parts[2];

                        if (recipientEmail.equals("LP")) {
                            // Armazena a mensagem no historico de LP
                            server.obterHistoricoConversasLP(userEmail, message);
                            // Envia mensagem a todos os users disponiveis
                            for (ClientHandler cliente : server.getConnectedClients().values()) {
                                cliente.sendMessage(userEmail, message);
                            }
                        } else {
                            server.sendMessage(userEmail, recipientEmail, message);
                        }
                    }
                } else if (clientMessage.equals("/clientes")) {
                    List<String> connectedClients = server.getConnectedClientsList();
                    String clientList = String.join("\n", connectedClients);
                    out.println(clientList);
                } else if (clientMessage.startsWith("/historico")) {
                    String[] parts = clientMessage.split(" ", 2);
                    if (parts.length == 2) {
                        String recipientEmail = parts[1];
                        // Caso o historico se destine ao user LP
                        if (recipientEmail.equals("LP")) {
                            List<String> historico = server.getterHistoricoLP();
                            for (String message : historico) {
                                out.println(message);
                            }
                            // Caso o historico se destine a um user normal
                        } else {
                            List<String> historico = server.obterHistoricoConversas(userEmail, recipientEmail);
                            for (String message : historico) {
                                out.println(message);
                            }
                        }
                    }
                } else if (clientMessage.equals("/sair")) {
                    // Desconecta o user de forma segura
                    break;
                } else {
                    System.out.println("Mensagem do user " + userEmail + ": " + clientMessage);
                }
            }
        } catch (SocketException e) {
            // Trecho que lida com a desconexão do cliente de forma inesperada
            server.removeUser(userEmail);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
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

    private boolean isValidEmail(String email) {
        // Verifica o formato do email usando uma expressão regular
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        Pattern pattern = Pattern.compile(emailRegex);
        return pattern.matcher(email).matches();
    }

    private void sendMenu() {
        StringBuilder menu = new StringBuilder();
        menu.append("Comandos disponíveis:\n");
        menu.append("/msg <destinatário> <mensagem> - Enviar mensagem para um usuário\n");
        menu.append("/clientes - Listar usuários conectados\n");
        menu.append("/historico <destinatário> - Listar historico com usuário\n");
        menu.append("/sair - Desconectar do servidor\n");
        out.println(menu.toString());
    }
}

public class Servidor {

    public static void main(String[] args) {
        int port = 2346;
        Server server = new Server(port);
        server.start();
    }
}
