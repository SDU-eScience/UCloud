package de.codeshelf.consoleui.elements.items.impl

import de.codeshelf.consoleui.elements.items.CheckboxItemIF
import de.codeshelf.consoleui.elements.items.ChoiceItemIF
import de.codeshelf.consoleui.elements.items.ListItemIF

/**
 * User: Andreas Wegmann
 * Date: 01.01.16
 */
class Separator : CheckboxItemIF, ListItemIF, ChoiceItemIF {
    var message: String? = null
        private set

    constructor(message: String?) {
        this.message = message
    }

    constructor() {}

    override val isSelectable: Boolean
        get() = false
    override val name: String?
        get() = null
}
