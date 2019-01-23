package bots.current_bot.mining;

import bots.current_bot.dropoffs.DropoffPlan;
import bots.current_bot.navigation.LongerAxisTiebreaker;
import bots.current_bot.navigation.MapStatsKeeper;
import bots.current_bot.navigation.MultiTurnNavigator;
import bots.current_bot.utils.BotConstants;
import bots.current_bot.utils.CommonFunctions;
import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import hlt.*;

import java.util.*;

public class MiningFunctions {

    public static Map<Ship, double[][]> miningScores;
    public static Map<Ship, int[][]> intendedMiningTurns;

    public static Map<Ship, boolean[][]> miningScoresPresent;
    public static Map<Position, double[][]> dropoffMiningScores;
    public static Map<Position, boolean[][]> dropoffMiningScoresPresent;
    public static Map<Position, Double> dropoffMiningValue = new HashMap<>();
    private static Map<Position, Ship> lastTurnClaims = new HashMap<>();

    public static int turnUpdated = -1;
    private static int shipValueUpdated = -1;
    private static double rollingTurnValueAverage = 30.0;

    public static void getMiningScores(
            Game game, Collection<Ship> ships, int budgetMilliseconds, Optional<Integer> budgetIterations, Optional<DropoffPlan> plan, MoveRegister moveRegister
    ) throws Exception {
        Logger.info(String.format("Getting mining scores"));

        turnUpdated = game.turnNumber;
        miningScores = new HashMap<>();
        intendedMiningTurns = new HashMap<>();

        miningScoresPresent = new HashMap<>();
        double[][] returnTurns = getReturnTurns(game, plan);

        long startTime = System.currentTimeMillis();
        Double estimatedIterationTime = 0.05;
        int shipsLeft = ships.size();
        int totalIterations = 0;
        int totalShips = ships.size();

        for(Ship ship : ships) {
            // Calculate how many iteration we are allowed for this ship. We try to split them evenly.
            int timeElapsed = (int) (System.currentTimeMillis() - startTime);
            int timeLeft = budgetMilliseconds - timeElapsed;
            int iterationsLeft = (int) (timeLeft / estimatedIterationTime);
            int iterationsAllowed = (int) (iterationsLeft / (shipsLeft + 0.5));
            if(budgetIterations.isPresent()) {
                // Override with fixed budget for repeatable games
                iterationsAllowed = budgetIterations.get() / totalShips;
            }
            Logger.info(String.format("Calculating score for ship %s", ship));
            Logger.info(String.format("Time gone %d, time left %d, total so far %d, est. time per iter %.4f, iterations left %d, iterations for ship %d",
                    timeElapsed, timeLeft, totalIterations, estimatedIterationTime, iterationsLeft, iterationsAllowed));

            // This bit actually gets the scores.
            MiningScoresFromPosition miningScoresFromPosition = new MiningScoresFromPosition(
                    game, moveRegister, returnTurns, ship, iterationsAllowed).invoke();
            double[][] scores = miningScoresFromPosition.getScores();
            int[][] miningTurns = miningScoresFromPosition.getMiningTurns();

            boolean[][] scored = miningScoresFromPosition.getScored();

            totalIterations += miningScoresFromPosition.getShipIterations();
            shipsLeft--;
            estimatedIterationTime = (System.currentTimeMillis() - startTime) / (double)totalIterations;
            miningScores.put(ship, scores);
            intendedMiningTurns.put(ship, miningTurns);

            miningScoresPresent.put(ship, scored);
        }
        Logger.info(String.format("Got all mining scores. %d iterations took %d milliseconds. %.3f per iteration.",
                totalIterations, System.currentTimeMillis() - startTime, (double)(System.currentTimeMillis() - startTime) / totalIterations
        ));

        // Score for imaginary ships at each dropoff. Used in deciding how valuable a ship turn is when returning.
        dropoffMiningScores = new HashMap<>();
        dropoffMiningScoresPresent = new HashMap<>();
        int i=-1;
        for(Position p : CommonFunctions.getDropoffPositions(game.me, plan)) {
            Ship fakeShip = new Ship(game.me.id, new EntityId(i), p, 0);
            i--;
            MiningScoresFromPosition miningScoresFromPosition = new MiningScoresFromPosition(
                    game, moveRegister, returnTurns, fakeShip, 1000000
            ).invoke();
            Logger.info(String.format("Populating dropoff scores for %s", p));
            dropoffMiningScoresPresent.put(p, miningScoresFromPosition.getScored());
            dropoffMiningScores.put(p, miningScoresFromPosition.getScores());
        }
    }

    private static double[][] getReturnTurns(Game game, Optional<DropoffPlan> plan) {
        double[][] returnTurns = new double[game.map.height][game.map.width];

        for(int x=0; x < game.map.height; x++) {
            for (int y = 0; y < game.map.height; y++) {
                MapCell c = game.map.cells[x][y];
                int dropoffDistance = MapStatsKeeper.nearestDropoffDistance(c.position, game.me, game, plan);
                returnTurns[x][y] = dropoffDistance * BotConstants.get().DROPOFF_DISTANCE_PENALTY();
            }
        }
        return returnTurns;
    }


    private static boolean getMiningCommandsOneRound(
            Game game,
            MoveRegister moveRegister,
            Map<Position, Double> scorePenalty,
            Set<Position> illegalPositions,
            Set<EntityId> forcedStayIds,
            Map<Integer, Map<Position, Ship>> futurePlannedPositions) {
        Logger.info("Getting a round of mining commands");
        boolean needToIterate = true;
        Set<Ship> happyShips = new HashSet<>();
        Map<Position, Ship> claims = new HashMap<>();
        Map<Ship, Position> inverseClaims = new HashMap<>();
        Map<Position, Ship> finalClaims = new HashMap<>();

        if(lastTurnClaims != null) {
            for (Map.Entry<Position, Ship> entry : lastTurnClaims.entrySet()) {
                Ship s = entry.getValue();
                if(forcedStayIds.contains(s.id)) {
                    Position p = entry.getKey();
                    Logger.info(String.format("Propagating claim of forced stayer %s to %s", s, p));
                    claims.put(p, s);
                    inverseClaims.put(s, p);
                }
            }
        }


        // First, ships make claims on squares they think they can reach. Priority goes to ships which are closer.
        // There is no check here that we can actually all navigate to the squares claimed - two ships may plan to use
        // the same route.
        while(needToIterate) {
            needToIterate = false;
            for(Ship ship : moveRegister.getRemainingShips()) {
                if(happyShips.contains(ship)) continue;
                Double bestScore = Double.POSITIVE_INFINITY;
                Optional<Position> bestPosition = Optional.empty();
                boolean[][] scoresPresent = miningScoresPresent.get(ship);
                double[][] scores = miningScores.get(ship);

                for(int x=0; x<game.map.height; x++) {
                    for (int y = 0; y < game.map.width; y++) {
                        if(!scoresPresent[x][y]) continue;
                        double score = scores[x][y];
                        if (score >= bestScore) continue;
                        Position p = Position.getPosition(x, y);
                        if(scorePenalty.containsKey(p)) score *= scorePenalty.get(p);
                        if (score >= bestScore) continue;
                        if (illegalPositions.contains(p)) continue;
                        boolean canClaim = true;
                        Ship other_ship = claims.get(p);
                        Integer ourDistance = game.map.calculateDistance(ship.position, p);

                        if (other_ship != null) {
                            Integer theirDistance = game.map.calculateDistance(other_ship.position, p);
                            if(forcedStayIds.contains(other_ship.id)) theirDistance += 1;
                            canClaim = ourDistance < theirDistance;
                        }
                        if (canClaim) {
                            // Logger.debug(String.format("New best- try to navigate"));
                            MultiTurnNavigator navigator = new MultiTurnNavigator(
                                    game, ship, p, moveRegister.getOccupiedPositions(), futurePlannedPositions, BotConstants.get().PLAN_HORIZON(), intendedMiningTurns.get(ship)[p.x][p.y]);
                            if (navigator.canNavigate()) {
                                // Logger.debug(String.format("New best!"));
                                bestScore = score;
                                bestPosition = Optional.of(p);
                            }
                        }
                    }
                }
                if(bestPosition.isPresent()){
                    // Logger.debug(String.format("Adding claim %s for %s", bestPosition, ship));

                    Position p = bestPosition.get();
                    Ship otherShip = claims.get(p);
                    if(otherShip != null) {
                        Logger.info(String.format("%s removing claim of %s to %s - we are nearer", ship, otherShip, p));
                        happyShips.remove(otherShip);
                        needToIterate = true;
                    }
                    happyShips.add(ship);
                    claims.put(p, ship);
                    inverseClaims.put(ship, p);
                }
            }
        }

        boolean foundAnyCommands = false;

        boolean foundDetermined = true;
        boolean loop = true;

        while(loop) {
            List<Ship> shipsList = new ArrayList<>(moveRegister.getRemainingShips());
            shipsList.sort(new PreferencesComparator(game, inverseClaims));
            // We do one more loop after we find no forced moves.
            if(!foundDetermined) loop = false;
            foundDetermined = false;
            for (Ship ship : shipsList) {
                Position pos = inverseClaims.get(ship);
                if (pos == null) {
                    Logger.info(String.format("Ship %s found no useful mining to do", ship));
                    continue;
                }
                Logger.info(String.format("Processing claim for %s, %d from target %s",
                        ship, game.map.calculateDistance(ship.position, pos), pos));

                Entity structure = game.map.at(ship.position).structure;
                if (structure != null && structure.owner.equals(game.myId)) {
                    double value = miningScores.get(ship)[pos.x][pos.y] * scorePenalty.getOrDefault(pos, 1.0);
                    Logger.info(String.format("Using actual ship score %f for dropoff %s score", value, ship.position));
                    dropoffMiningValue.put(ship.position, value);
                }

                MultiTurnNavigator navigator = new MultiTurnNavigator(
                        game, ship, pos, moveRegister.getOccupiedPositions(), futurePlannedPositions, BotConstants.get().PLAN_HORIZON(), intendedMiningTurns.get(ship)[pos.x][pos.y]);
                if (navigator.canNavigate()) {
                    Logger.info(String.format("%s can navigate to %s!", ship, pos));
                    if (navigator.anyMovesDetermined()) {
                        Logger.info("Some moves determined - marking squares illegal");
                        Map<Integer, Position> determinedMoves = navigator.determinedMoves();
                        for(Map.Entry<Integer, Position> e : determinedMoves.entrySet()) {
                            int t = e.getKey();
                            Position p = e.getValue();
                            // We don't mark positions as illegal if there are enemies who might interrupt the plan.
                            if(MapStatsKeeper.nearestEnemy(game, p) <= t) continue;
                            if(!futurePlannedPositions.containsKey(t)) futurePlannedPositions.put(t, new HashMap<>());
                            if(!futurePlannedPositions.get(t).containsKey(p)) {
                                foundDetermined = true;
                                Logger.info(String.format("Marking %s illegal to time %d", p, t));
                                futurePlannedPositions.get(t).put(p, ship);
                            }
                        }
                    }

                    if(navigator.firstMoveDetermined() || !foundDetermined) {
                        Direction d = navigator.firstMove(new LongerAxisTiebreaker(game, ship, pos));
                        Logger.info(String.format("Found move for miner %s towards %s", ship, pos));
                        moveRegister.registerMove(ship, d);
                        foundAnyCommands = true;
                        scorePenalty.put(pos, scorePenalty.getOrDefault(pos, 1.0) * BotConstants.get().SECOND_MINING_PENALTY());
                        Flogger.log(game.turnNumber, pos, String.format("Target of ship %s", ship), Optional.of("blue"));
                        Flogger.log(game.turnNumber, ship.position, String.format("Targetting %s", pos), Optional.empty());
                        finalClaims.put(pos, ship);
                    }
                    else {
                        Logger.info("Not navigating just yet - we have options");
                    }
                }
                else {
                    Logger.info(String.format("Miner %s unable to move to destination %s", ship, pos));
                }
            }
        }
        lastTurnClaims = finalClaims;
        return foundAnyCommands;
    }

    public static void getMiningCommands(
            Game game,
            MoveRegister moveRegister,
            Set<Position> illegalPositions,
            Set<EntityId> forcedStayIds,
            Optional<DropoffPlan> plan) {
        dropoffMiningValue = new HashMap<>();
        boolean loop = true;
        Map<Position, Double> scorePenalty = new HashMap<>();

        Map<Integer, Map<Position, Ship>> futurePlannedPositions = new HashMap<>();
        while(loop) {
            loop = getMiningCommandsOneRound(game, moveRegister, scorePenalty, illegalPositions, forcedStayIds, futurePlannedPositions);
        }
        for(Position p : CommonFunctions.getDropoffPositions(game.me, plan)) {
            if(dropoffMiningValue.containsKey(p)) continue;
            Logger.info(String.format("Getting best score for dropoff %s", p));

            boolean[][] scoresPresent = dropoffMiningScoresPresent.get(p);

            double[][] scores = dropoffMiningScores.get(p);
            double bestScore = Double.POSITIVE_INFINITY;
            for(int x=0; x<game.map.height; x++) {
                for (int y = 0; y < game.map.width; y++) {
                    if (!scoresPresent[x][y]) continue;
                    double score = scores[x][y] * scorePenalty.getOrDefault(Position.getPosition(x, y), 1.0);
                    if(score < bestScore) bestScore = score;
                }
            }
            Logger.info(String.format("Hypothetical ship at dropoff %s would score %f", p, bestScore));
            dropoffMiningValue.put(p, bestScore);
        }

        if(shipValueUpdated < game.turnNumber) {
            shipValueUpdated = game.turnNumber;
            double decay = BotConstants.get().SHIP_VALUE_DECAY();
            rollingTurnValueAverage = rollingTurnValueAverage * decay + oneTurnShipTurnValue(game) * (1 - decay);
        }
    }

    private static double oneTurnShipTurnValue(Game game) {
        if(turnUpdated < 2) return 10;
        Map<Position, Integer> shipsPerDropoff = new HashMap<>();
        for(Ship s : game.me.ships.values()) {
            Position dropoff = MapStatsKeeper.nearestDropoff(s.position, game.me, game, Optional.<DropoffPlan>empty());
            shipsPerDropoff.put(dropoff, shipsPerDropoff.getOrDefault(dropoff, 0) + 1);
        }
        int ships = 0;
        double value = 0;
        for(Position dropoff : shipsPerDropoff.keySet()) {
            int dropoffShips = shipsPerDropoff.get(dropoff);
            ships += dropoffShips;
            value += dropoffShips * (Constants.MAX_HALITE / dropoffMiningValue.get(dropoff));
        }
        return value / ships;
    }

    public static double shipTurnValue() {
        return rollingTurnValueAverage;
    }
}


