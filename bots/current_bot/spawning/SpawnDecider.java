package bots.current_bot.spawning;

import bots.current_bot.dropoffs.DropoffPlan;
import bots.current_bot.utils.BotConstants;
import bots.current_bot.utils.Logger;
import bots.current_bot.utils.MoveRegister;
import hlt.*;

import java.util.*;

public class SpawnDecider {
    public static double shipValue = 10000;
    public static boolean spawnedLast;
    public static Set<EntityId> enemyShipIds = new HashSet<>();
    public static int turnsSinceLastEnemySpawn = 0;

    public static boolean shouldSpawn(
            Game game,
            MoveRegister moveRegister,
            List<Integer> haliteHistory, List<Integer> shipHistory, Optional<DropoffPlan> plan, int haliteForExceptionalDropoffs, boolean runningLocally) {
        turnsSinceLastEnemySpawn++;
        // Check for new ships
        for(Player p : game.players) {
            if(p.equals(game.me)) continue;
            for(Ship s : p.ships.values()) {
                if(!enemyShipIds.contains(s.id)) {
                    Logger.info(String.format("Found a new enemy ship %s!", s));
                    turnsSinceLastEnemySpawn = 0;
                    enemyShipIds.add(s.id);
                }
            }
        }
        Logger.info(String.format("Turn since last enemy spawn: %d", turnsSinceLastEnemySpawn));


        if (game.turnNumber > Constants.MAX_TURNS * BotConstants.get().MAX_SPAWN_TURNS()){
            Logger.info("Not spawning - too late in the game");
            spawnedLast = false;
            return false;
        }
        if(moveRegister.getOccupiedPositions().contains(game.me.shipyard.position)){
            Logger.info("Not spawning - shipyard occupied");
            return false;
        }

        Integer haliteNeeded = Constants.SHIP_COST + haliteForExceptionalDropoffs;

        if(plan.isPresent() && plan.get().haliteNeeded > 0) haliteNeeded += plan.get().haliteNeeded;
        if(game.me.halite < haliteNeeded) {
            Logger.info("Not spawning - not enough halite");
            return false;
        }

        // Find the range of ship numbers our opponents have.
        Integer ourShips = game.me.ships.size();
        Integer minShips = game.players.stream().filter(
                p -> !p.equals(game.me)).map(
                p -> p.ships.size()).min(
                Comparator.<Integer>naturalOrder()).get();
        Integer maxShips = game.players.stream().filter(
                p -> !p.equals(game.me)).map(
                p -> p.ships.size()).max(
                Comparator.<Integer>naturalOrder()).get();
        Logger.info(String.format("Our ships %d, min ships %d, max ships %d", ourShips, minShips,maxShips));
        boolean enemySpawning = turnsSinceLastEnemySpawn < BotConstants.get().ENEMY_SPAWN_TURNS();
        // Catch up with the lowest opponent count if necessary.
        if(!runningLocally && ourShips * BotConstants.get().SHIP_DEFICIT_BUILD() <= minShips && enemySpawning){
            Logger.info("Spawning - need to catch up");
            spawnedLast = true;
            return true;
        }
        // Stop if we get too far ahead.
        if(!runningLocally && maxShips + BotConstants.get().SHIP_ADVANTAGE_STOP() < ourShips) {
            Logger.info("Not spawning - too far ahead");
            spawnedLast = false;
            return false;
        }

        // In sane ranges - decide based on halite collected
        if(shipHistory.get(shipHistory.size() - 1) > 0) {
            Integer haliteRemaining = haliteHistory.get(haliteHistory.size()-1);
            int haliteCarried = 0;
            for(Player p : game.players) {
                for(Ship s : p.ships.values()) {
                    haliteCarried += s.halite;
                }
            }
            Double finalHalite = haliteHistory.get(0) * (1-BotConstants.get().TOTAL_HALITE_COLLECTION()) + haliteCarried*BotConstants.get().CARRIED_PROPORTION();
            Integer shipsAlive = shipHistory.get(shipHistory.size()-1);

            // Calculate halite left per ship, adjusting it for how much inspiration we expect on this map size.
            shipValue = BotConstants.get().SPAWN_INSPIRATION_BONUS() * (haliteRemaining - finalHalite) / shipsAlive;
            Logger.info(String.format("Halite remaining per ship %f (%d-%f)/%d)",
                    shipValue,
                    haliteRemaining,
                    finalHalite,
                    shipsAlive));
            if(shipValue < Constants.SHIP_COST){
                Logger.info("Not spawning - not enough halite left");
                spawnedLast = false;
                return false;
            }
        }
        Logger.info("Enough halite left and being mined - spawning");
        spawnedLast = true;
        return true;
    }
}
