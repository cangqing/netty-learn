package org.learn.nio;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClassicClient {
  //同步阻塞示例
  public static void main(String args[]) throws IOException, InterruptedException {
    Socket socket;
    OutputStream out;
    InputStream in;

    socket = SocketFactory.getDefault().createSocket();
    socket.setTcpNoDelay(true);
    socket.setKeepAlive(true);
    InetSocketAddress server = new InetSocketAddress("localhost", 8080);
    socket.connect(server, 3000);
    socket.getOutputStream().write("Hello Reactor Server from Client...".getBytes());
    socket.shutdownOutput();
    in = socket.getInputStream();
    byte[] content = new byte[1024];
    in.read(content);
    System.out.println(new String(content));
    Thread.sleep(100000);
  }
}


