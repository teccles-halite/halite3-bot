// This Java API uses camelCase instead of the snake_case as documented in the API docs.
//     Otherwise the names of methods are consistent.

import bots.BaseBot;
import bots.current_bot.Bot;
import bots.current_bot.utils.Logger;
import hlt.*;

import java.util.HashMap;
import java.util.Map;

public class MyBot {
    public static void main(final String[] args) throws Exception {
        // At this point "game" variable is populated with initial map data.
        // This is a good place to do computationally expensive start-up pre-processing.
        // As soon as you call "ready" function below, the 2 second per turn timer will start.

        boolean local = false;
        Logger.level = Logger.NONE;
        Logger.log_time = false;
        Map<String, String> configOverrides = new HashMap<>();
        if(args.length > 0 && args[0].equals("-l")) {
            local = true;
            for(int i = 1; i<args.length;) {
                if(args[i].equals("-v")) {
                    Logger.level = Logger.INFO;
                    Logger.log_time = true;

                    i++;
                }
                else {
                    configOverrides.put(args[i], args[i + 1]);
                    i+=2;
                }
            }
        }

        boolean log = Logger.level != Logger.NONE;
        Game game = new Game(local, log);
        game.ready("CurrentBot");
        if(log) {
            Log.log("Successfully created bot! My Player ID is " + game.myId + ".");
        }


        BaseBot bot = new Bot(game, configOverrides);

        while(true) {
            bot.run_turn(game, local);
        }
    }
}
