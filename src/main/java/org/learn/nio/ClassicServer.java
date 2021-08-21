package org.learn.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ClassicServer implements Runnable {
  int port;

  public ClassicServer(int port){
    this.port=port;
  }

  public void run() {
    try {
      ServerSocket ss = new ServerSocket(port);
      while (!Thread.interrupted())
        new Thread(new Handler(ss.accept())).start();
      // or, single-threaded, or a thread pool
    } catch (IOException ex) {
      /* ... */
    }
  }

  static class Handler implements Runnable {
    final Socket socket;

    Handler(Socket s) {
      socket = s;
    }

    @Override
    public void run() {
      try {
        byte[] input = new byte[1024];
        socket.getInputStream().read(input);
        byte[] output = process(input);
        socket.getOutputStream().write(output);
      } catch (IOException ex) {
        /* ... */
      }
    }

    private byte[] process(byte[] input) {
      System.out.println(new String(input));
      return input;
    }
  }

  public static void main(String args[]){
    new Thread(new ClassicServer(8080)).start();
  }
}
