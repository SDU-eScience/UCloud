package de.codeshelf.consoleui.prompt

import de.codeshelf.consoleui.elements.PageSizeType
import de.codeshelf.consoleui.elements.items.ConsoleUIItemIF
import jline.Terminal
import jline.TerminalFactory
import org.fusesource.jansi.Ansi
import java.io.IOException

/**
 * Abstract base class for all listable prompts (checkbox, list and expandable choice).
 * This class contains some helper methods for the list prompts.
 *
 *
 * User: Andreas Wegmann
 * Date: 19.01.16
 */
abstract class AbstractListablePrompt : AbstractPrompt() {
    // holds the index of the selected item (of course)
    protected var selectedItemIndex = 0

    // the item list of the prompt
    lateinit var itemList: ArrayList<out ConsoleUIItemIF>
    protected var terminal: Terminal

    /**
     * first item in view
     */
    protected var topDisplayedItem = 0
    protected var nearBottomMargin = 3
    protected var viewPortHeight = 0
    protected var rollOverMode: Rollover? = null

    /**
     * Empty default constructor.
     *
     * @throws IOException may be thrown by super class
     */
    init {
        terminal = TerminalFactory.get()
    }

    /**
     * total height of screen
     * @return number of lines on terminal.
     */
    protected fun screenHeight(): Int {
        return terminal.height
    }

    /**
     * calculate renderHeight by relative or absolute type, screen size and item count.
     */
    protected fun initRendering() {
        topDisplayedItem = 0
        renderHeight =
            if (pageSizeType == PageSizeType.ABSOLUTE) // requested absolute page size plus header plus baseline, but not bigger than screen size
                Math.min(screenHeight(), pageSize + 2) else {
                // relative page size for complete list plus header plus baseline
                screenHeight() * pageSize / 100
            }

        // if less items than renderHeight, we reduce the height
        renderHeight = Math.min(renderHeight, itemSize + 2)

        // renderHeight must be at least 3, for a single list item. may be smaller with relative or absolute
        // settings, so we correct this to at least 3.
        renderHeight = Math.max(3, renderHeight)

        // viewPortHeight is the height of the list items itself, without header and baseline
        viewPortHeight = renderHeight - 2

        // if list size is bigger than viewPort, then we disable the rollover feature.
        rollOverMode =
            if (viewPortHeight == itemSize) Rollover.ALLOWED else Rollover.HALT_AT_LIST_END

        // select a non separator
        if (itemList[selectedItemIndex].name?.startsWith("sep-") == true) {
            selectedItemIndex = getNextSelectableItemIndex(rollOverMode!!)
        }
    }

    protected abstract val pageSize: Int
    protected abstract val pageSizeType: PageSizeType?
    protected fun gotoRenderTop() {
        println(Ansi.ansi().cursorUp(renderHeight))
        //System.out.println(ansi().cursor(0, screenHeight() - renderHeight));
    }

    enum class Rollover {
        ALLOWED, HALT_AT_LIST_END
    }

    /**
     * Find the next selectable Item (user pressed 'down').
     *
     * @param rollover enables or disables rollover feature when searching for next item
     * @return index of the next selectable item.
     */
    protected fun getNextSelectableItemIndex(rollover: Rollover): Int {
        val normalResult = if (rollover == Rollover.ALLOWED) nextSelectableItemIndexWithRollover else nextSelectableItemIndexWithoutRollover
        val isSeparator = itemList[normalResult].name?.startsWith("sep-") == true
        if (isSeparator) {
            selectedItemIndex = normalResult
            return getNextSelectableItemIndex(rollover)
        }
        return normalResult
    }

    /**
     * Find the next selectable Item (user pressed 'down').
     *
     * @return index of the next selectable item.
     */
    private val nextSelectableItemIndexWithRollover: Int
        private get() {
            for (i in itemList!!.indices) {
                val newIndex = (selectedItemIndex + 1 + i) % itemList!!.size
                val item = itemList!![newIndex]
                if (item.isSelectable) return newIndex
            }
            return selectedItemIndex
        }

    /**
     * Find the next selectable Item (user pressed 'down'), but does not start at the beginning
     * if end of list is reached.
     *
     * @return index of the next selectable item.
     */
    private val nextSelectableItemIndexWithoutRollover: Int
        private get() {
            for (newIndex in selectedItemIndex + 1 until itemList!!.size) {
                val item = itemList!![newIndex]
                if (item.isSelectable) return newIndex
            }
            return selectedItemIndex
        }

    /**
     * Find the previous selectable item (user pressed 'up').
     *
     * @param rollover enables or disables rollover feature when searching for previous item
     * @return index of the previous selectable item.
     */
    protected fun getPreviousSelectableItemIndex(rollover: Rollover): Int {
        val normalResult = if (rollover == Rollover.ALLOWED) previousSelectableItemIndexWithRollover else previousSelectableItemIndexWithoutRollover
        val isSeparator = itemList[normalResult].name?.startsWith("sep-") == true
        if (isSeparator) {
            selectedItemIndex = normalResult
            return getPreviousSelectableItemIndex(rollover)
        }
        return normalResult
    }

    /**
     * Find the previous selectable item (user pressed 'up').
     *
     * @return index of the previous selectable item.
     */
    private val previousSelectableItemIndexWithRollover: Int
        private get() {
            for (i in itemList!!.indices) {
                val newIndex = (selectedItemIndex - 1 - i + itemList!!.size) % itemList!!.size
                val item = itemList!![newIndex]
                if (item.isSelectable) return newIndex
            }
            return selectedItemIndex
        }

    /**
     * Find the previous selectable item (user pressed 'up'), but does not start at end of list if
     * beginning of list is reached.
     *
     * @return index of the previous selectable item.
     */
    private val previousSelectableItemIndexWithoutRollover: Int
        private get() {
            for (newIndex in selectedItemIndex - 1 downTo 0) {
                val item = itemList!![newIndex]
                if (item.isSelectable) return newIndex
            }
            return selectedItemIndex
        }

    /**
     * Find the first selectable item of the item list.
     *
     * @return index of the first selectable item.
     * @throws IllegalStateException if no item is selectable.
     */
    protected val firstSelectableItemIndex: Int
        protected get() {
            var index = 0
            for (item in itemList!!) {
                if (item.isSelectable) return index
                index++
            }
            throw IllegalStateException("no selectable item in list")
        }

    protected fun recalculateViewWindow(upward: Boolean, downward: Boolean) {
        if (viewPortHeight < itemSize) {
            if (downward && itemsBelowEnd() && selectedItemNearBottom()) topDisplayedItem++
            if (upward && itemsAboveTop() && selectedItemNearTop()) topDisplayedItem--
        }
    }

    private fun selectedItemNearTop(): Boolean {
        return topDisplayedItem + nearBottomMargin - 1 > selectedItemIndex
    }

    private fun itemsAboveTop(): Boolean {
        return topDisplayedItem > 0
    }

    private fun selectedItemNearBottom(): Boolean {
        return selectedItemIndex + nearBottomMargin > topDisplayedItem + viewPortHeight
    }

    private fun itemsBelowEnd(): Boolean {
        return topDisplayedItem + viewPortHeight < itemSize
    }

    abstract val itemSize: Int
}
