package org.learn.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class ReactorClient extends Thread {
  final Selector selector;
  final SocketChannel clientSocket;

  public ReactorClient() throws IOException {
    // 创建SocketChannel对象
    clientSocket = SocketChannel.open();
    // 设置为非阻塞模式
    clientSocket.configureBlocking(false);
    // 创建选择器
    selector = Selector.open();
    // 将SocketChannel对象注册到选择器上，并设置为选择器对连接事件感兴趣
    clientSocket.register(selector, SelectionKey.OP_CONNECT);
    // 与服务器连接
    clientSocket.connect(new InetSocketAddress("localhost", 8080));
  }

  @Override
  public void run() {
    try {
      // 当选择器检测到有IO事件等待处理时
      while (!Thread.interrupted()) {
        selector.select(100);
        // 获取选择器待处理的IO事件集合，并遍历
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
          SelectionKey key = it.next();
          it.remove();
          // 如果是可连接的(这里和服务端不同，服务端为isAcceptable，代表可接受连接的)
          if (key.isConnectable()) {
            SocketChannel clientSocket = (SocketChannel) key.channel();
            // 这里需要检测是否完成连接
            if (clientSocket.finishConnect()) {
              // 连接成功后需要将选择器设置为对该通道的读事件感兴趣（不然同样会无限循环）
              //clientSocket.register(selector,SelectionKey.OP_WRITE);
              key.interestOps(key.interestOps() | SelectionKey.OP_READ | SelectionKey.OP_WRITE); // 监听读就绪和写就绪
            } else {
              System.out.println("连接失败");
              System.exit(1);
            }
          } else if (key.isReadable()) {
            read(key);
          } else if (key.isWritable()) {
            write(key);
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void read(SelectionKey key) throws IOException {
    try {
      // 获取对应的Socket通道
      SocketChannel socket = (SocketChannel) key.channel();
      // 创建一个字节缓冲区
      ByteBuffer buffer = ByteBuffer.allocate(1024);
      if (socket.read(buffer) > 0) {
        System.out.println(new String(buffer.array()));
        key.cancel();
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(0);
    }
  }

  public void write(SelectionKey key) throws IOException {
    SocketChannel socket = (SocketChannel) key.channel();
    ByteBuffer buffer = ByteBuffer.allocate(256);
    buffer.put("Hello Reactor Server from Client...".getBytes());
    buffer.flip();
    socket.write(buffer);
    //socket.register(selector,SelectionKey.OP_READ);
    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    key.selector().wakeup();
    System.out.println(new String(buffer.array()));
  }

  public static void main(String args[]) throws IOException {
    new ReactorClient().run();
  }
}
