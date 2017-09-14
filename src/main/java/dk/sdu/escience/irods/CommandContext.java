package dk.sdu.escience.irods;

import java.util.Arrays;
import java.util.List;

class CommandContext {
    private final String command;
    private final List<Object> parameters;
    private final String irodsUser;
    private final String irodsZone;
    private final long timestampStart;

    CommandContext(AccountServices context, String command, Object... parameters) {
        this(context, command, Arrays.asList(parameters));
    }

    CommandContext(AccountServices context, String command, List<Object> parameters) {
        this.command = command;
        this.parameters = parameters;
        this.irodsUser = context.getAccount().getUserName();
        this.irodsZone = context.getAccount().getZone();
        this.timestampStart = System.currentTimeMillis();
    }

    public String getCommand() {
        return command;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    public String getIrodsUser() {
        return irodsUser;
    }

    public String getIrodsZone() {
        return irodsZone;
    }

    public long getTimestampStart() {
        return timestampStart;
    }
}
