package de.codeshelf.consoleui.prompt.builder

import de.codeshelf.consoleui.elements.PromptableElementIF

/**
 * PromptBuilder is the builder class which creates
 *
 * Created by Andreas Wegmann
 * on 20.01.16.
 */
class PromptBuilder {
    var promptList: MutableList<PromptableElementIF> = ArrayList()
    fun build(): List<PromptableElementIF> {
        return promptList
    }

    fun addPrompt(promptableElement: PromptableElementIF) {
        promptList.add(promptableElement)
    }

    fun createInputPrompt(): InputValueBuilder {
        return InputValueBuilder(this)
    }

    fun createListPrompt(): ListPromptBuilder {
        return ListPromptBuilder(this)
    }

    fun createCheckboxPrompt(): CheckboxPromptBuilder {
        return CheckboxPromptBuilder(this)
    }

    fun createConfirmPromp(): ConfirmPromptBuilder {
        return ConfirmPromptBuilder(this)
    }
}
