package bots.current_bot.navigation;

import bots.current_bot.dropoffs.DropoffPlan;
import bots.current_bot.spawning.SpawnDecider;
import bots.current_bot.utils.BotConstants;
import bots.current_bot.utils.CommonFunctions;
import bots.current_bot.utils.Logger;
import hlt.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates various stats which it's more efficient to calculate once each turn than on the fly.
 */
public class MapStatsKeeper {
    // What squares are inspired, for every player
    private static boolean[][][] inspiredMaps;

    // The maximum halite a ship can have to safely go to each square, for each player
    private static double[][][] haliteThreshholdMap;

    // A smoothed version of haliteThreshholdMap, for us only.
    private static double[][] futureThresholdMap;

    // Smoothed amount of halite near each square.
    private static double[][] nearbyHaliteMap;

    // Largest number in the nearbyHaliteMap, used for normalisation.
    private static double maxNearbyHalite;

    // Distance to the nearest dropoff for every square and player.
    private static int[][][] nearestDropoffDistance;
    // Location of the nearest dropoff for every square and player.
    private static Position[][][] nearestDropoff;

    // Distance to the nearest enemy for every square.
    private static int[][] nearestEnemyDistance;

    // Last turn these maps were updated.
    private static int turnSeen = -1;

    // The set of positions some enemy ship is brave enough to go to.
    private static Set<Position> enemyHappyWithCollision;

    // For every other player, how aggressive they are when moving to empty squares adjacent to enemies.
    private static double[] moveEmptyAggression;
    // For every other player, how aggressive they are when moving to squares with enemies.
    private static double[] moveFullAggression;

    // A load of trackers used to detect collisions and potential collisions.
    private static Map<EntityId, Integer> previousTurnHalites = new HashMap<>();
    private static Map<EntityId, Position> previousShipsToPositions = new HashMap<>();
    private static Map<Position, EntityId> previousPositionsToShips = new HashMap<>();
    private static Map<EntityId, PlayerId> previousShipOwners = new HashMap<>();
    private static Set<EntityId> previousShips = new HashSet<>();
    private static Map<EntityId, Boolean> surrounded = new HashMap<>();
    private static int[][] previousTurnHalite;

    private static boolean[][] getInspirationMap(Game game, PlayerId player) {
        // Work out what is inspired.
        boolean[][] inspiration = new boolean[game.map.height][game.map.width];
        for(int x=0; x < game.map.height; x++) {
            for (int y = 0; y < game.map.height; y++) {
                Position pos = Position.getPosition(x, y);
                int enemies = 0;
                for(Position p : CommonFunctions.getNeighbourhood(game.map, pos, Constants.INSPIRATION_RADIUS)) {
                    if(game.map.at(p).hasShip() && !game.map.at(p).ship.owner.equals(player)) enemies++;
                }
                boolean inspired = enemies >= Constants.INSPIRATION_SHIP_COUNT;
                inspiration[pos.x][pos.y] = inspired;
            }
        }
        return inspiration;
    }

    public static void updateMaps(Game game) {
        Logger.info(String.format("Updating inspiration maps for turn %d", game.turnNumber));
        turnSeen = game.turnNumber;
        updateAggressionMaps(game);

        inspiredMaps = new boolean[game.players.size()][game.map.height][game.map.width];
        for(Player player : game.players) {
            inspiredMaps[player.id.id] = getInspirationMap(game, player.id);
        }
        haliteThreshholdMap = new double[game.players.size()][game.map.height][game.map.width];
        for(Player player : game.players) {
            haliteThreshholdMap[player.id.id] = playerHaliteThresholdMap(game, player);
        }
        futureThresholdMap = new double[game.map.height][game.map.width];
        for(int x=0; x< game.map.height; x++){
            for(int y=0; y < game.map.width; y++) {
                futureThresholdMap[x][y] = thresholdFuture(game, game.me, x, y);
            }
        }

        enemyHappyWithCollision = calculateEnemyCollisionMap(game);

        // Update the maps for nearest dropoffs and their distances. No idea why this isn't its own function.
        nearestDropoff = new Position[game.players.size()][game.map.height][game.map.width];
        nearestDropoffDistance = new int[game.players.size()][game.map.height][game.map.width];
        for(Player player : game.players) {
            int id = player.id.id;
            List<Position> dropoffPositions = player.dropoffs.values().stream().map(d -> d.position).collect(Collectors.toList());
            for(int x=0; x< game.map.height; x++){
                for(int y=0; y < game.map.width; y++){
                    int bestDistance = game.map.calculateDistance(x, y, player.shipyard.position);
                    Position bestDropoff = player.shipyard.position;
                    for(Position p : dropoffPositions) {
                        int distance = game.map.calculateDistance(x, y, p);
                        if(distance < bestDistance) {
                            bestDistance = distance;
                            bestDropoff = p;
                        }
                    }
                    nearestDropoff[id][x][y] = bestDropoff;
                    nearestDropoffDistance[id][x][y] = bestDistance;
                }
            }
        }

        updateNearestEnemyDistance(game);

        updateNearbyHaliteMap(game);

    }

    private static void updateNearbyHaliteMap(Game game) {
        // Calculate the amount of nearby halite at every square. We count squares exponentially less with distance.
        nearbyHaliteMap = new double[game.map.height][game.map.width];
        maxNearbyHalite = 0;
        for(int x=0; x < game.map.height; x++) {
            for (int y = 0; y < game.map.height; y++) {
                Position pos =  Position.getPosition(x, y);
                double nearbyHalite = 0;
                for(int distance=1; distance<=BotConstants.get().NEARBY_HALITE_RADIUS(); distance++) {
                    double weight = Math.pow(BotConstants.get().NEARBY_HALITE_DROPOFF(), distance);
                    for(int dx = -distance; dx <= distance; dx++) {
                        int dy = distance - Math.abs(dx);
                        Position nbr = pos.withVectorOffset(game.map, dx, dy);
                        nearbyHalite += weight * game.map.at(nbr).halite;
                        if(dy != 0) {
                            dy = -dy;
                            nbr = pos.withVectorOffset(game.map, dx, dy);
                            nearbyHalite += weight * game.map.at(nbr).halite;
                        }
                    }
                }
                if(nearbyHalite > maxNearbyHalite) maxNearbyHalite = nearbyHalite;
                nearbyHaliteMap[x][y] = nearbyHalite;
            }
        }
    }

    public static double getNearbyHaliteScore(Game game, int x, int y) {
        if(game.turnNumber > turnSeen) updateMaps(game);
        return nearbyHaliteMap[x][y] / maxNearbyHalite;
    }

    private static void updateNearestEnemyDistance(Game game) {
        // Finds the distance to the nearest enemy at every square, by searching outwards from enemy ships.
        nearestEnemyDistance = new int[game.map.height][game.map.width];

        Set<Position> found = new HashSet<>();
        List<Position> currentLayer = new LinkedList<>();
        for(Player p : game.players) {
            if(p.equals(game.me)) continue;
            for(Ship s : p.ships.values()) {
                found.add(s.position);
                currentLayer.add(s.position);
            }
        }
        int distance = 1;
        while(!currentLayer.isEmpty()) {
            List<Position> nextLayer = new LinkedList<>();
            for(Position p : currentLayer) {
                for(Position nbr : CommonFunctions.getNeighbourhood(game.map, p, 1)) {
                    if(found.contains(nbr)) continue;
                    found.add(nbr);
                    nearestEnemyDistance[nbr.x][nbr.y] = distance;
                    nextLayer.add(nbr);
                }
            }
            currentLayer = nextLayer;
            distance++;
        }
    }

    private static void updateAggressionMaps(Game game) {
        // Update how aggressive we think everyone is. This must be called early in the update process - it relies on
        // the previous turn's collision threshold maps.

        if(game.turnNumber < 2) {
            // Initialise the maps.
            previousTurnHalite = new int[game.map.height][game.map.width];

            // We assume everyone is a wimp. We'll probably get a decent picture before too many collisions!
            moveEmptyAggression = new double[game.players.size()];
            for(int i = 0; i<game.players.size(); i++) moveEmptyAggression[i] = -Constants.MAX_HALITE;
            moveFullAggression = new double[game.players.size()];
            for(int i = 0; i<game.players.size(); i++) moveFullAggression[i] = -Constants.MAX_HALITE;
        }
        for(int i = 0; i<game.players.size(); i++) {
            if(moveEmptyAggression[i] > 0) moveEmptyAggression[i] *= BotConstants.get().AGGRESSION_DECAY();
        }
        Map<EntityId, PlayerId> nextPreviousShipOwners = new HashMap<>();
        Map<EntityId, Position> nextShipsToPositions = new HashMap<>();
        Map<Position, EntityId> nextPositionsToShips = new HashMap<>();
        Map<EntityId, Integer> nextPreviousTurnHalites = new HashMap<>();
        Set<EntityId> nextPreviousShips = new HashSet<>();
        Set<EntityId> collisionShips = new HashSet<>(previousShips);


        for(Player p : game.players) {
            for (EntityId shipId : p.ships.keySet()) {
                Ship ship = p.ships.get(shipId);
                nextPreviousShips.add(shipId);
                collisionShips.remove(shipId);
                // Ignore surrounded ships, because they do crazy stuff.
                if(previousTurnHalites.containsKey(shipId) && !surrounded.get(shipId)) {
                    int previousHalite = previousTurnHalites.get(shipId);

                    // The maximum halite we judged as wise to make this move. If the square didn't have an enemy turtle
                    // adjacent this will be 2000.
                    double threshold = haliteThreshholdMap[ship.owner.id][ship.position.x][ship.position.y];
                    // This is how much they exceeded a wise move by.
                    double aggression = previousHalite - threshold;
                    boolean wasMove = !ship.position.equals(previousShipsToPositions.get(shipId));

                    // We only care about moves. We assume everyone is brave enough to stay still.
                    if(wasMove) {
                        EntityId previousShip = previousPositionsToShips.get(ship.position);
                        boolean hadEnemy = previousShip != null && !previousShipOwners.get(previousShip).equals(p.id);
                        if(hadEnemy) {
                            // This was a move onto an enemy turtle.
                            if(aggression > moveFullAggression[p.id.id]) {
                                Logger.info(String.format("Player %d has reached a new aggression %f for moves to enemies", p.id.id, aggression));
                                Logger.info(String.format("Ship %s moving to %s, threshold %f, halite %d", ship.id, ship.position, threshold, previousTurnHalites.get(shipId)));

                                // For moving onto enemy turtles, we are a bit conservative - after the first time a player
                                // does this, we move their score all the way to 0. This prevents some strings of collisions
                                // where a player gets gradually more aggressive.
                                moveFullAggression[p.id.id] = aggression < 0 ? 0 : aggression;
                            }
                        }
                        else {
                            // This was a move to an empty square.
                            if(aggression > moveEmptyAggression[p.id.id]) {
                                Logger.info(String.format("Player %d has reached a new aggression %f for moves to empties", p.id.id, aggression));
                                Logger.info(String.format("Ship %s moving to %s, threshold %f, halite %d", ship.id, ship.position, threshold, previousTurnHalites.get(shipId)));
                                moveEmptyAggression[p.id.id] = aggression;
                            }
                        }
                    }
                }
                nextPreviousTurnHalites.put(shipId, ship.halite);
                nextShipsToPositions.put(shipId, ship.position);
                nextPositionsToShips.put(ship.position, shipId);
                surrounded.put(shipId, isSurrounded(game, ship.position, p));
                nextPreviousShipOwners.put(shipId, p.id);
            }
        }

        // We also have to update the aggressions for actual collisions. Detect as many of these as possible.
        for(EntityId id : collisionShips) {
            Logger.info(String.format("Investigating the death of ship %s", id));
            Position pos_1 = previousShipsToPositions.get(id);
            EntityId culprit = null;
            boolean found = false;
            for(EntityId id_2 : collisionShips) {
                Position pos_2 = previousShipsToPositions.get(id_2);
                int distance = game.map.calculateDistance(pos_1, pos_2);
                if(distance > 2 || distance == 0) continue;
                if(found) {
                    Logger.info("Ambiguous collision - ignoring");
                    culprit = null;
                }
                else {
                    Logger.info(String.format("Found culprit %s", id_2));
                    culprit = id_2;
                    found = true;
                }
            }
            if(culprit != null) {
                PlayerId p_1 = previousShipOwners.get(id);
                PlayerId p_2 = previousShipOwners.get(culprit);
                if(p_1.equals(p_2)) {
                    Logger.info("Self collision. Well, it's your funeral.");
                    continue;
                }
                Position collisionSquare = null;
                boolean foundSquare = false;
                for(Position p : CommonFunctions.getNeighbourhood(game.map, previousShipsToPositions.get(culprit), 1)) {
                    int currentHalite = game.map.at(p).halite;
                    if(currentHalite > previousTurnHalite[p.x][p.y]) {
                        Logger.info(String.format("%s has increased in halite - collision site found!", p));
                        if(foundSquare){
                            Logger.info("Ambiguous site - ignoring");
                            collisionSquare = null;
                        }
                        else {
                            foundSquare = true;
                            collisionSquare = p;
                        }
                    }
                }
                if(collisionSquare != null) {
                    // We have found a collision, and know where it happened. We update the aggression maps as before.
                    for (EntityId shipId : new EntityId[]{id, culprit}) {
                        PlayerId p = previousShipOwners.get(shipId);
                        // Ignore surrounded ships, because they do crazy stuff.
                        if(previousTurnHalites.containsKey(shipId) && !surrounded.get(shipId)) {
                            int previousHalite = previousTurnHalites.get(shipId);
                            double threshold = haliteThreshholdMap[p.id][collisionSquare.x][collisionSquare.y];
                            // This is how much they exceeded a wise move by.
                            double aggression = previousHalite - threshold;
                            boolean wasMove = !collisionSquare.equals(previousShipsToPositions.get(shipId));

                            if(wasMove) {
                                EntityId previousShip = previousPositionsToShips.get(collisionSquare);
                                boolean hadEnemy = previousShip != null && !previousShipOwners.get(previousShip).equals(p);
                                Logger.info(String.format("Threshold %f, aggression %f, hadEnemy %s", threshold, aggression, hadEnemy));
                                if(hadEnemy) {
                                    if(aggression > moveFullAggression[p.id]) {
                                        Logger.info(String.format("Collision - Player %d has reached a new aggression %f for moves to enemies", p.id, aggression));
                                        Logger.info(String.format("Collision - Ship %s moving to %s, threshold %f, halite %d",
                                                shipId, collisionSquare, threshold, previousTurnHalites.get(shipId)));

                                        moveFullAggression[p.id] = aggression < 0 ? 0 : aggression;
                                    }
                                }
                                else {
                                    if(aggression > moveEmptyAggression[p.id]) {
                                        Logger.info(String.format("Collision - Player %d has reached a new aggression %f for moves to empties", p.id, aggression));

                                        Logger.info(String.format("Collision - Ship %s moving to %s, threshold %f, halite %d",
                                                shipId, collisionSquare, threshold, previousTurnHalites.get(shipId)));
                                        moveEmptyAggression[p.id] = aggression;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        previousPositionsToShips = nextPositionsToShips;
        previousShipsToPositions = nextShipsToPositions;
        previousTurnHalites = nextPreviousTurnHalites;
        previousShipOwners = nextPreviousShipOwners;
        previousShips = nextPreviousShips;
        for(int x=0; x< game.map.height; x++) {
            for(int y=0; y< game.map.height; y++) {
                previousTurnHalite[x][y] = game.map.cells[x][y].halite;
            }
        }
    }

    private static boolean isSurrounded(Game game, Position position, Player p) {
        for(Position nbr : CommonFunctions.getNeighbourhood(game.map, position, 1)) {
            if(nbr.equals(position)) continue;
            boolean noEnemies = true;
            for(Position nbr2 : CommonFunctions.getNeighbourhood(game.map, nbr, 1)) {
                if(CommonFunctions.hasEnemyShip(game, nbr2, p)) noEnemies = false;
            }
            if(noEnemies) return false;
        }
        return true;
    }

    private static Set<Position> calculateEnemyCollisionMap(Game game) {
        // Calculates where the enemy would be happy to collide.
        Set<Position> collisions = new HashSet<>();
        for(Player p : game.players) {
            double[][] map = haliteThreshholdMap[p.id.id];
            if(p.id.equals(game.myId)) continue;
            for(Ship s : p.ships.values()) {
                // We assume everyone will stand their ground.
                collisions.add(s.position);
                boolean canMove = CommonFunctions.moveCost(game.map.at(s.position).halite) <= s.halite;

                for(Position nbr : CommonFunctions.getNeighbourhood(game.map, s.position, 1)) {
                    if(!canMove && !nbr.equals(s.position)) continue;
                    boolean enemyPresent = CommonFunctions.hasEnemyShip(game, nbr, p);
                    double aggresion = enemyPresent ? moveFullAggression[p.id.id] : moveEmptyAggression[p.id.id];
                    // The enemy will move if their halite is less the correct threshold, plus their (possibly negative)
                    // aggression score, plus a safety margin.
                    if(s.halite <= map[nbr.x][nbr.y] + aggresion + BotConstants.get().AGGRESSION_SAFETY_MARGIN()) collisions.add(nbr);
                }
            }
        }
        return collisions;
    }

    private static double[][] playerHaliteThresholdMap(Game game, Player player) {
        // For a single player, calculates the maxium halite for them to be happy with a collision on each square.
        double[][] haliteThreshholdMap = new double[game.map.width][game.map.height];
        for(int x=0; x < game.map.height; x++) {
            for (int y = 0; y < game.map.height; y++) {
                Position pos = Position.getPosition(x, y);
                Integer leastSurroundingHalite = null;

                // Find the lowest halite enemy adjacent to the square.
                for(Position p : CommonFunctions.getNeighbourhood(game.map, pos, 1)) {
                    if(p.equals(pos)) continue;
                    if(CommonFunctions.hasEnemyShip(game, p, player)) {
                        Ship enemy = game.map.at(p).ship;
                        if(enemy.halite < CommonFunctions.moveCost(game.map.at(p).halite)) continue;
                        leastSurroundingHalite = Math.min(
                                enemy.halite,
                                leastSurroundingHalite == null ? Constants.MAX_HALITE : leastSurroundingHalite);
                    }
                }

                // For stationary enemies, we count their mining this turn into their halite.
                if(CommonFunctions.hasEnemyShip(game, pos, player)) {
                    Ship enemy = game.map.at(pos).ship;
                    boolean inspiration = getInspiration(game, pos, enemy.owner);
                    Integer amountMined = CommonFunctions.mineAmount(enemy, game.map.at(pos).halite, inspiration);
                    leastSurroundingHalite = Math.min(
                            enemy.halite + amountMined,
                            leastSurroundingHalite == null ? Constants.MAX_HALITE : leastSurroundingHalite);
                }

                // We can always visit our own dropoffs. They get a threshold of 2000. 2000 is convenient for our
                // aggression calculations, because it means that even if a 1000 ship moves to this square, the enemy
                // aggression score stays at -1000.
                if(game.map.at(pos).hasStructure() && game.map.at(pos).structure.owner.equals(player.id)) {
                    haliteThreshholdMap[pos.x][pos.y] = 2*Constants.MAX_HALITE + 0.5;
                }
                else if(leastSurroundingHalite != null) {
                    if(game.map.at(pos).hasStructure() && !game.map.at(pos).structure.owner.equals(player.id)) {
                        // Enemy dropoff with adjacent ship - these are never safe to visit.
                        haliteThreshholdMap[pos.x][pos.y] = -1;
                    }
                    else {
                        // Calculate the control of this square, and therefore the threshold for visiting.
                        double territory = getTerritory(game, pos, player);
                        double threshhold = haliteThreshhold(game, territory, leastSurroundingHalite);
                        haliteThreshholdMap[pos.x][pos.y] = threshhold;
                    }
                }
                else {
                    // No adjacent ships - threshold of 2000.
                    haliteThreshholdMap[pos.x][pos.y] = 2*Constants.MAX_HALITE + 0.5;
                }
            }
        }
        return haliteThreshholdMap;
    }

    private static double getTerritory(Game game, Position pos, Player player) {
        // Control of a square. Roughly the proportion of nearby ships and structures that is owned by the player, with
        // nearer ones counted exponentially more.
        double enemies = BotConstants.get().BASE_TERRITORY_WEIGHT();
        double friends = BotConstants.get().BASE_TERRITORY_WEIGHT();
        boolean foundFriend = false;
        boolean foundEnemy = false;
        for(Position p : CommonFunctions.getNeighbourhood(game.map, pos, BotConstants.get().DROPPED_TERRITORY_RADIUS())) {
            if(game.map.at(p).hasShip()) {
                double weight = Math.pow(BotConstants.get().TERRITORY_DROPOFF(), game.map.calculateDistance(p, pos));
                if(!game.map.at(p).ship.owner.equals(player.id)) {
                    if(foundEnemy) enemies+=weight;
                    foundEnemy = true;
                }
                else {
                    if(foundFriend) friends+=weight;
                    foundFriend = true;
                }
            }
            if(game.map.at(p).hasStructure()) {
                double weight = Math.pow(BotConstants.get().TERRITORY_DROPOFF(), game.map.calculateDistance(p, pos)) * BotConstants.get().TERRITORY_STRUCTURE_WEIGHT();
                if(!game.map.at(p).structure.owner.equals(player.id)) {
                    enemies+=weight;
                }
                else {
                    friends+=weight;
                }
            }
        }
        return (friends)/(friends+enemies);
    }

    public static boolean getInspiration(Game game, int x, int y, PlayerId owner) {
        if(game.turnNumber > turnSeen) updateMaps(game);
        return inspiredMaps[owner.id][x][y];
    }

    public static boolean getInspiration(Game game, Position destination, PlayerId owner) {
        return getInspiration(game, destination.x, destination.y, owner);
    }

    public static boolean happyWithCollision(Game game, Player player, Position pos, Integer halite, boolean isStationary) {
        if(game.turnNumber > turnSeen) updateMaps(game);
        // The player is happy to collide if the ship's halite is less than the threshold for this square. We add in a
        // small bonus for staying still.
        int bonus = isStationary ? BotConstants.get().STATIONARY_THRESHOLD_BONUS() : 0;
        return halite - bonus <= haliteThreshholdMap[player.id.id][pos.x][pos.y];
    }

    public static boolean happyWithCollisionFuture(Game game, Position pos, int halite, int turnsInFuture) {
        // Interpolates between the collision threshold now for the square, and a smoothed version, depending on how far
        // in the future we are looking.
        if(game.turnNumber > turnSeen) updateMaps(game);
        double futureProp = ((double)turnsInFuture) / BotConstants.get().TURNS_TO_FUTURE_PLAN();
        futureProp = futureProp > 1 ? 1.0 : futureProp;
        double thresh = haliteThreshholdMap[game.myId.id][pos.x][pos.y];
        thresh = thresh < 0 ? 0 : thresh;
        thresh = thresh > Constants.MAX_HALITE ? Constants.MAX_HALITE : thresh;
        return halite <= futureProp*futureThresholdMap[pos.x][pos.y] + (1-futureProp) * thresh;
    }

    private static double thresholdFuture(Game game, Player player, int x, int y) {
        // The collision threshold for a square in the far future. This is a weighted average of the nearby thresholds
        // now, clipped to [0, 1000], with nearby thresholds counting more.
        double totalThreshold = 0;
        double totalWeight = 0;
        Position pos = Position.getPosition(x, y);
        for(int distance=0; distance<=BotConstants.get().FUTURE_THRESHOLD_RADIUS(); distance++) {
            double weight = Math.pow(BotConstants.get().FUTURE_THRESHOLD_DROPOFF(), distance);
            for(int dx = -distance; dx <= distance; dx++) {
                int dy = distance - Math.abs(dx);
                Position nbr = pos.withVectorOffset(game.map, dx, dy);
                totalThreshold += weight * game.map.at(nbr).halite;
                totalWeight += weight;
                if(dy != 0) {
                    dy = -dy;
                    nbr = pos.withVectorOffset(game.map, dx, dy);
                    double thresh = haliteThreshholdMap[player.id.id][nbr.x][nbr.y];
                    thresh = thresh < 0 ? 0 : thresh;
                    thresh = thresh > Constants.MAX_HALITE ? Constants.MAX_HALITE : thresh;
                    totalThreshold += weight * thresh;
                    totalWeight += weight;
                }
            }
        }
        return totalThreshold / totalWeight;
    }

    public static boolean canVisit(Game game, Position pos, Ship ship) {
        return canVisit(game, pos, ship.halite, ship.position.equals(pos));
    }


    public static boolean canVisit(Game game, Position pos, int shipHalite, boolean isStationary) {
        if(game.turnNumber > turnSeen) updateMaps(game);
        // We visit this square if we are happy to collide there, or if no enemy is and we are exploiting that.
        return happyWithCollision(game, game.me, pos, shipHalite, isStationary) || (
                BotConstants.get().EXPLOIT_THE_WEAK() && !enemyHappyWithCollision.contains(pos));
    }

    public static boolean canVisitFuture(Game game, Position pos, int shipHalite, int turnNumber) {
        if(game.turnNumber > turnSeen) updateMaps(game);
        // We visit this square if we are happy to collide there, or if no enemy is and we are exploiting that.
        return happyWithCollisionFuture(game, pos, shipHalite, turnNumber) || (
                BotConstants.get().EXPLOIT_THE_WEAK() && !enemyHappyWithCollision.contains(pos));
    }

    public static Position nearestDropoff(
            Position destination, Player player, Game game, Optional<DropoffPlan> plan) {
        int id = player.id.id;
        if(game.turnNumber > turnSeen) updateMaps(game);
        int bestDistance = nearestDropoffDistance[id][destination.x][destination.y];
        if(plan.isPresent() && game.map.calculateDistance(destination, plan.get().destination) < bestDistance) return plan.get().destination;
        return nearestDropoff[id][destination.x][destination.y];
    }

    public static int nearestDropoffDistance(Position destination, Player player, Game game, Optional<DropoffPlan> plan) {
        if(game.turnNumber > turnSeen) updateMaps(game);
        int id = player.id.id;
        int bestDistance = nearestDropoffDistance[id][destination.x][destination.y];
        if(plan.isPresent()) {
            int fakeDist = game.map.calculateDistance(destination, plan.get().destination);
            return fakeDist < bestDistance ? fakeDist : bestDistance;
        }
        return bestDistance;
    }

    private static double haliteThreshhold(Game game, double territory, double effectiveEnemyHalite) {
        double t = territory;
        double e = effectiveEnemyHalite;
        // In normal operation p is the number of enemy players. We can also override it to produce more aggressive
        // versions for testing.
        double p = BotConstants.get().AGGRO_PLAYERS() == 0 ? game.players.size() - 1 : BotConstants.get().AGGRO_PLAYERS();
        double s = SpawnDecider.shipValue;

        // This is the halite carred that makes
        // OUR_VALUE_AFTER_COLLISION - OUR_VALUE_BEFORE_COLLISION - (ENEMY_VALUE_AFTER_COLLISION - ENEMY_VALUE_BEFORE_COLLISION)/ p
        // equal 0.
        double thresh = (e*t*(p + 1) - s*(p-1)) / ((1-t) * (1+p));
        return thresh >= 0 ? thresh : -1;

    }

    public static int nearestEnemy(Game game, Position p) {
        if(game.turnNumber > turnSeen) updateMaps(game);
        return nearestEnemyDistance[p.x][p.y];
    }

    public static int nearestEnemy(Game game, int x, int y) {
        if(game.turnNumber > turnSeen) updateMaps(game);
        return nearestEnemyDistance[x][y];
    }
}
