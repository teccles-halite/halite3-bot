package bots.current_bot.utils;

import bots.current_bot.dropoffs.DropoffPlan;
import hlt.*;

import java.util.*;
import java.util.stream.Collectors;

public class CommonFunctions {
    public static Set<EntityId> getForcedStills(
            GameMap map,
            MoveRegister moveRegister) {
        assert moveRegister.getOccupiedPositions().isEmpty();
        Set<EntityId> ships = new HashSet<>();
        for(Ship ship : moveRegister.getRemainingShips()) {
            if(ship.halite < map.at(ship).halite / Constants.MOVE_COST_RATIO) {
                Logger.info(String.format("Ship %s forced to stay", ship));
                moveRegister.registerMove(ship, Direction.STILL);
                ships.add(ship.id);
            }
        }
        return ships;
    }


    public static List<Position> getNeighbourhood(GameMap map, Position position, int distance) {
        List<Position> neighbourhood = new ArrayList<>();
        for(int sum=0; sum<=distance; sum++) {
            for(int x=-sum; x<=sum; x++) {
                int y = sum - Math.abs(x);
                neighbourhood.add(position.withVectorOffset(map, x, y));
                if(y != 0) {
                    neighbourhood.add(position.withVectorOffset(map, x, -y));
                }
            }
        }
        return neighbourhood;
    }

    public static Integer mineAmount(Ship ship, int halite) {
        return Math.min(
                (halite + Constants.EXTRACT_RATIO - 1) / Constants.EXTRACT_RATIO,
                Constants.MAX_HALITE - ship.halite);
    }

    public static Integer mineAmount(int shipHalite, int halite) {
        return Math.min(
                (halite + Constants.EXTRACT_RATIO - 1) / Constants.EXTRACT_RATIO,
                Constants.MAX_HALITE - shipHalite);
    }

    public static List<Position> getDropoffPositions(Player me, Optional<DropoffPlan> plan) {
        List<Position> positions = new ArrayList<>();
        positions.add(me.shipyard.position);
        if(plan.isPresent()) positions.add(plan.get().destination);
        positions.addAll(me.dropoffs.values().stream().map(d -> d.position).collect(Collectors.toList()));
        return positions;
    }

    public static boolean hasFriendlyShip(Game game, Position position) {
        return game.map.at(position).hasShip() && game.map.at(position).ship.owner.equals(game.me.id);
    }

    public static int moveCost(int halite) {
        return halite / Constants.MOVE_COST_RATIO;
    }


    public static List<Ship> ourShipsNearby(Game game, Position position, Integer radius) {
        List<Ship> ourShips = new ArrayList<>();
        for(Position p : getNeighbourhood(game.map, position, radius)) {
            if(hasFriendlyShip(game, p)) {
                ourShips.add(game.map.at(p).ship);
            }
        }
        return ourShips;
    }

    public static Integer nearestFriendlyShip(Game game, Position dest) {
        Integer distance = 1000;
        for(Ship ship : game.me.ships.values()) {
            distance = Math.min(distance, game.map.calculateDistance(dest, ship.position));
        }
        return distance;
    }

    public static Integer mineAmount(Ship ship, int halite, boolean inspiration) {
        if(!inspiration) return mineAmount(ship, halite);
        else {
            int extractAmount = (halite + Constants.EXTRACT_RATIO - 1) / Constants.EXTRACT_RATIO;
            extractAmount += (int)(extractAmount*Constants.INSPIRED_BONUS_MULTIPLIER);
            return Math.min(Constants.MAX_HALITE - ship.halite, extractAmount);
        }
    }

    public static boolean hasEnemyShip(Game game, Position position, Player player) {
        return game.map.at(position).hasShip() && !game.map.at(position).ship.owner.equals(player.id);
    }

    public static boolean hasEnemyShip(Game game, Position position) {
        return hasEnemyShip(game, position, game.me);
    }

    public static double haliteNearby(Game game, Position position, Integer distance, double dropoff) {
        double halite = 0;
        for(int sum=0; sum<=distance; sum++) {
            for(int x=-sum; x<=sum; x++) {
                int y = sum - Math.abs(x);
                halite += game.map.at(position.withVectorOffset(game.map, x, y)).halite * Math.pow(dropoff, distance);
                if(y != 0) {
                    halite += game.map.at(position.withVectorOffset(game.map, x, -y)).halite * Math.pow(dropoff, distance);
                }
            }
        }
        return halite;
    }

    public static boolean hasFriendlyStructure(Game game, Position position) {
        return game.map.at(position).hasStructure() && game.map.at(position).structure.owner.equals(game.myId);
    }
}
