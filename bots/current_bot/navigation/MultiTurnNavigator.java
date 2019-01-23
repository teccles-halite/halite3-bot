package bots.current_bot.navigation;

import bots.current_bot.utils.Logger;
import hlt.Direction;
import hlt.Game;
import hlt.Position;
import hlt.Ship;

import java.util.*;

final class PartialPlan {
    private final Game game;
    private final List<Position> positions;
    private int shipHalite;
    private final Position startingPosition;

    PartialPlan(Position position, Game game, int shipHalite) {
        this.game = game;
        this.positions = new ArrayList<>();
        this.positions.add(position);
        this.startingPosition = position;
        this.shipHalite = shipHalite;
    }

    PartialPlan(List<Position> positions, Game game, int shipHalite, Position startingPosition) {
        this.game = game;
        this.positions = positions;
        this.shipHalite = shipHalite;
        this.startingPosition = startingPosition;
    }

    public List<PartialPlan> extendTowards(Position dest, Set<Position> occupied) {
        List<Direction> directions = Navigation.navigateAll(game, last(), shipHalite, dest, dest.equals(startingPosition), occupied);
        List<PartialPlan> plans = new LinkedList<>();
        for(Direction d : directions) {
            Position newPosition = last().directionalOffset(d, game.map);
            if(occupied.contains(newPosition)) continue;
            List<Position> extended = new ArrayList<>(positions);
            extended.add(newPosition);
            plans.add(new PartialPlan(extended, game, shipHalite, startingPosition));
        }
        return plans;
    }

    public Position last() {
        return positions.get(positions.size()-1);
    }

    @Override
    public String toString() {
        return String.format("Positions %s, halite %d", positions, shipHalite);
    }

    public int length() {
        return positions.size();
    }

    public Position getSquare(int i) {
        return positions.get(i);
    }
}

public class MultiTurnNavigator {
    private boolean canNavigate;
    private Map<Integer, Set<Position>> options;
    private Map<Integer, Position> determined;
    private Game game;
    private Ship ship;


    public MultiTurnNavigator(
            Game game,
            Ship ship,
            Position dest,
            Set<Position> occupiedPositions,
            Map<Integer, Map<Position, Ship>> futurePlannedPositions,
            int maxPlanLength,
            int intendedStayLength) {
        List<PartialPlan> plans = new LinkedList<>();
        plans.add(new PartialPlan(ship.position, game, ship.halite));
        int t = 1;
        boolean reachedDest = false;
        this.game = game;
        this.ship = ship;
        int turnsAtDest = 0;

        while(t <= maxPlanLength && turnsAtDest < intendedStayLength) {


            Set<Position> occupied;
            if(t == 1) {
                occupied = occupiedPositions;
            }
            else {
                occupied = new HashSet<>();
                if(futurePlannedPositions.containsKey(t)) {
                    Map<Position, Ship> illegalMap = futurePlannedPositions.get(t);
                    for(Position p : illegalMap.keySet()) {
                        if(illegalMap.get(p).equals(ship)) continue;
                        occupied.add(p);
                    }
                }
            }
            for (PartialPlan p : plans) {
                if(dest.equals(p.last())) reachedDest = true;
            }

            if(!reachedDest) {
                List<PartialPlan> newPlans = new LinkedList<>();

                for (PartialPlan p : plans) {
                    newPlans.addAll(p.extendTowards(dest, occupied));
                }
                plans = newPlans;
            }
            else {
                List<PartialPlan> newPlans = new LinkedList<>();
                for (PartialPlan p : plans) {
//                    if(game.turnNumber == 30 && ship.id.id == 0) Logger.info("Plans have reached dest; all extending still");
                    newPlans.addAll(p.extendTowards(dest, occupied));
                }
                turnsAtDest++;
                plans = newPlans;
            }
            if(game.turnNumber == 30 && ship.id.id == 0) Logger.info(String.format("Partial plans: %s", plans));
            t++;
        }

        if(plans.isEmpty()) {
            canNavigate = false;
            return;
        }
        canNavigate = true;

        int length = plans.get(0).length();
        options = new HashMap<>();
        determined = new HashMap<>();

        for(int i=0; i<length; i++) {
            Set<Position> turnOptions = new HashSet<>();

            for(PartialPlan p : plans) {
                turnOptions.add(p.getSquare(i));
            }
            if(turnOptions.size() == 1) determined.put(i, turnOptions.iterator().next());
            options.put(i, turnOptions);
        }


    }

    public boolean canNavigate() {
        return canNavigate;
    }

    public boolean anyMovesDetermined() {
        return canNavigate && !determined.isEmpty();
    }

    public Map<Integer, Position> determinedMoves() {
        return determined;
    }

    public boolean firstMoveDetermined() {
        return canNavigate && determined.containsKey(1);
    }

    public Direction firstMove(Navigation.DirectionTiebreaker tiebreaker) {
        Set<Position> firstTurnOptions = options.get(1);
        Direction bestDirection = null;
        Logger.info(String.format("Getting first move. Options %s", firstTurnOptions));
        for(Position p : firstTurnOptions) {
            Direction d = ship.position.getDirectionTo(p);
            if(bestDirection == null) {
                bestDirection = d;
            }
            else {
                bestDirection = tiebreaker.betterDirection(game, ship, d, bestDirection);
            }
        }
        return bestDirection;
    }
}
