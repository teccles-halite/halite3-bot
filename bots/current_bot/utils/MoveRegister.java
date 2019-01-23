package bots.current_bot.utils;

import bots.current_bot.dropoffs.DropoffPlan;
import bots.current_bot.navigation.MapStatsKeeper;
import bots.current_bot.navigation.Navigation;
import bots.current_bot.navigation.RandomTiebreaker;
import hlt.*;

import java.util.*;
import java.util.stream.Collectors;
/**
 * Collects all the moves, ensures we have no accidental self-collisions, and tracks ships which must move this turn.
 */
public class MoveRegister {
    public boolean outOfTime = false;
    private final Game game;
    private Set<Position> occupiedPositions = new HashSet<>();
    public final Collection<Ship> allShips;
    private Collection<Ship> remainingShips;
    private List<ShipCommand> commandRegister = new ArrayList<>();
    private Set<Position> collisionsAllowed = new HashSet<>();
    private Map<Position, List<Direction>> mustMoveShips = new HashMap<>();
    private Set<Position> mustMoveDests = new HashSet<>();

    private Map<Ship, List<Direction>> shouldMoveShips = new HashMap<>();
    private Map<Position, List<Ship>> shouldMoveShipAttentionMaps = new HashMap<>();

    private Set<Ship> forcedMoves = new HashSet<>();
    private boolean spawn;


    public MoveRegister(Collection<Ship> ships, Game game, boolean rushOn) {
        remainingShips = new ArrayList<>(ships);
        allShips = new ArrayList<>(ships);
        this.game = game;
        if(!rushOn) {
            // Ships on dropoffs must move this turn.
            for (Position p : CommonFunctions.getDropoffPositions(game.me, Optional.<DropoffPlan>empty())) {
                if (game.map.at(p).hasShip() && game.map.at(p).ship.owner.equals(game.me.id)) {
                    List<Direction> legalMoves = new ArrayList<>();
                    for (Direction d : Direction.ALL_CARDINALS) {
                        legalMoves.add(d);
                    }
                    mustMoveShips.put(p, legalMoves);
                }
            }
        }
    }

    public void startTrackingLegalMoves() {
        for (Ship s : getRemainingShips()) {
            if(mustMoveShips.containsKey(s.position)) continue;
            if(!MapStatsKeeper.canVisit(game, s.position, s)) {
                Logger.info(String.format("Ship %s needs to move if possible", s));
                List<Direction> legalMoves = new ArrayList<>();
                for (Direction d : Direction.ALL_CARDINALS) {
                    Position p = s.position.directionalOffset(d, game.map);
                    if(MapStatsKeeper.canVisit(game, p, s) && !occupiedPositions.contains(p)) {
                        legalMoves.add(d);
                    }
                }
                if(legalMoves.isEmpty()) {
                    Logger.info("No legal moves! Ouch.");
                }
                else if(legalMoves.size() == 1) {
                    Logger.info("One legal move! Going for it.");
                    registerMove(s, legalMoves.get(0));
                }
                else {
                    Logger.info("Multiple legal moves. Registering them.");
                    shouldMoveShips.put(s, legalMoves);
                    for(Direction d : legalMoves) {
                        Position p = s.position.directionalOffset(d, game.map);
                        if(!shouldMoveShipAttentionMaps.containsKey(p))shouldMoveShipAttentionMaps.put(p, new LinkedList<>());
                        shouldMoveShipAttentionMaps.get(p).add(s);
                    }
                }
            }
        }
    }

    public Set<Ship> getRemainingShips(){
        return new HashSet<>(remainingShips);
    }

    public void registerMove(Ship ship, Direction direction) {
        if(!remainingShips.contains(ship)) {
            // This can happen when a ship on a dropoff gets a forced move before it makes its own decision.
            if(!forcedMoves.contains(ship)) throw new IllegalArgumentException("Attempted to issue duplicate" +
                    String.format("command to ship not forced to move %s.", ship));
            Logger.warn(String.format("Attempted to issue duplicate command to ship %s on forced to move - ignoring", ship));

            return;
        }
        ShipCommand command = ShipCommand.move(game.map, ship, direction);
        if(getOccupiedPositions().contains(command.destination)) {
            Logger.warn(String.format("Position %s already occupied while getting command for %s direction %s!",
                    command.destination, ship, direction));
        }

        Logger.info(String.format("Moving %s to %s",
                ship, command.destination));
        getOccupiedPositions().add(command.destination);

        remainingShips.remove(ship);
        commandRegister.add(command);

        List<Position> checkPos = new ArrayList<>(mustMoveShips.keySet());
        for(Position p : checkPos) {
            if(game.map.calculateDistance(p, command.destination) == 1) {
                Logger.info("Adjacent to must move ship - removing legal move");
                List<Direction> remainingMoves = mustMoveShips.get(p).stream().filter(
                        d -> !p.directionalOffset(d, game.map).equals(command.destination)).collect(
                        Collectors.toList());
                mustMoveShips.put(p, remainingMoves);

                checkMustMovePosition(p);
            }
        }

        List<Ship> checkShips = shouldMoveShipAttentionMaps.get(command.destination);
        if(checkShips != null) {
            for (Ship s : checkShips) {
                if(!remainingShips.contains(s)) continue;
                shouldMoveShips.get(s).removeIf(d -> s.position.directionalOffset(d, game.map).equals(command.destination));
                if(shouldMoveShips.get(s).size() == 1) {
                    Logger.info(String.format("Should move ship %s has one square left!", s));
                    Direction d = shouldMoveShips.get(s).get(0);
                    Position dest = s.position.directionalOffset(d, game.map);
                    if(occupiedPositions.contains(dest)) {
                        Logger.info("Only safe dest is already full somehow!");
                    }
                    else {
                        forcedMoves.add(s);
                        registerMove(s, d);
                    }
                }
            }
        }
    }

    private void checkMustMovePosition(Position p) {
        List<Direction> remainingMoves = mustMoveShips.get(p);
        if(remainingMoves.size() != 1) return;
        Ship dropoffShip = game.map.at(p).ship;
        if(remainingShips.contains(dropoffShip)) {
            Logger.info("Ship on dropoff needs to move!");
            Direction forcedDir = remainingMoves.get(0);
            forcedMoves.add(dropoffShip);
            registerMove(dropoffShip, forcedDir);
            Position dest = p.directionalOffset(forcedDir, game.map);
            mustMoveDests.add(dest);

            // If there's a ship there, it must move!
            if(game.map.at(dest).hasShip() && game.map.at(dest).ship.owner.equals(game.me.id)) {
                Ship s = game.map.at(dest).ship;

                Logger.info(String.format("Ship %s on forced target needs to move!", s));
                if(!remainingShips.contains(s)) {
                    Logger.info("Phew - it already is");
                    return;
                }

                List<Direction> validDirections = new ArrayList<>();
                for(Direction d : Direction.ALL_CARDINALS) {
                    Position forcedDest = dest.directionalOffset(d, game.map);
                    if(mustMoveDests.contains(forcedDest) || mustMoveShips.containsKey(forcedDest)) continue;
                    if(occupiedPositions.contains(forcedDest)) continue;
                    Logger.info(String.format("Direction %s moving to %s is valid", d, forcedDest));
                    validDirections.add(d);
                }
                if(validDirections.isEmpty()) {
                    Logger.info("No valid direction for forced mover. Cancelling a random order!");
                    Direction bestDir = null;
                    for(Direction d : Direction.ALL_CARDINALS) {
                        Position forcedDest = dest.directionalOffset(d, game.map);
                        if(mustMoveDests.contains(forcedDest) || mustMoveShips.containsKey(forcedDest)) continue;
                        bestDir = d;
                    }
                    if(bestDir == null){
                        Logger.warn("The end times are here. Forced mover is surrounded by forced moves. " +
                                "We will allow a collision to prevent gridlock.");
                        registerPossibleCollision(dest);
                    }
                    else {
                        Position newDest = dest.directionalOffset(bestDir, game.map);
                        Logger.info(String.format("Cancelling any order to move to %s!", newDest));
                        commandRegister = commandRegister.stream().filter(
                                c -> c.destination != newDest).collect(Collectors.toList());
                        validDirections.add(bestDir);
                    }
                }
                Logger.info(String.format("Valid directions %s", validDirections));

                mustMoveShips.put(s.position, validDirections);

                // Check whether this ship needs to move.
                checkMustMovePosition(s.position);
            }
        }
    }

    public void registerDropoff(Ship ship) {
        assert remainingShips.contains(ship);
        ShipCommand command = ShipCommand.transformShipIntoDropoffSite(ship);
        remainingShips.remove(ship);
        commandRegister.add(command);
    }

    private void fixCommands() {
        // Add a STILL command for any ship without one. This is just to fix collisions.
        Set<Ship> ships = commandRegister.stream().map(s->s.ship).collect(Collectors.toSet());
        for(Ship s : allShips){
            if(!ships.contains(s)) commandRegister.add(ShipCommand.move(game.map, s, Direction.STILL));
        }

        while(true) {
            Logger.info("Looping to fix commands");
            Set<Position> positions = new HashSet<>();
            Set<Position> badPositions = new HashSet<>();
            for(ShipCommand c : commandRegister) {
                if(c.canCollideDestinations()) continue;
                if(!collisionsAllowed.contains(c.destination)) {
                    if(positions.contains(c.destination)) {
                            badPositions.add(c.destination);
                    }
                    positions.add(c.destination);
                }
            }
            if(badPositions.isEmpty()) break;
            ArrayList<ShipCommand> newCommands = new ArrayList<>();
            for(ShipCommand c : commandRegister) {
                if(c.canCollideDestinations()){
                    newCommands.add(c);
                }
                else if(!badPositions.contains(c.destination)) {
                    newCommands.add(c);
                }
                else if(c.ship.position.equals(c.destination)) {
                    newCommands.add(c);
                }
                else {
                    Logger.warn(String.format("Removing command to move %s to %s", c.ship.position, c.destination));
                    badPositions.remove(c.destination);
                    Direction d = Direction.STILL;
                    if(occupiedPositions.contains(c.ship.position)) {
                        Logger.warn("Shouldn't stay still - another ship wants this position");

                        d = Navigation.moveAnywhere(game, c.ship, occupiedPositions, false, new RandomTiebreaker());
                    }
                    Logger.warn(String.format("Alternative direction %s", d));
                    newCommands.add(ShipCommand.move(game.map, c.ship, d));
                    getOccupiedPositions().add(c.ship.position.directionalOffset(d, game.map));
                }
            }
            commandRegister = newCommands;
        }

        if(outOfTime) {
            // Remove all STILL commands to signal.
            Logger.warn("Out of time! Signalling.");
            commandRegister = commandRegister.stream().filter(c -> !c.destination.equals(c.ship.position)).collect(Collectors.toList());
        }
    }

    public ArrayList<Command> getCommands() {
        ArrayList<Command> commands = new ArrayList<>();
        fixCommands();
        for(ShipCommand command : commandRegister) {
            commands.add(command);
        }
        if(spawn) {
            commands.add(Command.spawnShip());
        }
        return commands;
    }

    public void registerPossibleCollision(Position position) {
        Logger.warn(String.format("Allowing collisions on %s this turn!", position));
        collisionsAllowed.add(position);
    }

    public Set<Position> getOccupiedPositions() {
        return occupiedPositions;
    }

    public boolean mustMove(Ship ship) {
        // This ship may have moved, in which case it's OK for it not to move again.
        return mustMoveShips.containsKey(ship.position) && remainingShips.contains(ship);
    }

    public void registerSpawn() {
        if(spawn) {
            throw new IllegalArgumentException("Already spawning!");
        }
        spawn = true;
        occupiedPositions.add(game.me.shipyard.position);
    }
}
