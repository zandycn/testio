package cn.zandy.testio.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * 在【主线程】中处理 acceptable, readable, writable 事件.
 *
 * 注意：
 * · 处理完的事件需要主动 remove - iter.remove()
 * · 要响应对端的连接关闭 connection.close()
 * · 处理完 writable 需要 cancel, 并且把连接关闭 connection.close()
 */
public class MainThread_IOMultiplexingServer {

    private static final boolean DEBUG = false;
    private static final int PORT = 9090;
    private static final int READ_MAX_BYTES = 1 << 3;

    private static Selector selector = null;

    public static void main(String[] args) {
        ServerSocketChannel listener = null; // 持有【监听 FD】
        try {
            listener = ServerSocketChannel.open();
            listener.configureBlocking(false);
            listener.bind(new InetSocketAddress(PORT));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            // select & poll:
            //         epoll: 执行系统调用 epoll_create, 生成 epfd
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("====================================================");
        System.out.println("server started, selector is : " + selector);
        System.out.println("====================================================");

        // select & poll: 把 listener 对应的 fd 放到 JVM 的一个容器中(fds)，作为 select & poll 的系统调用参数
        // epoll        : 延迟执行 epoll_ctl(epfd, add, listener_fd) 系统调用
        try {
            listener.register(selector, SelectionKey.OP_ACCEPT, null);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }

        while (true) {
            if (DEBUG) {
                System.out.println("publicKeys         : " + selector.keys());
                System.out.println("publicSelectedKeys : " + selector.selectedKeys());
            }

            /*
               selector.select() 一次系统调用，解决了 NIO 中用户态多次系统调用（造成多次用户态内核态切换）的问题

               select & poll: 去内核中【遍历】所有的连接，从中找出 "ready" 的连接，返回 fd+event
               epoll        :
               ① 执行系统调用 epoll_ctl, 把 listener 持有的 fd 放到内核维护的【EPOLL_红黑树】结构中（即这此刻执行上面的 register）
               ② 直接通过 epoll_wait 系统调用获得 "ready" 的 fd+event
             */
            int res = 0;
            try {
                // select() 阻塞！
                // select() 方法返回值代表添加到就绪操作集的键的数目，该数目可能为 0, 代表就绪操作集中的内容并没有添加新的键，保持内容不变
                res = selector.select();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (DEBUG) {
                System.out.println("loop, res = " + res);
            }

            if (res > 0) {
                Set<SelectionKey> keys = selector.selectedKeys();
                if (DEBUG) {
                    System.out.println("new 'ready' event(fds)          count : " + res);
                    System.out.println("new 'ready' event(fds)             is : " + keys);
                }

                Iterator<SelectionKey> iter = keys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    // 基于 select() 方法的特征，这里将已经通知过的 SelectionKey remove 掉
                    iter.remove();

                    if (key.isAcceptable()) {
                        handleAcceptable(key);
                    } else if (key.isReadable()) {
                        // 使用 handleReadable 时：
                        // 客户端 <-> 服务端，每次通信要创建一个连接，因为注册了 handleWritable，handleWritable 写完会关连接
                        // handleReadable(key);

                        // handleReadableAndWriteResult 特征是：服务端不主动关连接
                        handleReadableAndWriteResult(key);
                    } else if (key.isWritable()) {
                        handleWritable(key);
                    } else if (key.isConnectable()) {
                    }
                }
            }

            System.out.println("====================================================");
        }
    }

    private static void handleAcceptable(SelectionKey key) {
        System.out.println("Acceptable....................... key : " + key);
        //System.out.println("key.selector                          : " + key.selector());

        ServerSocketChannel listener = (ServerSocketChannel) key.channel();
        //System.out.println("isBlocking ? " + listener.isBlocking());

        SocketChannel client = null; // 持有【连接 FD】
        try {
            client = listener.accept(); // 非阻塞
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (client != null) {
            System.out.println("accepted connection : " + client);

            ByteBuffer readBuffer = ByteBuffer.allocate(READ_MAX_BYTES);
            try {
                client.configureBlocking(false);
                client.register(selector, SelectionKey.OP_READ, readBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleReadable(SelectionKey key) {
        System.out.println("Readable.......................   key : " + key);
        //System.out.println("key.selector                          : " + key.selector());

        SocketChannel client = (SocketChannel) key.channel();
        System.out.println("current  connection = " + client);
        //System.out.println("isBlocking ? " + connection.isBlocking());

        ByteBuffer readBuffer = (ByteBuffer) key.attachment();
        System.out.println("1: readBuffer = " + readBuffer);

        try {
            String input = "";
            // 这里使用 while(true) 用来解决用户发来的字节长度比 READ_MAX_BYTES 大时，要循环读取。
            // 如果不循环读取，客户端发到这个【 fd 缓冲区的数据】一次没读完，多路复用器会将没读完的内容通过 Readable 事件继续给你发过来。
            while (true) {
                readBuffer.clear();
                int num = client.read(readBuffer); // 非阻塞
                System.out.println("2: readBuffer = " + readBuffer);

                if (num > 0) {
                    readBuffer.flip();
                    System.out.println("3: readBuffer = " + readBuffer);

                    byte[] bs = new byte[readBuffer.remaining()];
                    readBuffer.get(bs, 0, bs.length);
                    String readResult = new String(bs, 0, bs.length);

                    System.out.println("readResult:[" + readResult + "]");
                    input += readResult;
                } else if (num == 0) {
                    System.out.println("read nothing!!");
                    break;
                } else {
                    System.out.println("客户端请求断开连接！");
                    client.close();
                    break;
                }
            }

            System.out.println("user input:[" + input + "]");
            if (!"".equals(input)) {
                ByteBuffer writeBuffer = ByteBuffer.allocate(input.length());
                writeBuffer.put(input.getBytes());
                client.register(selector, SelectionKey.OP_WRITE, writeBuffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleWritable(SelectionKey key) {
        System.out.println("Writable.......................   key : " + key);
        //System.out.println("key.selector                          : " + key.selector());

        SocketChannel client = (SocketChannel) key.channel();
        System.out.println("current  connection = " + client);
        //System.out.println("isBlocking ? " + connection.isBlocking());

        ByteBuffer buffer = (ByteBuffer) key.attachment();
        System.out.println("1: buffer = " + buffer);
        buffer.flip();
        System.out.println("2: buffer = " + buffer);

        try {
            client.write(buffer);

            // 多路复用器是基于 Send-Q 有没有空间，来决定一个写事件是否 "ready" 的！！！
            // 如果我们向多路复用器中注册了一个写事件，那么只要 Send-Q 有空间，多路复用器每次 select() 就会给我们返回这个写事件！
            // 所以我们处理完写事件后，就要将其 cancel——对于 epoll 就是系统调用 epoll_ctl(epfd, del, conn_fd) 从【EPOLL_红黑树】结构中将其删除
            // 注意：这里删除的是——>这条连接对应的 fd
            key.cancel();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (DEBUG) {
            try {
                // 这里一定会抛 CancelledKeyException
                client.register(selector, SelectionKey.OP_READ, buffer);
            } catch (ClosedChannelException e) {
                e.printStackTrace();
            } catch (CancelledKeyException e) {
                System.out.println("[ERROR] register cause exception: " + e);
            }
        }

        // cancel 之后必须要关闭连接：因为【EPOLL_红黑树】中没有这个连接的 fd 了，以后在这个连接上发生的任何事件，多路复用器都 get 不到了！
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleReadableAndWriteResult(SelectionKey key) {
        System.out.println("Readable...and...write.........   key : " + key);
        //System.out.println("key.selector                          : " + key.selector());

        SocketChannel client = (SocketChannel) key.channel();
        System.out.println("current  connection = " + client);
        //System.out.println("isBlocking ? " + connection.isBlocking());

        ByteBuffer readBuffer = (ByteBuffer) key.attachment();
        System.out.println("1: readBuffer = " + readBuffer);

        try {
            String input = "";
            while (true) {
                readBuffer.clear();
                int num = client.read(readBuffer); // 非阻塞
                System.out.println("2: readBuffer = " + readBuffer);

                if (num > 0) {
                    readBuffer.flip();
                    System.out.println("3: readBuffer = " + readBuffer);

                    byte[] bs = new byte[readBuffer.remaining()];
                    readBuffer.get(bs, 0, bs.length);
                    String readResult = new String(bs, 0, bs.length);

                    System.out.println("readResult:[" + readResult + "]");
                    input += readResult;
                } else if (num == 0) {
                    System.out.println("read nothing!!");
                    break;
                } else {
                    System.out.println("客户端请求断开连接！");
                    client.close();
                    break;
                }
            }

            System.out.println("user input:[" + input + "]");
            if (!"".equals(input)) {
                ByteBuffer writeBuffer = ByteBuffer.allocate(input.length());
                writeBuffer.put(input.getBytes());
                writeBuffer.flip();
                client.write(writeBuffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
