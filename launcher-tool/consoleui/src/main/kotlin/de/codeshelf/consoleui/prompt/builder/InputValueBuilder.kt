package de.codeshelf.consoleui.prompt.builder

import de.codeshelf.consoleui.elements.InputValue
import jline.console.completer.Completer

/**
 * Created by andy on 22.01.16.
 */
class InputValueBuilder(private val promptBuilder: PromptBuilder) {
    private var name: String? = null
    private var defaultValue: String? = null
    private var message: String? = null
    private var mask: Char? = null
    private var completers: ArrayList<Completer>? = null
    fun name(name: String?): InputValueBuilder {
        this.name = name
        return this
    }

    fun defaultValue(defaultValue: String?): InputValueBuilder {
        this.defaultValue = defaultValue
        return this
    }

    fun message(message: String?): InputValueBuilder {
        this.message = message
        return this
    }

    fun addCompleter(completer: Completer): InputValueBuilder {
        if (completers == null) {
            completers = ArrayList()
        }
        completers!!.add(completer)
        return this
    }

    fun mask(mask: Char): InputValueBuilder {
        this.mask = mask
        return this
    }

    fun addPrompt(): PromptBuilder {
        val inputValue = InputValue(name, message, null, defaultValue)
        if (completers != null) {
            inputValue.completer = completers
        }
        if (mask != null) {
            inputValue.mask = mask
        }
        promptBuilder.addPrompt(inputValue)
        return promptBuilder
    }
}
