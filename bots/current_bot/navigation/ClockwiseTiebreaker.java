package bots.current_bot.navigation;

import hlt.Direction;
import hlt.Game;
import hlt.Ship;

public class ClockwiseTiebreaker implements Navigation.DirectionTiebreaker {
    @Override
    public Direction betterDirection(Game game, Ship ship, Direction d_1, Direction d_2) {
        if(d_1 == Direction.STILL) return d_2;
        if(d_2 == Direction.STILL) return d_1;
        if(d_1 == Direction.NORTH) return (d_2 == Direction.EAST) ? d_1 : d_2;
        if(d_1 == Direction.SOUTH) return (d_2 == Direction.WEST) ? d_1 : d_2;
        if(d_1 == Direction.EAST) return (d_2 == Direction.SOUTH) ? d_1 : d_2;
        if(d_1 == Direction.WEST) return (d_2 == Direction.NORTH) ? d_1 : d_2;
        return d_1;
    }
}
