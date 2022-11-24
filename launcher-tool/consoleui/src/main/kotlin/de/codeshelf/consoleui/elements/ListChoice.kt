package de.codeshelf.consoleui.elements

import de.codeshelf.consoleui.elements.items.ConsoleUIItemIF
import de.codeshelf.consoleui.elements.items.ListItemIF

/**
 * User: Andreas Wegmann
 * Date: 04.01.16
 */
class ListChoice(
    message: String?,
    name: String?,
    pageSize: Int,
    pageSizeType: PageSizeType,
    listItemList: List<ListItemIF>
) : AbstractPromptableElement(message, name) {
    val pageSize: Int
    val pageSizeType: PageSizeType
    private val _listItemList: List<ListItemIF>
    val listItemList: ArrayList<ConsoleUIItemIF>
        get() = ArrayList(_listItemList)

    init {
        require(!(pageSizeType == PageSizeType.RELATIVE && (pageSize < 1 || pageSize > 100))) { "for relative page size, the valid values are from 1 to 100" }
        this.pageSizeType = pageSizeType
        this.pageSize = pageSize
        this._listItemList = listItemList
    }
}
