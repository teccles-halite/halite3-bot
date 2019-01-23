package hlt;

public class ShipCommand extends Command{

    public final Ship ship;
    public final Position destination;
    private final boolean canCollideDestinations;

    private ShipCommand(final String command, final Position destination, final Ship ship, boolean canCollideDestinations) {
        super(command);
        this.destination = destination;
        this.ship = ship;
        this.canCollideDestinations = canCollideDestinations;
    }

    public static ShipCommand transformShipIntoDropoffSite(final Ship ship) {
            return new ShipCommand("c " + ship.id, ship.position, ship, true);
    }

    public static ShipCommand move(GameMap map, final Ship ship, final Direction direction) {
        Position new_position = ship.position.directionalOffset(direction, map);
        return new ShipCommand("m " + ship.id + ' ' + direction.charValue, new_position, ship, false);
    }

    public boolean canCollideDestinations() {
        return this.canCollideDestinations;
    }
}
