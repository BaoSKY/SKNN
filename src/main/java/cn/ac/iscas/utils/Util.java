package cn.ac.iscas.utils;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Random;

import cn.ac.iscas.sknn.SKNNV2.Point;

public class Util {

    public final static int DEFAULT_RADIX = 36;
    public static Charset charset = Charset.forName("ISO-8859-1");

    private static Random random = new SecureRandom();
    
    public static double log2(double N) {
        return Math.log(N) / Math.log(2);//Math.log的底为e
    }

    public static BigInteger[] decimalToBinaryV2(BigInteger x, int bitLength) {
        String xBinaryStr = x.toString(2); // LGB在字符串低位，需要反转
        BigInteger[] xBinary = new BigInteger[bitLength];
        for (int i = 0; i < bitLength; i++) {
            if (i < xBinaryStr.length()) {
                if (xBinaryStr.charAt(xBinaryStr.length() - 1 - i) == '0')
                    xBinary[i] = BigInteger.ZERO;
                else
                    xBinary[i] = BigInteger.ONE;
            } else {
                xBinary[i] = BigInteger.ZERO;
            }
        }

        return xBinary;
    }

    public static long readLong(BufferedReader reader) throws IOException {
        // return Long.parseLong(reader.readLine());
        String line = reader.readLine();
        return Long.parseLong(line);
    }

    public static void writeLong(long x, PrintWriter writer) throws IOException {
        writer.println(x);
        writer.flush();
    }

    public static int readInt(BufferedReader reader) throws IOException {
        return Integer.parseInt(reader.readLine());
    }

    public static void writeInt(int x, PrintWriter writer) throws IOException {
        writer.println(x);
        writer.flush();
    }

    public static int[] readIntegers(int m, BufferedReader reader) throws IOException {
        int[] x = new int[m];
        for (int i = 0; i < m; i++) {
            x[i] = Integer.parseInt(reader.readLine());
        }

        return x;
    }

    public static void writeIntegers(int[] x, PrintWriter writer) {
        for (int i = 0; i < x.length; i++) {
            writer.println(x);
        }
        writer.flush();
    }

    public static BigInteger readBigInteger(BufferedReader reader) throws IOException {
        String s = reader.readLine();
        return new BigInteger(s, DEFAULT_RADIX);
    }

    public static void writeBigInteger(BigInteger x, PrintWriter writer) throws IOException {
        writer.println(x.toString(DEFAULT_RADIX));
        writer.flush();
    }

    public static BigInteger[] readBigIntegers(int m, BufferedReader reader) throws IOException {
        BigInteger[] x = new BigInteger[m];
        for (int i = 0; i < m; i++) {
            x[i] = new BigInteger(reader.readLine(), DEFAULT_RADIX);
        }

        return x;
    }

    public static void writeBigIntegers(BigInteger[] x, PrintWriter writer) {
        for (int i = 0; i < x.length; i++) {
            // if (i % 1000 == 0)
            //     System.out.println(i + " ");
            writer.println(x[i].toString(DEFAULT_RADIX));
        }
        writer.flush();
    }

    public static BigInteger[] exchangeBigIntegers(BigInteger[] x, BufferedReader reader, PrintWriter writer)
            throws IOException {

        int num = x.length;
        BigInteger[] y;

        // 开线程需要消耗额外的时间（大概几十ms？）
        // 对于少量数据，先传后读的速度会快很多。
        // 但若阈值设置不恰当，可能会出现需要传太多，传不完一直waiting的情况。
        // 之前测试时发现num=256000左右出现waiting，但是可能跟单个数据大小也有关。
        // 由于实验中数据量都较大，所以就统一使用多线程。
        // if (num < 100000) {
        //     writeBigIntegers(x, writer);
        //     y = readBigIntegers(num, reader);
        // } else {
        y = new BigInteger[num];

        Runnable writerRunnable = new Runnable() {
            @Override
            public void run() {
                writeBigIntegers(x, writer);
            }
        };

        Runnable readerRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < num; i++) {
                        y[i] = new BigInteger(reader.readLine(), DEFAULT_RADIX);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        Thread writerThread = new Thread(writerRunnable);
        Thread readerThread = new Thread(readerRunnable);
        RunningTimeCounter.updatePreviousTime(RunningTimeCounter.COMMUNICATION_TIME);
        writerThread.start();
        readerThread.start();
        try {
            writerThread.join();
            readerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // }
        RunningTimeCounter.accumulate(RunningTimeCounter.COMMUNICATION_TIME);

        return y;
    }

    public static BigInteger[][] readBigIntegers(int m, int n, BufferedReader reader) throws IOException {
        BigInteger[][] x = new BigInteger[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                x[i][j] = new BigInteger(reader.readLine(), DEFAULT_RADIX);
            }
        }

        return x;
    }

    public static void writeBigIntegers(BigInteger[][] x, PrintWriter writer) {
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[0].length; j++) {
                writer.println(x[i][j].toString(DEFAULT_RADIX));
            }
        }
        writer.flush();
    }

    public static BigInteger[][][] readBigIntegers(int m, int n, int l, BufferedReader reader) throws IOException {
        BigInteger[][][] x = new BigInteger[m][n][l];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < l; k++) {
                    x[i][j][k] = new BigInteger(reader.readLine(), DEFAULT_RADIX);
                }
            }
        }

        return x;
    }

    public static void writeBigIntegers(BigInteger[][][] x, PrintWriter writer) {
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[0].length; j++) {
                for (int k = 0; k < x[0][0].length; k++) {
                    writer.println(x[i][j][k].toString(DEFAULT_RADIX));
                }
            }
        }
        writer.flush();
    }

    public static Point[] readPoints(int num, int m, BufferedReader reader) throws IOException {
        Point[] points = new Point[num];
        for (int i = 0; i < num; i++) {
            points[i] = new Point(m);

            points[i].id = new BigInteger(reader.readLine(), DEFAULT_RADIX);

            points[i].data = new BigInteger[m];
            for (int j = 0; j < m; j++) {
                points[i].data[j] = new BigInteger(reader.readLine(), DEFAULT_RADIX);
            }
        }

        return points;
    }

    public static void writePoints(Point[] points, PrintWriter writer) {
        int num = points.length;
        int m = points[0].data.length;

        for (int i = 0; i < num; i++) {
            writer.println(points[i].id.toString(DEFAULT_RADIX));

            for (int j = 0; j < m; j++) {
                writer.println(points[i].data[j].toString(DEFAULT_RADIX));
            }
        }

        writer.flush();
    }

    public static BigInteger getRandomBigInteger(BigInteger bound) {

        BigInteger r;
        do {
            r = new BigInteger(bound.bitLength(), random);
        } while (r.compareTo(bound) >= 0);

        return r;
    }

    public static void main(String[] args) {
     
    }
}
