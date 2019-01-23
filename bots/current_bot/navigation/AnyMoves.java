package bots.current_bot.navigation;

import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import hlt.Direction;
import hlt.Game;
import hlt.Ship;

public class AnyMoves {
    public static void getMoveCommands(Game game, MoveRegister moveRegister) throws Exception {
        for(Ship ship : moveRegister.getRemainingShips()) {
            Logger.info(String.format("Ship %s moving anywhere", ship));
            Direction d = Navigation.moveAnywhere(game, ship, moveRegister.getOccupiedPositions(), moveRegister.mustMove(ship), new RandomTiebreaker());
            moveRegister.registerMove(ship, d);
        }
    }
}
