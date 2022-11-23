package de.codeshelf.consoleui.prompt

/**
 *
 * User: Andreas Wegmann
 * Date: 03.02.16
 */
class InputResult(val input: String?) : PromtResultItemIF {

    override fun toString(): String {
        return "InputResult{" +
                "input='" + input + '\'' +
                '}'
    }
}
