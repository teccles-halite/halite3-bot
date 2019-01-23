package bots.current_bot.utils;

public class BotConstants4PSmall extends BotConstants4P {

    @Override
    public double SPAWN_INSPIRATION_BONUS() {
        return 2.2;
    }
    public double TOTAL_HALITE_COLLECTION() {return 1.0;}

    public int MIN_DROPOFF_DISTANCE() {return 15;}
    public int SHIPS_PER_DROPOFF() {return 12;}
    public int FIRST_DROPOFF_SHIPS() {return 12;}
    public int SECOND_DROPOFF_SHIPS() {return 13; }
    public int DROPOFF_MIN_NEARBY_SHIPS() {return 1;}
    public int DROPOFF_SHIP_MAX_DISTANCE() {return 7;}
    public int DROPOFF_TERRITORY_SHIPS() {return 10;}
    public double DROPOFF_TURNS() {return 0.738615;}
    public double DROPOFF_EXTRA_DIST_BONUS() {return 0.341001;}
    public double DROPOFF_HALITE_DROPOFF() {return 0.88286;}
    public double DROPOFF_HALITE() {return 14277.2;}
    public double DROPOFF_HALITE_SHIP_TURN() {return 217884;}
}
