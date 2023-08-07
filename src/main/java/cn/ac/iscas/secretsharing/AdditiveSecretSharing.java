package cn.ac.iscas.secretsharing;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;

import com.alibaba.fastjson.JSON;

import cn.ac.iscas.utils.Util;

/**
 * 两方加性秘密分享
 * <p>
 * 其中“[]”符号表示秘密分享，如[a]等同于将a分享成[a]_1和[a]_2
 */
public class AdditiveSecretSharing {


    public enum PartyID {
        C1, C2
    }

    /**
     * 此处是某方的乘法三元组的秘密
     */
    public static class MultiplicationTriple {
        // [a]_i, [b]_i, [c]_i
        public BigInteger ai, bi, ci;
        // private int ai, bi, ci;

        public MultiplicationTriple(BigInteger ai, BigInteger bi, BigInteger ci) {
            this.ai = ai;
            this.bi = bi;
            this.ci = ci;
        }
    }

    public static MultiplicationTriple[] generateMultiplicationTriples(BigInteger mod) {
        BigInteger a = Util.getRandomBigInteger(mod);
        BigInteger b = Util.getRandomBigInteger(mod);
        BigInteger c = a.multiply(b).mod(mod);

        BigInteger[] aSecrets = new BigInteger[2], bSecrets = new BigInteger[2], cSecrets = new BigInteger[2];
        aSecrets = AdditiveSecretSharing.randomSplit(a, mod);
        bSecrets = AdditiveSecretSharing.randomSplit(b, mod);
        cSecrets = AdditiveSecretSharing.randomSplit(c, mod);

        MultiplicationTriple[] triples = new MultiplicationTriple[2];
        triples[0] = new MultiplicationTriple(aSecrets[0], bSecrets[0], cSecrets[0]);
        triples[1] = new MultiplicationTriple(aSecrets[1], bSecrets[1], cSecrets[1]);

        System.out.println("a = " + a + ", b = " + b + ", c = " + c);

        return triples;
    }

    public static String parseMultiplicationTripleToJson(MultiplicationTriple triple) {
        return JSON.toJSONString(triple);
    }

    public static MultiplicationTriple parseJsonToMultiplicationTriple(String json) {
        return JSON.parseObject(json, MultiplicationTriple.class);
    }

    /**
     * 要求：x < mod/2
     * 计算中会限制：  mod / 2 < x1 < mod， 则 x1 + x2 = x % mod 的同时保证 x1 + x2 > mod
     * 保证 x1 + x2 > mod，就无须担心后续判断大小时会出错。
     * 
     * 将x随机划分x_1和x_2，要求x = x_1 + x_2
     * <p>
     * 过程：先随机抽取x_1，然后x_2 = x - x_1
     * <p>
     * 注意：由于此代码只是实验性质，所以不考虑伪随机数的安全性
     *
     * @param x
     * @param mod
     * @return 二元数组[x_1, x_2]
     */
    public static BigInteger[] randomSplit(BigInteger x, BigInteger mod) {
        if (x == null)
            return new BigInteger[] { null, null };

        BigInteger[] xSecrets = new BigInteger[2];

        xSecrets[0] = Util.getRandomBigInteger(mod);
        xSecrets[1] = subtract(x, xSecrets[0], mod);

        return xSecrets;
    }

    /**
     * 在一些算法中会涉及到公开常量的运算，这里公开常量并没有被秘密分享，所以在C_1和C_2各自运算前需要设置一个拆分规则：
     * 假设公开常量为a，
     * C_1直接使用a参与运算；
     * C_2直接使用0参与运算。
     *
     * @param partyID C_1或C_2
     * @param a       公开常量
     * @return
     */
    public static BigInteger shareConstant(PartyID partyID, BigInteger a) {
        return (partyID == PartyID.C1) ? a : BigInteger.ZERO;
    }

    public static BigInteger recover(PartyID partyID, BigInteger xi, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        Util.writeBigInteger(xi, writer); // 将自己的秘密发送给对方
        BigInteger xTemp = Util.readBigInteger(reader); // 接收对方的秘密

        return add(xi, xTemp, mod); // 加性秘密恢复
    }

    public static BigInteger[] recover(PartyID partyID, BigInteger[] xiArray, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        int len = xiArray.length;

        // Util.writeBigIntegers(xiArray, writer); // 将自己的秘密发送给对方
        // BigInteger[] tiArray = Util.readBigIntegers(len, reader); // 接收对方的秘密
        BigInteger[] tiArray = Util.exchangeBigIntegers(xiArray, reader, writer);

        BigInteger[] result = new BigInteger[len];
        for (int i = 0; i < len; i++) {
            result[i] = add(xiArray[i], tiArray[i], mod);
        }

        return result; // 加性秘密恢复
    }

    /**
     * 取模加法
     * 当x和y为一个秘密元组时，为恢复秘密
     *
     * @param x   [x]的之一
     * @param y   [y]的之一
     * @param mod 模数
     * @return [z]的之一或者重建结果
     */
    public static BigInteger add(BigInteger x, BigInteger y, BigInteger mod) {
        return x.add(y).mod(mod);
    }

    public static BigInteger subtract(BigInteger x, BigInteger y, BigInteger mod) {
        return x.subtract(y).mod(mod);
    }

    /**
    * 常量和元素乘法
    * <p>
    * z_i = alpha * x_i
    */
    public static BigInteger multiply(BigInteger alpha, BigInteger xi, BigInteger mod) {
        return alpha.multiply(xi).mod(mod);
    }

    /**
     * 元素乘法，目的是计算z = x*y
     * <p>
     * multiplyInC1()函数代表C_1的操作
     * multiplyInC2()函数代表C_2的操作
     * Socket通信由C_1来初始化
     * <p>
     * C_1和C_2先各自计算：
     * [e]_i = [x]_i - [a]_i
     * [f]_i = [y]_i - [b]_i
     * <p>
     * C_1和C_2交换秘密并恢复e和f
     * <p>
     * C_1计算： [z]_1 = f * [a]_1 + e * [b]_1 + [c]_1
     * C_2计算： [z]_2 = e * f + f * [a]_2 + e * [b]_2 + [c]_2
     */
    public static BigInteger multiply(PartyID partyID, BigInteger xi, BigInteger yi, MultiplicationTriple triple,
            BigInteger mod, BufferedReader reader, PrintWriter writer) throws IOException {
        BigInteger ei = subtract(xi, triple.ai, mod);
        BigInteger fi = subtract(yi, triple.bi, mod);

        // C_1与C_2交换秘密并恢复e和f
        Util.writeBigIntegers(new BigInteger[] { ei, fi }, writer);
        BigInteger[] t = Util.readBigIntegers(2, reader);
        BigInteger e = add(ei, t[0], mod);
        BigInteger f = add(fi, t[1], mod);

        BigInteger x;
        if (partyID == PartyID.C1) // C_1计算： [z]_1 = f * [a]_1 + e * [b]_1 + [c]_1
            // x = f * triple.ai + e * triple.bi + triple.ci;
            x = f.multiply(triple.ai).add(e.multiply(triple.bi)).add(triple.ci);
        else
            // x = e * f + f * triple.ai + e * triple.bi + triple.ci;
            x = e.multiply(f).add(f.multiply(triple.ai)).add(e.multiply(triple.bi)).add(triple.ci);

        return x.mod(mod);
    }

    public static BigInteger[] multiplyS(PartyID partyID, BigInteger[] xis, BigInteger[] yis,
            MultiplicationTriple triple, BigInteger mod, BufferedReader reader, PrintWriter writer) throws IOException {
        int num = xis.length;
        BigInteger[] result = new BigInteger[num];

        BigInteger[] efis = new BigInteger[num * 2];
        for (int i = 0; i < num; i++) {
            efis[i] = subtract(xis[i], triple.ai, mod);
            efis[i + num] = subtract(yis[i], triple.bi, mod);
        }

        BigInteger[] ts = Util.exchangeBigIntegers(efis, reader, writer);

        for (int i = 0; i < num; i++) {
            // C_1与C_2交换秘密并恢复e和f
            BigInteger e = add(efis[i], ts[i], mod);
            BigInteger f = add(efis[i + num], ts[i + num], mod);

            BigInteger x;
            if (partyID == PartyID.C1) // C_1计算： [z]_1 = f * [a]_1 + e * [b]_1 + [c]_1
                // x = f * triple.ai + e * triple.bi + triple.ci;
                x = f.multiply(triple.ai).add(e.multiply(triple.bi)).add(triple.ci);
            else
                // x = e * f + f * triple.ai + e * triple.bi + triple.ci;
                x = e.multiply(f).add(f.multiply(triple.ai)).add(e.multiply(triple.bi)).add(triple.ci);

            result[i] = x.mod(mod);
        }

        return result;
    }

    /*
     * 两分法连乘
     * 
     * 减少通讯复杂度为：log_2(n)，其中n为数值个数。
     */
    public static BigInteger secureProduct(PartyID partyID, BigInteger[] xiArray, MultiplicationTriple triple,
            BigInteger mod, BufferedReader reader, PrintWriter writer) throws IOException {

        if (xiArray == null || xiArray.length == 0)
            return null;

        while (xiArray.length > 1) {
            int subLen = xiArray.length / 2;
            BigInteger[] preiArray = Arrays.copyOfRange(xiArray, 0, subLen);
            BigInteger[] postiArray = Arrays.copyOfRange(xiArray, subLen, subLen * 2);

            BigInteger[] tiArray = multiplyS(partyID, preiArray, postiArray, triple, mod, reader, writer);

            if (xiArray.length % 2 != 0) { // 若长度为奇数，则末尾元素未参与此轮乘法
                BigInteger taili = xiArray[xiArray.length - 1];

                xiArray = new BigInteger[subLen + 1];
                xiArray[subLen] = taili;
            } else {
                xiArray = new BigInteger[subLen];
            }

            System.arraycopy(tiArray, 0, xiArray, 0, subLen);
        }

        return xiArray[0];
    }

    public static BigInteger[] secureProduct(PartyID partyID, BigInteger[][] xiArrays, MultiplicationTriple triple,
            BigInteger mod, BufferedReader reader, PrintWriter writer) throws IOException {

        int arrNum = xiArrays.length; // 数组个数
        int arrLen = xiArrays[0].length; // 各数组长度

        while (xiArrays[0].length > 1) {
            int subLen = arrLen / 2;

            BigInteger[] preisArray = new BigInteger[arrNum * subLen];
            BigInteger[] postisArray = new BigInteger[arrNum * subLen];
            for (int i = 0; i < arrNum; i++) {
                System.arraycopy(xiArrays[i], 0, preisArray, i * subLen, subLen);
                System.arraycopy(xiArrays[i], subLen, postisArray, i * subLen, subLen);
            }

            BigInteger[] tisArray = multiplyS(partyID, preisArray, postisArray, triple, mod, reader, writer);

            if (arrLen % 2 != 0) { // 若长度为奇数，则末尾元素未参与此轮乘法
                for (int i = 0; i < arrNum; i++) {
                    BigInteger tailii = xiArrays[i][arrLen - 1];

                    xiArrays[i] = new BigInteger[subLen + 1];
                    xiArrays[i][subLen] = tailii;
                }
            } else {
                for (int i = 0; i < arrNum; i++) {
                    xiArrays[i] = new BigInteger[subLen];
                }
            }

            for (int i = 0; i < arrNum; i++) {
                System.arraycopy(tisArray, i * subLen, xiArrays[i], 0, subLen);
            }

            arrLen = xiArrays[0].length;
        }

        BigInteger[] resultis = new BigInteger[arrNum];
        for (int i = 0; i < arrNum; i++) {
            resultis[i] = xiArrays[i][0];
        }

        return resultis;
    }

    public static class RandomNumberTuple {
        public BigInteger r;
        public int l;
        public BigInteger[] rBinary; // LSB在数组低位

        public RandomNumberTuple(BigInteger r, int l, BigInteger[] rBinary) {
            this.r = r;
            this.l = l;
            this.rBinary = rBinary;
        }
    }

    public static RandomNumberTuple[] generateRandomNumberTuples(int l, BigInteger mod) {
        RandomNumberTuple[] tuples = new RandomNumberTuple[2];

        BigInteger r = Util.getRandomBigInteger(mod);
        BigInteger[] rSecrets = randomSplit(r, mod);

        BigInteger[] rBinary = Util.decimalToBinaryV2(r, l);

        BigInteger[][] rBinarySecrets = new BigInteger[2][l];
        for (int i = 0; i < l; i++) {
            BigInteger[] secrets = randomSplit(rBinary[i], mod);
            rBinarySecrets[0][i] = secrets[0];
            rBinarySecrets[1][i] = secrets[1];
        }

        tuples[0] = new RandomNumberTuple(rSecrets[0], l, rBinarySecrets[0]);
        tuples[1] = new RandomNumberTuple(rSecrets[1], l, rBinarySecrets[1]);

        return tuples;
    }

    public static String parseRandomNumberTupleToJson(RandomNumberTuple rTuple) {
        return JSON.toJSONString(rTuple);
    }

    public static RandomNumberTuple parseJsonToRandomNumberTuple(String json) {
        return JSON.parseObject(json, RandomNumberTuple.class);
    }

    /**
    * SC - v2
    * 计算 bool(a < b)
    *
    * mod: p，大素数，比特长度为l
    * 0 <= ai, bi < p/2
    *
    * 为保证数值总小于模数的一半，则模数的长度至少为数值的长度+2
    */
    public static BigInteger secureComparision(PartyID partyID, BigInteger ai, BigInteger bi,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        // 计算 <c> = <a> - <b>
        BigInteger ci = ai.subtract(bi).mod(mod);

        // 计算 < c<p/2 >
        BigInteger ti = secureComparisionSub1(partyID, ci, triple, rTuple, mod, reader, writer);

        // 计算 < a<b > = 1 - < c<p/2 >
        BigInteger resulti = shareConstant(partyID, BigInteger.ONE).subtract(ti).mod(mod);

        return resulti;
    }

    /*
     * 计算< a < p/2 >
     * mod: p，大素数，比特长度为l
     */
    private static BigInteger secureComparisionSub1(PartyID partyID, BigInteger ai,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        // <x> = 2<a>
        BigInteger xi = BigInteger.TWO.multiply(ai).mod(mod);

        // <c> = <x> + <r>
        BigInteger ci = xi.add(rTuple.r).mod(mod);

        // open/recover c
        BigInteger c = recover(partyID, ci, mod, reader, writer);

        // 计算<alpha> = <c0 XOR r0>。当c0 = 0，<alpha> = <r0>；当c0 = 1, <alpha> = 1 - <r0>。
        BigInteger c0 = c.mod(BigInteger.TWO);
        BigInteger alphai = (c0.equals(BigInteger.ZERO)) ? rTuple.rBinary[0]
                : shareConstant(partyID, BigInteger.ONE).subtract(rTuple.rBinary[0]);

        // 计算 <beta> = < c<r >
        BigInteger betai = secureComparisionSub2(partyID, c, rTuple.rBinary, triple, rTuple, mod, reader, writer);

        // 计算<x_0> = <beta> + <alpha> - 2 <alpha> <beta>
        BigInteger ti = multiply(partyID, alphai, betai, triple, mod, reader, writer);
        BigInteger x0i = alphai.add(betai).subtract(BigInteger.TWO.multiply(ti)).mod(mod);

        // 计算< a<p/2 > = 1 - <x_0>
        BigInteger resulti = shareConstant(partyID, BigInteger.ONE).subtract(x0i).mod(mod);

        return resulti;
    }

    /*
     * 计算< a < b >
     * 其中，a是公开值，b是秘密分享
     */
    private static BigInteger secureComparisionSub2(PartyID partyID, BigInteger a, BigInteger[] biArray,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        int l = rTuple.l;

        BigInteger[] aBinary = Util.decimalToBinaryV2(a, l);

        BigInteger[] ciArray = new BigInteger[l];
        for (int i = 0; i < l; i++) {
            ciArray[i] = (aBinary[i].equals(BigInteger.ZERO)) ? biArray[i]
                    : shareConstant(partyID, BigInteger.ONE).subtract(biArray[i]);
        }

        BigInteger[] diArray = new BigInteger[l];
        diArray[l - 1] = ciArray[l - 1];
        BigInteger[] eiArray = new BigInteger[l];
        eiArray[l - 1] = diArray[l - 1];
        for (int i = l - 2; i >= 0; i--) {
            // <d_{i+1} > < c_i >
            BigInteger ti = multiply(partyID, diArray[i + 1], ciArray[i], triple, mod, reader, writer);

            // <d_i> =  <d_{i+1} > +  < c_i > - <d_{i+1} > <c_i>
            diArray[i] = diArray[i + 1].add(ciArray[i]).subtract(ti).mod(mod);

            // <e_i> = <d_i> - <d_{i+1}>
            eiArray[i] = diArray[i].subtract(diArray[i + 1]).mod(mod);
        }

        // 计算 < a<b > = SUM( <e_i> <r_i> )
        BigInteger[] tiArray = multiplyS(partyID, eiArray, rTuple.rBinary, triple, mod, reader, writer);

        BigInteger sumi = BigInteger.ZERO;
        for (int i = 0; i < tiArray.length; i++) {
            sumi = sumi.add(tiArray[i]);
        }

        return sumi.mod(mod);
    }

    /**
    * SC S - v2
    * mod: p，大素数，比特长度为l
    * 0 <= ai, bi < p/2
    *
    * 为保证数值总小于模数的一半，则模数的长度至少为数值的长度+2
    */
    public static BigInteger[] secureComparision(PartyID partyID, BigInteger[] aiArray, BigInteger[] biArray,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        int arrLen = aiArray.length;

        // 计算 <c> = <a> - <b>
        BigInteger[] ciArray = new BigInteger[arrLen];
        for (int i = 0; i < arrLen; i++) {
            ciArray[i] = aiArray[i].subtract(biArray[i]).mod(mod);
        }

        // 计算 < c<p/2 >
        BigInteger[] tiArray = secureComparisionSub1(partyID, ciArray, triple, rTuple, mod, reader, writer);

        // 计算 < a<b > = 1 - < c<p/2 >
        BigInteger[] resultis = new BigInteger[arrLen];
        for (int i = 0; i < arrLen; i++) {
            resultis[i] = shareConstant(partyID, BigInteger.ONE).subtract(tiArray[i]).mod(mod);
        }

        return resultis;
    }

    /*
     * 计算< a < p/2 >
     * mod: p，大素数，比特长度为l
     */
    private static BigInteger[] secureComparisionSub1(PartyID partyID, BigInteger[] aiArray,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        int arrayLen = aiArray.length;

        // <x> = 2<a>
        BigInteger[] xiArray = new BigInteger[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            xiArray[i] = BigInteger.TWO.multiply(aiArray[i]).mod(mod);
        }

        // <c> = <x> + <r>
        BigInteger[] ciArray = new BigInteger[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            ciArray[i] = xiArray[i].add(rTuple.r).mod(mod);
        }

        // open/recover c
        BigInteger[] cArray = recover(partyID, ciArray, mod, reader, writer);

        // 计算<alpha> = <c0 XOR r0>。当c0 = 0，<alpha> = <r0>；当c0 = 1, <alpha> = 1 - <r0>。
        BigInteger[] alphaiArray = new BigInteger[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            BigInteger c0 = cArray[i].mod(BigInteger.TWO);
            alphaiArray[i] = (c0.equals(BigInteger.ZERO)) ? rTuple.rBinary[0]
                    : shareConstant(partyID, BigInteger.ONE).subtract(rTuple.rBinary[0]);
        }

        // 计算 <beta> = < c<r >
        BigInteger[][] triArray = new BigInteger[arrayLen][];
        for (int i = 0; i < arrayLen; i++) {
            triArray[i] = rTuple.rBinary;
        }
        BigInteger[] betaiArray = secureComparisionSub2(partyID, cArray, triArray, triple, rTuple, mod, reader, writer);

        // 计算<x_0> = <beta> + <alpha> - 2 <alpha> <beta>
        BigInteger[] tis = multiplyS(partyID, alphaiArray, betaiArray, triple, mod, reader, writer);
        BigInteger[] resultis = new BigInteger[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            // BigInteger ti = multiply(partyID, alphaiArray[i], betaiArray[i], triple, mod, reader, writer);
            BigInteger x0i = alphaiArray[i].add(betaiArray[i]).subtract(BigInteger.TWO.multiply(tis[i])).mod(mod);

            // 计算< a<p/2 > = 1 - <x_0>
            resultis[i] = shareConstant(partyID, BigInteger.ONE).subtract(x0i).mod(mod);
        }

        return resultis;
    }

    /*
     * 计算< a < b >
     * 其中，a是公开值，b是秘密分享
     */
    private static BigInteger[] secureComparisionSub2(PartyID partyID, BigInteger[] aArray, BigInteger[][] biArrays,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        int arrLen = aArray.length;
        int l = rTuple.l;

        BigInteger[][] aBinarys = new BigInteger[arrLen][];
        for (int i = 0; i < arrLen; i++) {
            aBinarys[i] = Util.decimalToBinaryV2(aArray[i], l);
        }

        BigInteger[][] ciArrays = new BigInteger[arrLen][l];
        for (int j = 0; j < arrLen; j++) {
            for (int i = 0; i < l; i++) {
                ciArrays[j][i] = (aBinarys[j][i].equals(BigInteger.ZERO)) ? biArrays[j][i]
                        : shareConstant(partyID, BigInteger.ONE).subtract(biArrays[j][i]);
            }
        }

        BigInteger[][] diArrays = new BigInteger[arrLen][l];
        BigInteger[][] eiArrays = new BigInteger[arrLen][l];
        for (int i = 0; i < arrLen; i++) {
            diArrays[i][l - 1] = ciArrays[i][l - 1];
            eiArrays[i][l - 1] = diArrays[i][l - 1];
        }
        for (int i = l - 2; i >= 0; i--) {
            // <d_{i+1} > < c_i >
            BigInteger[] tdis = new BigInteger[arrLen];
            BigInteger[] tcis = new BigInteger[arrLen];
            for (int j = 0; j < arrLen; j++) {
                tdis[j] = diArrays[j][i + 1];
                tcis[j] = ciArrays[j][i];
            }
            BigInteger[] tis = multiplyS(partyID, tdis, tcis, triple, mod, reader, writer);

            for (int j = 0; j < arrLen; j++) {
                // <d_i> =  <d_{i+1} > +  < c_i > - <d_{i+1} > <c_i>
                diArrays[j][i] = diArrays[j][i + 1].add(ciArrays[j][i]).subtract(tis[j]).mod(mod);

                // <e_i> = <d_i> - <d_{i+1}>
                eiArrays[j][i] = diArrays[j][i].subtract(diArrays[j][i + 1]).mod(mod);
            }
        }

        // 计算 < a<b > = SUM( <e_i> <r_i> )
        BigInteger[] teis = new BigInteger[arrLen * l];
        BigInteger[] trbis = new BigInteger[arrLen * l];
        for (int i = 0; i < arrLen; i++) {
            System.arraycopy(eiArrays[i], 0, teis, i * l, l);
            System.arraycopy(rTuple.rBinary, 0, trbis, i * l, l);
        }
        BigInteger[] tiArray = multiplyS(partyID, teis, trbis, triple, mod, reader, writer);
        BigInteger[] sumiArray = new BigInteger[arrLen];
        for (int i = 0; i < arrLen; i++) {
            sumiArray[i] = BigInteger.ZERO;
            for (int j = 0; j < l; j++) {
                sumiArray[i] = sumiArray[i].add(tiArray[i * l + j]).mod(mod);
            }
        }

        return sumiArray;
    }

    /*
     * 等值比较协议
     */
    public static BigInteger secureEqual(PartyID partyID, BigInteger ai, BigInteger bi,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        // 本地计算 <c> = <a> - <b> + <r>
        BigInteger ci = ai.subtract(bi).add(rTuple.r).mod(mod);

        // open/recover c
        BigInteger c = recover(partyID, ci, mod, reader, writer);

        // 计算 <c=r>
        BigInteger resulti = secureEqualSub(partyID, c, rTuple.r, triple, rTuple, mod, reader, writer);

        return resulti;
    }

    private static BigInteger secureEqualSub(PartyID partyID, BigInteger c, BigInteger ri,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        BigInteger[] cBinary = Util.decimalToBinaryV2(c, rTuple.l);

        BigInteger[] alphaiArray = new BigInteger[rTuple.l];
        for (int i = 0; i < rTuple.l; i++) {
            alphaiArray[i] = (cBinary[i].equals(BigInteger.ONE)) ? rTuple.rBinary[i]
                    : shareConstant(partyID, BigInteger.ONE).subtract(rTuple.rBinary[i]);
        }

        // 计算 <c=r> = PROD(<\alpha_i>)
        BigInteger resulti = secureProduct(partyID, alphaiArray, triple, mod, reader, writer);

        return resulti;
    }

    /*
    * 等值比较协议 S
    */
    public static BigInteger[] secureEqual(PartyID partyID, BigInteger[] aiArray, BigInteger[] biArray,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        int arrLen = aiArray.length;

        // 本地计算 <c> = <a> - <b> + <r>
        BigInteger[] ciArray = new BigInteger[arrLen];
        for (int i = 0; i < arrLen; i++) {
            ciArray[i] = aiArray[i].subtract(biArray[i]).add(rTuple.r).mod(mod);
        }
        // BigInteger ci = ai.subtract(bi).add(rTuple.r).mod(mod);

        // open/recover c
        BigInteger[] cArray = recover(partyID, ciArray, mod, reader, writer);
        // BigInteger c = recover(partyID, ci, mod, reader, writer);

        // 计算 <c=r>
        BigInteger[] riArray = new BigInteger[arrLen];
        Arrays.fill(riArray, 0, arrLen, rTuple.r);
        BigInteger[] resulti = secureEqualSub(partyID, cArray, riArray, triple, rTuple, mod, reader, writer);

        return resulti;
    }

    private static BigInteger[] secureEqualSub(PartyID partyID, BigInteger[] cArray, BigInteger[] riArray,
            MultiplicationTriple triple, RandomNumberTuple rTuple, BigInteger mod,
            BufferedReader reader, PrintWriter writer) throws IOException {

        int arrLen = cArray.length;
        int l = rTuple.l;

        BigInteger[][] cBinarys = new BigInteger[arrLen][];
        for (int i = 0; i < arrLen; i++) {
            cBinarys[i] = Util.decimalToBinaryV2(cArray[i], l);
        }

        BigInteger[][] alphaiArrays = new BigInteger[arrLen][l];
        for (int i = 0; i < arrLen; i++) {
            for (int j = 0; j < l; j++) {
                alphaiArrays[i][j] = (cBinarys[i][j].equals(BigInteger.ONE)) ? rTuple.rBinary[j]
                        : shareConstant(partyID, BigInteger.ONE).subtract(rTuple.rBinary[j]);
            }
        }

        // 计算 <c=r> = PROD(<\alpha_i>)
        BigInteger[] resultis = secureProduct(partyID, alphaiArrays, triple, mod, reader, writer);

        return resultis;
    }

}
