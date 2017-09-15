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
        boolean hadErrors = false;
        CommandContext commandContext = new CommandContext(ctx, name, arguments);
        access.entry(commandContext);
        try {
            return command.call();
        } catch (RuntimeException e) {
            hadErrors = true;
            errors.entry(new CommandException(commandContext, e));
            throw e;
        } catch (Exception e) {
            hadErrors = true;
            // The command is not allowed to throw a checked exception unless it is of type E. Cast should be safe.
            //noinspection unchecked
            throw (E) e;
        } finally {
            performance.entry(new CommandPerf(commandContext, hadErrors));
        }
    }

    @SuppressWarnings("Duplicates")
    <T, E extends Throwable, E2 extends Throwable> T wrapCommand2(
            Class<E> e1, Class<E2> e2,
            AccountServices ctx, String name, List<Object> arguments, CommandCall2<T, E, E2> command) throws E, E2 {
        // Copy & paste. There is nothing we can do about this. The compiler simply cannot figure out the
        // exception types unless we do this.

        boolean hadErrors = false;
        CommandContext commandContext = new CommandContext(ctx, name, arguments);
        access.entry(commandContext);
        try {
            return command.call();
        } catch (RuntimeException e) {
            hadErrors = true;
            errors.entry(new CommandException(commandContext, e));
            throw e;
        } catch (Exception e) {
            hadErrors = true;
            if (e1.isInstance(e)) {
                //noinspection unchecked
                throw (E) e;
            }
            if (e2.isInstance(e)) {
                //noinspection unchecked
                throw (E2) e;
            }

            throw new RuntimeException(e);
        } finally {
            performance.entry(new CommandPerf(commandContext, hadErrors));
        }
    }

    void close() {
        access.close();
        performance.close();
        errors.close();
    }

}
