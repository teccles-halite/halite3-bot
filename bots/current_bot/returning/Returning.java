package bots.current_bot.returning;

import bots.current_bot.dropoffs.DropoffPlan;
import bots.current_bot.mining.MiningFunctions;
import bots.current_bot.navigation.MapStatsKeeper;
import bots.current_bot.spawning.SpawnDecider;
import bots.current_bot.utils.BotConstants;
import bots.current_bot.utils.CommonFunctions;
import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import bots.current_bot.navigation.Navigation;
import hlt.*;

import java.util.*;


final class ReturnerComparator implements Comparator<Ship>
{
    private final Optional<DropoffPlan> plan;
    private SafeRouteMap routeMap;
    private Game game;
    private Player me;

    public ReturnerComparator(Game game, Player me, SafeRouteMap routeMap, Optional<DropoffPlan> plan) {
        this.routeMap = routeMap;
        this.game = game;
        this.me = me;
        this.plan = plan;
    }

    public int compare(Ship s1, Ship s2)
    {
        Integer s1_safe_distance = routeMap.safeDistance(s1.position).orElse(1000);
        Integer s2_safe_distance = routeMap.safeDistance(s2.position).orElse(1000);
        if(!Objects.equals(s1_safe_distance, s2_safe_distance)){
            return s1_safe_distance.compareTo(s2_safe_distance);
        }
        Integer s1_distance = MapStatsKeeper.nearestDropoffDistance(s1.position, me, game, plan);
        Integer s2_distance = MapStatsKeeper.nearestDropoffDistance(s2.position, me, game, plan);
        return s1_distance.compareTo(s2_distance);
    }
}


final class SafeRouteMap {
    private final Dictionary<Position, Integer> distances;
    private final Dictionary<Position, Integer> haliteOnRoute;

    public SafeRouteMap(Game game, Optional<DropoffPlan> plan, int shipyardQueueLength, boolean avoidAdjacent) {
        distances = new Hashtable<>();
        haliteOnRoute = new Hashtable<>();
        Map<Position, List<Integer>> layer = new HashMap<>();
        for(Position d : CommonFunctions.getDropoffPositions(game.me, Optional.<DropoffPlan>empty())) {
            // Deal with the shipyard separately, because we want to account for any queue there.
            if(d.equals(game.me.shipyard.position)) continue;
            layer.put(d, new ArrayList<>());
        }
        if(plan.isPresent() && plan.get().underway) {
            Logger.info(String.format("Dropoff plan underway! Include destination %s for returners.", plan.get().destination));
            layer.put(plan.get().destination, new ArrayList<>());
        }

        Set<Position> found = new HashSet<>();
        found.addAll(layer.keySet());
        int distance = 0;
        while(!layer.isEmpty() || distance <= shipyardQueueLength) {
            Map<Position, List<Integer>> nextLayerMap = new HashMap<>();
            // Sneak in the shipyard
            if(distance == shipyardQueueLength) {
                layer.put(game.me.shipyard.position, new ArrayList<>());
                found.add(game.me.shipyard.position);
            }

            for(Map.Entry<Position, List<Integer>> pair : layer.entrySet()) {
                Position pos = pair.getKey();
                distances.put(pos, distance);
                List<Integer> parentHalites = pair.getValue();
                Integer lowestParentHalite = parentHalites.isEmpty() ? 0 : Collections.min(parentHalites);
                Integer halite = lowestParentHalite + game.map.at(pos).halite;
                haliteOnRoute.put(pos, halite);

                for(Position n : pos.getSurroundingCardinals(game.map)) {
                    if(found.contains(n)) {
                        List<Integer> nbrParentHalites = nextLayerMap.get(n);
                        if(nbrParentHalites != null){
                            nbrParentHalites.add(halite);
                        }
                        continue;
                    }
                    found.add(n);

                    boolean enemyAdjacent = false;
                    if(CommonFunctions.hasEnemyShip(game, n)) {
                        // Ignore ships on dropoffs.
                        if(!(game.map.at(n).hasStructure() && game.map.at(n).structure.owner.equals(game.myId))) {
                            enemyAdjacent = true;
                        }
                    }
                    if(avoidAdjacent) {
                        for (Position n_2 : n.getSurroundingCardinals(game.map)) {
                            // Ignore ships on dropoffs.
                            if (game.map.at(n_2).hasStructure() && game.map.at(n_2).structure.owner.equals(game.myId))
                                continue;
                            if (CommonFunctions.hasEnemyShip(game, n_2)) {
                                enemyAdjacent = true;
                            }
                        }
                    }
                    if(!enemyAdjacent) {
                        List<Integer> parents = new ArrayList<>();
                        parents.add(halite);
                        nextLayerMap.put(n, parents);
                    }
                }
            }
            layer = nextLayerMap;
            distance++;
        }
    }

    public Optional<Integer> safeDistance(Position position) {
        Integer d = distances.get(position);
        return d == null ? Optional.<Integer>empty() : Optional.of(d);
    }

    public Optional<Integer> safeDistanceHalite(Position position) {
        Integer d = haliteOnRoute.get(position);
        return d == null ? Optional.<Integer>empty() : Optional.of(d);
    }
}


class SquareScore {
    public Optional<Integer> safeDistance;
    private final Integer unsafeDistance;
    private final Integer halite;
    private final Boolean freeOfShips;
    private final Boolean isMove;
    public final Direction direction;
    private Optional<Integer> safeHalite;

    public SquareScore(Ship ship, Game game, Direction direction, SafeRouteMap routeMap, SafeRouteMap unsafeRouteMap, Optional<DropoffPlan> plan){
        Position position = ship.position.directionalOffset(direction, game.map);
        safeDistance = routeMap.safeDistance(position);
        safeHalite = routeMap.safeDistanceHalite(position);
        Optional<Integer> noObstacleDistance = unsafeRouteMap.safeDistance(position);
        // Dodgy late-competition hack here. We allow an unsafe route, if its much shorter than the safe one.
        if(noObstacleDistance.isPresent() && noObstacleDistance.get() * BotConstants.get().RETURNING_UNSAFE_PENALTY() < safeDistance.orElse(1000)) {
            safeDistance = Optional.of(noObstacleDistance.get() * BotConstants.get().RETURNING_UNSAFE_PENALTY());
            safeHalite = unsafeRouteMap.safeDistanceHalite(position);
        }

        unsafeDistance = MapStatsKeeper.nearestDropoffDistance(position, game.me, game, plan);
        halite = game.map.at(position).halite;
        freeOfShips = !CommonFunctions.hasEnemyShip(game, position) || CommonFunctions.hasFriendlyStructure(game, position);
        isMove = (Direction.STILL != direction);
        this.direction = direction;
    }

    public String toString() {
        Integer actualSafe = safeDistance.orElse(-1);
        return String.format("Direction %s, safe %d, unsafe %d, halite %d, free %s, move %s",
                direction, actualSafe, unsafeDistance, halite, freeOfShips, isMove);
    }

    public boolean betterThan(SquareScore other, Boolean queueing, boolean scared) {
        // Avoid other ships being present.
        if(!scared && freeOfShips != other.freeOfShips) return freeOfShips;
        // Logger.debug(String.format("Comparing %s to %s", direction, other.direction));

        // Scared means that it's the endgame, and we need to get a shift on.
        if(scared) {
            // If scared, we care about absolute distance most of all.
            if(unsafeDistance < other.unsafeDistance) return true;
            if(other.unsafeDistance < unsafeDistance) return false;

            Integer thisSafety = safeDistance.orElse(1000);
            Integer otherSafety = other.safeDistance.orElse(1000);
            // Next we care about the safe distance, because safe routes are still better.
            if(thisSafety < otherSafety) return true;
            if(otherSafety < thisSafety) return false;

            // Moving is better than not moving.
            if(isMove != other.isMove) return isMove;

            int thisSafetyHalite = safeHalite.orElse(10000);
            int otherSaftetyHalite = other.safeHalite.orElse(10000);

            // Burn less where possible.
            if(thisSafetyHalite < otherSaftetyHalite) return true;
            if(otherSaftetyHalite < thisSafetyHalite) return false;

            return halite < other.halite;
        }
        else if(queueing) {
            // If queueing, things are simple. We want least safety distance, then least distance, then to move,
            // then low safety halite, the low halite.
            Integer thisSafety = safeDistance.orElse(1000);
            Integer otherSafety = other.safeDistance.orElse(1000);
            if(thisSafety < otherSafety) return true;
            if(otherSafety < thisSafety) return false;

            if(unsafeDistance < other.unsafeDistance) return true;
            if(other.unsafeDistance < unsafeDistance) return false;

            if(isMove != other.isMove) return isMove;

            int thisSafetyHalite = safeHalite.orElse(10000);
            int otherSaftetyHalite = other.safeHalite.orElse(10000);

            if(thisSafetyHalite < otherSaftetyHalite) return true;
            if(otherSaftetyHalite < thisSafetyHalite) return false;

            return halite < other.halite;
        }
        else {
            // When not queueing, the key thing is to move *somewhere*. Otherwise we tend to get stuck in the middle of
            // enemies, too scared to move. Within that, safety, distance and low halite are again the priorities.
            if(isMove != other.isMove) return isMove;

            Integer thisSafety = safeDistance.orElse(1000);
            Integer otherSafety = other.safeDistance.orElse(1000);
            if(thisSafety < otherSafety) return true;
            if(otherSafety < thisSafety) return false;

            if(unsafeDistance < other.unsafeDistance) return true;
            if(other.unsafeDistance < unsafeDistance) return false;

            int thisSafetyHalite = safeHalite.orElse(10000);
            int otherSaftetyHalite = other.safeHalite.orElse(10000);

            if(thisSafetyHalite < otherSaftetyHalite) return true;
            if(otherSaftetyHalite < thisSafetyHalite) return false;

            return halite < other.halite;
        }
    }
}

public class Returning {
    public static double arrayMin(double[][] array) {
        double min = array[0][0];
        for(int i = 0; i < array.length; i++) {
            for (int j=0; j<array[0].length; j++) {
                if(array[i][j] < min) min = array[i][j];
            }
        }
        return min;
    }

    public static Map<Integer, List<Double>> fixReturningShips(
            Game game,
            Collection<Ship> ships,
            Set<EntityId> returningShipsIds,
            Set<EntityId> rushingShipIds,
            Map<EntityId, Position> guardShips,
            Optional<DropoffPlan> plan) {
        // Decide which ships need to return

        // We record when we expect each ship to bring some halite home, for use in dropoff planning.
        Map<Integer, List<Double>> haliteAtTime = new HashMap<>();

        // Make a map of all the routes home which avoid enemy ships.
        SafeRouteMap safeRouteMap = new SafeRouteMap(game, plan, 0, true);

        for (Ship ship : ships) {
            Logger.info(String.format("Consider returning for %s", ship));

            Position nearestDropoff = MapStatsKeeper.nearestDropoff(ship.position, game.me, game, plan);
            int dropoffDistance = MapStatsKeeper.nearestDropoffDistance(ship.position, game.me, game, plan);
            Optional<Integer> safeTimeHome = safeRouteMap.safeDistance(ship.position);
            boolean rushing;
            if(safeTimeHome.isPresent()) rushing = game.turnsRemaining() < safeTimeHome.get() * BotConstants.get().SAFE_RUSH_FACTOR();
            else rushing = game.turnsRemaining() < BotConstants.get().UNSAFE_RUSH_FACTOR()*dropoffDistance;
            Logger.info(String.format("Rushing? %s", rushing));

            MapCell cell = game.map.at(ship.position);

            boolean onDropoff = cell.hasStructure() && cell.structure.owner.equals(game.me.id);

            if(guardShips.containsKey(ship.id) && ship.halite > BotConstants.get().ABANDON_GUARD_DUTY()){
                Logger.info(String.format("%s has ended up with lots of halite - abandoning its post", ship));
                guardShips.remove(ship.id);
            }

            if(guardShips.containsKey(ship.id)) {
                // Guard ships don't return home.
                Logger.info(String.format("Ship %s is guarding", ship));
            }
            else if (rushing && !onDropoff) {
                // Time to come home - the game is nearly over. Should probably check for 0 halite here!
                Logger.info(String.format("Ship %s rushing home.", ship));
                returningShipsIds.add(ship.id);
                rushingShipIds.add(ship.id);
            }
            else if (returningShipsIds.contains(ship.id)) {
                // Returners continue returning, unless they have reached a dropoff.
                Logger.info("Ship is currently returning.");
                if(onDropoff) {
                    Logger.info(String.format("Ship %s has found dropoff", ship));
                    returningShipsIds.remove(ship.id);
                    if(rushingShipIds.contains(ship.id)) {
                        guardShips.put(ship.id, nearestDropoff);
                        rushingShipIds.remove(ship.id);
                    }
                }
            }
            else if (ship.halite >= Constants.MAX_HALITE*BotConstants.get().HALITE_TO_ALWAYS_RETURN()) {
                // Ships over a halite threshold return.
                Logger.info("Full - return");
                returningShipsIds.add(ship.id);
            }
            else if(MiningFunctions.turnUpdated == game.turnNumber) {
                // If we've scored mining this turn, compare the gains we get from continuing to mine with the gains
                // from coming home.
                Double bestScore = arrayMin(MiningFunctions.miningScores.get(ship));
                if (dropoffDistance > 0 && ship.halite > 0) {
                    Logger.info(String.format(
                            "Ship %s considering banking - turns to fill and home %f, distance %d, halite %d",
                            ship, bestScore, dropoffDistance, ship.halite
                    ));
                    if (ship.halite / dropoffDistance > BotConstants.get().RETURN_RATIO() * Constants.MAX_HALITE / bestScore) {
                        Logger.info("Banking!");
                        returningShipsIds.add(ship.id);
                    }
                }
            }

            if(returningShipsIds.contains(ship.id)) {
                // Track the time we expect to get this halite.
                int expectedReturnTime = (int)(dropoffDistance*BotConstants.get().RETURN_SPEED());
                List<Double> haliteAtReturnTime = haliteAtTime.getOrDefault(expectedReturnTime, new ArrayList<>());
                haliteAtReturnTime.add(ship.halite * BotConstants.get().ASSUMED_RETURNING_PROPORTION());
                haliteAtTime.put(expectedReturnTime, haliteAtReturnTime);
            }
            else {
                Logger.info(String.format("No return condition fulfilled."));
            }
        }
        return haliteAtTime;
    }

    public static void getReturningMoves(Game game,
                                         MoveRegister moveRegister,
                                         Set<EntityId> returningShipIds,
                                         Set<EntityId> rushingShips,
                                         Optional<DropoffPlan> plan,
                                         int exceptionalHaliteNeeded) {
        // Get the moves for all returning ships.

        // Make map for returning routes avoiding adjacency to enemies.
        SafeRouteMap safeRouteMap = new SafeRouteMap(game, plan, 0, true);
        // Make map for returning routes avoiding squares with enemies.
        SafeRouteMap unsafeRouteMap = new SafeRouteMap(game, plan, 0, false);

        Logger.info(String.format("returning ships %s", returningShipIds));

        ArrayList<Ship> sortedReturningShips = new ArrayList<>();
        for(Ship ship : moveRegister.getRemainingShips()) {
            Logger.info(String.format("Considering %s", ship));

            if(returningShipIds.contains(ship.id)) {
                sortedReturningShips.add(ship);
            }
        }

        // Sort returning ships by how close they are to a dropoff.
        sortedReturningShips.sort(new ReturnerComparator(game, game.me, safeRouteMap, plan));
        Logger.info(String.format("Getting moves for %d returning ships", sortedReturningShips.size()));

        // Decide whether we are in endgame mode.
        boolean suicideOnDropoff = false;
        if(returningShipIds.size() > game.turnsRemaining() * BotConstants.get().UNSAFE_RUSH_FACTOR()) {
            Logger.warn("Suicide on dropoff if necessary!");
            suicideOnDropoff = true;
        }

        // Get moves for each ship.
        for(Ship ship : sortedReturningShips) {
            getReturningMove(
                    game, ship, moveRegister, safeRouteMap, unsafeRouteMap, suicideOnDropoff, !rushingShips.isEmpty(), plan, exceptionalHaliteNeeded);
        }
    }

    private static void getReturningMove(
            Game game, Ship ship, MoveRegister moveRegister, SafeRouteMap safeRouteMap, SafeRouteMap unsafeRouteMap, boolean suicideOnDropoff, boolean rushOn,
            Optional<DropoffPlan> plan, int exceptionalHaliteNeeded) {
        Logger.info(String.format("Get returner direction for %s", ship));
        Integer currentSquareHalite = game.map.at(ship.position).halite;

        Position dropoff = MapStatsKeeper.nearestDropoff(ship.position, game.me, game, plan);
        int dropoffDistance = MapStatsKeeper.nearestDropoffDistance(ship.position, game.me, game, plan);
        Logger.info(String.format("Distance %d from nearest dropoff.", dropoffDistance));

        if(dropoffDistance == 0) {
            // On a dropoff.
            Logger.info(String.format("Ship %s is on dropoff, but marked returning.", ship));
            if(suicideOnDropoff) {
                // Waiting for death
                Logger.info(String.format("Ship %s staying on dropoff.", ship));
                moveRegister.registerMove(ship, Direction.STILL);
                moveRegister.registerPossibleCollision(dropoff);
                return;
            }
            else if(game.map.at(ship.position).hasStructure()) {
                throw new IllegalStateException(String.format("Ship %s is on dropoff, and marked as returning.", ship));
            }
            else if(!plan.get().destination.equals(ship.position)) {
                throw new IllegalStateException(String.format("Ship %s is at safe distance 0, marked as returning, but this is not the dropoff plan.", ship));
            }
            else {
                // This is a planned dropoff! Ship should create dropoff if possible.
                if(game.me.halite + ship.halite + game.map.at(ship.position).halite >= Constants.DROPOFF_COST + exceptionalHaliteNeeded) {
                    Logger.info(String.format("Ship %s making dropoff!", ship));

                    moveRegister.registerDropoff(ship);
                    plan.get().haliteNeeded = Constants.DROPOFF_COST - ship.halite - game.map.at(ship.position).halite;
                    return;
                }
                else {
                    Logger.info(String.format("Ship %s arrived to create dropoff, but doesn't have enough halite", ship));
                    moveRegister.registerMove(ship, Direction.STILL);
                    return;
                }
            }
        }


        Double gainByStaying = (double) (CommonFunctions.mineAmount(ship, currentSquareHalite) +
                CommonFunctions.moveCost(currentSquareHalite) -
                CommonFunctions.moveCost(currentSquareHalite - CommonFunctions.mineAmount(ship, currentSquareHalite)));

        double turnValueAfterReturn = Constants.MAX_HALITE / MiningFunctions.dropoffMiningValue.getOrDefault(dropoff, 1000.0);
        if(gainByStaying * BotConstants.get().STAYING_RETURN_WEIGHT() > turnValueAfterReturn && !rushOn) {
            // We stay and mine if the gain from doing so is greater than the value of a turn after we return.
            Logger.info(String.format("Stay gain %s greater than turn value %s",
                    gainByStaying, turnValueAfterReturn));
            Optional<Direction> direction = Navigation.navigateLowHaliteDefaultSafety(
                    game, ship, ship.position, moveRegister.getOccupiedPositions());
            if(direction.isPresent()) {
                moveRegister.registerMove(ship, direction.get());
                return;
            }
            else {
                Logger.info("Couldn't stay still.");
            }
        }

        if(suicideOnDropoff) {
            if(dropoffDistance == 1) {
                // Allow self-collisions on dropoffs at the end.
                Logger.info(String.format("Ship %s going in, whether or not dropoff is occupied.", ship));
                Logger.info(String.format("Dropoff %s.", dropoff));
                Direction d = game.map.getUnsafeMoves(ship.position, dropoff).get(0);
                Logger.info(String.format("Direction %s.", d));
                moveRegister.registerMove(ship, d);
                moveRegister.registerPossibleCollision(dropoff);
                return;
            }
        }

        // Normal returning case. Get all the options for the ship. Also, work out if they are queuing - that is, a
        // square nearer to the dropoff is already claimed by another ship. This affects our returning strategy.
        int ourDistance = safeRouteMap.safeDistance(ship.position).orElse(1000);
        boolean queueing = false;
        ArrayList<SquareScore> options = new ArrayList<>();
        if(!moveRegister.getOccupiedPositions().contains(ship.position)) {
            options.add(new SquareScore(ship, game, Direction.STILL, safeRouteMap, unsafeRouteMap,  plan));
        }
        for(Direction d : Direction.ALL_CARDINALS) {
            Position dest = ship.position.directionalOffset(d, game.map);
            if(moveRegister.getOccupiedPositions().contains(dest)) {
                Logger.info(String.format("Occupied %s", dest));
                if (safeRouteMap.safeDistance(dest).isPresent()) {
                    Integer safeDistance = safeRouteMap.safeDistance(dest).get();
                    Logger.info(String.format("Safe distance %d, ours %d", safeDistance, ourDistance));
                    if (safeDistance < ourDistance) {
                        Logger.info(String.format("Queueing to get to %s", dest));
                        queueing = true;
                    }
                }
            }
            else {
                options.add(new SquareScore(ship, game, d, safeRouteMap, unsafeRouteMap, plan));
            }
        }
        // Logger.debug(String.format("Queueing? %s", queueing));
        Logger.info(String.format("Ship %s options: %s", ship, options));
        boolean scared = game.turnsRemaining() <= dropoffDistance + BotConstants.get().RETURN_SAFETY_MARGIN();

        // Compare our options.
        Optional<SquareScore> bestOption = Optional.empty();
        for(SquareScore score : options) {
            if(!bestOption.isPresent()) {
                bestOption = Optional.of(score);
            }
            else {
                if(score.betterThan(bestOption.get(), queueing, scared)) {
                    // Logger.debug(String.format("New best option %s", score.direction));
                    bestOption = Optional.of(score);
                }
            }
        }

        if(!bestOption.isPresent()) {
            Logger.warn(String.format("Returning ship %s found no possible directions!", ship));
            moveRegister.registerMove(ship, Direction.STILL);
        }
        else {
            moveRegister.registerMove(ship, bestOption.get().direction);
        }
    }
}
