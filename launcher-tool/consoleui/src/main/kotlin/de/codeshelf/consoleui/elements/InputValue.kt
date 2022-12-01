package de.codeshelf.consoleui.elements

import jline.console.completer.Completer

/**
 * User: Andreas Wegmann
 * Date: 06.01.16
 */
class InputValue : AbstractPromptableElement {
    var value: String? = null
        private set
    var defaultValue: String?
        private set
    var completer: MutableList<Completer>? = null
    var mask: Char? = null

    constructor(name: String?, message: String?) : super(message, name) {
        value = null
        defaultValue = null
    }

    constructor(name: String?, message: String?, value: String?, defaultValue: String?) : super(message, name) {
        //this.value = value;
        check(value == null) { "pre filled values for InputValue are not supported at the moment." }
        this.defaultValue = defaultValue
    }

    fun addCompleter(completer: Completer) {
        if (this.completer == null) {
            this.completer = ArrayList()
        }
        this.completer!!.add(completer)
    }
}
