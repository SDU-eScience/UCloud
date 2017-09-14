package dk.sdu.escience.irods;

import java.util.List;

class CommandExecutor {
    private final JSONLogger access;
    private final JSONLogger performance;
    private final JSONLogger errors;

    CommandExecutor(JSONLogger access, JSONLogger performance, JSONLogger errors) {
        this.access = access;
        this.performance = performance;
        this.errors = errors;
    }

    @FunctionalInterface
    interface CommandCall<T, E extends Throwable> {
        T call() throws E;
    }

    @FunctionalInterface
    interface CommandCall2<T, E extends Throwable, E2 extends Throwable> {
        T call() throws E, E2;
    }

    @SuppressWarnings("Duplicates")
    <T, E extends Throwable> T wrapCommand(AccountServices ctx, String name, List<Object> arguments,
                                           CommandCall<T, E> command) throws E {
        CommandContext commandContext = new CommandContext(ctx, name, arguments);
        access.entry(commandContext);
        try {
            return command.call();
        } catch (IRodsException e) {
            errors.entry(e.getOriginalMessage());
            throw e;
        } finally {
            performance.entry(new CommandPerf(commandContext));
        }
    }

    @SuppressWarnings("Duplicates")
    <T, E extends Throwable, E2 extends Throwable> T wrapCommand2(
            AccountServices ctx, String name, List<Object> arguments, CommandCall2<T, E, E2> command) throws E, E2 {
        // Copy & paste. There is nothing we can do about this. The compiler simply cannot figure out the
        // exception types unless we do this.
        CommandContext commandContext = new CommandContext(ctx, name, arguments);
        access.entry(commandContext);
        try {
            return command.call();
        } catch (IRodsException e) {
            errors.entry(e.getOriginalMessage());
            throw e;
        } finally {
            performance.entry(new CommandPerf(commandContext));
        }
    }

    void close() {
        access.close();
        performance.close();
        errors.close();
    }

}
