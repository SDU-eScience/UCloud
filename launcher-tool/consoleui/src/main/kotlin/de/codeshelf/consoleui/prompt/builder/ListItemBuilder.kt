package de.codeshelf.consoleui.prompt.builder

import de.codeshelf.consoleui.elements.items.impl.ListItem

/**
 * Created by andy on 22.01.16.
 */
class ListItemBuilder(private val listPromptBuilder: ListPromptBuilder) {
    private var text: String? = null
    private var name: String? = null
    fun text(text: String?): ListItemBuilder {
        this.text = text
        return this
    }

    fun name(name: String?): ListItemBuilder {
        this.name = name
        return this
    }

    fun add(): ListPromptBuilder {
        listPromptBuilder.addItem(ListItem(text, name))
        return listPromptBuilder
    }
}
