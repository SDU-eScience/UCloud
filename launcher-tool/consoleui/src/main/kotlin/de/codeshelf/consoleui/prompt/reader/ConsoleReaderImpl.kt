package de.codeshelf.consoleui.prompt.reader

import jline.console.ConsoleReader
import jline.console.Operation
import jline.console.completer.Completer
import java.io.IOException
import java.util.*

/**
 * User: Andreas Wegmann
 * Date: 02.01.16
 */
class ConsoleReaderImpl : ReaderIF {
    var console: ConsoleReader
    private val allowedSpecialKeys: MutableSet<ReaderIF.SpecialKey>
    private val allowedPrintableKeys: MutableSet<Char?>

    init {
        allowedPrintableKeys = HashSet()
        allowedSpecialKeys = HashSet()
        console = ConsoleReader()
    }

    override fun setAllowedSpecialKeys(allowedSpecialKeys: Set<ReaderIF.SpecialKey>?) {
        this.allowedSpecialKeys.clear()
        this.allowedSpecialKeys.addAll(allowedSpecialKeys!!)
    }

    override fun setAllowedPrintableKeys(allowedPrintableKeys: Set<Char?>?) {
        this.allowedPrintableKeys.clear()
        this.allowedPrintableKeys.addAll(allowedPrintableKeys!!)
    }

    override fun addAllowedPrintableKey(character: Char?) {
        allowedPrintableKeys.add(character)
    }

    override fun addAllowedSpecialKey(specialKey: ReaderIF.SpecialKey) {
        allowedSpecialKeys.add(specialKey)
    }

    override fun read(): ReaderIF.ReaderInput? {
        var op: Any?
        var sb = StringBuilder()
        val pushBackChar = Stack<Char>()
        try {
            while (true) {
                val c = if (pushBackChar.isEmpty()) console.readCharacter() else pushBackChar.pop().code
                if (c == -1) {
                    return null
                }
                sb.appendCodePoint(c)
                op = console.keys.getBound(sb)
                if (op is Operation) {
                    val operation = op
                    if (operation == Operation.NEXT_HISTORY && allowedSpecialKeys.contains(ReaderIF.SpecialKey.DOWN)) return ReaderIF.ReaderInput(
                        ReaderIF.SpecialKey.DOWN
                    )
                    if (operation == Operation.PREVIOUS_HISTORY && allowedSpecialKeys.contains(ReaderIF.SpecialKey.UP)) return ReaderIF.ReaderInput(
                        ReaderIF.SpecialKey.UP
                    )
                    if (operation == Operation.ACCEPT_LINE && allowedSpecialKeys.contains(ReaderIF.SpecialKey.ENTER)) return ReaderIF.ReaderInput(
                        ReaderIF.SpecialKey.ENTER
                    )
                    if (operation == Operation.BACKWARD_CHAR && allowedSpecialKeys.contains(ReaderIF.SpecialKey.BACKSPACE)) return ReaderIF.ReaderInput(
                        ReaderIF.SpecialKey.BACKSPACE
                    )
                    if (operation == Operation.SELF_INSERT) {
                        val lastBinding = sb.toString()
                        val cc = lastBinding[0]
                        sb = if (allowedPrintableKeys.contains(cc)) {
                            return ReaderIF.ReaderInput(ReaderIF.SpecialKey.PRINTABLE_KEY, cc)
                        } else {
                            StringBuilder()
                        }
                    }
                    return ReaderIF.ReaderInput(ReaderIF.SpecialKey.NONE)
                }
                if (Objects.isNull(op)) return ReaderIF.ReaderInput(ReaderIF.SpecialKey.NONE)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
//    	System.out.println("!!!!!!!!!!!!!! UPDTED");
//    	console.shutdown();
        }
        return null
    }

    /**
     * Wrapper around JLine 2 library.
     *
     * @param completer List of completes to use
     * @param prompt the text to display as prompt left side from the input
     * @param mask optional mask character (may be used for password entry)
     * @return a ReaderInput object with results
     */
    @Throws(IOException::class)
    override fun readLine(
        completer: List<Completer?>?,
        prompt: String?,
        value: String?,
        mask: Char?
    ): ReaderIF.ReaderInput {
        if (completer != null) {
            for (c in completer) {
                console.addCompleter(c)
            }
        }
        val readLine: String
        readLine = if (mask == null) {
            console.readLine(prompt)
        } else {
            console.readLine(prompt, mask)
        }
        return ReaderIF.ReaderInput(ReaderIF.SpecialKey.PRINTABLE_KEY, readLine)
    }
}
