package bots.current_bot.mining;

import bots.current_bot.navigation.MapStatsKeeper;
import bots.current_bot.utils.BotConstants;
import bots.current_bot.utils.CommonFunctions;
import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import hlt.Constants;
import hlt.Game;
import hlt.Position;
import hlt.Ship;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Calculate the scores for all of the map for a single ship.
 */
public class MiningScoresFromPosition {
    private Game game;
    private MoveRegister moveRegister;
    private double[][] returnTurns;
    private Ship ship;
    private int iterationsAllowed;
    private int shipIterations;
    private boolean[][] scored;
    private double[][] scores;
    private int[][] miningTurns;

    public MiningScoresFromPosition(Game game, MoveRegister moveRegister, double[][] returnTurns, Ship ship, int iterationsAllowed) {
        this.game = game;
        this.moveRegister = moveRegister;
        this.returnTurns = returnTurns;
        this.ship = ship;
        this.iterationsAllowed = iterationsAllowed;
        this.shipIterations = 0;
    }

    public int getShipIterations() {
        return shipIterations;
    }

    public boolean[][] getScored() {
        return scored;
    }

    public double[][] getScores() {
        return scores;
    }

    public int[][] getMiningTurns() {
        return miningTurns;
    }

    public MiningScoresFromPosition invoke() throws Exception {
        // Get the scores for all squares for this ship.
        scored = new boolean[game.map.height][game.map.width];
        int[][] turnsToReach = new int[game.map.height][game.map.width];
        int[][] haliteOnReaching = new int[game.map.height][game.map.width];
        scores = new double[game.map.height][game.map.width];
        miningTurns = new int[game.map.height][game.map.width];


        Set<Position> positionsToScore = new HashSet<>();
        Set<Position> nextPositionsToScore = new HashSet<>();

        Position p = ship.position;
        positionsToScore.add(p);
        turnsToReach[p.x][p.y] = 0;
        haliteOnReaching[p.x][p.y] = ship.halite;
        scored[p.x][p.y] = true;
        int currentTurnsToReach = 0;

        // Calculate the cost in turns and halite burned to get anywhere on the map. We do this by a breadth first
        // search from the ship position.
        while (!positionsToScore.isEmpty() || !nextPositionsToScore.isEmpty()) {
            if (shipIterations > iterationsAllowed) {
                // If this happens, we'll only end up evaluating a subset of squares near us. In practise, this never
                // happens online.
                Logger.info("Out of iterations");
                moveRegister.outOfTime = true;
                break;
            }
            Set<Position> plusOnePositionsToScore = new HashSet<>();
            for (Position pos : positionsToScore) {
                // Run through the positions we can get to on a particular turn, finding the squares beyond them.
                shipIterations++;
                Integer posHalite = game.map.at(pos).halite;
                int previousShipHalite = haliteOnReaching[pos.x][pos.y];
                int previousTurns = turnsToReach[pos.x][pos.y];
                int one_step_cost = posHalite / Constants.MOVE_COST_RATIO;
                int nextTurns;
                int nextHalite;

                if (one_step_cost > previousShipHalite) {
                    // We'll have to stay here for a turn. Following squares will take two more turns, and we'll have a
                    // bit more halite.
                    int haliteAfterStay = previousShipHalite + CommonFunctions.mineAmount(ship, posHalite);
                    int haliteLeft = (int) posHalite - CommonFunctions.mineAmount(ship, posHalite);
                    nextHalite = haliteAfterStay - haliteLeft / Constants.MOVE_COST_RATIO;
                    nextTurns = previousTurns + 2;
                } else {
                    nextTurns = previousTurns + 1;
                    nextHalite = previousShipHalite - one_step_cost;
                }

                for (Position nbr : CommonFunctions.getNeighbourhood(game.map, pos, 1)) {
                    // Don't allow paths to use squares that aren't safe to visit.
                    boolean canVisit = MapStatsKeeper.canVisitFuture(game, nbr, ship.halite, previousTurns);
                    if (!canVisit) {
                        continue;
                    }

                    int currentTurns = turnsToReach[nbr.x][nbr.y];
                    int currentHalite = haliteOnReaching[nbr.x][nbr.y];

                    // Update the position if we can get there in fewer turns, or the same turns with more halite.
                    if (!scored[nbr.x][nbr.y] || nextTurns < currentTurns ||
                            (nextTurns == currentTurns && nextHalite > currentHalite)) {
                        scored[nbr.x][nbr.y] = true;
                        turnsToReach[nbr.x][nbr.y] = nextTurns;
                        haliteOnReaching[nbr.x][nbr.y] = nextHalite;
                        if (nextTurns == currentTurnsToReach + 1) {
                            nextPositionsToScore.add(nbr);
                        } else if (nextTurns == currentTurnsToReach + 2) {
                            plusOnePositionsToScore.add(nbr);
                        } else {
                            throw new Exception(
                                    String.format("Neighbour %s is an unexpected distance %d away", nbr, nextTurns));
                        }
                    }
                }
            }
            if (shipIterations > iterationsAllowed) break;
            positionsToScore = nextPositionsToScore;
            nextPositionsToScore = plusOnePositionsToScore;
            currentTurnsToReach++;
        }

        for (int x = 0; x < game.map.height; x++) {
            for (int y = 0; y < game.map.height; y++) {
                if (!scored[x][y]) {
                    scores[x][y] = Double.POSITIVE_INFINITY;
                    continue;
                }
                // Calculate the score for this square.
                Map.Entry<Double, Integer> pair = getMiningScore(
                        game,
                        ship,
                        x,
                        y,
                        game.map.cells[x][y].halite,
                        turnsToReach[x][y],
                        haliteOnReaching[x][y],
                        returnTurns[x][y],
                        MapStatsKeeper.getInspiration(game, x, y, game.myId));
                scores[x][y] = pair.getKey();
                miningTurns[x][y] = pair.getValue();
            }
        }
        return this;
    }


    private static Map.Entry<Double, Integer> getMiningScore(
            Game game,
            Ship ship, int x, int y, Integer destHalite, Integer turnsToReach, Integer haliteOnReaching,
            Double returnTurns, boolean inspired) {
        if(game.map.cells[x][y].hasStructure()) {
            return new HashMap.SimpleEntry<>(Double.POSITIVE_INFINITY, 0);
        }
        // No point mining if we can't make a profit with all the halite on the square.
        if(haliteOnReaching + game.map.cells[x][y].halite <= ship.halite) {
            return new HashMap.SimpleEntry<>(Double.POSITIVE_INFINITY, 0);
        }

        double bestScore = Double.POSITIVE_INFINITY;
        int turnsSoFar = turnsToReach;
        int haliteOnSquare = destHalite;
        int haliteInShip = haliteOnReaching;
        int miningTurns = 0;
        while(true) {
            // Simulate mining on this square. We mine until we reach the maximum mining rate, including travel time.
            // This allows planning to mine for several turns at far off squares to recoup our travel costs.
            int turnHaliteCollected = CommonFunctions.mineAmount(haliteInShip, haliteOnSquare);

            double inspiration_multiplier = 0;
            if(inspired) {
                // Inspired squares drop from full inspiration to some minimum linearly over some turns.
                double base = BotConstants.get().MIN_INSPIRATION_BONUS();
                double max_turns = BotConstants.get().INSPIRATION_TURN_DROPOFF();
                double proportion = turnsSoFar > max_turns ? 0.0 : (max_turns - turnsSoFar) / max_turns;
                inspiration_multiplier += proportion * Constants.INSPIRED_BONUS_MULTIPLIER + (1-proportion)*base;
            }

            // Reward squares which are close to an enemy, as a ratio of how far we are from the square. Relatively small
            // effect.
            int nearestEnemy = MapStatsKeeper.nearestEnemy(game, x, y);
            double ratio;
            if(nearestEnemy == 0 || turnsToReach == 0) {
                ratio = 0;
            }
            else {
                ratio = (double)turnsToReach / nearestEnemy;
                ratio = Math.min(ratio, BotConstants.get().MAX_NEARBY_RATIO());
            }
            inspiration_multiplier += ratio*BotConstants.get().NEARBY_ENEMY_BONUS();
            inspiration_multiplier = Math.min(inspiration_multiplier, Constants.INSPIRED_BONUS_MULTIPLIER);

            haliteInShip += (int)(turnHaliteCollected*(1 + inspiration_multiplier));
            haliteInShip = Math.min(haliteInShip, Constants.MAX_HALITE);
            turnsSoFar++;
            miningTurns++;

            haliteOnSquare -= turnHaliteCollected;
            if (haliteInShip > ship.halite) {
                // We've made a profit.
                double gain = haliteInShip - ship.halite;
                double rate = gain / turnsSoFar;
                double collectionTurns = (Constants.MAX_HALITE - ship.halite) / rate;
                collectionTurns += returnTurns;
                // If its less than the previous best profit, we're past the peak and should break.
                if (collectionTurns > bestScore) {
                    break;
                }
                bestScore = collectionTurns;
            }
        }
        // We add a tiny tiebreaker favouring nearby squares.
        bestScore += 1e-4 * game.map.calculateDistance(ship.position.x, ship.position.y, x, y);

        // Adjust for nearby halite. Another small effect giving a bonus to squares with lots of halite nearby.
        bestScore *= (1 + BotConstants.get().MAX_NEARBY_HALITE_BONUS()*0.5);
        bestScore /= (1 + BotConstants.get().MAX_NEARBY_HALITE_BONUS()*MapStatsKeeper.getNearbyHaliteScore(game, x, y));

        return new HashMap.SimpleEntry<>(bestScore, miningTurns);

    }
}
