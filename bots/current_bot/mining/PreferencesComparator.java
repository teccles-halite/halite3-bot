package bots.current_bot.mining;

import hlt.Game;
import hlt.Position;
import hlt.Ship;

import java.util.Comparator;
import java.util.Map;

public class PreferencesComparator implements Comparator<Ship> {
    private final Map<Ship, Position> inverseClaims;
    private final Game game;

    public PreferencesComparator(Game game, Map<Ship, Position> inverseClaims) {
        this.inverseClaims = inverseClaims;
        this.game = game;
    }

    @Override
    public int compare(Ship ship_1, Ship ship_2) {
        Position claim_1 = inverseClaims.get(ship_1);
        Position claim_2 = inverseClaims.get(ship_2);
        if(claim_1 == null && claim_2 == null) return 0;
        if(claim_1 == null) return 1;
        if(claim_2 == null) return -2;
        Integer d_1 =  game.map.calculateDistance(ship_1.position, claim_1);
        Integer d_2 =  game.map.calculateDistance(ship_2.position, claim_2);
        return d_1.compareTo(d_2);
    }
}
