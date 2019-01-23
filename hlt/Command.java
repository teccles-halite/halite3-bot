package hlt;

public class Command {
    public final String command;

    public static Command spawnShip() {
        return new Command("g");
    }

    protected Command(final String command) {
        this.command = command;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Command command1 = (Command) o;

        return command.equals(command1.command);
    }

    @Override
    public int hashCode() {
        return command.hashCode();
    }
}

