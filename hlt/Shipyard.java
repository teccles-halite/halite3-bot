package hlt;

public class Shipyard extends Entity {
    public Shipyard(final PlayerId owner, final Position position) {
        super(owner, EntityId.NONE, position);
    }

    public Command spawn() {
        return Command.spawnShip();
    }

    public String toString(){
        return String.format("Shipyard(owner=%s, position=%s)", owner, position);
    }
}
