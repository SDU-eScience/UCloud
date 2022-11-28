package de.codeshelf.consoleui.prompt

/**
 * Result of a list choice. Holds the id of the selected item.
 *
 *
 * Created by Andreas Wegmann on 03.02.16.
 */
class ListResult
/**
 * Default constructor.
 *
 * @param selectedId id of selected item.
 */(
    /**
     * Returns the ID of the selected item.
     *
     * @return id of selected item
     */
    var selectedId: String?
) : PromtResultItemIF {

    override fun toString(): String {
        return "ListResult{" +
                "selectedId='" + selectedId + '\'' +
                '}'
    }
}
