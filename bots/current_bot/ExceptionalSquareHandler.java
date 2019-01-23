package bots.current_bot;

import bots.current_bot.navigation.LowHaliteTiebreaker;
import bots.current_bot.navigation.MapStatsKeeper;
import bots.current_bot.navigation.Navigation;
import bots.current_bot.utils.BotConstants;
import bots.current_bot.utils.CommonFunctions;
import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import hlt.*;

import java.util.*;

final class FillState {
    public final Position position;
    public final int haliteNeeded;

    public FillState(Position pos, int halite) {
        position = pos;
        haliteNeeded = halite;
    }
}

public class ExceptionalSquareHandler {
    public static void getExceptionalSquaresMoves(Game game, MoveRegister moveRegister, int haliteSum, Set<EntityId> returningShips) {
        double haliteThreshold = BotConstants.get().EXCEPTIONAL_SQUARE_PROPORTION() * haliteSum / (game.map.width*game.map.height);
        Logger.info(String.format("Halite threshold %f", haliteThreshold));
        for(int x=0; x < game.map.height; x++) {
            for(int y=0; y<game.map.width; y++) {
                if(game.map.cells[x][y].halite > haliteThreshold && MapStatsKeeper.nearestEnemy(game, x, y) < BotConstants.get().EXCEPTIONAL_ENEMY_DISTANCE()) {
                    Logger.info(String.format("(%d, %d) is an exceptional square!", x, y));
                }
                else continue;

                boolean won = false;
                boolean lost = false;
                Position exceptionalPos = Position.getPosition(x, y);
                List<FillState> layer = new LinkedList<>();
                layer.add(new FillState(exceptionalPos, 0));
                Set<Position> foundPositions = new HashSet<>();
                List<Ship> ourShips = new LinkedList<>();
                List<Ship> theirShips = new LinkedList<>();
                int turns = 0;

                while(!won && !lost && !layer.isEmpty()) {
                    for(FillState state : layer) {
                        foundPositions.add(state.position);
                    }
                    Map<Position, Integer> minHalitePerPosition = new HashMap<>();

                    List<Ship> ourNextShips = new LinkedList<>();
                    List<Ship> theirNextShips = new LinkedList<>();
                    for(FillState state : layer) {
                        // Check if there's a ship satisfying the state
                        Ship ship = game.map.at(state.position).ship;
                        if(ship != null && ship.halite >= state.haliteNeeded ) {
                            Logger.info(String.format("Ship %s can reach the square in %d turns", ship, turns));
                            if(ship.owner.equals(game.myId) && !returningShips.contains(ship.id)) ourNextShips.add(ship);
                            else if(ship.halite < BotConstants.get().ASSUMED_RETURNING_HALITE()) theirNextShips.add(ship);
                        }
                        // Add nearby states
                        for(Position nbr : CommonFunctions.getNeighbourhood(game.map, state.position, 1)) {
                            if(foundPositions.contains(nbr)) continue;
                            int halite = state.haliteNeeded + CommonFunctions.moveCost(game.map.at(nbr).halite);
                            if(halite < minHalitePerPosition.getOrDefault(nbr, 10000)) {
                                minHalitePerPosition.put(nbr, halite);
                            }
                        }
                    }
                    List<FillState> nextLayer = new LinkedList<>();
                    for(Position nbr : minHalitePerPosition.keySet()) {
                        nextLayer.add(new FillState(nbr, minHalitePerPosition.get(nbr)));
                    }

                    layer = nextLayer;
                    if(ourShips.size() + ourNextShips.size() < theirShips.size()) lost = true;
                    if(theirShips.size() + theirNextShips.size() < ourShips.size()) won = true;
                    if(!lost && !won) {
                        ourShips.addAll(ourNextShips);
                        theirShips.addAll(theirNextShips);
                    }
                    turns++;
                }
                Logger.info(String.format("Our ships %d, their ships %d", ourShips.size(), theirShips.size()));
                if(won) {
                    Logger.info("We win.");
                    Set<Ship> remaining = moveRegister.getRemainingShips();
                    ourShips.sort((s_1, s_2) -> {
                        Integer d_1 = game.map.calculateDistance(s_1.position, exceptionalPos);
                        Integer d_2 = game.map.calculateDistance(s_2.position, exceptionalPos);
                        if(s_1 != s_2) return d_1.compareTo(d_2);
                        return Integer.compare(s_1.halite, s_2.halite);
                    });
                    for(Ship ship : ourShips) {
                        if(!remaining.contains(ship)) continue;

                        Logger.info(String.format("Ship %s navigating towards exceptional square", ship));
                        Optional<Direction> dir = Navigation.navigateLowHaliteDefaultSafety(
                                game, ship, exceptionalPos, moveRegister.getOccupiedPositions());
                        Logger.info(String.format("No direction - distance %d", game.map.calculateDistance(ship.position, exceptionalPos) ));
                        if(!dir.isPresent() && game.map.calculateDistance(ship.position, exceptionalPos) <= 1) {
                            Logger.info("No direction - trying unsafe");
                            dir = Navigation.navigateUnsafe(game, ship, exceptionalPos, moveRegister.getOccupiedPositions(), new LowHaliteTiebreaker());
                        }
                        if(dir.isPresent()) {
                            moveRegister.registerMove(ship, dir.get());
                        }
                        else {
                            Logger.info("Can't get close; trying to stay still");
                            dir = Navigation.navigateLowHaliteDefaultSafety(game, ship, ship.position, moveRegister.getOccupiedPositions());
                            if(!dir.isPresent() && game.map.calculateDistance(ship.position, exceptionalPos) <= 1) {
                                Logger.info("No direction - trying unsafe");
                                dir = Navigation.navigateUnsafe(game, ship, exceptionalPos, moveRegister.getOccupiedPositions(), new LowHaliteTiebreaker());
                            }
                            if(dir.isPresent()) {
                                moveRegister.registerMove(ship, dir.get());
                            }
                        }
                    }
                }
                else if(lost) {
                    Logger.info("We lost.");
                }
                else Logger.info("Drawn");
            }
        }
    }
}
