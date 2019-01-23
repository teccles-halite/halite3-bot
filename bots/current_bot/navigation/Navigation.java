package bots.current_bot.navigation;

import hlt.*;

import java.util.*;

public class Navigation {

    public static Optional<Direction> navigateLowHaliteDefaultSafety(Game game, Ship ship, Position pos, Set<Position> occupiedPositions) {
        return navigateLowHalite(game, ship, pos, occupiedPositions);
    }

    public static Optional<Direction> navigateLongerAxisDefaultSafety(Game game, Ship ship, Position pos, Set<Position> occupiedPositions) {
        return navigate(game, ship, pos, occupiedPositions, new LongerAxisTiebreaker(game, ship, pos));
    }

    public enum SafetyLevel {
        ALLOW_ENEMY(0),
        ALLOW_ADJACENT(1),
        NO_ADJACENT(2);

        public int level;

        SafetyLevel(int i) {
            level = i;
        }
    }

    public interface DirectionTiebreaker {
        Direction betterDirection(Game game, Ship ship, Direction d_1, Direction d_2);
    }


    public static Optional<Direction> navigateUnsafe(
            Game game,
            Ship ship,
            Position position,
            Set<Position> occupiedPositions,
            DirectionTiebreaker tiebreaker) {
        // Logger.debug(String.format("Navigating for ship %s", ship));
        List<Direction> potentialDirections = game.map.getUnsafeMoves(ship.position, position);
        // Logger.debug(potentialDirections.toString());
        Optional<Direction> bestDirection = Optional.empty();
        for(Direction d : potentialDirections){
            // Logger.debug(String.format("Trying direction %s", d));
            Position destination = ship.position.directionalOffset(d, game.map);
            if(occupiedPositions.contains(destination)) {
                // Logger.debug("Already taken");
                continue;
            }

            if(!bestDirection.isPresent()) {
                bestDirection = Optional.of(d);
            }
            else {
                bestDirection = Optional.of(tiebreaker.betterDirection(
                        game, ship, d, bestDirection.get()
                ));
            }
        }
        return bestDirection;
    }

    public static Optional<Direction> navigate(
            Game game,
            Ship ship,
            Position position,
            Set<Position> occupiedPositions,
            DirectionTiebreaker tiebreaker) {
        // Logger.debug(String.format("Navigating for ship %s", ship));
        List<Direction> potentialDirections = game.map.getUnsafeMoves(ship.position, position);
        // Logger.debug(potentialDirections.toString());
        Optional<Direction> bestDirection = Optional.empty();
        for(Direction d : potentialDirections){
            // Logger.debug(String.format("Trying direction %s", d));
            Position destination = ship.position.directionalOffset(d, game.map);
            if(occupiedPositions.contains(destination)) {
                // Logger.debug("Already taken");
                continue;
            }

            if(!MapStatsKeeper.canVisit(game, destination, ship)) {
                continue;
            }
            if(!bestDirection.isPresent()) {
                bestDirection = Optional.of(d);
            }
            else {
                bestDirection = Optional.of(tiebreaker.betterDirection(
                        game, ship, d, bestDirection.get()
                ));
            }
        }
        return bestDirection;
    }

    public static List<Direction> navigateAll(
            Game game,
            Position start,
            int shipHalite,
            Position dest,
            boolean isCurrentPosition,
            Set<Position> occupiedPositions) {
        List<Direction> potentialDirections = game.map.getUnsafeMoves(start, dest);
        List<Direction> validDirections = new LinkedList<>();
        for(Direction d : potentialDirections){
            Position destination = start.directionalOffset(d, game.map);
            if(occupiedPositions.contains(destination)) {
                continue;
            }

            if(!MapStatsKeeper.canVisit(game, destination, shipHalite, isCurrentPosition)) {
                continue;
            }
            validDirections.add(d);
        }
        return validDirections;
    }

    public static Direction moveAnywhere(
            Game game,
            Ship ship,
            Set<Position> occupiedPositions,
            boolean mustMove,
            DirectionTiebreaker tiebreaker) {
        Optional<Direction> bestDirection = Optional.empty();
        for(Direction d : Direction.ALL_CARDINALS){
            // Try to find a direction which is valid to visit.
            // Logger.debug(String.format("Trying direction %s", d));
            Position destination = ship.position.directionalOffset(d, game.map);
            if(occupiedPositions.contains(destination)) {
                // Logger.debug("Already taken");
                continue;
            }

            if(!MapStatsKeeper.canVisit(game, destination, ship)) {
                continue;
            }
            if(!bestDirection.isPresent()) {
                bestDirection = Optional.of(d);
            }
            else {
                bestDirection = Optional.of(tiebreaker.betterDirection(
                        game, ship, d, bestDirection.get()
                ));
            }
        }
        if(bestDirection.isPresent()) return bestDirection.get();

        for(Direction d : Direction.ALL_CARDINALS){
            // Try to find a direction which is unoccupied.

            // Logger.debug(String.format("Trying direction %s", d));
            Position destination = ship.position.directionalOffset(d, game.map);
            if(occupiedPositions.contains(destination)) {
                // Logger.debug("Already taken");
                continue;
            }

            if(game.map.at(destination).hasShip()) {
                continue;
            }
            if(!bestDirection.isPresent()) {
                bestDirection = Optional.of(d);
            }
            else {
                bestDirection = Optional.of(tiebreaker.betterDirection(
                        game, ship, d, bestDirection.get()
                ));
            }
        }
        if(bestDirection.isPresent()) return bestDirection.get();

        if(mustMove) {
            for (Direction d : Direction.ALL_CARDINALS) {
                // Try to find a direction which we don't have a ship on.

                // Logger.debug(String.format("Trying direction %s", d));
                Position destination = ship.position.directionalOffset(d, game.map);
                if (occupiedPositions.contains(destination)) {
                    // Logger.debug("Already taken");
                    continue;
                }

                if (!bestDirection.isPresent()) {
                    bestDirection = Optional.of(d);
                } else {
                    bestDirection = Optional.of(tiebreaker.betterDirection(
                            game, ship, d, bestDirection.get()
                    ));
                }
            }
            if(bestDirection.isPresent()) return bestDirection.get();
            else {
                throw new IllegalArgumentException(String.format("Ship %s must move, but every direction causes a collision", ship));
            }
        }

        return Direction.STILL;
    }

    private static Optional<Direction> navigateLowHalite(
            Game game,
            Ship ship,
            Position position,
            Set<Position> occupiedPositions) {
        return navigate(game, ship, position, occupiedPositions, new LowHaliteTiebreaker());
    }
}
