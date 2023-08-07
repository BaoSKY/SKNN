package cn.ac.iscas.utils;


import java.io.*;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DataProcessor {


    /**
     * 论文实验的数据集生成算法。
     * 本算法生成number个维度为dimension的定义域为[0,scope]的Point，以及指向元素的ptr，并不直接生成Rectangle。
     * 返回值为BigInteger[][]，其中每个BigInteger[]代表一条数据，BigInteger[][0,dimension-1]代表Point，BigInteger[][dimension]代表ptr。
     * <p>
     * 要求：
     * 1.随机生成
     * 2.维度由dimension决定
     * 3.数据量由number决定
     * 4.定义域由bitLength决定（[0, 2^bitLength-1]）
     * <p>
     *
     * @param dimension Point的维度
     * @param number    数据集大小，即Point的数量
     * @param bitLength     Point坐标值的范围：[0, 2^bitLength-1]
     * @return 
     */
    public static BigInteger[][] generateDataset(int dimension, int number, int bitLength, Random random) {
        BigInteger[][] dataset = new BigInteger[number][dimension + 1];

        // Random random = new Random();
        for (int i = 0; i < number; i++) {
            for (int j = 0; j < dimension; j++) {
                dataset[i][j] = new BigInteger(bitLength, random); // 该方法得到的整数范围为：[0, 2^bitLength - 1]，即其最大长度为bitLength
            }
            dataset[i][dimension] = BigInteger.valueOf(i); // ptr
        }

        return dataset;
    }

    public static Set<BigInteger> getKNearest(BigInteger[][] dataset, BigInteger[] point,
            int dimension, int pow, int k) {
        Set<BigInteger> result = new HashSet<>();

        // <pId, distance>
        List<SimpleEntry<BigInteger, BigInteger>> knnList = new ArrayList<>();
        for (int i = 0; i < dataset.length; i++) {
            BigInteger id = dataset[i][dimension];

            BigInteger d = BigInteger.ZERO;
            for (int j = 0; j < dimension; j++) {
                BigInteger absDis = dataset[i][j].subtract(point[j]).abs();
                if (pow <= 0) {
                    if (d.compareTo(absDis) < 0)
                        d = absDis;
                } else {
                    d = d.add(absDis.pow(pow));
                }
            }

            knnList.add(new SimpleEntry<BigInteger, BigInteger>(id, d));
        }

        knnList.sort((e1, e2) -> {
            return e1.getValue().compareTo(e2.getValue());
        });

        for (int i = 0; i < k; i++) {
            result.add(knnList.get(i).getKey());
        }

        return result;
    }

    public static BigInteger[][] loadGowallaDataset(String filePath, int number) throws IOException {
        File file = new File(filePath);
        BufferedReader reader = new BufferedReader(new FileReader(file));

        List<BigInteger[]> universalDataSet = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineSplit = line.split("\t");

            int latitude = (int) (100000 * (Float.parseFloat(lineSplit[2]) + 90)); // 取到小数点第五位，精度到米
            int longitude = (int) (100000 * (Float.parseFloat(lineSplit[3]) + 180));
            universalDataSet.add(new BigInteger[] { BigInteger.valueOf(latitude), BigInteger.valueOf(longitude),
                    new BigInteger(lineSplit[4]) });
        }

        Collections.shuffle(universalDataSet);

        List<BigInteger[]> dataset = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            dataset.add(universalDataSet.get(i));
        }

        reader.close();
        return dataset.toArray(new BigInteger[0][]);
    }
}
