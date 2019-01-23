package hlt;

import java.util.ArrayList;

public class Position {
    public final int x;
    public final int y;
    public static int MAX_DIM = 64;

    private static Position[][] positions;
    private static boolean positionsInitialised = false;

    private static void initialisePositions() {
        if(positionsInitialised) return;
        positions = new Position[MAX_DIM][MAX_DIM];
        for(int m_x=0; m_x<MAX_DIM; m_x++){
            for(int m_y=0; m_y<MAX_DIM; m_y++){
                positions[m_x][m_y] = new Position(m_x, m_y);
            }
        }
        positionsInitialised = true;
    }

    public static Position getPosition(int x, int y) {
        initialisePositions();
        return positions[x][y];
    }

    private Position(final int x, final int y) {
        this.x = x;
        this.y = y;
    }

    public ArrayList<Position> getSurroundingCardinals(GameMap map) {
        final ArrayList<Position> suroundingCardinals = new ArrayList<>();

        for(final Direction d : Direction.ALL_CARDINALS) {
            suroundingCardinals.add(directionalOffset(d, map));
        }
        
        return suroundingCardinals;
    }

    public Position withVectorOffset(GameMap map, int offset_x, int offset_y) {


        return getPosition(map.normaliseX(x + offset_x), map.normaliseY(y + offset_y));
    }

    public Position directionalOffset(final Direction d, GameMap map) {
        final int dx;
        final int dy;

        switch (d) {
            case NORTH:
                dx = 0;
                dy = -1;
                break;
            case SOUTH:
                dx = 0;
                dy = 1;
                break;
            case EAST:
                dx = 1;
                dy = 0;
                break;
            case WEST:
                dx = -1;
                dy = 0;
                break;
            case STILL:
                dx = 0;
                dy = 0;
                break;
            default:
                throw new IllegalStateException("Unknown direction " + d);
        }

        return getPosition(map.normaliseX(x + dx), map.normaliseY(y + dy));
    }


    public Direction getDirectionTo(Position dest) {
        if(dest.equals(this)) return Direction.STILL;
        if(dest.x == x + 1 || dest.x < x-1) return Direction.EAST;
        if(dest.x == x - 1 || dest.x > x+1) return Direction.WEST;
        if(dest.y == y - 1 || dest.y > y+1) return Direction.NORTH;
        if(dest.y == y + 1 || dest.y < y-1) return Direction.SOUTH;
        throw new IllegalArgumentException(String.format("Positions not adjacent! %s, %s", this, dest));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Position position = (Position) o;

        if (x != position.x) return false;
        return y == position.y;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        return result;
    }

    public String toString() {
        return String.format("Position(%d, %d)", x, y);
    }

    public Position directionalOffset(Direction d, GameMap map, int r) {
        final int dx;
        final int dy;

        switch (d) {
            case NORTH:
                dx = 0;
                dy = -r;
                break;
            case SOUTH:
                dx = 0;
                dy = r;
                break;
            case EAST:
                dx = r;
                dy = 0;
                break;
            case WEST:
                dx = -r;
                dy = 0;
                break;
            case STILL:
                dx = 0;
                dy = 0;
                break;
            default:
                throw new IllegalStateException("Unknown direction " + d);
        }

        return getPosition(map.normaliseX(x + dx), map.normaliseY(y + dy));
    }
}
