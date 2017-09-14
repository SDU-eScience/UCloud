package dk.sdu.escience.irods;

class CommandPerf {
    private final CommandContext command;
    private final long durationInMs;

    CommandPerf(CommandContext command) {
        this.command = command;
        this.durationInMs = System.currentTimeMillis() - command.getTimestampStart();
    }

    public CommandContext getCommand() {
        return command;
    }

    public long getDurationInMs() {
        return durationInMs;
    }
}
