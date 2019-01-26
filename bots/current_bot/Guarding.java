package bots.current_bot;

import bots.current_bot.navigation.LowHaliteTiebreaker;
import bots.current_bot.navigation.Navigation;
import bots.current_bot.utils.CommonFunctions;
import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import hlt.*;

import java.util.Map;
import java.util.Optional;

/**
 * Guard dropoffs in the endgame (mostly thanks to SiestaGuru). Guard ships stay near a dropoff, running at enemies who
 * approach and otherwise just hanging out.
 */
public class Guarding {
    public static void getGuardingMoves(Game game, MoveRegister moveRegister, Map<EntityId, Position> guardShips) {
        for(EntityId id : guardShips.keySet()) {
            Optional<Ship> maybeShip = moveRegister.getRemainingShips().stream().filter(s -> s.id.equals(id)).findAny();
            if(!maybeShip.isPresent()) {
                Logger.info(String.format("And now Ship %s's watch is ended.", id));
                continue;
            }
            Ship ship = maybeShip.get();
            Logger.info(String.format("Looking for guard move for %s", ship));

            Position dropoff = guardShips.get(id);

            if(game.turnsRemaining() == 0) {
                Logger.info("Last turn! Better go in");
                if (game.map.calculateDistance(ship.position, dropoff) <= 1) {
                    Logger.info(String.format("Going into dropoff"));

                    Direction d = game.map.getUnsafeMoves(ship.position, dropoff).get(0);
                    moveRegister.registerMove(ship, d);
                    moveRegister.registerPossibleCollision(dropoff);
                    continue;
                }
            }

            // If there's an enemy within 2 of the dropoff, kill it.
            int nearestEnemyDistance = 10;
            Direction nearestEnemyDirection = null;
            for(Position p : CommonFunctions.getNeighbourhood(game.map, dropoff, 2)) {
                if(CommonFunctions.hasEnemyShip(game, p)) {
                    Optional<Direction> d = Navigation.navigateUnsafe(game, ship, p, moveRegister.getOccupiedPositions(), new LowHaliteTiebreaker());
                    if(d.isPresent() && game.map.calculateDistance(p, ship.position) < nearestEnemyDistance) {
                        nearestEnemyDirection = d.get();
                        nearestEnemyDistance = game.map.calculateDistance(p, ship.position);
                    }
                }
            }
            if(nearestEnemyDirection != null) {
                Logger.info(String.format("Chasing enemy"));

                moveRegister.registerMove(ship, nearestEnemyDirection);
                continue;
            }

            // Move to any adjacent spot exactly 1 from the dropoff, as long as either a) we can move off it, or b) it has
            // no ships moving to it. This includes staying still.
            int nearestDistance = 10;
            Direction bestDirection = null;
            for(Position p : CommonFunctions.getNeighbourhood(game.map, dropoff, 1)) {
                if(p.equals(dropoff)) continue;
                Optional<Direction> d = Navigation.navigateUnsafe(game, ship, p, moveRegister.getOccupiedPositions(), new LowHaliteTiebreaker());
                if(d.isPresent()) {
                    int distance = game.map.calculateDistance(p, ship.position);
                    boolean occupied = game.map.at(p).ship != null;
                    if(distance < nearestDistance || distance == nearestDistance && !occupied) {
                        bestDirection = d.get();
                        nearestDistance = game.map.calculateDistance(p, ship.position);
                    }
                }
            }
            if(bestDirection != null) {
                Logger.info(String.format("Moving towards adjacent"));

                moveRegister.registerMove(ship, bestDirection);
                continue;
            }

            // Move to the dropoff.
            if(game.map.calculateDistance(ship.position, dropoff) == 1) {
                Logger.info(String.format("Moving towards dropoff"));

                Direction d = game.map.getUnsafeMoves(ship.position, dropoff).get(0);
                moveRegister.registerMove(ship, d);
                moveRegister.registerPossibleCollision(dropoff);
                return;
            }

            Logger.info(String.format("Did not find a move for guard %s! Worth checking whether they messed things up", ship));
        }
    }
}
