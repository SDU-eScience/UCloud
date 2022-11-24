package de.codeshelf.consoleui.prompt

/**
 * Result of a an expandable choice. ExpandableChoiceResult contains a String with the
 * IDs of the selected item.
 *
 *
 * User: Andreas Wegmann
 *
 *
 * Date: 03.02.16
 */
class ExpandableChoiceResult
/**
 * Default constructor.
 *
 * @param selectedId the selected id
 */(
    /**
     * Returns the selected id.
     *
     * @return selected id.
     */
    var selectedId: String?
) : PromtResultItemIF {

    override fun toString(): String {
        return "ExpandableChoiceResult{" +
                "selectedId='" + selectedId + '\'' +
                '}'
    }
}
