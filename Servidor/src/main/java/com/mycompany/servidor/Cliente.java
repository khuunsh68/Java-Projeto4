/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.servidor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 *
 * @author goncalo farias
 */
public class Cliente {

    private Socket serverSocket;
    private BufferedReader in;
    private PrintWriter out;

    public Cliente(String serverAddress, int serverPort) {
        try {
            serverSocket = new Socket(serverAddress, serverPort);
            System.out.println("Conectado ao servidor em " + serverAddress + ":" + serverPort);
            in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            out = new PrintWriter(serverSocket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printConnectedClients() {
        out.println("/clientes");
        try {
            String response = in.readLine();
            System.out.println("Clientes conectados:");
            System.out.println(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            // Solicitar o email do usuário ao entrar no chat
            System.out.println("Escreva o seu email: ");
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String email = consoleReader.readLine();
            out.println(email);

            // Verificar se é a primeira vez que o usuário entra
            String response = in.readLine();
            if (response.equals("new_user")) {
                // Solicitar o primeiro nome e o sobrenome
                System.out.println("Primeiro nome: ");
                String firstName = consoleReader.readLine();
                System.out.println("Sobrenome: ");
                String lastName = consoleReader.readLine();
                out.println(firstName);
                out.println(lastName);
            }

            // Cria uma nova thread para ler as mensagens do servidor
            Thread receiveThread = new Thread(new ReceiveHandler());
            receiveThread.start();

            // Lê as mensagens do console e envia ao servidor
            String inputLine;
            while ((inputLine = consoleReader.readLine()) != null) {
                if (inputLine.startsWith("/msg")) {
                    // Lógica para enviar mensagem direta
                    String[] parts = inputLine.split(" ", 3);
                    if (parts.length == 3) {
                        String recipientEmail = parts[1];
                        String message = parts[2];
                        out.println("/msg " + recipientEmail + " " + message);
                    }
                } else if (inputLine.equals("/clientes")) {
                    out.println("/clientes");
                } else {
                    out.println(inputLine);
                }
            }

            // Libera os recursos
            consoleReader.close();
            in.close();
            out.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ReceiveHandler implements Runnable {

        @Override
        public void run() {
            String serverMessage;
            try {
                while ((serverMessage = in.readLine()) != null) {
                    System.out.println(serverMessage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        String serverAddress = "localhost";
        int serverPort = 2346;
        Cliente client = new Cliente(serverAddress, serverPort);
        client.start();
    }
}
