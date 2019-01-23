package bots.current_bot.utils;


import bots.current_bot.navigation.Navigation;
import hlt.Game;

import java.util.Map;

public abstract class BotConstants {

    // Collisions
    public int DROPPED_TERRITORY_RADIUS(){return 6;}
    public abstract double TERRITORY_DROPOFF();
    public abstract double BASE_TERRITORY_WEIGHT();

    // General mining and returning
    public abstract double INSPIRATION_TURN_DROPOFF();
    public abstract double MIN_INSPIRATION_BONUS();
    public double DROPOFF_DISTANCE_PENALTY(){return 1.0;}
    public double HALITE_TO_RETURN(){return 0.5;}
    public double ASSUMED_RETURNING_PROPORTION() {return 0.9;}
    public double RETURN_SPEED() {return 1.2;}
    public double HALITE_TO_ALWAYS_RETURN() {return 0.95;}
    public double RETURN_RATIO() {return 10.0;}
    public double SECOND_MINING_PENALTY() { return 1.5;}
    public double NEARBY_ENEMY_BONUS() {return 0.1;}

    // Dropoffs
    // Not optimised
    public int DROPOFF_RADIUS() {return 7;}
    // Optimisable
    public abstract int MIN_DROPOFF_DISTANCE();
    public abstract int SHIPS_PER_DROPOFF();
    public abstract int DROPOFF_MIN_NEARBY_SHIPS();
    public abstract int DROPOFF_SHIP_MAX_DISTANCE();
    public abstract int DROPOFF_TERRITORY_SHIPS();

    public abstract double DROPOFF_TURNS();
    public abstract double DROPOFF_EXTRA_DIST_BONUS();
    public abstract double DROPOFF_HALITE_DROPOFF();
    public abstract double DROPOFF_HALITE();

    // Ship building
    public double SHIP_ADVANTAGE_STOP() {return 10;}
    public double SHIP_DEFICIT_BUILD() {return 1.1;}
    public double MAX_SPAWN_TURNS() {return 0.75;}
    public double TOTAL_HALITE_COLLECTION() {return 0.9;}
    public double CARRIED_PROPORTION() {return 0.0;}
    public abstract double SPAWN_INSPIRATION_BONUS();

    // Endgame aggro
    public double UNSAFE_RUSH_FACTOR(){return 2.0;}
    public double SAFE_RUSH_FACTOR() {return 1.5;}

    // Aggression decay (note: can't be optimized locally!)
    public double AGGRESSION_DECAY() {return 0.95;}


    public boolean EXPLOIT_THE_WEAK() {return false;}


    private static BotConstants constants;

    public static BotConstants get(){
        return constants;
    }

    public static void setSize(Game game) {
        if(game.players.size() == 2){
            if(game.map.width == 32) {
                constants = new BotConstants_2_32();
            }
            else if(game.map.width == 40) {
                constants = new BotConstants_2_40();
            }
            else if(game.map.width == 48) {
                constants = new BotConstants_2_48();
            }
            else if(game.map.width == 56) {
                constants = new BotConstants_2_56();
            }
            else constants = new BotConstants_2_64();
        }
        else {
            if(game.map.width < 44) {
                constants = new BotConstants4PSmall();
            }
            else if(game.map.width == 48) {
                constants = new BotConstants_4_48();
            }
            else if(game.map.width == 56) {
                constants = new BotConstants_4_56();
            }
            else constants = new BotConstants4PLarge();
        }
    }

    public static void setConfigOverrides(Map<String, String> configOverrides) {
        if(!configOverrides.isEmpty()) constants = new BotConstantsArgs(constants, configOverrides);
    }

    // Constants from here are not expected to be changed in normal operation.

    // Milliseconds for calculating mining scores. This is (at time of writing) the only significant per-ship work, and
    // everything else is pretty quick.
    public int MINING_SCORE_TIME() {
        return 1700;
    }

    // Local flag for using the "Total iterations" setting.
    public int USE_TOTAL_ITERATIONS() {
        return 1;
    }

    // Used to override and get an aggro bot
    public int AGGRO_PLAYERS() {
        return 0;
    }

    // Specify the complexity of the algorithm.
    public int ITERATIONS_ALLOWED() {
        return 20000;
    }

    public int RETURN_SAFETY_MARGIN() {
        return 2;
    }

    public int MIN_EXCEPTIONAL_HALITE() {
        return 1000;
    }

    public int ABANDON_GUARD_DUTY() {
        return 100;
    }

    public int ENEMY_SPAWN_TURNS() {
        return 5;
    }

    public double DROPOFF_HALITE_SHIP_TURN() {
        return 0.0;
    }

    public double SHIP_VALUE_DECAY() {
        return 0.95;
    }

    public int PLAN_HORIZON() {
        return 2;
    }

    public double TERRITORY_STRUCTURE_WEIGHT() {
        return 4.32934;
    }

    public double EXCEPTIONAL_SQUARE_PROPORTION() {
        return 6.0;
    }

    public int EXCEPTIONAL_ENEMY_DISTANCE() {
        return 3;
    }

    public int ASSUMED_RETURNING_HALITE() {
        return 900;
    }

    public int FUTURE_THRESHOLD_RADIUS() {
        return 4;
    }

    public double FUTURE_THRESHOLD_DROPOFF() {
        return 0.8;
    }

    public int TURNS_TO_FUTURE_PLAN() {
        return 10;
    }

    public int MAX_HUNTING_RADIUS() {
        return 2;
    }

    public double AGGRESSION_SAFETY_MARGIN() {
        return 300;
    }

    public abstract int FIRST_DROPOFF_SHIPS();

    public abstract int SECOND_DROPOFF_SHIPS();

    public double STAYING_RETURN_WEIGHT() {
        return 0.5;
    }

    public double MAX_NEARBY_RATIO() {
        return 2.0;
    }

    public int STATIONARY_THRESHOLD_BONUS() {
        return 100;
    }
    
    public int NEARBY_HALITE_RADIUS() {
        return 7;
    }

    public double NEARBY_HALITE_DROPOFF() {
        return 0.8;
    }

    public double MAX_NEARBY_HALITE_BONUS() {
        return 0.1;
    }

    public int RETURNING_UNSAFE_PENALTY() {
        return 2;
    }
}

