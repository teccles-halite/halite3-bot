package hlt;

import java.util.ArrayList;

public class Game {
    public int turnNumber;
    public final PlayerId myId;
    public final ArrayList<Player> players = new ArrayList<>();
    public final Player me;
    public final GameMap map;
    public final boolean log;

    public Game(boolean local, boolean log) {
        Constants.populateConstants(Input.readLine());

        final Input input = Input.readInput();
        final int numPlayers = input.getInt();
        myId = new PlayerId(input.getInt());
        this.log = log;

        if(log) {
            Log.open(myId.id, local);
            Flogger.open(myId.id);
        }

        for (int i = 0; i < numPlayers; ++i) {
            players.add(Player._generate());
        }
        me = players.get(myId.id);
        map = GameMap._generate();
    }

    public void ready(final String name) {
        System.out.println(name);
    }

    public void updateFrame() {
        turnNumber = Input.readInput().getInt();
        if(log) Log.log("=============== TURN " + turnNumber + " ================");

        for (int i = 0; i < players.size(); ++i) {
            final Input input = Input.readInput();

            final PlayerId currentPlayerId = new PlayerId(input.getInt());
            final int numShips = input.getInt();
            final int numDropoffs = input.getInt();
            final int halite = input.getInt();

            players.get(currentPlayerId.id)._update(numShips, numDropoffs, halite);
        }

        map._update();

        for (final Player player : players) {
            for (final Ship ship : player.ships.values()) {
                map.at(ship).markUnsafe(ship);
            }

            map.at(player.shipyard).structure = player.shipyard;

            for (final Dropoff dropoff : player.dropoffs.values()) {
                map.at(dropoff).structure = dropoff;
            }
        }
    }

    public void endTurn(final ArrayList<? extends Command> commands) {
        for (final Command command : commands) {
            System.out.print(command.command);
            System.out.print(' ');
        }
        System.out.println();
    }

    public int turnsRemaining() {
        return Constants.MAX_TURNS - turnNumber;
    }
}
