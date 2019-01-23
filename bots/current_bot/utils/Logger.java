package bots.current_bot.utils;

import hlt.Log;

public class Logger {

    public static final int NONE = 4;
    public static int DEBUG = 0;
    public static int INFO = 1;
    public static int WARN = 2;
    public static int ERROR = 3;
    public static int level;
    public static boolean log_time;
    public static long startTime;


    private static void log(String s, Integer log_level) {
        if(log_level >= level) {
            Log.log(s);
        }
    }

    public static void error(Exception e) {
        if(Logger.level <= Logger.ERROR) {
            String message = e.getMessage();
            StackTraceElement[] trace = e.getStackTrace();
            for (StackTraceElement traceElement : trace)
                message = message + ("\n\tat " + traceElement);
            message += "\n";

            log("ERROR: " + message, ERROR);
        }
    }


    public static void warn(String s) {
        log("WARN:  " + s, WARN);
    }


    public static void info(String s) {
        log("INFO:  " + s, INFO);
    }


    public static void debug(String s) {
        log("DEBUG: " + s, DEBUG);
    }

    public static void logtime(String s) {
        if(log_time) {
            long time = (System.currentTimeMillis() - startTime);
            Log.log(s + ": time " + time);
        }
    }
}
