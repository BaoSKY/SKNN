package cn.ac.iscas.utils;

import java.util.HashMap;
import java.util.Map;

public class RunningTimeCounter {

    public final static String COMMUNICATION_TIME = "COMMUNICATION_TIME";

    private static Map<String, Long> preTimeRecorder = new HashMap<>();
    private static Map<String, Long> runningTimeRecorder = new HashMap<>();

    public static void startRecord(String label) {
        preTimeRecorder.put(label, System.currentTimeMillis());
        runningTimeRecorder.put(label, 0L);
    }

    public static void updatePreviousTime(String label) {
        preTimeRecorder.replace(label, System.currentTimeMillis());
    }

    public static void accumulate(String label) {
        Long nowTime = System.currentTimeMillis();
        Long preTime = preTimeRecorder.get(label);

        Long preTotalTime = runningTimeRecorder.get(label);
        runningTimeRecorder.replace(label, preTotalTime + (nowTime - preTime));
        // preTimeRecorder.replace(label, nowTime);
    }

    public static long get(String label) {
        return runningTimeRecorder.get(label);
    }

    public static void showRunningTime() {
        System.out.println("The running time is:");
        for (Map.Entry<String, Long> entry : runningTimeRecorder.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue() + " ms.");
        }
    }
}
