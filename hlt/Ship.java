package hlt;

public class Ship extends Entity {
    public final int halite;

    public Ship(final PlayerId owner, final EntityId id, final Position position, final int halite) {
        super(owner, id, position);
        this.halite = halite;
    }

    public boolean isFull() {
        return halite >= Constants.MAX_HALITE;
    }

    static Ship _generate(final PlayerId playerId) {
        final Input input = Input.readInput();

        final EntityId shipId = new EntityId(input.getInt());
        final int x = input.getInt();
        final int y = input.getInt();
        final int halite = input.getInt();

        return new Ship(playerId, shipId, Position.getPosition(x, y), halite);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Ship ship = (Ship) o;

        return halite == ship.halite;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + halite;
        return result;
    }

    @Override
    public String toString() {
        return String.format("Ship(player=%d, id=%d, position=%s, halite=%d)", owner.id, id.id, position, halite);
    }
}
