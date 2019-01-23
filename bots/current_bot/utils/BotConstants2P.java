package bots.current_bot.utils;

public class BotConstants2P extends BotConstants {

    public double SHIP_ADVANTAGE_STOP() {return 5;}
    public double SHIP_DEFICIT_BUILD() {return 1.0;}
    public double NEARBY_ENEMY_BONUS() {return 0.0;}

    @Override
    public double TERRITORY_DROPOFF() {return 0.8;}
    public double BASE_TERRITORY_WEIGHT() {return 1.0;}

    @Override
	public double SPAWN_INSPIRATION_BONUS() {return 1.5;}

    @Override
    public boolean EXPLOIT_THE_WEAK() {return false;}

    public int FIRST_DROPOFF_SHIPS() {
        return 13;
    }
    public int SECOND_DROPOFF_SHIPS() {
        return 13;
    }

    @Override
    public int STATIONARY_THRESHOLD_BONUS() {
        return 0;
    }

    // Dropoffs
    public int MIN_DROPOFF_DISTANCE() {return 15;}
    public int SHIPS_PER_DROPOFF() {return 13;}
    public int DROPOFF_MIN_NEARBY_SHIPS() {return 1;}
    public int DROPOFF_SHIP_MAX_DISTANCE() {return 7;}
    public int DROPOFF_TERRITORY_SHIPS() {return 10;}

    public double DROPOFF_TURNS() {return 0.787869;}
    public double DROPOFF_EXTRA_DIST_BONUS() {return 0.303234;}
    public double DROPOFF_HALITE_DROPOFF() {return 0.858792;}
    public double DROPOFF_HALITE() {return 14830;}

    // Mining
    public double MIN_INSPIRATION_BONUS() {return 1.47334;}
    public double INSPIRATION_TURN_DROPOFF() {return 15.8982;}
    public double DROPOFF_DISTANCE_PENALTY(){return 1.1259;}
    public double HALITE_TO_RETURN(){return 0.53231;}
    public double HALITE_TO_ALWAYS_RETURN() {return 0.85811;}
    public double ASSUMED_RETURNING_PROPORTION() {return 0.742456;}
    public double RETURN_SPEED() {return 1.1567;}
    public double RETURN_RATIO() {return 10.0;}
}
