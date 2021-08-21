package org.learn.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultipleThreadReactorServer implements Runnable {
  final Selector selector;
  final ServerSocketChannel serverSocket;

  MultipleThreadReactorServer(int port) throws IOException {
    selector = Selector.open();
    serverSocket = ServerSocketChannel.open();
    serverSocket.socket().bind(new InetSocketAddress(port));
    serverSocket.configureBlocking(false);
    SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
    sk.attach(new Acceptor());
  }

  public void run() { // normally in a newThread
    try {
      while (!Thread.interrupted()) {
        selector.select();
        Set selected = selector.selectedKeys();
        Iterator it = selected.iterator();
        while (it.hasNext()) dispatch((SelectionKey) (it.next()));
        selected.clear();
      }
    } catch (IOException ex) {
      /* ... */
    }
  }

  void dispatch(SelectionKey k) {
    Runnable r = (Runnable) (k.attachment());
    if (r != null) {
      System.out.println("run:" + this.toString() + ":" + r.toString());
      r.run();
    }
  }

  class Acceptor implements Runnable {
    public void run() {
      try {
        SocketChannel socketChannel = serverSocket.accept();
        if (socketChannel != null) new Handler(selector, socketChannel);
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  static class Handler implements Runnable {
    static ExecutorService executorService = Executors.newFixedThreadPool(5);
    static final int READING = 0, SENDING = 1, PROCESSING = 2;
    int state = READING;

    final SocketChannel socket;
    final SelectionKey sk;
    ByteBuffer input = ByteBuffer.allocate(128);

    Handler(Selector sel, SocketChannel c) throws IOException {
      socket = c;
      c.configureBlocking(false);
      sk = socket.register(sel, 0);
      sk.attach(this);
      sk.interestOps(SelectionKey.OP_READ);
      sel.wakeup();
    }

    @Override
    public void run() {
      try {
        if (state == READING) read();
        else if (state == SENDING) send();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }

    synchronized void read() throws IOException {
      socket.read(input);
      System.out.println("Read:" + new String(input.array()));
      if (inputIsComplete()) {
        state = PROCESSING;
        executorService.execute(new Processor());
      }
    }

    synchronized void send() throws IOException {
      input.flip();
      String response = "echo:" + new String(input.array());
      ByteBuffer byteBuffer = ByteBuffer.allocate(response.getBytes().length);
      byteBuffer.put(response.getBytes());
      byteBuffer.flip();
      int cnt=socket.write(byteBuffer);
      System.out.println(String.format("Send:%s (%d bytes)",new String(byteBuffer.array()),cnt));
      if (outputIsComplete()) sk.cancel();
    }

    final class Processor implements Runnable {

      synchronized void process() {
        System.out.println(String.format("Process:%s",new String(input.array())));
        state = SENDING; // or rebind attachment
        sk.interestOps(SelectionKey.OP_WRITE);
        sk.selector().wakeup();
      }

      @Override
      public void run() {
        process();
      }
    }

    boolean inputIsComplete() {
      /* ... */
      return true;
    }

    boolean outputIsComplete() {
      /* ... */
      return true;
    }
  }

  public static void main(String args[]) throws IOException {
    new Thread(new MultipleThreadReactorServer(8080)).start();
  }
}
