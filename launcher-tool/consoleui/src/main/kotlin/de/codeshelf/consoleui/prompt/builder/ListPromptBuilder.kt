package de.codeshelf.consoleui.prompt.builder

import de.codeshelf.consoleui.elements.ListChoice
import de.codeshelf.consoleui.elements.PageSizeType
import de.codeshelf.consoleui.elements.items.ListItemIF
import de.codeshelf.consoleui.elements.items.impl.ListItem

/**
 * Created by andy on 22.01.16.
 */
class ListPromptBuilder(private val promptBuilder: PromptBuilder) {
    private var name: String? = null
    private var message: String? = null
    private var pageSize = 10
    private var pageSizeType: PageSizeType
    private val itemList: MutableList<ListItemIF> = ArrayList()

    init {
        pageSizeType = PageSizeType.ABSOLUTE
    }

    fun name(name: String?): ListPromptBuilder {
        this.name = name
        if (message != null) {
            message = name
        }
        return this
    }

    fun message(message: String?): ListPromptBuilder {
        this.message = message
        if (name == null) {
            name = message
        }
        return this
    }

    fun pageSize(absoluteSize: Int): ListPromptBuilder {
        pageSize = absoluteSize
        pageSizeType = PageSizeType.ABSOLUTE
        return this
    }

    fun relativePageSize(relativePageSize: Int): ListPromptBuilder {
        pageSize = relativePageSize
        pageSizeType = PageSizeType.RELATIVE
        return this
    }

    fun newItem(): ListItemBuilder {
        return ListItemBuilder(this)
    }

    fun newItem(name: String?): ListItemBuilder {
        val listItemBuilder = ListItemBuilder(this)
        return listItemBuilder.name(name).text(name)
    }

    fun addPrompt(): PromptBuilder {
        val listChoice = ListChoice(message, name, pageSize, pageSizeType, itemList)
        promptBuilder.addPrompt(listChoice)
        return promptBuilder
    }

    fun addItem(listItem: ListItem) {
        itemList.add(listItem)
    }
}
