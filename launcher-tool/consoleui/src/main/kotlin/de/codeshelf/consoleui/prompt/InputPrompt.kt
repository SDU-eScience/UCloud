package de.codeshelf.consoleui.prompt

import de.codeshelf.consoleui.elements.InputValue
import de.codeshelf.consoleui.prompt.reader.ConsoleReaderImpl
import de.codeshelf.consoleui.prompt.reader.ReaderIF
import de.codeshelf.consoleui.prompt.renderer.CUIRenderer
import java.io.IOException

/**
 * Implementation of the input choice prompt. The user will be asked for a string input value.
 * With support of completers an automatic expansion of strings and filenames can be configured.
 * Defining a mask character, a password like input is possible.
 *
 *
 * User: Andreas Wegmann
 *
 *
 * Date: 06.01.16
 */
class InputPrompt : AbstractPrompt(), PromptIF<InputValue, InputResult> {
    lateinit var inputElement: InputValue
    var itemRenderer: CUIRenderer = CUIRenderer.Companion.renderer
    @Throws(IOException::class)
    override fun prompt(inputElement: InputValue): InputResult {
        this.inputElement = inputElement
        if (reader == null) {
            reader = ConsoleReaderImpl()
        }
        if (renderHeight == 0) {
            renderHeight = 2
        }
        val prompt =
            renderMessagePrompt(this.inputElement.message) + itemRenderer.renderOptionalDefaultValue(this.inputElement)
        //System.out.print(prompt + itemRenderer.renderValue(this.inputElement));
        //System.out.flush();
        val completer = inputElement.completer
        val mask = inputElement.mask
        val readerInput = reader.readLine(completer, prompt, inputElement.value, mask)
        var lineInput = readerInput.lineInput
        if (lineInput == null || lineInput.trim { it <= ' ' }.length == 0) {
            lineInput = inputElement.defaultValue
        }
        var result: String?
        if (mask == null) {
            result = lineInput
        } else {
            result = ""
            if (lineInput != null) {
                for (i in 0 until lineInput.length) {
                    result += mask
                }
            }
        }
        renderMessagePromptAndResult(inputElement.message, result!!)
        return InputResult(lineInput)
    }
}
