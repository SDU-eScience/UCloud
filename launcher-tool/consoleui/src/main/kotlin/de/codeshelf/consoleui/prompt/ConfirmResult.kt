package de.codeshelf.consoleui.prompt

import de.codeshelf.consoleui.elements.ConfirmChoice

/**
 * Result of a confirmation choice. Holds a single value of 'yes' or 'no'
 * from enum [ConfirmChoice.ConfirmationValue].
 *
 *
 * User: Andreas Wegmann
 * Date: 03.02.16
 */
class ConfirmResult
/**
 * Default constructor.
 *
 * @param confirm the result value to hold.
 */(
    /**
     * Returns the confirmation value.
     * @return confirmation value.
     */
    var confirmed: ConfirmChoice.ConfirmationValue?
) : PromtResultItemIF {

    override fun toString(): String {
        return "ConfirmResult{" +
                "confirmed=" + confirmed +
                '}'
    }
}
