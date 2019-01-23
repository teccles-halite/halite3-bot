package bots.current_bot.utils;

public abstract class BotConstants4P extends BotConstants {

    public double INSPIRATION_TURN_DROPOFF() {return 10;}
    public double MIN_INSPIRATION_BONUS() {return 1.0;}

    public int FIRST_DROPOFF_SHIPS() {return 12;}
    public int SECOND_DROPOFF_SHIPS() {return 12; }

    public boolean EXPLOIT_THE_WEAK() {
        return true;
    }

    public double TERRITORY_DROPOFF() {return 0.8;}
    public double BASE_TERRITORY_WEIGHT() {return 1.0;}

    public double SHIP_DEFICIT_BUILD() {return 100.0;}

}
