package de.codeshelf.consoleui.prompt.builder

import de.codeshelf.consoleui.elements.items.CheckboxItemIF
import de.codeshelf.consoleui.elements.items.impl.CheckboxItem

/**
 * Created by andy on 22.01.16.
 */
class CheckboxItemBuilder(private val checkboxPromptBuilder: CheckboxPromptBuilder) {
    private var checked = false
    private var name: String? = null
    private var text: String? = null
    private var disabledText: String? = null
    fun name(name: String?): CheckboxItemBuilder {
        if (text == null) {
            text = name
        }
        this.name = name
        return this
    }

    fun text(text: String?): CheckboxItemBuilder {
        if (name == null) {
            name = text
        }
        this.text = text
        return this
    }

    fun add(): CheckboxPromptBuilder {
        val item: CheckboxItemIF = CheckboxItem(checked, text, disabledText, name)
        checkboxPromptBuilder.addItem(item)
        return checkboxPromptBuilder
    }

    fun disabledText(disabledText: String?): CheckboxItemBuilder {
        this.disabledText = disabledText
        return this
    }

    fun check(): CheckboxItemBuilder {
        checked = true
        return this
    }

    fun checked(checked: Boolean): CheckboxItemBuilder {
        this.checked = checked
        return this
    }
}
