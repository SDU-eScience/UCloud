package de.codeshelf.consoleui.prompt

import de.codeshelf.consoleui.prompt.reader.ConsoleReaderImpl
import de.codeshelf.consoleui.prompt.reader.ReaderIF
import org.fusesource.jansi.Ansi
import java.io.IOException
import java.util.*

/**
 * Abstract base class for all prompt implementations.
 * User: Andreas Wegmann
 * Date: 06.01.16
 */
abstract class AbstractPrompt {
    protected var renderHeight = 0
    protected var resourceBundle: ResourceBundle

    // the reader where we get the user input from
    var reader: ReaderIF

    /**
     * Generic method to render the message prompt and the users input after the prompt. This method is
     * used by all prompt implementations to display the question and result after the user has made
     * the input.
     *
     * @param message     message to render as colored prompt.
     * @param resultValue result value generated from the prompt implementation
     */
    protected fun renderMessagePromptAndResult(message: String?, resultValue: String) {
        println(
            Ansi.ansi().cursorUp(renderHeight - 1).a(renderMessagePrompt(message)).fg(Ansi.Color.CYAN).a(
                " $resultValue"
            ).eraseScreen(Ansi.Erase.FORWARD).reset()
        )
    }

    /**
     * Generic method to render a message prompt. The message (displayed white) is prefixed by a
     * green question mark.
     *
     * @param message message to render as a colored prompt.
     * @return String with ANSI-Color printable prompt.
     */
    protected fun renderMessagePrompt(message: String?): String {
        return Ansi.ansi().bold().fg(Ansi.Color.DEFAULT).a("[🙋] ").a(message)
            .boldOff().toString()
    }

    /**
     * Default constructor. Initializes the resource bundle for localized messages.
     *
     * @throws IOException may be thrown from console reader
     */
    init {
        resourceBundle = ResourceBundle.getBundle("consoleui_messages")
        reader = ConsoleReaderImpl()
    }
}
