package bots.current_bot.navigation;

import hlt.Direction;
import hlt.Game;
import hlt.Ship;

public class PreferredAxisTiebreaker implements Navigation.DirectionTiebreaker {
    private final Direction preferred;

    public PreferredAxisTiebreaker(Direction d) {
        this.preferred = d;
    }

    private boolean eastOrWest(Direction d) {
        return d == Direction.EAST || d == Direction.WEST;
    }

    @Override
    public Direction betterDirection(Game game, Ship ship, Direction d_1, Direction d_2) {
        return (eastOrWest(d_1) ^ eastOrWest(preferred)) ? d_1 : d_2;
    }
}
