package bots.current_bot;

import bots.*;
import bots.current_bot.dropoffs.DropoffPlan;
import bots.current_bot.dropoffs.Dropoffs;
import bots.current_bot.mining.MiningFunctions;
import bots.current_bot.navigation.AnyMoves;
import bots.current_bot.navigation.MapStatsKeeper;
import bots.current_bot.returning.Returning;
import bots.current_bot.spawning.SpawnDecider;
import bots.current_bot.utils.BotConstants;
import bots.current_bot.utils.CommonFunctions;
import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import hlt.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main bot class.
 */
public class Bot extends BaseBot {

    private Set<EntityId> returningShipIds = new HashSet<>();
    private Set<EntityId> rushingShipIds = new HashSet<>();
    private Map<EntityId, Position> guardShips = new HashMap<>();
    private List<Integer> halite_per_turn = new ArrayList<>();
    private List<Integer> ships_per_turn = new ArrayList<>();
    private Optional<DropoffPlan> dropoffPlan = Optional.empty();

    public Bot(Game game, Map<String, String> configOverrides) {
        BotConstants.setSize(game);
        BotConstants.setConfigOverrides(configOverrides);
    }

    @Override
    protected ArrayList<Command> getCommands(Game game, boolean runningLocally) throws Exception {
        Logger.startTime = System.currentTimeMillis();

        // Calculate some information that will be used throughout the turn.
        MapStatsKeeper.updateMaps(game);

        // Add the halite and ship numbers to our logs.
        final GameMap map = game.map;
        int turn_ships = 0;
        int turn_halite = 0;
        for(int x=0; x<map.height; x++) {
            for (int y = 0; y < map.width; y++) {
                MapCell cell = map.cells[x][y];
                if (cell.hasShip()) turn_ships += 1;
                turn_halite += cell.halite;
            }
        }
        halite_per_turn.add(turn_halite);
        ships_per_turn.add(turn_ships);
        Logger.logtime("Calculated map stats");
        Logger.info(String.format("Ships %d, halite %d", turn_ships, turn_halite));

        // Create the moveRegister, which tracks moves and avoids collisions.
        MoveRegister moveRegister = new MoveRegister(game.me.ships.values(), game, !rushingShipIds.isEmpty());

        // Build any dropoffs on super-high halite squares our turtles happen to be on.
        int haliteForExceptionalDropoffs = Dropoffs.getExceptionalDropoffs(game, moveRegister);
        Logger.logtime("Calculated exceptional dropoff moves");

        // Stay still if you need to.
        Set<EntityId> forcedStayIds = CommonFunctions.getForcedStills(map, moveRegister);
        Logger.logtime("Calculated forced stay moves");

        // From here, ships on dropoffs will move off if all the squares around them are filled.
        moveRegister.startTrackingLegalMoves();

        // Find new ships to return, and get the times we expect to have halite at for dropoff planning.
        Map<Integer, List<Double>> expectedHaliteTimes = Returning.fixReturningShips(
                game, moveRegister.getRemainingShips(), returningShipIds, rushingShipIds, guardShips,
                dropoffPlan);
        Logger.logtime("Calculated first wave of returners");

        // Get the planned dropoff site (if any).
        dropoffPlan = Dropoffs.getDropoffPlan(game, dropoffPlan, expectedHaliteTimes, haliteForExceptionalDropoffs);
        Logger.logtime("Calculated dropoff moves");

        // Budget time for mining evaluation (or iterations if running locally, for speed and repeatability).
        int localScoresTimeBudget = BotConstants.get().MINING_SCORE_TIME();
        Optional<Integer> iterationBudget = runningLocally && (BotConstants.get().USE_TOTAL_ITERATIONS() > 0) ? Optional.of(BotConstants.get().ITERATIONS_ALLOWED()) : Optional.empty();

        // This is the most important bit. All remaining ships score all squares on the map to decide where to mine.
        MiningFunctions.getMiningScores(
                game,
                moveRegister.getRemainingShips().stream().filter(s -> !returningShipIds.contains(s.id)).collect(Collectors.toList()),
                localScoresTimeBudget,
                iterationBudget,
                dropoffPlan, moveRegister);
        Logger.logtime("Calculated miner scores");

        // Recalculate returning ships. Some ships with disappointing mining scores might choose to return.
        Returning.fixReturningShips(
                game, moveRegister.getRemainingShips(), returningShipIds, rushingShipIds, guardShips, dropoffPlan);
        Logger.logtime("Calculated all returners");

        // Get moves for ships returning to dropoffs.
        Returning.getReturningMoves(
                game, moveRegister, returningShipIds, rushingShipIds, dropoffPlan, haliteForExceptionalDropoffs);


        // Decide if we should spawn.
        boolean shouldSpawn = SpawnDecider.shouldSpawn(game, moveRegister, halite_per_turn, ships_per_turn, dropoffPlan, haliteForExceptionalDropoffs, runningLocally);
        if(shouldSpawn) {
            Logger.info("Spawn!");
            moveRegister.registerSpawn();
        }

        // In 2 player only, get some moves where several turtles rush towards very big piles of halite.
        if(game.players.size() == 2) {
            ExceptionalSquareHandler.getExceptionalSquaresMoves(game, moveRegister, turn_halite, returningShipIds);
        }

        // Get moves to trap enemy ships for beneficial collisions.
        Hunting.getHuntingMoves(game, moveRegister, returningShipIds);

        // Get moves for endgame protection of dropoffs.
        Guarding.getGuardingMoves(
                game, moveRegister, guardShips);


        Set<Position> illegalPositions = new HashSet<>();
        if(dropoffPlan.isPresent()) {
            illegalPositions.add(dropoffPlan.get().destination);
        }
        // Get moves for miners.
        MiningFunctions.getMiningCommands(game, moveRegister, illegalPositions, forcedStayIds, dropoffPlan);
        Logger.logtime("Calculated miner moves");

        // Any ship which hasn't moved moves somewhere.
        AnyMoves.getMoveCommands(game, moveRegister);

        // Finalise turn commands. This fixes our collisions.
        ArrayList<Command> commands = moveRegister.getCommands();

        Logger.logtime("Finished getting commands");

        return commands;
    }
}
