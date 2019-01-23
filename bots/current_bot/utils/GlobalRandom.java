package bots.current_bot.utils;

import java.util.Random;

public class GlobalRandom {
    private static GlobalRandom ourInstance = new GlobalRandom();

    public static Random getInstance() {
        return ourInstance.random;
    }
    public final Random random;

    private GlobalRandom() {
        random = new Random(1);
    }
}
