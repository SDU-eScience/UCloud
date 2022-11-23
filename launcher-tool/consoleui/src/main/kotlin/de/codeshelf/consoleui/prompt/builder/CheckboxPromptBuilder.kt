package de.codeshelf.consoleui.prompt.builder

import de.codeshelf.consoleui.elements.Checkbox
import de.codeshelf.consoleui.elements.PageSizeType
import de.codeshelf.consoleui.elements.items.CheckboxItemIF

/**
 * Created by andy on 22.01.16.
 */
class CheckboxPromptBuilder(private val promptBuilder: PromptBuilder) {
    private var name: String? = null
    private var message: String? = null
    private var pageSize = 10
    private var pageSizeType: PageSizeType
    private val itemList: MutableList<CheckboxItemIF>

    init {
        pageSizeType = PageSizeType.ABSOLUTE
        itemList = ArrayList()
    }

    fun addItem(checkboxItem: CheckboxItemIF) {
        itemList.add(checkboxItem)
    }

    fun name(name: String?): CheckboxPromptBuilder {
        this.name = name
        if (message == null) {
            message = name
        }
        return this
    }

    fun message(message: String?): CheckboxPromptBuilder {
        this.message = message
        if (name == null) {
            name = message
        }
        return this
    }

    fun pageSize(absoluteSize: Int): CheckboxPromptBuilder {
        pageSize = absoluteSize
        pageSizeType = PageSizeType.ABSOLUTE
        return this
    }

    fun relativePageSize(relativePageSize: Int): CheckboxPromptBuilder {
        pageSize = relativePageSize
        pageSizeType = PageSizeType.RELATIVE
        return this
    }

    fun newItem(): CheckboxItemBuilder {
        return CheckboxItemBuilder(this)
    }

    fun newItem(name: String?): CheckboxItemBuilder {
        val checkboxItemBuilder = CheckboxItemBuilder(this)
        return checkboxItemBuilder.name(name)
    }

    fun addPrompt(): PromptBuilder {
        val checkbox = Checkbox(message, name, pageSize, pageSizeType, itemList)
        promptBuilder.addPrompt(checkbox)
        return promptBuilder
    }

    fun newSeparator(): CheckboxSeperatorBuilder {
        return CheckboxSeperatorBuilder(this)
    }

    fun newSeparator(text: String?): CheckboxSeperatorBuilder {
        val checkboxSeperatorBuilder = CheckboxSeperatorBuilder(this)
        return checkboxSeperatorBuilder.text(text)
    }
}
