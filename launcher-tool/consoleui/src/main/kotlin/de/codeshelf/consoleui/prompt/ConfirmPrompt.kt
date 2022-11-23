package de.codeshelf.consoleui.prompt

import de.codeshelf.consoleui.elements.ConfirmChoice
import de.codeshelf.consoleui.prompt.reader.ConsoleReaderImpl
import de.codeshelf.consoleui.prompt.reader.ReaderIF
import de.codeshelf.consoleui.prompt.renderer.CUIRenderer
import org.fusesource.jansi.Ansi
import java.io.IOException

/**
 * Implementation of the confirm choice. The user will be asked for a yes/no questions.
 * both of the answers can be the default choice.
 *
 *
 * User: Andreas Wegmann
 * Date: 06.01.16
 */
class ConfirmPrompt : AbstractPrompt(), PromptIF<ConfirmChoice, ConfirmResult> {
    var itemRenderer: CUIRenderer = CUIRenderer.renderer
    lateinit var confirmChoice: ConfirmChoice
    var yes_key: Char
    var no_key: Char
    var yes_answer: String
    var no_answer: String
    var givenAnswer: ConfirmChoice.ConfirmationValue? = null

    /**
     * Default Constructor. Initializes the localized strings and keys from resourceBundle.
     *
     * @throws IOException can be thrown by base class construction.
     */
    init {
        yes_key = resourceBundle.getString("confirmation_yes_key").trim { it <= ' ' }[0]
        no_key = resourceBundle.getString("confirmation_no_key").trim { it <= ' ' }[0]
        yes_answer = resourceBundle.getString("confirmation_yes_answer")
        no_answer = resourceBundle.getString("confirmation_no_answer")
    }

    /**
     * Prompt the user for a question which can be answered with yes or no.
     *
     * @param confirmChoice the question for the user.
     * @return [ConfirmResult] object with answer.
     * @throws IOException can be thrown by the console reader.
     */
    @Throws(IOException::class)
    override fun prompt(confirmChoice: ConfirmChoice): ConfirmResult {
        givenAnswer = null
        this.confirmChoice = confirmChoice
        if (reader == null) {
            reader = ConsoleReaderImpl()
        }
        if (renderHeight == 0) {
            renderHeight = 2
        } else {
            println(Ansi.ansi().cursorUp(renderHeight))
        }
        this.reader.addAllowedPrintableKey(no_key)
        this.reader.addAllowedPrintableKey(yes_key)
        this.reader.addAllowedSpecialKey(ReaderIF.SpecialKey.ENTER)
        this.reader.addAllowedSpecialKey(ReaderIF.SpecialKey.BACKSPACE)
        render()
        var readerInput = this.reader.read()
        while (readerInput != null) {
            if (readerInput.specialKey == ReaderIF.SpecialKey.ENTER) {
                if (givenAnswer != null) {
                    break
                } else if (confirmChoice.defaultConfirmation != null) {
                    givenAnswer = confirmChoice.defaultConfirmation
                    break
                }
            }
            if (readerInput.specialKey == ReaderIF.SpecialKey.PRINTABLE_KEY) {
                if (readerInput.printableKey == yes_key) {
                    givenAnswer = ConfirmChoice.ConfirmationValue.YES
                } else if (readerInput.printableKey == no_key) {
                    givenAnswer = ConfirmChoice.ConfirmationValue.NO
                }
            } else if (readerInput.specialKey == ReaderIF.SpecialKey.BACKSPACE) {
                givenAnswer = null
            }
            render()
            readerInput = this.reader.read()
        }
        val resultValue = calcResultValue()
        println()
        renderMessagePromptAndResult(confirmChoice.message, resultValue)
        return ConfirmResult(givenAnswer)
    }

    /**
     * Renders the confirmation message on the screen.
     */
    private fun render() {
        println("")
        println(Ansi.ansi().eraseLine().cursorUp(2))
        print(
            renderMessagePrompt(confirmChoice.message) +
                    itemRenderer.renderConfirmChoiceOptions(confirmChoice) + " " + Ansi.ansi().reset()
                .a(calcResultValue() + " ").eraseLine()
        )
        System.out.flush()
        renderHeight = 2
    }

    /**
     * Returns the localized string representation of 'yes' or 'no' depending on the given answer.
     *
     * @return localized answer string.
     */
    private fun calcResultValue(): String {
        if (givenAnswer == ConfirmChoice.ConfirmationValue.YES) {
            return yes_answer
        } else if (givenAnswer == ConfirmChoice.ConfirmationValue.NO) {
            return no_answer
        }
        return ""
    }
}
