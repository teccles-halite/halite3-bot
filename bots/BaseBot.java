package bots;

import bots.current_bot.utils.Logger;
import hlt.*;

import java.util.ArrayList;

public abstract class BaseBot {
    protected abstract ArrayList<Command> getCommands(Game game, boolean runningLocally) throws Exception;

    public void run_turn(Game game, boolean runningLocally) throws Exception {
        try {
            game.updateFrame();
            game.endTurn(getCommands(game, runningLocally));
        }
        catch(Exception e) {
            Logger.error(e);
            throw e;
        }
    }
}
