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
            // Ships on dropoffs must move this turn. We track what moves they can still make, so that they can take
            // the last available move if the others get taken.
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
        // After this, we will move all ships whenever they have exactly one square they are happy with.
        for (Ship s : getRemainingShips()) {
            // mustMoveShips are dealt with differently - they will definitely move, and don't need to be tracked here.
            if(mustMoveShips.containsKey(s.position)) continue;
            // We only worry about ships which aren't happy to stay put. Actually, this is probably wrong - if a ship's
            // square gets taken, it should be tracked like this.
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
        // Makes the ship move in the direction; checks for collisions, updates the taken positions maps, and makes any
        // forced moves that result from this move.
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

        // Check ships that must move in case they now need to move.
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

        // Check ships which should move in case they now need to move.
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
        // Checks whether a ship which must move now needs to move.
        List<Direction> remainingMoves = mustMoveShips.get(p);
        // Only need to move if there's one move left.
        if(remainingMoves.size() != 1) return;
        Ship mustMoveShip = game.map.at(p).ship;
        if(remainingShips.contains(mustMoveShip)) {
            Logger.info(String.format("Ship %s needs to move!", mustMoveShip));
            Direction forcedDir = remainingMoves.get(0);
            forcedMoves.add(mustMoveShip);
            registerMove(mustMoveShip, forcedDir);
            Position dest = p.directionalOffset(forcedDir, game.map);
            mustMoveDests.add(dest);

            // If there's a ship in the square we are moving to, it must also move.
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
                    // The ship we are moving onto has no options to move away. We cancel a random order to a square
                    // next to it to make it move.
                    Logger.info("No valid direction for forced mover. Cancelling a random order!");
                    Direction bestDir = null;
                    for(Direction d : Direction.ALL_CARDINALS) {
                        Position forcedDest = dest.directionalOffset(d, game.map);
                        if(mustMoveDests.contains(forcedDest) || mustMoveShips.containsKey(forcedDest)) continue;
                        bestDir = d;
                    }
                    if(bestDir == null){
                        // This can probably never happen, but if all the squares around the ship are destinations of
                        // forced moves, it stays still and causes a collision.
                        Logger.warn("The end times are here. Forced mover is surrounded by forced moves. " +
                                "We will allow a collision to prevent gridlock.");
                        registerPossibleCollision(dest);
                    }
                    else {
                        Position newDest = dest.directionalOffset(bestDir, game.map);
                        Logger.info(String.format("Cancelling any order to move to %s!", newDest));
                        // There's a bug here - we cancel the order, but we don't put the ship back into the pool of
                        // ships.
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
        // Ew, an assertion.
        assert remainingShips.contains(ship);
        ShipCommand command = ShipCommand.transformShipIntoDropoffSite(ship);
        // This doesn't add the destination to occupiedPositions, because other ships are very welcome to move there.
        remainingShips.remove(ship);
        commandRegister.add(command);
    }

    private void fixCommands() {
        // Add a STILL command for any ship without one. This is just to help in fixing collisions.
        Set<Ship> ships = commandRegister.stream().map(s->s.ship).collect(Collectors.toSet());
        for(Ship s : allShips){
            if(!ships.contains(s)) commandRegister.add(ShipCommand.move(game.map, s, Direction.STILL));
        }

        while(true) {
            // We loop over the command register, cancelling orders which end up on the same square. This map happen
            // several times.
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
                    // Not sure whether this ever happens, but this ends up cancelling both orders if two ships are
                    // planning to make the same move, which seems wrong.
                    Logger.warn(String.format("Removing command to move %s to %s", c.ship.position, c.destination));
                    badPositions.remove(c.destination);
                    Direction d = Direction.STILL;
                    if(occupiedPositions.contains(c.ship.position)) {
                        // Make an effort to move off this square, because another ship wants it.
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
        // If a self-collision is registered on a position, we won't change the orders on the collision prevention pass.
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
