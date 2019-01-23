package hlt;

public class Dropoff extends Entity {
    public Dropoff(final PlayerId owner, final EntityId id, final Position position) {
        super(owner, id, position);
    }

    static Dropoff _generate(final PlayerId playerId) {
        final Input input = Input.readInput();

        final EntityId dropoffId = new EntityId(input.getInt());
        final int x = input.getInt();
        final int y = input.getInt();

        return new Dropoff(playerId, dropoffId, Position.getPosition(x, y));
    }

    public String toString() {
        return String.format("Dropoff(owner=%s, position=%s)", owner, position);
    }

}
