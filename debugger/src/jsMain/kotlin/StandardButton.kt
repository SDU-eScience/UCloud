import kotlinx.html.BUTTON
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button

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
