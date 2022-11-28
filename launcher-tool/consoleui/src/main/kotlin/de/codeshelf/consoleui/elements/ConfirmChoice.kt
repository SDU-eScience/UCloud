package de.codeshelf.consoleui.elements

/**
 * User: Andreas Wegmann
 * Date: 07.01.16
 */
class ConfirmChoice : AbstractPromptableElement {
    enum class ConfirmationValue {
        YES, NO
    }

    var defaultConfirmation: ConfirmationValue? = null
        private set

    constructor(message: String?, name: String?) : super(message, name) {}
    constructor(message: String?, name: String?, defaultConfirmation: ConfirmationValue?) : super(message, name) {
        this.defaultConfirmation = defaultConfirmation
    }
}
