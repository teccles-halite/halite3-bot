package bots.current_bot.navigation;

import hlt.Direction;
import hlt.Game;
import hlt.Position;
import hlt.Ship;

public class LongerAxisTiebreaker implements Navigation.DirectionTiebreaker {
    private boolean xBetter;
    public LongerAxisTiebreaker(Game game, Ship ship, Position pos) {
        final int dx = Math.abs(ship.position.x - pos.x);
        final int dy = Math.abs(ship.position.y - pos.y);

        final int toroidal_dx = Math.min(dx, game.map.width - dx);
        final int toroidal_dy = Math.min(dy, game.map.height - dy);

        xBetter = toroidal_dx > toroidal_dy;
    }

    @Override
    public Direction betterDirection(Game game, Ship ship, Direction d_1, Direction d_2) {
        boolean d_1_x = (d_1 == Direction.EAST || d_1 == Direction.WEST);
        return xBetter ^ d_1_x ? d_2 : d_1;
    }
}
