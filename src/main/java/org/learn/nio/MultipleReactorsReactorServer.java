package org.learn.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultipleReactorsReactorServer implements Runnable {
  int port;
  int subReactorIndex = 0;
  Reactor mainReactor;
  Reactor subReactors[] = new Reactor[2];

  static ExecutorService executorService = Executors.newFixedThreadPool(3);

  MultipleReactorsReactorServer(int port) {
    this.port = port;
  }

  @Override
  public void run() {
    try {
      ServerSocketChannel serverSocket = ServerSocketChannel.open();
      serverSocket.socket().bind(new InetSocketAddress(port));
      serverSocket.configureBlocking(false);

      for (int index = 0; index < subReactors.length; index++) {
        Reactor reactor;
        reactor = new Reactor();
        subReactors[index] = reactor;
        new Thread(reactor).start();
      }

      mainReactor = new Reactor();
      SelectionKey sk = serverSocket.register(mainReactor.selector, SelectionKey.OP_ACCEPT);
      sk.attach(new Acceptor(serverSocket));
      new Thread(mainReactor).start();
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException();
    }
  }

  class Reactor implements Runnable {
    Selector selector;

    public Reactor() throws IOException {
      selector = Selector.open();
    }

    @Override
    public void run() {
      try {
        while (!Thread.interrupted()) {
          //只有当获取到就绪IO事件，才会释放 lock
          //不可以使用阻塞的select方式，否则accept后subReactor的selector在register的时候会一直阻塞
          if (selector.select(100) > 0) {
            Iterator it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
              SelectionKey sk = (SelectionKey) (it.next());
              dispatch(sk);
              it.remove();
            }
          }
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }

    void dispatch(SelectionKey k) {
      Runnable r = (Runnable) (k.attachment());
      if (r != null) {
        System.out.println("submit:" + this.toString() + ":" + r.toString());
        //r.run();
        k.cancel();
        executorService.submit(r);
      }
    }
  }

  class Acceptor implements Runnable {
    ServerSocketChannel serverSocket;

    public Acceptor(ServerSocketChannel serverSocket) {
      this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
      accept();
    }

    void accept() {
      try {
        SocketChannel socketChannel = serverSocket.accept();
        if (socketChannel != null) {
          new Reader(subReactors[subReactorIndex].selector, socketChannel);
          if (++subReactorIndex == subReactors.length) subReactorIndex = 0;
        }
        SelectionKey sk = serverSocket.register(mainReactor.selector, SelectionKey.OP_ACCEPT);
        sk.attach(new Acceptor(serverSocket));
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  class Reader implements Runnable {
    final SocketChannel socket;
    final SelectionKey sk;
    final Selector sel;

    ByteBuffer input = ByteBuffer.allocate(128);

    Reader(Selector sel, SocketChannel c) throws IOException {
      socket = c;
      this.sel = sel;
      c.configureBlocking(false);
      sk = socket.register(sel, 0);
      sk.interestOps(SelectionKey.OP_READ);
      sk.attach(this);
      sk.selector().wakeup();
    }

    @Override
    public void run() {
      try {
        read();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }

    void read() throws IOException {
      do {
        socket.read(input);
      } while (!inputIsComplete());
      System.out.println("Read:" + new String(input.array()));
      executorService.execute(new Processor(this.sel, socket, input));
    }

    boolean inputIsComplete() {
      /* ... */
      return true;
    }
  }

  class Processor implements Runnable {
    final SocketChannel socket;
    final Selector sel;
    ByteBuffer input;
    ByteBuffer output = ByteBuffer.allocate(128);
    SelectionKey sk;

    public Processor(Selector sel, SocketChannel c, ByteBuffer input) throws IOException {
      this.socket = c;
      this.sel = sel;
      this.input = input;
    }

    @Override
    public void run() {
      process();
    }

    void process() {
      try {
        input.flip();
        output.put(input);
        System.out.println("process output:" + new String(output.array()));
        sk = socket.register(sel, SelectionKey.OP_WRITE);
        sk.attach(new Sender(sk, socket, output));
        sk.selector().wakeup();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  class Sender implements Runnable {
    final SocketChannel socket;
    final SelectionKey sk;
    ByteBuffer output;

    public Sender(SelectionKey sk, SocketChannel c, ByteBuffer output) {
      this.sk = sk;
      this.socket = c;
      this.output = output;
    }

    @Override
    public void run() {
      send();
    }

    void send() {
      try {
        String response = "echo:" + new String(output.array());
        ByteBuffer byteBuffer = ByteBuffer.allocate(response.getBytes().length);
        byteBuffer.put(response.getBytes());
        byteBuffer.flip();
        do {
          int cnt=socket.write(byteBuffer);
          System.out.println(String.format("send:%s (%d bytes)",new String(byteBuffer.array()),cnt));
        } while (!outputIsComplete());
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    boolean outputIsComplete() {
      /* ... */
      return true;
    }
  }

  public static void main(String args[]) throws IOException {
    new Thread(new MultipleReactorsReactorServer(8080)).start();
  }
}
