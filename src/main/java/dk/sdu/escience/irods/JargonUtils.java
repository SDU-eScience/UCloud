package dk.sdu.escience.irods;

import org.irods.jargon.core.exception.JargonException;

class JargonUtils {
    @FunctionalInterface
    interface JargonCall<T> {
        T call() throws JargonException;
    }

    static <T> T rethrow(JargonCall<T> producer) {
        try {
            return producer.call();
        } catch (JargonException e) {
            throw new IRodsException(e);
        }
    }
}
