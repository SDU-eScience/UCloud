package dk.sdu.escience.irods;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

class JSONLogger {
    private static final String LOGGER_PREFIX = "dk.sdu.escience.irods.json.";
    private static final String DEBUG_PREFIX = LOGGER_PREFIX + "debug.";
    private static final String PERF_PREFIX = LOGGER_PREFIX + "perf.";
    private static final String ERROR_PREFIX = LOGGER_PREFIX + "error.";

    private final Logger logger;
    private final ObjectMapper mapper = new ObjectMapper(); // TODO I'm assuming that this is safe

    private JSONLogger(Logger logger) {
        this.logger = logger;
    }

    static JSONLogger getDebugLogger(String name) {
        return new JSONLogger(LoggerFactory.getLogger(DEBUG_PREFIX + name));
    }

    static JSONLogger getErrorLogger(String name) {
        return new JSONLogger(LoggerFactory.getLogger(ERROR_PREFIX + name));
    }

    static JSONLogger getPerfLogger(String name) {
        return new JSONLogger(LoggerFactory.getLogger(PERF_PREFIX + name));
    }

    public <T> void trace(T objectToSerialize) {
        logger.trace(asJson(objectToSerialize));
    }

    public <T> void trace(Supplier<T> objectToSerializeSupplier) {
        if (!logger.isTraceEnabled()) return;
        logger.trace(asJson(objectToSerializeSupplier.get()));
    }

    public <T> void debug(T objectToSerialize) {
        logger.debug(asJson(objectToSerialize));
    }

    public <T> void debug(Supplier<T> objectToSerializeSupplier) {
        if (!logger.isDebugEnabled()) return;
        logger.debug(asJson(objectToSerializeSupplier.get()));
    }

    public <T> void info(T objectToSerialize) {
        logger.info(asJson(objectToSerialize));
    }

    public <T> void warn(T objectToSerialize) {
        logger.warn(asJson(objectToSerialize));
    }

    public <T> void error(T objectToSerialize) {
        logger.error(asJson(objectToSerialize));
    }

    private <T> String asJson(T objectToSerialize) {
        try {
            return mapper.writeValueAsString(objectToSerialize);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
