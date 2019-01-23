package hlt;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

public class Flogger {
    private final FileWriter file;

    private static Flogger INSTANCE;
    private static ArrayList<String> LOG_BUFFER = new ArrayList<>();
    public static boolean shouldLog = false;

    static {
        Runtime.getRuntime().addShutdownHook(new AtExit());
    }

    private static class AtExit extends Thread {
        @Override
        public void run() {
            if (INSTANCE != null) {
                return;
            }

            final long now_in_nanos = System.nanoTime();
            final String filename = "bot-unknown-" + now_in_nanos + ".log";
            try (final FileWriter writer = new FileWriter(filename)) {
                for (final String message : LOG_BUFFER) {
                    writer.append(message).append('\n');
                }
            } catch (final IOException e) {
                // Nothing much we can do here.
            }
        }
    }

    private Flogger(final FileWriter f) {
        file = f;
    }

    static void open(final int botId) {
        shouldLog = true;
        if (INSTANCE != null) {
            throw new IllegalStateException();
        }

        String filename = "bot-" + botId + System.currentTimeMillis() +  ".flog";
        filename = "flogs/" + filename;
        final FileWriter writer;
        try {
            writer = new FileWriter(filename);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        INSTANCE = new Flogger(writer);

        LOG_BUFFER.add(0, "[");
        try {
            for (final String message : LOG_BUFFER) {
                writer.append(message).append('\n');
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        LOG_BUFFER.clear();
    }

    public static void log(int time, Position p, String message, Optional<String> color) {
        if(!shouldLog) return;
        String log = String.format("{\"t\": %d, \"x\": %d, \"y\": %d, \"msg\": \"%s\" },", time, p.x, p.y, message);
        if(color.isPresent()) {
            log = String.format("{\"t\": %d, \"x\": %d, \"y\": %d, \"msg\": \"%s\", \"color\":\"%s\" },", time, p.x, p.y, message, color.get());
        }
        if (INSTANCE == null) {
            LOG_BUFFER.add(log);
            return;
        }

        try {
            INSTANCE.file.append(log).append('\n').flush();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
