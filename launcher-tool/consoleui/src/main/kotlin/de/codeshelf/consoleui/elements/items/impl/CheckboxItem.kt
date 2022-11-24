package de.codeshelf.consoleui.elements.items.impl

import de.codeshelf.consoleui.elements.items.CheckboxItemIF

/**
 * User: Andreas Wegmann
 * Date: 07.12.15
 */
class CheckboxItem @JvmOverloads constructor(
    var isChecked: Boolean = false,
    var text: String? = null,
    var disabledText: String? = null,
    override var name: String? = null
) : CheckboxItemIF {
    override val isSelectable: Boolean
        get() = isEnabled

    fun setDisabled() {
        disabledText = "disabled"
    }

    fun setDisabled(disabledText: String?) {
        this.disabledText = disabledText
    }

    fun setEnabled() {
        disabledText = null
    }

    val isDisabled: Boolean
        get() = disabledText != null
    val isEnabled: Boolean
        get() = disabledText == null
}
