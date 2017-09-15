package dk.sdu.escience.irods;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CommandException {
    private final CommandContext ctx;
    private final String exceptionType;
    private final String trace;

    public CommandException(CommandContext ctx, Throwable throwable) {
        this.ctx = ctx;
        StringWriter exWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(exWriter));
        this.trace = exWriter.toString();
        this.exceptionType = throwable.getClass().getTypeName();
    }

    public CommandContext getCtx() {
        return ctx;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getTrace() {
        return trace;
    }
}
