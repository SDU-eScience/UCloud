package de.codeshelf.consoleui.prompt.reader

import jline.console.completer.Completer
import java.io.IOException

/**
 * User: Andreas Wegmann
 * Date: 02.01.16
 */
interface ReaderIF {
    enum class SpecialKey {
        NONE, UP, DOWN, ENTER, BACKSPACE, PRINTABLE_KEY
        // not really a special key, but indicates an ordianry printable key
    }

    fun setAllowedSpecialKeys(allowedSpecialKeys: Set<SpecialKey>?)
    fun setAllowedPrintableKeys(allowedPrintableKeys: Set<Char?>?)
    fun addAllowedPrintableKey(character: Char?)
    fun addAllowedSpecialKey(specialKey: SpecialKey)
    fun read(): ReaderInput?

    @Throws(IOException::class)
    fun readLine(completer: List<Completer?>?, promt: String?, value: String?, mask: Char?): ReaderInput
    class ReaderInput {
        var specialKey: SpecialKey
            private set
        var printableKey: Char? = null
            private set
        var lineInput: String? = null
            private set

        constructor(specialKey: SpecialKey) {
            this.specialKey = specialKey
        }

        constructor(specialKey: SpecialKey, printableKey: Char?) {
            this.specialKey = specialKey
            this.printableKey = printableKey
        }

        constructor(specialKey: SpecialKey, lineInput: String?) {
            this.specialKey = specialKey
            this.lineInput = lineInput
        }
    }
}
