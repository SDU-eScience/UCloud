package de.codeshelf.consoleui.elements.items.impl

import de.codeshelf.consoleui.elements.items.ListItemIF

/**
 * User: Andreas Wegmann
 * Date: 01.01.16
 */
class ListItem @JvmOverloads constructor(var text: String? = null, name: String? = null) : ListItemIF {
    override var name: String? = null

    init {
        if (name == null) {
            this.name = text
        } else {
            this.name = name
        }
    }

    override val isSelectable: Boolean
        get() = true
}
