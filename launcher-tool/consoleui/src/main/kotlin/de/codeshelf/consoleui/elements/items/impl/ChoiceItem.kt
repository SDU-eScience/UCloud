package de.codeshelf.consoleui.elements.items.impl

import de.codeshelf.consoleui.elements.items.ChoiceItemIF

/**
 * User: Andreas Wegmann
 * Date: 07.01.16
 */
class ChoiceItem(val key: Char?, override val name: String?, val message: String?, val isDefaultChoice: Boolean) :
    ChoiceItemIF {

    override val isSelectable: Boolean
        get() = true
}
