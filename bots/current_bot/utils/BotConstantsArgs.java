package bots.current_bot.utils;

import java.util.Map;
import java.util.Optional;

public class BotConstantsArgs extends BotConstants {
    private Optional<Integer> iterationsAllowed = Optional.empty();
    private BotConstants baseConfig;

    private Optional<Integer> aggroPlayers = Optional.empty();
    private Optional<Double> spawnInspirationBonus = Optional.empty();
    private Optional<Double> maxSpawnTurns = Optional.empty();
    private Optional<Double> totalHaliteCollection = Optional.empty();
    private Optional<Double> territoryDropoff = Optional.empty();
    private Optional<Double> baseTerritoryWeight = Optional.empty();

    // Dropoffs
    private Optional<Integer> shipsPerDropoff = Optional.empty();
    private Optional<Integer> firstDropoffShips = Optional.empty();
    private Optional<Integer> secondDropoffShips = Optional.empty();
    private Optional<Double> dropoffHalite = Optional.empty();
    private Optional<Double> dropoffSquareBonus = Optional.empty();
    private Optional<Integer> minDropoffDistance = Optional.empty();
    private Optional<Integer> dropoffMinNearbyShips = Optional.empty();
    private Optional<Integer> dropoffTerritoryShips = Optional.empty();
    private Optional<Integer> dropoffShipMaxDistance = Optional.empty();
    private Optional<Double> dropoffHaliteDropoff = Optional.empty();
    private Optional<Double> dropoffExtraDistBonus = Optional.empty();
    private Optional<Double> dropoffTurns = Optional.empty();
    private Optional<Double> dropoffDistancePenalty = Optional.empty();
    private Optional<Double> dropoffHaliteShipTurn = Optional.empty();

    // Mining and returning
    private Optional<Double> haliteToReturn = Optional.empty();
    private Optional<Double> assumedReturningProportion = Optional.empty();
    private Optional<Double> returnSpeed = Optional.empty();
    private Optional<Double> returnTerritoryDropoff = Optional.empty();
    private Optional<Double> haliteToAlwaysReturn = Optional.empty();
    private Optional<Double> inspirationTurnDropoff = Optional.empty();
    private Optional<Double> minInspirationBonus = Optional.empty();
    private Optional<Double> returnRatio = Optional.empty();;
    private Optional<Double> carriedProportion = Optional.empty();
    private Optional<Double> territoryStructureWeight = Optional.empty();


    public BotConstantsArgs(BotConstants constants, Map<String, String> args) {
        baseConfig = constants;
        for(String key : args.keySet()) {
            switch (key) {
                case "DROPOFF_DISTANCE_PENALTY":
                    dropoffDistancePenalty = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "HALITE_TO_RETURN":
                    haliteToReturn = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "ASSUMED_RETURNING_PROPORTION":
                    assumedReturningProportion = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "RETURN_SPEED":
                    returnSpeed = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "RETURN_TERRITORY_DROPOFF":
                    returnTerritoryDropoff = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "HALITE_TO_ALWAYS_RETURN":
                    haliteToAlwaysReturn = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "ITERATIONS_ALLOWED":
                    iterationsAllowed = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "INSPIRATION_TURN_DROPOFF":
                    inspirationTurnDropoff = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "MIN_INSPIRATION_BONUS":
                    minInspirationBonus = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "SPAWN_INSPIRATION_BONUS":
                    spawnInspirationBonus = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "CARRIED_PROPORTION":
                    carriedProportion = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "MAX_SPAWN_TURNS":
                    maxSpawnTurns = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "TOTAL_HALITE_COLLECTION":
                    totalHaliteCollection = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "TERRITORY_DROPOFF":
                    territoryDropoff = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "TERRITORY_STRUCTURE_WEIGHT":
                    territoryStructureWeight = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "BASE_TERRITORY_WEIGHT":
                    baseTerritoryWeight = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "RETURN_RATIO":
                    returnRatio = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "AGGRO_PLAYERS":
                    aggroPlayers = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "SHIPS_PER_DROPOFF":
                    shipsPerDropoff = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "FIRST_DROPOFF_SHIPS":
                    firstDropoffShips = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "SECOND_DROPOFF_SHIPS":
                    secondDropoffShips = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "DROPOFF_HALITE":
                    dropoffHalite = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "DROPOFF_SQUARE_BONUS":
                    dropoffSquareBonus = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "DROPOFF_HALITE_DROPOFF":
                    dropoffHaliteDropoff = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "DROPOFF_EXTRA_DIST_BONUS":
                    dropoffExtraDistBonus = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "DROPOFF_TURNS":
                    dropoffTurns = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                case "DROPOFF_SHIP_MAX_DISTANCE":
                    dropoffShipMaxDistance = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "DROPOFF_TERRITORY_SHIPS":
                    dropoffTerritoryShips = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "MIN_DROPOFF_DISTANCE":
                    minDropoffDistance = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "DROPOFF_MIN_NEARBY_SHIPS":
                    dropoffMinNearbyShips = Optional.of(Integer.parseInt(args.get(key)));
                    break;
                case "DROPOFF_HALITE_SHIP_TURN":
                    dropoffHaliteShipTurn = Optional.of(Double.parseDouble(args.get(key)));
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Key %s is not a valid argument", key));
            }
        }
    }

    // Mining and returning
    public double MIN_INSPIRATION_BONUS() { return minInspirationBonus.orElse(baseConfig.MIN_INSPIRATION_BONUS());    }
    public double INSPIRATION_TURN_DROPOFF() {  return inspirationTurnDropoff.orElse(baseConfig.INSPIRATION_TURN_DROPOFF());}
    public double DROPOFF_DISTANCE_PENALTY(){return dropoffDistancePenalty.orElse(baseConfig.DROPOFF_DISTANCE_PENALTY());}
    public double HALITE_TO_RETURN(){return haliteToReturn.orElse(baseConfig.HALITE_TO_RETURN());}
    public double ASSUMED_RETURNING_PROPORTION() {return assumedReturningProportion.orElse(baseConfig.ASSUMED_RETURNING_PROPORTION());}
    public double RETURN_SPEED() {return returnSpeed.orElse(baseConfig.RETURN_SPEED());}
    public double HALITE_TO_ALWAYS_RETURN() {return haliteToAlwaysReturn.orElse(baseConfig.HALITE_TO_ALWAYS_RETURN());}

    public double SPAWN_INSPIRATION_BONUS() {return spawnInspirationBonus.orElse(baseConfig.SPAWN_INSPIRATION_BONUS());}
    public double CARRIED_PROPORTION() {return carriedProportion.orElse(baseConfig.CARRIED_PROPORTION());}


    @Override
    public boolean EXPLOIT_THE_WEAK() {
        return baseConfig.EXPLOIT_THE_WEAK();
    }

    public double MAX_SPAWN_TURNS() {
        return maxSpawnTurns.orElse(baseConfig.MAX_SPAWN_TURNS());
    }


    @Override
    public double TOTAL_HALITE_COLLECTION() {
        return totalHaliteCollection.orElse(baseConfig.TOTAL_HALITE_COLLECTION());
    }

    @Override
    public double TERRITORY_DROPOFF() {
        return territoryDropoff.orElse(baseConfig.TERRITORY_DROPOFF());
    }

    @Override
    public double TERRITORY_STRUCTURE_WEIGHT() {
        return territoryStructureWeight.orElse(baseConfig.TERRITORY_STRUCTURE_WEIGHT());
    }

    @Override
    public int FIRST_DROPOFF_SHIPS() {
        return firstDropoffShips.orElse(baseConfig.FIRST_DROPOFF_SHIPS());
    }

    @Override
    public int SECOND_DROPOFF_SHIPS() {
        return secondDropoffShips.orElse(baseConfig.SECOND_DROPOFF_SHIPS());
    }


    public int AGGRO_PLAYERS(){
        return aggroPlayers.orElse(baseConfig.AGGRO_PLAYERS());
    }

    public double RETURN_RATIO(){
        return returnRatio.orElse(baseConfig.RETURN_RATIO());
    }

    @Override
    public int MIN_DROPOFF_DISTANCE() {
        return minDropoffDistance.orElse(baseConfig.MIN_DROPOFF_DISTANCE());
    }

    @Override
    public double BASE_TERRITORY_WEIGHT() {
        return baseTerritoryWeight.orElse(baseConfig.BASE_TERRITORY_WEIGHT());
    }


    @Override
    public int SHIPS_PER_DROPOFF() {
        return shipsPerDropoff.orElse(baseConfig.SHIPS_PER_DROPOFF());
    }

    @Override
    public int DROPOFF_MIN_NEARBY_SHIPS() {
        return dropoffMinNearbyShips.orElse(baseConfig.DROPOFF_MIN_NEARBY_SHIPS());
    }

    @Override
    public int DROPOFF_SHIP_MAX_DISTANCE() {
        return dropoffShipMaxDistance.orElse(baseConfig.DROPOFF_SHIP_MAX_DISTANCE());
    }

    @Override
    public int DROPOFF_TERRITORY_SHIPS() {
        return dropoffTerritoryShips.orElse(baseConfig.DROPOFF_TERRITORY_SHIPS());
    }

    @Override
    public double DROPOFF_TURNS() {
        return dropoffTurns.orElse(baseConfig.DROPOFF_TURNS());
    }

    public double DROPOFF_HALITE() {
        return dropoffHalite.orElse(baseConfig.DROPOFF_HALITE());
    }

    @Override
    public double DROPOFF_EXTRA_DIST_BONUS() {
        return dropoffExtraDistBonus.orElse(baseConfig.DROPOFF_EXTRA_DIST_BONUS());
    }

    @Override
    public double DROPOFF_HALITE_DROPOFF() {
        return dropoffHaliteDropoff.orElse(baseConfig.DROPOFF_HALITE_DROPOFF());
    }

    @Override
    public double DROPOFF_HALITE_SHIP_TURN() {
        return dropoffHaliteShipTurn.orElse(baseConfig.DROPOFF_HALITE_SHIP_TURN());
    }

    @Override
    public int ITERATIONS_ALLOWED() {
        return iterationsAllowed.orElse(baseConfig.ITERATIONS_ALLOWED());
    }

}
