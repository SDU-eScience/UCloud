package de.codeshelf.consoleui.prompt

import de.codeshelf.consoleui.elements.Checkbox
import de.codeshelf.consoleui.elements.PageSizeType
import de.codeshelf.consoleui.elements.items.impl.CheckboxItem
import de.codeshelf.consoleui.prompt.reader.ReaderIF
import de.codeshelf.consoleui.prompt.renderer.CUIRenderer
import java.io.IOException

/**
 * CheckboxPrompt implements the checkbox choice handling.
 */
class CheckboxPrompt
/**
 * Empty default constructor.
 *
 * @throws IOException may be thrown by super class
 */
    : AbstractListablePrompt(), PromptIF<Checkbox, CheckboxResult> {
    // checkbox object to prompt the user for.
    private var checkbox: Checkbox? = null

    /**
     * helper class with render functionality.
     */
    var itemRenderer: CUIRenderer = CUIRenderer.renderer
    override val pageSize: Int
        get() = checkbox!!.pageSize
    override val pageSizeType: PageSizeType
        get() = checkbox!!.pageSizeType
    override val itemSize: Int
        get() = checkbox!!.checkboxItemList.size

    /**
     * Prompt the user for selecting zero to many choices from a checkbox.
     *
     * @param checkbox checkbox with items to choose from.
     * @return [CheckboxResult] which holds the users choices.
     */
    override fun prompt(checkbox: Checkbox): CheckboxResult {
        this.checkbox = checkbox
        itemList = this.checkbox!!.checkboxItemList
        reader.addAllowedPrintableKey('j')
        reader.addAllowedPrintableKey('k')
        reader.addAllowedPrintableKey(' ')
        reader.addAllowedSpecialKey(ReaderIF.SpecialKey.DOWN)
        reader.addAllowedSpecialKey(ReaderIF.SpecialKey.UP)
        reader.addAllowedSpecialKey(ReaderIF.SpecialKey.ENTER)
        selectedItemIndex = firstSelectableItemIndex
        initRendering()
        render()
        var readerInput = reader.read()
        while (readerInput != null && readerInput.specialKey != ReaderIF.SpecialKey.ENTER) {
            var downward = false
            var upward = false
            if (readerInput.specialKey == ReaderIF.SpecialKey.PRINTABLE_KEY) {
                if (readerInput.printableKey == ' ') {
                    toggleSelection()
                } else if (readerInput.printableKey == 'j') {
                    selectedItemIndex = getNextSelectableItemIndex(rollOverMode!!)
                    downward = true
                } else if (readerInput.printableKey == 'k') {
                    selectedItemIndex = getPreviousSelectableItemIndex(rollOverMode!!)
                    upward = true
                }
            } else if (readerInput.specialKey == ReaderIF.SpecialKey.DOWN) {
                selectedItemIndex = getNextSelectableItemIndex(rollOverMode!!)
                downward = true
            } else if (readerInput.specialKey == ReaderIF.SpecialKey.UP) {
                selectedItemIndex = getPreviousSelectableItemIndex(rollOverMode!!)
                upward = true
            }
            if (upward || downward) recalculateViewWindow(upward, downward)
            gotoRenderTop()
            render()
            readerInput = reader.read() ?: break
        }
        val selections = LinkedHashSet<String?>()
        for (item in itemList) {
            if (item is CheckboxItem) {
                val checkboxItem = item
                if (checkboxItem.isChecked) {
                    selections.add(checkboxItem.name)
                }
            }
        }
        renderMessagePromptAndResult(checkbox.message, selections.toString())
        return CheckboxResult(selections)
    }

    /**
     * render the checkbox on the terminal.
     */
    private fun render() {
        var itemNumber: Int
        var renderedLines = 0
        println(renderMessagePrompt(checkbox!!.message))
        itemNumber = topDisplayedItem
        while (renderedLines < viewPortHeight) {
            val renderedItem = itemRenderer.render(itemList[itemNumber], selectedItemIndex == itemNumber)
            println(renderedItem)
            itemNumber++
            renderedLines++
        }
    }

    /**
     * Toggles the selection of the currently selected checkbox item.
     */
    private fun toggleSelection() {
        val checkboxItem = itemList[selectedItemIndex] as CheckboxItem
        checkboxItem.isChecked = !checkboxItem.isChecked
    }
}
