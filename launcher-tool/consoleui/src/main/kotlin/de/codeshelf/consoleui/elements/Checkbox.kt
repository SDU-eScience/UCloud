package de.codeshelf.consoleui.elements

import de.codeshelf.consoleui.elements.items.CheckboxItemIF

/**
 * User: Andreas Wegmann
 * Date: 01.01.16
 */
class Checkbox(
    initialMessage: String?,
    name: String?,
    pageSize: Int,
    pageSizeType: PageSizeType,
    checkboxItemList: List<CheckboxItemIF>
) : AbstractPromptableElement(initialMessage, name) {
    val pageSize: Int
    val pageSizeType: PageSizeType
    private val _checkboxItemList: List<CheckboxItemIF>
    val checkboxItemList: ArrayList<CheckboxItemIF>
        get() = ArrayList(_checkboxItemList)

    init {
        require(!(pageSizeType == PageSizeType.RELATIVE && (pageSize < 1 || pageSize > 100))) { "for relative page size, the valid values are from 1 to 100" }
        this.pageSizeType = pageSizeType
        this.pageSize = pageSize
        this._checkboxItemList = checkboxItemList
    }
}
