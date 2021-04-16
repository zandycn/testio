package cn.zandy.testio.newio;

import java.nio.ByteBuffer;

public class TestByteBuffer2Array {

    public static void main(String[] args) {
        System.out.println("------------ wrap ------------");
        byte[] bytes__0 = new byte[10];
        ByteBuffer buf__0 = ByteBuffer.wrap(bytes__0);
        System.out.println("buf__0     : " + buf__0);
        printBBItems(buf__0);

        System.out.println("------------ wrap1 ------------");
        byte[] bytes__1 = new byte[] {65, 66, 67, 68, 69, 70, 71};
        ByteBuffer buf__1 = ByteBuffer.wrap(bytes__1);
        System.out.println("buf__1     : " + buf__1);
        printBBItems(buf__1);

        System.out.println("------------ wrap 之后 flip, limit 变成 0 ------------");
        byte[] bytes__2 = new byte[] {65, 66, 67, 68, 69, 70, 71};
        ByteBuffer buf__2 = ByteBuffer.wrap(bytes__2);
        System.out.println("buf__2     : " + buf__2);
        buf__2.flip();
        System.out.println("after flip : " + buf__2);
        printBBItems(buf__2);

        System.out.println("\n");
        System.out.println("------------ m1    ------------");
        m1();
    }

    private static void m1() {
        System.out.println("------ test : put ------");
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.put("abc".getBytes());
        buf.flip();
        printBBItems(buf);

        System.out.println("------ test : mark, reset 的用法 ------");
        buf.limit(buf.capacity());
        byte[] bytes = new byte[buf.remaining()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 65);
        }
        printBAItems(bytes);

        buf.mark();
        buf.put(bytes);
        System.out.println("buf:" + buf);
        buf.reset(); // pos -> mark
        System.out.println("buf:" + buf);
        printBBItems(buf);

        System.out.println("------ test : clear 只调整指针，不删除内容 ------");
        buf.clear();
        bytes = new byte[buf.capacity()];
        printBBItems(buf);
        printBAItems(bytes);

        System.out.println("");

        buf.flip();
        buf.get(bytes, 0, bytes.length);
        printBBItems(buf);
        printBAItems(bytes);
    }

    private static void printBBItems(ByteBuffer buf) {
        System.out.print("ByteBuffer : [");

        while (buf.hasRemaining()) {
            System.out.print(buf.get());
            if (buf.remaining() > 0) {

                System.out.print(",");
            }
        }
        System.out.print("]\n");
    }

    private static void printBAItems(byte[] bytes) {
        int len = bytes.length;

        System.out.print("Array      : [");
        for (int i = 0; i < len; i++) {
            System.out.print((char) bytes[i]);
            if (i != len - 1) {
                System.out.print(",");
            }
        }
        System.out.print("]\n");
    }
}
