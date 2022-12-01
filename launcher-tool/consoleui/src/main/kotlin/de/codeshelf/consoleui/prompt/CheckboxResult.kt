package de.codeshelf.consoleui.prompt

/**
 * Result of a checkbox choice. CheckboxResult contains a [java.util.Set] with the
 * IDs of the selected checkbox items.
 *
 *
 * User: Andreas Wegmann
 * Date: 03.02.16
 */
class CheckboxResult
/**
 * Default Constructor.
 * @param selectedIds Selected IDs.
 */(
    /**
     * Returns the set with the IDs of selected checkbox items.
     *
     * @return set with IDs
     */
    var selectedIds: HashSet<String?>
) : PromtResultItemIF {

    override fun toString(): String {
        return "CheckboxResult{" +
                "selectedIds=" + selectedIds +
                '}'
    }
}
