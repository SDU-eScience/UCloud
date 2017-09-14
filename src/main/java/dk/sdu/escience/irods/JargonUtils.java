package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.JargonException;

import java.util.List;

class JargonUtils {
    private static final JSONLogger logger = JSONLogger.getDebugLogger("IRodsFileService");
    private static final JSONLogger perf = JSONLogger.getPerfLogger("IRodsFileService");
    private static final JSONLogger err = JSONLogger.getErrorLogger("asdqwe");

    @FunctionalInterface
    interface JargonCall<T> {
        T call() throws JargonException;
    }

    @FunctionalInterface
    interface CommandCall<T, E extends Throwable> {
        T call() throws E;
    }

    @FunctionalInterface
    interface CommandCall2<T, E extends Throwable, E2 extends Throwable> {
        T call() throws E, E2;
    }

    static <T> T rethrow(JargonCall<T> producer) {
        try {
            return producer.call();
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }

    @SuppressWarnings("Duplicates")
    static <T, E extends Throwable> T wrapCommand(AccountServices ctx, String name, List<Object> arguments,
                                                  CommandCall<T, E> command) throws E {
        CommandContext commandContext = new CommandContext(ctx, name, arguments);
        logger.debug(commandContext);
        try {
            return command.call();
        } catch (IRodsException e) {
            err.error(e.getOriginalMessage());
            throw e;
        } finally {
            perf.info(new CommandPerf(commandContext));
        }
    }

    @SuppressWarnings("Duplicates")
    static <T, E extends Throwable, E2 extends Throwable> T wrapCommand2(
            AccountServices ctx, String name, List<Object> arguments, CommandCall2<T, E, E2> command) throws E, E2 {
        // Copy & paste. There is nothing we can do about this. The compiler simply cannot figure out the
        // exception types unless we do this.
        CommandContext commandContext = new CommandContext(ctx, name, arguments);
        logger.debug(commandContext);
        try {
            return command.call();
        } catch (IRodsException e) {
            err.error(e.getOriginalMessage());
            throw e;
        } finally {
            perf.info(new CommandPerf(commandContext));
        }

    }

}
