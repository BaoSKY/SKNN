package cn.ac.iscas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.alibaba.fastjson2.JSON;

import static cn.ac.iscas.secretsharing.AdditiveSecretSharing.*;

import cn.ac.iscas.secretsharing.AdditiveSecretSharing;
import cn.ac.iscas.secretsharing.AdditiveSecretSharing.MultiplicationTriple;
import cn.ac.iscas.secretsharing.AdditiveSecretSharing.RandomNumberTuple;
import cn.ac.iscas.sknn.SKNNV2;
import cn.ac.iscas.sknn.SKNNV2.AG;
import cn.ac.iscas.sknn.SKNNV2.Point;
import cn.ac.iscas.sknn.SKNNV2.VG;
import cn.ac.iscas.utils.RunningTimeCounter;
import cn.ac.iscas.utils.Util;
import static cn.ac.iscas.utils.DataProcessor.*;

public class TestSKNNV2 {

    /**
     * test type:   0-Linear SKNN   1-Voronoi SKNN for debug  2-Voronoi SKNN for test
     * 
     * testType = 0 or 1
     * args: role ipC1 portC1 ipC2 portC2 randomSeed testType testNumber dataNumber dataLength dimension k
     * 
     * testType = 2
     * args: role ipC1 portC1 ipC2 portC2 randomSeed testType testNumber dataNumber dataLength dimension k agNum agSize vgNum vgSize
     * 
     * @param args
     * @throws IOException
     */
    public static void user(String[] args) throws IOException {
        /* 提取测试数据 */
        int index = 1;
        String ipC1 = args[index++];
        int portC1 = Integer.parseInt(args[index++]);
        String ipC2 = args[index++];
        int portC2 = Integer.parseInt(args[index++]);

        String randomSeed = args[index++];
        int testType = Integer.parseInt(args[index++]);
        int testNumber = Integer.parseInt(args[index++]);
        int dataNumber = Integer.parseInt(args[index++]);
        int dataLength = Integer.parseInt(args[index++]);
        int m = Integer.parseInt(args[index++]); // dimension
        int k = Integer.parseInt(args[index++]);

        // 模数mod的长度为l，计算方法如下：
        // 点的范围是dataLength，则单个维度计算欧氏距离的范围是2 * dataLength
        // 计算欧式距离后，还要对各维度进行求和，此时范围是：2 * dataLength + log_2(m)
        // 则需要的判断范围是2 * dataLength + log_2(m) + 2
        int l = dataLength * 2 + (int) Util.log2(m) + 2;

        Random random = randomSeed.equals("null") ? new Random() : new Random(Long.parseLong(randomSeed));
        BigInteger mod = BigInteger.probablePrime(l, random);
        MultiplicationTriple[] triples = generateMultiplicationTriples(mod);
        RandomNumberTuple[] tuples = generateRandomNumberTuples(l, mod);
        System.out.println("mod = " + mod);

        BigInteger[][] dataset = generateDataset(m, dataNumber, dataLength, random);

        Point[][] pointsSecrets = null;
        AG[] ags = null;
        VG[] vgs = null;
        AG[][] agsSecrets = null;
        VG[][] vgsSecrets = null;
        if (testType == 0) {
            pointsSecrets = new Point[2][dataNumber];
            for (int i = 0; i < dataNumber; i++) {
                pointsSecrets[0][i] = new Point(m);
                pointsSecrets[1][i] = new Point(m);

                BigInteger[] idSecrets = randomSplit(dataset[i][m], mod);
                pointsSecrets[0][i].id = idSecrets[0];
                pointsSecrets[1][i].id = idSecrets[1];

                for (int j = 0; j < m; j++) {
                    BigInteger[] pSecrets = randomSplit(dataset[i][j], mod);

                    pointsSecrets[0][i].data[j] = pSecrets[0];
                    pointsSecrets[1][i].data[j] = pSecrets[1];
                }
            }
        } else if (testType == 1) {
            dataset[0] = new BigInteger[] { BigInteger.valueOf(20), BigInteger.valueOf(60), BigInteger.valueOf(0) };
            dataset[1] = new BigInteger[] { BigInteger.valueOf(40), BigInteger.valueOf(60), BigInteger.valueOf(1) };
            dataset[2] = new BigInteger[] { BigInteger.valueOf(80), BigInteger.valueOf(60), BigInteger.valueOf(2) };
            dataset[3] = new BigInteger[] { BigInteger.valueOf(20), BigInteger.valueOf(20), BigInteger.valueOf(3) };
            dataset[4] = new BigInteger[] { BigInteger.valueOf(40), BigInteger.valueOf(20), BigInteger.valueOf(4) };
            dataset[5] = new BigInteger[] { BigInteger.valueOf(80), BigInteger.valueOf(20), BigInteger.valueOf(5) };

            ags = new AG[6];
            vgs = new VG[2];
            generateVoronoiSKNNTestData(ags, vgs);
            agsSecrets = shareAGs(ags, mod);
            vgsSecrets = shareVGs(vgs, mod);
        } else if (testType == 2) {
            int agNum = Integer.parseInt(args[index++]);
            int agSize = Integer.parseInt(args[index++]);
            int vgNum = Integer.parseInt(args[index++]);
            int vgSize = Integer.parseInt(args[index++]);

            ags = new AG[agNum];
            vgs = new VG[vgNum];
            generateVoronoiSKNNVirtualData(dataLength, ags, agSize, vgs, vgSize);
            agsSecrets = shareAGs(ags, mod);
            vgsSecrets = shareVGs(vgs, mod);
        }

        try (Socket socketC1 = new Socket(ipC1, portC1); Socket socketC2 = new Socket(ipC2, portC2);) {
            PrintWriter writerC1 = new PrintWriter(socketC1.getOutputStream());
            BufferedReader readerC1 = new BufferedReader(new InputStreamReader(socketC1.getInputStream()));

            PrintWriter writerC2 = new PrintWriter(socketC2.getOutputStream());
            BufferedReader readerC2 = new BufferedReader(new InputStreamReader(socketC2.getInputStream()));

            Util.writeInt(testType, writerC1);
            Util.writeInt(k, writerC1);
            Util.writeBigInteger(mod, writerC1);
            Util.writeInt(dataNumber, writerC1);
            Util.writeInt(m, writerC1);
            if (testType == 0) {
                Util.writePoints(pointsSecrets[0], writerC1);
            } else if (testType == 1 || testType == 2) {
                writerC1.println(parseAGsToJson(agsSecrets[0]));
                writerC1.println(parseVGsToJson(vgsSecrets[0]));
            }
            writerC1.println(parseMultiplicationTripleToJson(triples[0]));
            writerC1.println(parseRandomNumberTupleToJson(tuples[0]));
            writerC1.flush();

            Util.writeInt(testType, writerC2);
            Util.writeInt(k, writerC2);
            Util.writeBigInteger(mod, writerC2);
            Util.writeInt(dataNumber, writerC2);
            Util.writeInt(m, writerC2);
            if (testType == 0) {
                Util.writePoints(pointsSecrets[1], writerC2);
            } else if (testType == 1 || testType == 2) {
                writerC2.println(parseAGsToJson(agsSecrets[1]));
                writerC2.println(parseVGsToJson(vgsSecrets[1]));
            }
            writerC2.println(parseMultiplicationTripleToJson(triples[1]));
            writerC2.println(parseRandomNumberTupleToJson(tuples[1]));
            writerC2.flush();

            /* 计算过程 */
            Util.writeInt(testNumber, writerC1);
            Util.writeInt(testNumber, writerC2);

            for (int i = 0; i < testNumber; i++) {
                System.out.print(i + " ");

                BigInteger[] q = new BigInteger[m];
                BigInteger[][] qSecrets = new BigInteger[2][m];
                for (int j = 0; j < m; j++) {
                    q[j] = new BigInteger(dataLength, random);

                    BigInteger[] t = randomSplit(q[j], mod);
                    qSecrets[0][j] = t[0];
                    qSecrets[1][j] = t[1];
                }

                Util.writeBigIntegers(qSecrets[0], writerC1);
                Util.writeBigIntegers(qSecrets[1], writerC2);

                Point[] r1 = Util.readPoints(k, m, readerC1);
                Point[] r2 = Util.readPoints(k, m, readerC2);

                if (testType == 0 || testType == 1) {
                    Set<BigInteger> r = new HashSet<>();
                    for (int j = 0; j < k; j++) {
                        r.add(r1[j].id.add(r2[j].id).mod(mod));
                    }
                    // System.out.println("Data user get: " + r);

                    // 验证结果
                    Set<BigInteger> validResult = getKNearest(dataset, q, m, 2, k);

                    for (BigInteger id : validResult) {
                        if (!r.contains(id)) {
                            System.out.println("Result is wrong!");
                            System.out.println("SKNN: " + r);
                            System.out.println("KNN" + validResult);
                            System.out.println("q = " + Arrays.asList(q));

                            break;
                            // return;
                        }
                    }
                }
            }
            System.out.println();
            long timeC1 = Util.readLong(readerC1);
            long communicationTimeC1 = Util.readLong(readerC1);
            long computingTimeC1 = Util.readLong(readerC1);
            long timeC2 = Util.readLong(readerC2);
            long communicationTimeC2 = Util.readLong(readerC2);
            long computingTimeC2 = Util.readLong(readerC2);
            System.out.println("Time C1: " + timeC1 + " ms");
            System.out.println("Communication Time C1: " + communicationTimeC1 + " ms");
            System.out.println("Computing Time C1: " + computingTimeC1 + " ms");
            System.out.println("Time C2: " + timeC2 + " ms");
            System.out.println("Communication Time C2: " + communicationTimeC2 + " ms");
            System.out.println("Computing Time C2: " + computingTimeC2 + " ms");
        }
    }

    /**
    * args: role portC1
    * 
    * @param args
    * @throws IOException
    */
    public static void c1(String[] args) throws IOException {
        /* 提取测试数据 */
        int index = 1;
        int portC1 = Integer.parseInt(args[index++]);

        ServerSocket serverSocket = new ServerSocket(portC1);

        Socket socketUser = serverSocket.accept();
        PrintWriter writerUser = new PrintWriter(socketUser.getOutputStream());
        BufferedReader readerUser = new BufferedReader(new InputStreamReader(socketUser.getInputStream()));

        Socket socketC2 = serverSocket.accept();
        PrintWriter writerC2 = new PrintWriter(socketC2.getOutputStream());
        BufferedReader readerC2 = new BufferedReader(new InputStreamReader(socketC2.getInputStream()));

        /* 计算过程 */
        int testType = Util.readInt(readerUser);
        int k = Util.readInt(readerUser);
        BigInteger mod = Util.readBigInteger(readerUser);
        int dataNumber = Util.readInt(readerUser);
        int m = Util.readInt(readerUser);
        Point[] points = null;
        AG[] ags = null;
        VG[] vgs = null;
        if (testType == 0) {
            points = Util.readPoints(dataNumber, m, readerUser);
        } else if (testType == 1 || testType == 2) {
            ags = parseJsonToAGs(readerUser.readLine());
            vgs = parseJsonToVGs(readerUser.readLine());
        }
        MultiplicationTriple triple = parseJsonToMultiplicationTriple(readerUser.readLine());
        RandomNumberTuple tuple = parseJsonToRandomNumberTuple(readerUser.readLine());

        AdditiveSecretSharing.PartyID partyID = AdditiveSecretSharing.PartyID.C1;

        int testNumber = Util.readInt(readerUser);
        long timeSum = 0L;
        long communicationTimeSum = 0l;
        for (int i = 0; i < testNumber; i++) {

            BigInteger[] q = Util.readBigIntegers(m, readerUser);

            Util.writeInt(i, writerC2);
            Util.readInt(readerC2);

            Point[] r1 = null;
            long timePre = System.currentTimeMillis();
            RunningTimeCounter.startRecord(RunningTimeCounter.COMMUNICATION_TIME);
            if (testType == 0) {
                r1 = SKNNV2.secureLinearSKNN(partyID, points, q, k, triple, tuple, mod, readerC2, writerC2);
            } else if (testType == 1 || testType == 2) {
                r1 = SKNNV2.secureVoronoiSKNN(partyID, ags, vgs, q, k, triple, tuple, mod, readerC2, writerC2);
            }
            timeSum += System.currentTimeMillis() - timePre;
            communicationTimeSum += RunningTimeCounter.get(RunningTimeCounter.COMMUNICATION_TIME);

            Util.writePoints(r1, writerUser);
        }
        long timeAvg = timeSum / testNumber;
        long communicationTimeAvg = communicationTimeSum / testNumber;
        long computingTimeAvg = timeAvg - communicationTimeAvg;
        Util.writeLong(timeAvg, writerUser);
        Util.writeLong(communicationTimeAvg, writerUser);
        Util.writeLong(computingTimeAvg, writerUser);

        socketC2.close();
        socketUser.close();
        serverSocket.close();
    }

    /**
    * args: role ipC1 portC1 portC2
    * 
    * @param args
    * @throws IOException
    */
    public static void c2(String[] args) throws IOException {
        /* 提取测试数据 */
        int index = 1;
        String ipC1 = args[index++];
        int portC1 = Integer.parseInt(args[index++]);
        int portC2 = Integer.parseInt(args[index++]);

        ServerSocket serverSocket = new ServerSocket(portC2);

        Socket socketUser = serverSocket.accept();
        PrintWriter writerUser = new PrintWriter(socketUser.getOutputStream());
        BufferedReader readerUser = new BufferedReader(new InputStreamReader(socketUser.getInputStream()));

        Socket socketC1 = new Socket(ipC1, portC1);
        PrintWriter writerC1 = new PrintWriter(socketC1.getOutputStream());
        BufferedReader readerC1 = new BufferedReader(new InputStreamReader(socketC1.getInputStream()));

        /* 计算过程 */
        int testType = Util.readInt(readerUser);
        int k = Util.readInt(readerUser);
        BigInteger mod = Util.readBigInteger(readerUser);
        int dataNumber = Util.readInt(readerUser);
        int m = Util.readInt(readerUser);
        Point[] points = null;
        AG[] ags = null;
        VG[] vgs = null;
        if (testType == 0) {
            points = Util.readPoints(dataNumber, m, readerUser);
        } else if (testType == 1 || testType == 2) {
            ags = parseJsonToAGs(readerUser.readLine());
            vgs = parseJsonToVGs(readerUser.readLine());
        }
        MultiplicationTriple triple = parseJsonToMultiplicationTriple(readerUser.readLine());
        RandomNumberTuple tuple = parseJsonToRandomNumberTuple(readerUser.readLine());

        AdditiveSecretSharing.PartyID partyID = AdditiveSecretSharing.PartyID.C2;
        int testNumber = Util.readInt(readerUser);
        long timeSum = 0L;
        long communicationTimeSum = 0l;
        for (int i = 0; i < testNumber; i++) {

            BigInteger[] q = Util.readBigIntegers(m, readerUser);

            Util.writeInt(i, writerC1);
            Util.readInt(readerC1);

            Point[] r2 = null;
            long timePre = System.currentTimeMillis();
            RunningTimeCounter.startRecord(RunningTimeCounter.COMMUNICATION_TIME);
            // testing function
            if (testType == 0) {
                r2 = SKNNV2.secureLinearSKNN(partyID, points, q, k, triple, tuple, mod, readerC1, writerC1);
            } else if (testType == 1 || testType == 2) {
                r2 = SKNNV2.secureVoronoiSKNN(partyID, ags, vgs, q, k, triple, tuple, mod, readerC1, writerC1);
            }
            timeSum += System.currentTimeMillis() - timePre;
            communicationTimeSum += RunningTimeCounter.get(RunningTimeCounter.COMMUNICATION_TIME);

            Util.writePoints(r2, writerUser);
        }
        long timeAvg = timeSum / testNumber;
        long communicationTimeAvg = communicationTimeSum / testNumber;
        long computingTimeAvg = timeAvg - communicationTimeAvg;
        Util.writeLong(timeAvg, writerUser);
        Util.writeLong(communicationTimeAvg, writerUser);
        Util.writeLong(computingTimeAvg, writerUser);

        socketC1.close();
        socketUser.close();
        serverSocket.close();
    }

    public static Point[] sharePoint(Point point, BigInteger mod) {
        int m = point.data.length;

        BigInteger[] idSecrets = randomSplit(point.id, mod);
        BigInteger[][] pointDataSecrets = new BigInteger[2][m];
        for (int i = 0; i < m; i++) {
            BigInteger[] secrets = randomSplit(point.data[i], mod);
            pointDataSecrets[0][i] = secrets[0];
            pointDataSecrets[1][i] = secrets[1];
        }

        return new Point[] { new Point(idSecrets[0], pointDataSecrets[0]),
                new Point(idSecrets[1], pointDataSecrets[1]) };
    }

    public static void generateVoronoiSKNNTestData(AG[] ags, VG[] vgs) {
        Point[] points = new Point[6];
        points[0] = new Point(BigInteger.valueOf(0),
                new BigInteger[] { BigInteger.valueOf(20), BigInteger.valueOf(60) });
        points[1] = new Point(BigInteger.valueOf(1),
                new BigInteger[] { BigInteger.valueOf(40), BigInteger.valueOf(60) });
        points[2] = new Point(BigInteger.valueOf(2),
                new BigInteger[] { BigInteger.valueOf(80), BigInteger.valueOf(60) });
        points[3] = new Point(BigInteger.valueOf(3),
                new BigInteger[] { BigInteger.valueOf(20), BigInteger.valueOf(20) });
        points[4] = new Point(BigInteger.valueOf(4),
                new BigInteger[] { BigInteger.valueOf(40), BigInteger.valueOf(20) });
        points[5] = new Point(BigInteger.valueOf(5),
                new BigInteger[] { BigInteger.valueOf(80), BigInteger.valueOf(20) });

        ags[0] = new AG(BigInteger.valueOf(0), new Point[] { points[1], points[3], points[4] },
                new BigInteger[] { BigInteger.valueOf(1), BigInteger.valueOf(3), BigInteger.valueOf(4) });
        ags[1] = new AG(BigInteger.valueOf(1), new Point[] { points[0], points[2], points[4] },
                new BigInteger[] { BigInteger.valueOf(0), BigInteger.valueOf(2), BigInteger.valueOf(4) });
        ags[2] = new AG(BigInteger.valueOf(2), new Point[] { points[1], points[4], points[5] },
                new BigInteger[] { BigInteger.valueOf(1), BigInteger.valueOf(4), BigInteger.valueOf(5) });
        ags[3] = new AG(BigInteger.valueOf(3), new Point[] { points[0], points[1], points[4] },
                new BigInteger[] { BigInteger.valueOf(0), BigInteger.valueOf(1), BigInteger.valueOf(4) });
        ags[4] = new AG(BigInteger.valueOf(4), new Point[] { points[1], points[3], points[5] },
                new BigInteger[] { BigInteger.valueOf(1), BigInteger.valueOf(3), BigInteger.valueOf(5) });
        ags[5] = new AG(BigInteger.valueOf(5), new Point[] { points[1], points[2], points[4] },
                new BigInteger[] { BigInteger.valueOf(1), BigInteger.valueOf(2), BigInteger.valueOf(4) });

        vgs[0] = new VG(new Point(null, new BigInteger[] { BigInteger.valueOf(0), BigInteger.valueOf(40) }),
                new Point(null, new BigInteger[] { BigInteger.valueOf(128), BigInteger.valueOf(128) }),
                new Point[] { points[0], points[1], points[2] },
                new BigInteger[] { BigInteger.valueOf(0), BigInteger.valueOf(1), BigInteger.valueOf(2) });
        vgs[1] = new VG(new Point(null, new BigInteger[] { BigInteger.valueOf(0), BigInteger.valueOf(0) }),
                new Point(null, new BigInteger[] { BigInteger.valueOf(128), BigInteger.valueOf(40) }),
                new Point[] { points[3], points[4], points[5] },
                new BigInteger[] { BigInteger.valueOf(3), BigInteger.valueOf(4), BigInteger.valueOf(5) });
    }

    public static void generateVoronoiSKNNVirtualData(int dataLength, AG[] ags, int agSize,
            VG[] vgs, int vgSize) {

        int agNum = ags.length;
        int vgNum = vgs.length;

        Random random = new Random();

        List<Point> dataset = new ArrayList<>();
        for (int i = 0; i < agNum * agSize; i++) {
            BigInteger[] pData = new BigInteger[2];
            for (int j = 0; j < 2; j++) {
                pData[j] = new BigInteger(dataLength, random);
            }

            dataset.add(new Point(BigInteger.valueOf(i), pData));
        }

        List<BigInteger> agLabels = new ArrayList<>();
        for (int i = 0; i < agNum; i++) {
            agLabels.add(BigInteger.valueOf(i));
        }

        int index = 0;
        for (int i = 0; i < agNum; i++) {
            Point[] points = new Point[agSize];
            for (int j = 0; j < agSize; j++) {
                points[j] = dataset.get(index++);
            }

            Collections.shuffle(agLabels);
            BigInteger[] subLabels = new BigInteger[agSize];
            for (int j = 0; j < agSize; j++) {
                subLabels[j] = agLabels.get(j);
            }

            ags[i] = new AG(BigInteger.valueOf(i), points, subLabels);
        }

        BigInteger maxHigh = BigInteger.TWO.pow(dataLength);
        BigInteger interval = maxHigh.divide(BigInteger.valueOf(vgNum));
        for (int i = 0; i < vgNum; i++) {
            Point low = new Point(null, new BigInteger[] { interval.multiply(BigInteger.valueOf(i)), BigInteger.ZERO });
            Point high = new Point(null, new BigInteger[] { interval.multiply(BigInteger.valueOf(i + 1)), maxHigh });

            if (i == vgNum - 1) {
                high = new Point(null, new BigInteger[] { maxHigh, maxHigh });
            }

            Collections.shuffle(dataset);
            Point[] points = new Point[vgSize];
            for (int j = 0; j < vgSize; j++) {
                points[j] = dataset.get(j);
            }

            Collections.shuffle(agLabels);
            BigInteger[] subLabels = new BigInteger[vgSize];
            for (int j = 0; j < vgSize; j++) {
                subLabels[j] = agLabels.get(j);
            }

            vgs[i] = new VG(low, high, points, subLabels);
        }
    }

    public static AG[][] shareAGs(AG[] ags, BigInteger mod) {
        int num = ags.length;
        int size = ags[0].points.length;
        int m = ags[0].points[0].data.length;

        AG[][] agsSecrets = new AG[2][num];
        for (int i = 0; i < num; i++) {
            agsSecrets[0][i] = new AG(size, m);
            agsSecrets[1][i] = new AG(size, m);

            BigInteger[] labelSecrets = randomSplit(ags[i].label, mod);
            agsSecrets[0][i].label = labelSecrets[0];
            agsSecrets[1][i].label = labelSecrets[1];

            for (int j = 0; j < size; j++) {
                BigInteger[] subLabelSecrets = randomSplit(ags[i].subLabels[j], mod);
                agsSecrets[0][i].subLabels[j] = subLabelSecrets[0];
                agsSecrets[1][i].subLabels[j] = subLabelSecrets[1];

                Point[] pointSecrets = sharePoint(ags[i].points[j], mod);
                agsSecrets[0][i].points[j] = pointSecrets[0];
                agsSecrets[1][i].points[j] = pointSecrets[1];
            }
        }

        return agsSecrets;
    }

    public static String parseAGsToJson(AG[] ags) {
        return JSON.toJSONString(ags);
    }

    public static AG[] parseJsonToAGs(String json) {
        return JSON.parseArray(json, AG.class).toArray(new AG[] {});
    }

    public static VG[][] shareVGs(VG[] vgs, BigInteger mod) {
        int num = vgs.length;
        int size = vgs[0].points.length;
        int m = vgs[0].points[0].data.length;

        VG[][] vgsSecrets = new VG[2][num];
        for (int i = 0; i < num; i++) {
            vgsSecrets[0][i] = new VG(size, m);
            vgsSecrets[1][i] = new VG(size, m);

            Point[] lowSecrets = sharePoint(vgs[i].low, mod);
            vgsSecrets[0][i].low = lowSecrets[0];
            vgsSecrets[1][i].low = lowSecrets[1];

            Point[] highSecrets = sharePoint(vgs[i].high, mod);
            vgsSecrets[0][i].high = highSecrets[0];
            vgsSecrets[1][i].high = highSecrets[1];

            for (int j = 0; j < size; j++) {
                BigInteger[] subLabelSecrets = randomSplit(vgs[i].subLabels[j], mod);
                vgsSecrets[0][i].subLabels[j] = subLabelSecrets[0];
                vgsSecrets[1][i].subLabels[j] = subLabelSecrets[1];

                Point[] pointSecrets = sharePoint(vgs[i].points[j], mod);
                vgsSecrets[0][i].points[j] = pointSecrets[0];
                vgsSecrets[1][i].points[j] = pointSecrets[1];
            }
        }

        return vgsSecrets;
    }

    public static String parseVGsToJson(VG[] vgs) {
        return JSON.toJSONString(vgs);
    }

    public static VG[] parseJsonToVGs(String json) {
        return JSON.parseArray(json, VG.class).toArray(new VG[] {});
    }

    public static void main(String[] args) throws IOException {

        // args不是null，其长度为0。
        System.out.println("args: " + Arrays.asList(args));

        /*
        * test type:   0-Linear SKNN   1-Voronoi SKNN for debug  2-Voronoi SKNN for test
        * 
        * testType = 0 or 1
        * args: role ipC1 portC1 ipC2 portC2 randomSeed testType testNumber dataNumber dataLength dimension k
        * 
        * testType = 2
        * args: role ipC1 portC1 ipC2 portC2 randomSeed testType testNumber dataNumber dataLength dimension k agNum agSize vgNum vgSize
        */

        String c1 = "c1 8001";
        String c2 = "c2 127.0.0.1 8001 8002";
        String user = "user 127.0.0.1 8001 127.0.0.1 8002 " // role ipC1 portC1 ipC2 portC2 
                + "null 0 10 1000 20 5 3"; // randomSeed testType testNumber dataNumber dataLength dimension k
                // + "null 1 100 6 7 2 3"; // randomSeed testType testNumber dataNumber dataLength dimension k
                // + "null 2 100 6 7 2 3 6 3 2 3"; // randomSeed testType testNumber dataNumber dataLength dimension k agNum agSize vgNum vgSize

        args = c1.split(" ");
        args = c2.split(" ");
        args = user.split(" ");

        if (args[0].equals("user"))
            user(args);
        else if (args[0].equals("c1"))
            c1(args);
        else if (args[0].equals("c2"))
            c2(args);
    }
}
