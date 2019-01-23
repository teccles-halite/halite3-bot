package bots.current_bot.navigation;

import bots.current_bot.utils.GlobalRandom;
import hlt.Direction;
import hlt.Game;
import hlt.Ship;

public class RandomTiebreaker implements Navigation.DirectionTiebreaker {
    @Override
    public Direction betterDirection(Game game, Ship ship, Direction d_1, Direction d_2) {
        return GlobalRandom.getInstance().nextBoolean() ? d_1 : d_2;
    }
}
