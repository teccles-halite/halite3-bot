package bots.current_bot.navigation;

import hlt.Direction;
import hlt.Game;
import hlt.Ship;

public class LowHaliteTiebreaker implements Navigation.DirectionTiebreaker {
    @Override
    public Direction betterDirection(Game game, Ship ship, Direction d_1, Direction d_2) {
        Integer halite_1 = game.map.at(ship.position.directionalOffset(d_1, game.map)).halite;
        Integer halite_2 = game.map.at(ship.position.directionalOffset(d_2, game.map)).halite;
        return halite_1 < halite_2 ? d_1 : d_2;
    }
}
