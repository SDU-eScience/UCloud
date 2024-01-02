/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jline.internal;

import org.fusesource.jansi.AnsiConsole;
import org.fusesource.jansi.AnsiPrintStream;
import org.fusesource.jansi.io.AnsiOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Ansi support.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 * @since 2.13
 */
public class Ansi {

    public static String stripAnsi(String str) {
        if (str == null) return "";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            AnsiPrintStream origOut = AnsiConsole.out();
            AnsiOutputStream out = new AnsiOutputStream(
                    baos,
                    origOut::getTerminalWidth,
                    origOut.getMode(),
                    null,
                    origOut.getType(),
                    origOut.getColors(),
                    Charset.defaultCharset(),
                    null,
                    null,
                    true
            );

            baos.write(str.getBytes());
            baos.close();
            return baos.toString();
        } catch (IOException e) {
            return str;
        }
    }
}


