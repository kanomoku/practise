package io_nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Scanner;

public class NIOServer implements Runnable {

    // 多路复用器(选择器）， 用于注册通道的
    private Selector selector;
    // 定义了两个缓存分别用于读和写 初始化空间大小单位为1个字节。
    private ByteBuffer readBuffer = ByteBuffer.allocate(1024);
    private ByteBuffer writeBuffer = ByteBuffer.allocate(1024);

    public static void main(String[] args) {
        new Thread(new NIOServer(9999)).start();
    }

    public NIOServer(int port) {
        init(port);
    }

    private void init(int port) {
        try {
            System.out.println("server starting at port " + port + " ...");

            // 开启多路复用器
            this.selector = Selector.open();

            // 开启服务通道
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            // false为非阻塞，true为阻塞模式;
            serverChannel.configureBlocking(false);
            // 绑定端口
            serverChannel.bind(new InetSocketAddress(port));
            // 注册，并标记当前服务通道状态
            serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
            /*
             * register(Selector, int) int - 状态编码
             *  OP_ACCEPT ： 连接成功的标记位。
             *  OP_READ ：   可以读取数据的标记
             *  OP_WRITE ：  可以写入数据的标记
             *  OP_CONNECT ：连接建立后的标记
             */
            System.out.println("server started.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        while (true) {
            try {
                // 函数功能：选择一些I/O操作已经准备好的channel;每个channel对应着一个key;
                // 这个方法是一个阻塞的选择操作,当至少有一个通道被选择时才返回;当这个方法被执行时,当前线程是允许被中断的;
                // 通道是否选中，由注册到多路复用器中的通道标记决定
                this.selector.select();

                // 返回已选中的 通道标记 的集合, 集合中保存的是 通道的标记;相当于是通道的ID。
                Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    // 将本次要处理的通道从集合中删除，因为处理的通道会被赋予新的通道标记状态；
                    keys.remove();

                    // 通道是否有效
                    if (key.isValid()) {
                        // 阻塞状态
                        try {
                            if (key.isAcceptable()) {
                                accept(key);
                            }
                        } catch (CancelledKeyException cke) {
                            // 断开连接，出现异常；
                            key.cancel();
                        }
                        // 可读状态
                        try {
                            if (key.isReadable()) {
                                read(key);
                            }
                        } catch (CancelledKeyException cke) {
                            key.cancel();
                        }
                        // 可写状态
                        try {
                            if (key.isWritable()) {
                                write(key);
                            }
                        } catch (CancelledKeyException cke) {
                            key.cancel();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private void accept(SelectionKey key) {
        try {
            // 此通道为init方法中注册到Selector上的ServerSocketChannel
            ServerSocketChannel serverChannel = (ServerSocketChannel)key.channel();
            // 阻塞方法，当客户端发起请求后返回。 此通道和客户端一一对应。
            SocketChannel channel = serverChannel.accept();
            // false为非阻塞，true为阻塞模式;
            channel.configureBlocking(false);
            // 设置对应客户端的通道标记状态，此通道为读取数据使用的。
            channel.register(this.selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void read(SelectionKey key) {
        try {
            // 清空读缓存,一旦有残留的可能会导致数据不一致;
            this.readBuffer.clear();
            // 获取通道: 把selector 选中的 channel 给找出来;
            SocketChannel channel = (SocketChannel)key.channel();
            // 将通道中的数据读取到缓存中，通道中的数据就是客户端发送给服务器的数据。
            int readLength = channel.read(readBuffer);
            // 检查客户端是否写入数据。
            if (readLength == -1) {
                // 关闭通道
                key.channel().close();
                // 关闭连接
                key.cancel();
                return;
            }

            /*
             * flip， NIO中最复杂的操作就是Buffer的控制。
             * Buffer中有一个游标。游标信息在操作后不会归零，如果直接访问Buffer的话，数据有不一致的可能。
             * flip是重置游标的方法。NIO编程中，flip方法是常用方法。
             */
            this.readBuffer.flip();
            // 字节数组来保存具体数据的； Buffer.remaining()是获取Buffer中有效数据长度的方法
            byte[] datas = new byte[readBuffer.remaining()];
            // 是将Buffer中的有效数据保存到字节数组中
            readBuffer.get(datas);
            System.out.println(
                "from " + channel.getRemoteAddress() + " client : " + new String(datas, StandardCharsets.UTF_8));

            // 注册通道， 标记下一步该到了写操作;
            channel.register(this.selector, SelectionKey.OP_WRITE);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                key.channel().close();
                key.cancel();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void write(SelectionKey key) {
        this.writeBuffer.clear();
        SocketChannel channel = (SocketChannel)key.channel();

        Scanner reader = new Scanner(System.in);
        try {
            System.out.print("put message for send to client > ");
            String line = reader.nextLine();

            // 将控制台输入的字符串写入Buffer中,写入的数据是一个字节数组。
            writeBuffer.put(line.getBytes(StandardCharsets.UTF_8));
            writeBuffer.flip();
            channel.write(writeBuffer);

            channel.register(this.selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
