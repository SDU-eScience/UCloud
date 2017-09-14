package dk.sdu.escience.irods;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.function.Supplier;

class JSONLogger {
    private final PrintWriter writer;
    private final ObjectMapper mapper = new ObjectMapper(); // TODO I'm assuming that this is safe

    JSONLogger(Writer writer) {
        if (writer == null) this.writer = null;
        else this.writer = new PrintWriter(writer);
    }

    void entry(Object objectToSerialize) {
        if (writer == null) return; // You can turn off a logger by setting the writer = null
        long timestamp = System.currentTimeMillis();
        String thread = Thread.currentThread().getName();
        writer.format("{\"ts\":%d,\"thread\":\"%s\",\"message\":", timestamp, thread);
        writer.print(asJson(objectToSerialize));
        writer.println("}");
        writer.flush();
    }

    private <T> String asJson(T objectToSerialize) {
        try {
            return mapper.writeValueAsString(objectToSerialize);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        if (writer != null) writer.close();
    }
}
