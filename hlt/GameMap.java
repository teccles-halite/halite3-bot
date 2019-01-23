package hlt;

import java.util.ArrayList;

public class GameMap {
    public final int width;
    public final int height;
    public final MapCell[][] cells;

    public GameMap(final int width, final int height) {
        this.width = width;
        this.height = height;

        cells = new MapCell[height][];
        for (int x = 0; x < height; ++x) {
            cells[x] = new MapCell[width];
        }
    }

    public MapCell at(final Position position) {
        return cells[position.x][position.y];
    }

    public MapCell at(final Entity entity) {
        return at(entity.position);
    }

    public int calculateDistance(final Position source, final Position target) {

        final int dx = Math.abs(source.x - target.x);
        final int dy = Math.abs(source.y - target.y);

        final int toroidal_dx = Math.min(dx, width - dx);
        final int toroidal_dy = Math.min(dy, height - dy);

        return toroidal_dx + toroidal_dy;
    }

    public int calculateDistance(int x_1, int y_1, int x_2, int y_2) {

        final int dx = Math.abs(x_1 - x_2);
        final int dy = Math.abs(y_1 - y_2);

        final int toroidal_dx = Math.min(dx, width - dx);
        final int toroidal_dy = Math.min(dy, height - dy);

        return toroidal_dx + toroidal_dy;
    }


    public int calculateDistance(int x, int y, Position p) {
        final int dx = Math.abs(x - p.x);
        final int dy = Math.abs(y - p.y);

        final int toroidal_dx = Math.min(dx, width - dx);
        final int toroidal_dy = Math.min(dy, height - dy);

        return toroidal_dx + toroidal_dy;
    }

    public ArrayList<Direction> getUnsafeMoves(final Position source, final Position destination) {
        if(source.equals(destination)) {
            ArrayList<Direction> list = new ArrayList<>();
            list.add(Direction.STILL);
            return list;
        }
        final ArrayList<Direction> possibleMoves = new ArrayList<>();

        final int dx = Math.abs(source.x - destination.x);
        final int dy = Math.abs(source.y - destination.y);
        final int wrapped_dx = width - dx;
        final int wrapped_dy = height - dy;

        if (source.x < destination.x) {
            possibleMoves.add(dx > wrapped_dx ? Direction.WEST : Direction.EAST);
        } else if (source.x > destination.x) {
            possibleMoves.add(dx < wrapped_dx ? Direction.WEST : Direction.EAST);
        }

        if (source.y < destination.y) {
            possibleMoves.add(dy > wrapped_dy ? Direction.NORTH : Direction.SOUTH);
        } else if (source.y > destination.y) {
            possibleMoves.add(dy < wrapped_dy ? Direction.NORTH : Direction.SOUTH);
        }

        return possibleMoves;
    }

    public Direction naiveNavigate(final Ship ship, final Position destination) {
        // getUnsafeMoves normalizes for us
        for (final Direction direction : getUnsafeMoves(ship.position, destination)) {
            final Position targetPos = ship.position.directionalOffset(direction, this);
            if (!at(targetPos).hasShip()) {
                at(targetPos).markUnsafe(ship);
                return direction;
            }
        }

        return Direction.STILL;
    }

    void _update() {
        for (int x = 0; x < height; ++x) {
            for (int y = 0; y < width; ++y) {
                cells[x][y].ship = null;
            }
        }

        final int updateCount = Input.readInput().getInt();

        for (int i = 0; i < updateCount; ++i) {
            final Input input = Input.readInput();
            final int x = input.getInt();
            final int y = input.getInt();

            cells[x][y].halite = input.getInt();
        }
    }

    static GameMap _generate() {
        final Input mapInput = Input.readInput();
        final int width = mapInput.getInt();
        final int height = mapInput.getInt();

        final GameMap map = new GameMap(width, height);

        for (int y = 0; y < height; ++y) {
            final Input rowInput = Input.readInput();

            for (int x = 0; x < width; ++x) {
                final int halite = rowInput.getInt();
                map.cells[x][y] = new MapCell(Position.getPosition(x, y), halite);
            }
        }

        return map;
    }

    public int normaliseX(int x) {
        return (x + height) % height;
    }

    public int normaliseY(int y) {
        return (y + width) % width;
    }

}
