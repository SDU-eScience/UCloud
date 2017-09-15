package dk.sdu.escience.irods;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream which delegates all write calls to an underlying stream. However, this wrapper will
 * protect the underlying stream from being closed. This is useful for passing stdout/stderr to wrappers
 * that ordinarily need to close their stream when a file-writer or similar is passed, but not when using
 * stdout/stderr.
 */
class GuardedOutputStream extends OutputStream {
    private final OutputStream guardedStream;

    GuardedOutputStream(OutputStream guardedStream) {
        this.guardedStream = guardedStream;
    }

    @Override
    public void write(int b) throws IOException {
        guardedStream.write(b);
    }

    @Override
    public void write(@NotNull byte[] b) throws IOException {
        guardedStream.write(b);
    }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
        guardedStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        guardedStream.flush();
    }

    @Override
    public void close() throws IOException {
        // Do nothing
    }
}
