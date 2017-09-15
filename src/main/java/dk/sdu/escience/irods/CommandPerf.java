package dk.sdu.escience.irods;

class CommandPerf {
    private final CommandContext ctx;
    private final boolean hadErrors;
    private final long durationInMs;

    CommandPerf(CommandContext command, boolean hadErrors) {
        this.ctx = command;
        this.durationInMs = System.currentTimeMillis() - command.getTimestampStart();
        this.hadErrors = hadErrors;
    }

    public CommandContext getCtx() {
        return ctx;
    }

    public long getDurationInMs() {
        return durationInMs;
    }

    public boolean isHadErrors() {
        return hadErrors;
    }
}
