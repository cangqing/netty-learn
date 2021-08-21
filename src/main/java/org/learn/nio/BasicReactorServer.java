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

public class BasicReactorServer implements Runnable {
  final Selector selector;
  final ServerSocketChannel serverSocket;

  BasicReactorServer(int port) throws IOException {
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
    if (r != null) r.run();
  }

  final class Acceptor implements Runnable {
    @Override
    public void run() {
      try {
        SocketChannel socketChannel = serverSocket.accept();
        if (socketChannel != null) new Handler(selector, socketChannel);
      } catch (IOException ex) {
        /* ... */
      }
    }
  }

  final class Handler implements Runnable {
    final SocketChannel socket;
    final SelectionKey sk;
    ByteBuffer input = ByteBuffer.allocate(1024);
    ByteBuffer output = ByteBuffer.allocate(1024);

    static final int READING = 0, SENDING = 1;
    int state = READING;

    Handler(Selector sel, SocketChannel c) throws IOException {
      socket = c;
      c.configureBlocking(false);
      // Optionally try first read now
      sk = socket.register(sel, 0);
      sk.attach(this);
      sk.interestOps(SelectionKey.OP_READ);
      sel.wakeup(); // 注册OP_READ兴趣之后，让select()方法返回，接受要读取的数据
    }

    @Override
    public void run() {
      try {
        if (state == READING) read();
        else if (state == SENDING) send();
      } catch (IOException ex) {
        /* ... */
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

    void process(ByteBuffer input) {
      System.out.println(new String(input.array()));
    }

    void read() throws IOException {
      socket.read(input);
      if (inputIsComplete()) {
        process(input);
        state = SENDING;
        // Normally also do first write now
        sk.interestOps(SelectionKey.OP_WRITE); // 将状态变为SENDING之后，接下来就是往外写数据，对写感兴趣。
      }
    }

    void send() throws IOException {
      socket.write(output);
      if (outputIsComplete()) sk.cancel();
    }
  }

  public static void main(String args[]) throws IOException {
    new Thread(new BasicReactorServer(8080)).start();
  }
}
