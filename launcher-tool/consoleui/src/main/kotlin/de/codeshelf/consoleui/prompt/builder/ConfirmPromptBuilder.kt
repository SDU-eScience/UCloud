package de.codeshelf.consoleui.prompt.builder

import de.codeshelf.consoleui.elements.ConfirmChoice

/**
 * User: Andreas Wegmann
 * Date: 24.01.16
 */
class ConfirmPromptBuilder(private val promptBuilder: PromptBuilder) {
    private var name: String? = null
    private var message: String? = null
    private var defaultConfirmationValue: ConfirmChoice.ConfirmationValue? = null
    fun name(name: String?): ConfirmPromptBuilder {
        this.name = name
        if (message == null) {
            message = name
        }
        return this
    }

    fun message(message: String?): ConfirmPromptBuilder {
        this.message = message
        if (name == null) {
            name = message
        }
        return this
    }

    fun defaultValue(confirmationValue: ConfirmChoice.ConfirmationValue?): ConfirmPromptBuilder {
        defaultConfirmationValue = confirmationValue
        return this
    }

    fun addPrompt(): PromptBuilder {
        promptBuilder.addPrompt(ConfirmChoice(message, name, defaultConfirmationValue))
        return promptBuilder
    }
}
