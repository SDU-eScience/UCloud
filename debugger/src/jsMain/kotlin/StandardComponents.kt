package dk.sdu.cloud.debug

import kotlinx.html.*

// Button
// =====================================================================================================================
fun FlowContent.standardButton(classes: String? = null, block: BUTTON.() -> Unit) {
    standardButtonStyle.mount()
    button(classes = standardButtonClass + " " + (classes ?: "")) {
        type = ButtonType.button
        block()
    }
}

private const val standardButtonClass = "standard-button"
private val standardButtonStyle = CssMounter {
    (byClass(standardButtonClass)) {
        display = "inline-flex"
        justifyContent = "center"
        alignItems = "center"
        textDecoration = "none"
        fontFamily = "inherit"
        fontWeight = "700"
        lineHeight = "1.5"
        cursor = "pointer"
        borderRadius = 5.px
        backgroundColor = "#006aff"
        color = "white"
        borderWidth = 0.px
        borderStyle = "solid"
        transition = "all 60ms cubic-bezier(0.5, 0, 0.25, 1) 0s"
        width = 100.percent
        fontSize = 14.px
        padding = "9.5px 18px"
        marginBottom = 4.px
    }

    (byClass(standardButtonClass).withPseudoClass("hover")) {
        transform = "translateY(-2px)"
    }

    (byClass(standardButtonClass).attributePresent("disabled")) {
        cursor = "not-allowed"
        opacity = "0.25"
    }
}

// Input
// =====================================================================================================================
fun FlowContent.standardInput(classes: String? = null, block: INPUT.() -> Unit) {
    standardInputStyle.mount()
    input(classes = standardInputClass + " " + (classes ?: "")) { block() }
}

private const val standardInputClass = "standard-input"
private val standardInputStyle = CssMounter {
    (byClass(standardInputClass)) {
        display = "block"
        fontFamily = "inherit"
        color = "black"
        backgroundColor = "transparent"
        margin = 0.px
        borderWidth = 2.px
        borderColor = Rgb(201, 211, 223).toString()
        borderStyle = "solid"
        padding = "7px 12px"
        borderRadius = 5.px
        width = 100.percent
    }

    (byClass(standardInputClass).withPseudoClass("focus")) {
        outline = "none"
        backgroundColor = "transparent"
        borderColor = Rgb(0, 106, 255).toString()
    }
}

// Select
// =====================================================================================================================
fun FlowContent.standardSelect(classes: String? = null, block: SELECT.() -> Unit) {
    standardSelectStyle.mount()
    div(standardSelectClass) {
        select(classes = classes) { block() }
    }
}

private const val standardSelectClass = "standard-select"
private val standardSelectStyle = CssMounter {
    (byClass(standardSelectClass)) {
        display = "grid"
        gridTemplateAreas = "\"select\""
        alignItems = "center"
        position = "relative"
    }

    val selectElement = byClass(standardSelectClass) directChild byTag("select")

    selectElement {
        appearance = "none"
        display = "block"
        fontFamily = "inherit"

        color = "inherit"
        backgroundColor = "transparent"

        width = 100.percent
        margin = 0.px
        paddingLeft = 12.px
        paddingRight = 32.px
        paddingTop = 7.px
        paddingBottom = 7.px

        borderRadius = 5.px
        borderWidth = 2.px
        borderStyle = "solid"
        borderColor = "#c9d3df"

        gridArea = "select"
    }

    (selectElement directChild byTag("option")) {
        color = "black"
    }

    (selectElement.withPseudoClass("invalid")) {
        borderColor = "#c00"
    }

    (selectElement.withPseudoClass("focus")) {
        outline = "none"
        borderColor = "#006aff"
    }

    (byClass(standardSelectClass).withPseudoElement("after")) {
        display = "block"
        width = "0.7em"
        height = "0.7em"
        content = "url('/static/chevronDown.svg')"
        justifySelf = "end"
        gridArea = "select"
        marginRight = 10.px
    }
}

// Checkbox
// =====================================================================================================================
fun FlowContent.standardCheckbox(classes: String? = null, label: String? = null, block: INPUT.() -> Unit = {}) {
    standardCheckboxStyle.mount()
    if (label != null) {
        label {
            inlineStyle { userSelect = "none" }
            standardCheckbox(classes, block = block)
            text(label)
        }
    } else {
        div(standardCheckboxClass) {
            input(type = InputType.checkBox) { block() }
            unsafe {
                raw(
                    """
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentcolor" width="32px" data-name="checked">
                            <path d="M6 3h12c1.7 0 3 1.3 3 3v12c0 1.7-1.3 3-3 3H6c-1.7 0-3-1.3-3-3V6c0-1.7 1.3-3 3-3zm4 14l9-8.6L17.6 7 10 14.3l-3.6-3.5L5 12.2l5 4.8z"/>
                        </svg>                                  
                    """.trimIndent()
                )
                raw(
                    """
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentcolor" width="32px" data-name="empty"> <path d="M6 5c-.6 0-1 .4-1 1v12c0 .6.4 1 1 1h12c.6 0 1-.4 1-1V6c0-.6-.4-1-1-1H6zm0-2h12c1.7 0 3 1.3 3 3v12c0 1.7-1.3 3-3 3H6c-1.7 0-3-1.3-3-3V6c0-1.7 1.3-3 3-3z" />
                        </svg> 
                    """.trimIndent()
                )
            }
        }
    }
}

private const val standardCheckboxClass = "standard-checkbox"
private val standardCheckboxStyle = CssMounter {
    val self = byClass(standardCheckboxClass)
    val inputElement = self descendant byTag("input")
    val inputIsChecked = inputElement.withPseudoClass("checked")
    val checkedSvg = byTag("svg").attributeEquals("data-name", "checked")
    val uncheckedSvg = byTag("svg").attributeEquals("data-name", "empty")

    self {
        display = "inline-block"
        position = "relative"
        verticalAlign = "middle"
        cursor = "pointer"

        color = "#c9d3df"
    }

    inputElement {
        appearance = "none"
        opacity = "0"
        position = "absolute"
    }

    checkedSvg {
        display = "none"
    }

    (inputIsChecked anySibling checkedSvg) {
        display = "inline-block"
        color = "#006aff"
    }

    (inputIsChecked anySibling uncheckedSvg) {
        display = "none"
    }
}
