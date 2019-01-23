package bots.current_bot.dropoffs;

import hlt.Position;

public class DropoffPlan {
    public Integer haliteNeeded = 0;
    public final Position destination;
    public boolean underway = false;
    public boolean complete = false;

    public DropoffPlan(Position destination) {
        this.destination = destination;
    }
}
