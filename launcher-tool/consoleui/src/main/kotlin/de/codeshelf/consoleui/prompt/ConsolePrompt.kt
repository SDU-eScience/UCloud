package de.codeshelf.consoleui.prompt

import de.codeshelf.consoleui.elements.Checkbox
import de.codeshelf.consoleui.elements.ConfirmChoice
import de.codeshelf.consoleui.elements.InputValue
import de.codeshelf.consoleui.elements.ListChoice
import de.codeshelf.consoleui.elements.PromptableElementIF
import de.codeshelf.consoleui.prompt.builder.PromptBuilder
import java.io.IOException

/**
 * ConsolePrompt encapsulates the prompting of a list of input questions for the user.
 *
 *
 * Created by Andreas Wegmann on 20.01.16.
 */
class ConsolePrompt
/**
 * Default constructor for this class.
 */
{
    /* Lazy getter for input prompt */
    // input prompt implementation
    @get:Throws(IOException::class)
    private var inputPrompt: InputPrompt? = null
        private get() {
            if (field == null) {
                field = InputPrompt()
            }
            return field
        }

    /* Lazy getter for checkbox prompt */
    // checkbox prompt implementation
    @get:Throws(IOException::class)
    private var checkboxPrompt: CheckboxPrompt? = null
        private get() {
            if (field == null) {
                field = CheckboxPrompt()
            }
            return field
        }

    /* Lazy getter for list prompt */
    // list box prompt implementation
    @get:Throws(IOException::class)
    private var listPrompt: ListPrompt? = null
        private get() {
            if (field == null) {
                field = ListPrompt()
            }
            return field
        }

    /* Lazy getter for confirm prompt */
    // confirmation prompt implementation
    @get:Throws(IOException::class)
    private var confirmPrompt: ConfirmPrompt? = null
        private get() {
            if (field == null) {
                field = ConfirmPrompt()
            }
            return field
        }

    /**
     * Prompt a list of choices (questions). This method takes a list of promptable elements, typically
     * created with [PromptBuilder]. Each of the elements is processed and the user entries and
     * answers are filled in to the result map. The result map contains the key of each promtable element
     * and the user entry as an object implementing [PromtResultItemIF].
     *
     * @param promptableElementList the list of questions / promts to ask the user for.
     * @return a map containing a result for each element of promptableElementList
     * @throws IOException  may be thrown by console reader
     */
    @Throws(IOException::class)
    fun prompt(promptableElementList: List<PromptableElementIF?>?): HashMap<String?, out PromtResultItemIF?> {
        val resultMap = HashMap<String?, PromtResultItemIF?>()
        for (i in promptableElementList!!.indices) {
            val promptableElement = promptableElementList[i]
            if (promptableElement is ListChoice) {
                val result = doPrompt(promptableElement)
                resultMap[promptableElement.name] = result
            } else if (promptableElement is InputValue) {
                val result = doPrompt(promptableElement)
                resultMap[promptableElement.name] = result
            } else if (promptableElement is Checkbox) {
                val result = doPrompt(promptableElement)
                resultMap[promptableElement.name] = result
            } else if (promptableElement is ConfirmChoice) {
                val result = doPrompt(promptableElement)
                resultMap[promptableElement.name] = result
            } else {
                throw IllegalArgumentException("wrong type of promptable element")
            }
        }
        return resultMap
    }

    /**
     * Process a [ConfirmChoice].
     *
     * @param confirmChoice the confirmation to ask the user for.
     * @return Object of type [ConfirmResult] holding the users answer
     * @throws IOException may be thrown by console reader
     */
    @Throws(IOException::class)
    private fun doPrompt(confirmChoice: ConfirmChoice): ConfirmResult? {
        return confirmPrompt!!.prompt(confirmChoice)
    }

    /**
     * Process a [ListChoice].
     *
     * @param listChoice the list to let the user choose an item from.
     * @return Object of type [ListResult] holding the uses choice.
     * @throws IOException may be thrown by console reader
     */
    @Throws(IOException::class)
    private fun doPrompt(listChoice: ListChoice): ListResult? {
        return listPrompt!!.prompt(listChoice)
    }

    /**
     * Process a [InputValue].
     *
     * @param inputValue the input value to ask the user for.
     * @return Object of type [InputResult] holding the uses input.
     * @throws IOException may be thrown by console reader
     */
    @Throws(IOException::class)
    private fun doPrompt(inputValue: InputValue): InputResult? {
        return inputPrompt!!.prompt(inputValue)
    }

    /**
     * Process a [Checkbox].
     *
     * @param checkbox the checkbox displayed where the user can check values.
     * @return Object of type [CheckboxResult] holding the uses choice.
     * @throws IOException may be thrown by console reader
     */
    @Throws(IOException::class)
    private fun doPrompt(checkbox: Checkbox): CheckboxResult? {
        return checkboxPrompt!!.prompt(checkbox)
    }

    /**
     * Creates a [PromptBuilder].
     *
     * @return a new prompt builder object.
     */
    val promptBuilder: PromptBuilder
        get() = PromptBuilder()
}
