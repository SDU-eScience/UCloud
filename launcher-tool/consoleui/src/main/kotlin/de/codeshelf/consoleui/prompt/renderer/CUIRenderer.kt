package de.codeshelf.consoleui.prompt.renderer

import de.codeshelf.consoleui.elements.ConfirmChoice
import de.codeshelf.consoleui.elements.InputValue
import de.codeshelf.consoleui.elements.items.ConsoleUIItemIF
import de.codeshelf.consoleui.elements.items.impl.CheckboxItem
import de.codeshelf.consoleui.elements.items.impl.ChoiceItem
import de.codeshelf.consoleui.elements.items.impl.ListItem
import de.codeshelf.consoleui.elements.items.impl.Separator
import org.fusesource.jansi.Ansi
import java.util.*

/**
 * User: Andreas Wegmann
 * Date: 01.01.16
 */
class CUIRenderer {
    //private final String cursorSymbol = ansi().fg(Ansi.Color.CYAN).a("\uF078> ").toString();
    private var cursorSymbol: String? = null
    private var noCursorSpace: String? = null
    private var uncheckedBox: String? = null
    private var checkedBox: String? = null
    private var line: String? = null
    private val resourceBundle: ResourceBundle

    init {
        val os = System.getProperty("os.name")
        if (os.startsWith("Windows")) {
            checkedBox = "(*) "
            uncheckedBox = "( ) "
            line = "---------"
            cursorSymbol = Ansi.ansi().fg(Ansi.Color.CYAN).a("    > ").toString()
            noCursorSpace = Ansi.ansi().fg(Ansi.Color.DEFAULT).a("      ").toString()
        } else {
            checkedBox = "\u25C9 "
            uncheckedBox = "\u25EF "
            line = "\u2500─────────────"
            cursorSymbol = Ansi.ansi().fg(Ansi.Color.CYAN).a("   \u276F ").toString()
            noCursorSpace = Ansi.ansi().fg(Ansi.Color.DEFAULT).a("     ").toString()
        }
        resourceBundle = ResourceBundle.getBundle("consoleui_messages")
    }

    fun render(item: ConsoleUIItemIF, withCursor: Boolean): String {
        if (item is CheckboxItem) {
            val checkboxItem = item
            val prefix: String?
            prefix = if (withCursor) {
                cursorSymbol
            } else {
                noCursorSpace
            }
            return prefix + Ansi.ansi()
                .fg(if (checkboxItem.isEnabled) Ansi.Color.GREEN else Ansi.Color.WHITE)
                .a(if (checkboxItem.isChecked) checkedBox else uncheckedBox)
                .reset().a(
                    checkboxItem.text +
                            if (checkboxItem.isDisabled) " (" + checkboxItem.disabledText + ")" else ""
                )
                .eraseLine(Ansi.Erase.FORWARD)
                .reset().toString()
        }
        if (item is ListItem) {
            val listItem = item
            return if (listItem.name?.startsWith("sep-") == true) {
                noCursorSpace + Ansi.ansi()
                    .bold()
                    .fg(Ansi.Color.DEFAULT).a(listItem.text)
                    .eraseLine(Ansi.Erase.FORWARD)
                    .reset().toString()
            } else if (withCursor) {
                cursorSymbol + Ansi.ansi()
                    .fg(Ansi.Color.CYAN).a(listItem.text)
                    .eraseLine(Ansi.Erase.FORWARD)
                    .reset().toString()
            } else {
                noCursorSpace + Ansi.ansi()
                    .fg(Ansi.Color.DEFAULT).a(listItem.text)
                    .eraseLine(Ansi.Erase.FORWARD)
                    .reset().toString()
            }
        }
        if (item is ChoiceItem) {
            val checkboxItem = item
            return if (withCursor) {
                cursorSymbol + Ansi.ansi()
                    .fg(Ansi.Color.CYAN).a(checkboxItem.key.toString() + " - " + checkboxItem.message)
                    .eraseLine(Ansi.Erase.FORWARD)
                    .reset().toString()
            } else noCursorSpace + Ansi.ansi()
                .fg(Ansi.Color.DEFAULT).a(checkboxItem.key.toString() + " - " + checkboxItem.message)
                .eraseLine(Ansi.Erase.FORWARD)
                .reset().toString()
        }
        if (item is Separator) {
            val separator = item
            return Ansi.ansi().fg(Ansi.Color.WHITE).a(
                if (separator.message != null) separator.message else line
            ).reset().toString()
        }
        return ""
    }

    fun renderOptionalDefaultValue(inputElement: InputValue): String {
        val defaultValue = inputElement.defaultValue
        return if (defaultValue != null) {
            " ($defaultValue) "
        } else " "
    }

    fun renderValue(inputElement: InputValue): String {
        return inputElement.value ?: ""
    }

    fun renderConfirmChoiceOptions(confirmChoice: ConfirmChoice): String {
        if (confirmChoice.defaultConfirmation == ConfirmChoice.ConfirmationValue.YES) {
            return resourceBundle.getString("confirmation_yes_default")
        } else if (confirmChoice.defaultConfirmation == ConfirmChoice.ConfirmationValue.NO) {
            return resourceBundle.getString("confirmation_no_default")
        }
        return resourceBundle.getString("confirmation_without_default")
    }

    companion object {
        val renderer = CUIRenderer()
    }
}
