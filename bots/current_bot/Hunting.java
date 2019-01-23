package bots.current_bot;

import bots.current_bot.dropoffs.DropoffPlan;
import bots.current_bot.mining.MiningFunctions;
import bots.current_bot.navigation.MapStatsKeeper;
import bots.current_bot.spawning.SpawnDecider;
import bots.current_bot.utils.BotConstants;
import bots.current_bot.utils.CommonFunctions;
import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import bots.current_bot.navigation.PreferredAxisTiebreaker;
import bots.current_bot.navigation.Navigation;
import hlt.*;

import java.util.*;

public class Hunting {

    public static void getHuntingMoves(Game game, MoveRegister moveRegister, Set<EntityId> returningShips) {
        double shipSwapCost = SpawnDecider.shipValue * (1.0 - 1.0/(double)game.players.size());
        for(Player enemy : game.players) {
            if(enemy.equals(game.me)) continue;
            Set<Ship> remainingShips = moveRegister.getRemainingShips();
            for(Ship s : enemy.ships.values()) {
                int maxRadius = Math.min(BotConstants.get().MAX_HUNTING_RADIUS(),
                        MapStatsKeeper.nearestDropoffDistance(s.position, enemy, game, Optional.<DropoffPlan>empty())-1);
                int friendDistance = 10;
                for(Position p : CommonFunctions.getNeighbourhood(game.map, s.position, 4)) {
                    if(p.equals(s.position)) continue;
                    if(CommonFunctions.hasEnemyShip(game, p)) {
                        friendDistance = game.map.calculateDistance(p, s.position);
                        break;
                    }
                }
                boolean canCatch = false;
                for(int r=1; r<=maxRadius; r++) {
                    if(canCatch) break;
                    double cost = 3 * r * MiningFunctions.shipTurnValue() + shipSwapCost;
                    if(cost > s.halite) break;
                    if(friendDistance <= r) break;
                    Logger.info(String.format("Considering hunting %s at radius %d - cost %f", s, r, cost));
                    double maxHunterHalite = s.halite - cost;
                    Map<Ship, Direction> hunterToCutoff = new HashMap<>();
                    canCatch = true;
                    for(Direction d : Direction.ALL_CARDINALS) {
                        Position offset = s.position.directionalOffset(d, game.map, r);
                        Logger.info(String.format("Seeing if we can catch at %s", offset));
                        int leastHalite = (int)(maxHunterHalite + 1);
                        Ship bestShip = null;
                        for(Position p : CommonFunctions.getNeighbourhood(game.map, offset, r)) {
                            Ship interceptor = game.map.at(p).ship;
                            if(interceptor == null || !interceptor.owner.equals(game.myId)) continue;
                            boolean canMove = interceptor.halite >= CommonFunctions.moveCost(game.map.at(p).halite);
                            boolean inRange = canMove || game.map.calculateDistance(p, offset) < r;
                            boolean eligible = remainingShips.contains(interceptor) || (!returningShips.contains(interceptor.id) && !canMove);

                            if(inRange && eligible && !hunterToCutoff.containsKey(interceptor) && interceptor.halite < leastHalite) {
                                bestShip = interceptor;
                                leastHalite= interceptor.halite;
                            }
                        }
                        if(bestShip != null) {
                            Logger.info(String.format("Can cut off with %s", bestShip));
                            hunterToCutoff.put(bestShip, d);
                        }
                        else {
                            Logger.info("Can't catch");
                            canCatch = false;
                            break;
                        }
                    }
                    if(canCatch) {
                        Logger.info(String.format("Can catch %s!", s));
                        for(Ship interceptor : hunterToCutoff.keySet()) {
                            if(!remainingShips.contains(interceptor)) {
                                Logger.info(String.format("Interceptor %s can't move this turn", interceptor));
                                continue;
                            }
                            Direction d = hunterToCutoff.get(interceptor);
                            Optional<Direction> bestDir = Navigation.navigate(
                                    game, interceptor, s.position, moveRegister.getOccupiedPositions(),
                                    new PreferredAxisTiebreaker(d));

                            if(bestDir.isPresent()) {
                                boolean shouldMove = true;
                                if(bestDir.get().invertDirection().equals(d)) {
                                    Logger.info("Moving along axis - checking.");

                                    Position twoAway = interceptor.position.directionalOffset(bestDir.get(), game.map, 2);
                                    if(game.map.calculateDistance(twoAway, s.position) == game.map.calculateDistance(interceptor.position, s.position)) {
                                        Logger.info("Would bring onto axis.");
                                        if(game.map.calculateDistance(twoAway, s.position) > 1) {
                                            Logger.info("Not moving in - would let them escape.");
                                            shouldMove = false;
                                        }
                                        else {
                                            Logger.info("Adjacent - only go in if surrounded");
                                            for(Position nbr : CommonFunctions.getNeighbourhood(game.map, s.position, 1)) {
                                                if(nbr.equals(s.position)) continue;
                                                Ship nbrShip = game.map.at(nbr).ship;
                                                if(nbrShip == null || !nbrShip.owner.equals(game.myId)) shouldMove = false;
                                                else if(nbrShip.halite < interceptor.halite && nbrShip.halite >= CommonFunctions.moveCost(game.map.at(nbr).halite)) shouldMove = false;
                                            }
                                        }
                                    }
                                }
                                if(shouldMove) {
                                    Logger.info("Found an intercept move");
                                    moveRegister.registerMove(interceptor, bestDir.get());
                                    continue;
                                }
                            }
                            bestDir = Navigation.navigate(
                                    game, interceptor, interceptor.position, moveRegister.getOccupiedPositions(),
                                    new PreferredAxisTiebreaker(d));
                            if (bestDir.isPresent()) {
                                Logger.info("Stayed still, as couldn't intercept");
                                moveRegister.registerMove(interceptor, bestDir.get());
                            }
                        }
                        remainingShips = moveRegister.getRemainingShips();
                    }
                }
            }
        }
    }
}
