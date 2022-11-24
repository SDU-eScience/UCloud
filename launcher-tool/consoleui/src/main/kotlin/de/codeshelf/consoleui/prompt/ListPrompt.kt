package de.codeshelf.consoleui.prompt

import de.codeshelf.consoleui.elements.ListChoice
import de.codeshelf.consoleui.elements.PageSizeType
import de.codeshelf.consoleui.elements.items.impl.ListItem
import de.codeshelf.consoleui.prompt.reader.ConsoleReaderImpl
import de.codeshelf.consoleui.prompt.reader.ReaderIF
import de.codeshelf.consoleui.prompt.renderer.CUIRenderer
import java.io.IOException

/**
 * ListPrompt implements the list choice handling.
 *
 *
 * User: Andreas Wegmann
 * Date: 01.01.16
 */
class ListPrompt
/**
 * Empty default constructor.
 *
 * @throws IOException may be thrown by super class
 */
    : AbstractListablePrompt(), PromptIF<ListChoice, ListResult> {
    // the list to let the user choose from
    lateinit var listChoice: ListChoice

    /**
     * helper class with render functionality.
     */
    var itemRenderer: CUIRenderer = CUIRenderer.renderer
    override val pageSize: Int
        get() = listChoice.pageSize
    override val pageSizeType: PageSizeType
        get() = listChoice.pageSizeType
    override val itemSize: Int
        get() = itemList!!.size

    /**
     * Prompt the user for selecting zero to many choices from a checkbox.
     *
     * @param listChoice list with items to choose from.
     * @return [ListResult] which holds the users choices.
     * @throws IOException may be thrown by console reader
     */
    @Throws(IOException::class)
    override fun prompt(listChoice: ListChoice): ListResult {
        this.listChoice = listChoice
        itemList = listChoice.listItemList
        if (reader == null) {
            reader = ConsoleReaderImpl()
        }
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
                if (readerInput.printableKey == 'j') {
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
            readerInput = reader.read()
        }
        val listItem = itemList[selectedItemIndex] as ListItem
        val selection = ListResult(listItem.name)
        renderMessagePromptAndResult(listChoice.message, (itemList[selectedItemIndex] as ListItem).text ?: "")
        return selection
    }

    private fun render() {
        var itemNumber: Int
        var renderedLines = 0
        println(renderMessagePrompt(listChoice.message))
        itemNumber = topDisplayedItem
        while (renderedLines < viewPortHeight) {
            val renderedItem = itemRenderer.render(itemList!![itemNumber], selectedItemIndex == itemNumber)
            println(renderedItem)
            itemNumber++
            renderedLines++
        }
    }
}
