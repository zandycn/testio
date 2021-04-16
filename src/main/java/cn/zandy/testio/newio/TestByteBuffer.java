package cn.zandy.testio.newio;

import java.nio.ByteBuffer;

public class TestByteBuffer {

    public static void main(String[] args) {
        // 开辟JVM堆内内存
        ByteBuffer heapByteBuffer = ByteBuffer.allocate(4096);
        // 开辟JVM堆外，进程堆(C堆)内内存
        ByteBuffer directByteBuffer = ByteBuffer.allocateDirect(4096);

        System.out.println(heapByteBuffer);
        System.out.println(directByteBuffer);

        test(directByteBuffer);
    }

    private static void test(ByteBuffer buffer) {
        String str = "abcdefghijklnmopqrstuvwxyz1234567890";

        buffer.put(str.getBytes());
        System.out.println("\n------------ after put ------------");
        print(buffer);

        buffer.flip(); // 预备读
        System.out.println("\n------------ after flip ------------");
        print(buffer);

        System.out.println("\n------------ get ------------");
        byte b = buffer.get();
        System.out.println(b);
        System.out.println((char) b);
        System.out.println("\n------------ after get ------------");
        print(buffer);

        buffer.compact(); // 预备写
        System.out.println("\n------------ after compact ------------");
        print(buffer);

        buffer.clear();
        System.out.println("\n------------ after clear ------------");
        print(buffer);
    }

    private static void print(ByteBuffer buffer) {
        System.out.println(buffer);
        System.out.println("hasRemaining: " + buffer.hasRemaining());
        System.out.println("remaining: " + buffer.remaining());
    }
}
