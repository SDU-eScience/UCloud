package de.codeshelf.consoleui.prompt.builder

import de.codeshelf.consoleui.elements.items.impl.Separator

/**
 * Created by andy on 22.01.16.
 */
class CheckboxSeperatorBuilder(private val promptBuilder: CheckboxPromptBuilder) {
    private var text: String? = null
    fun add(): CheckboxPromptBuilder {
        val separator = Separator(text)
        promptBuilder.addItem(separator)
        return promptBuilder
    }

    fun text(text: String?): CheckboxSeperatorBuilder {
        this.text = text
        return this
    }
}
