package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.html.CommonAttributeGroupFacade
import kotlinx.html.STYLE
import kotlinx.html.TagConsumer
import kotlinx.html.dom.create
import kotlinx.html.style
import kotlinx.html.unsafe
import org.w3c.dom.HTMLElement
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KProperty

fun TagConsumer<HTMLElement>.css(builder: CssBuilder.() -> Unit): HTMLElement {
    return style { css(builder) }
}

fun STYLE.css(builder: CssBuilder.() -> Unit) {
    unsafe { +generateCss(builder) }
}

fun CommonAttributeGroupFacade.inlineStyle(builder: CssPropertyListBuilder.() -> Unit) {
    style = generateCss(useSelectors = false, builder = {
        root {
            builder()
        }
    })
}

private fun generateCss(builder: CssBuilder.() -> Unit, useSelectors: Boolean = true): String {
    val cssBuilder = CssBuilder().also(builder)
    val cssRuleBuilder = StringBuilder()
    cssBuilder.imports.forEach { import ->
        cssRuleBuilder.appendLine("@import url('$import');")
    }
    cssBuilder.rules.forEach { (selector, props) ->
        if (useSelectors) cssRuleBuilder.append("$selector {\n")
        props.forEach { (k, v) -> cssRuleBuilder.append("  $k:$v;\n") }
        if (useSelectors) cssRuleBuilder.append("}\n")
    }
    return cssRuleBuilder.toString()
}

class CssBuilder : CssSelectorContext, CssPropertyListBuilder() {
    val imports = ArrayList<String>()
    val rules = ArrayList<Pair<String, Map<String, String>>>()

    operator fun CssSelector.invoke(builder: CssPropertyListBuilder.() -> Unit) {
        rules.add(textValue to CssPropertyListBuilder().also(builder).properties)
    }
}

open class CssPropertyListBuilder {
    val properties: MutableMap<String, String> = HashMap()

    fun add(property: String, value: String) {
        properties[property] = value
    }
}

fun CssBuilder.importGoogleFont(name: String, weights: List<Int>) {
    imports.add("https://fonts.googleapis.com/css2?family=${name.replace(" ", "+")}:wght@${weights.joinToString(";")}&display=swap")
}

class WriteOnlyProperty() : RuntimeException("Write only property")

var CssPropertyListBuilder.textDecoration: String by CSSDelegate()
var CssPropertyListBuilder.color: String by CSSDelegate()
var CssPropertyListBuilder.transition: String by CSSDelegate()
var CssPropertyListBuilder.verticalAlign: String by CSSDelegate()
var CssPropertyListBuilder.position: String by CSSDelegate()
var CssPropertyListBuilder.top: String by CSSDelegate()
var CssPropertyListBuilder.bottom: String by CSSDelegate()
var CssPropertyListBuilder.left: String by CSSDelegate()
var CssPropertyListBuilder.right: String by CSSDelegate()
var CssPropertyListBuilder.background: String by CSSDelegate()
var CssPropertyListBuilder.backgroundColor: String by CSSDelegate()
var CssPropertyListBuilder.content: String by CSSDelegate()
var CssPropertyListBuilder.opacity: String by CSSDelegate()
var CssPropertyListBuilder.outline: String by CSSDelegate()
var CssPropertyListBuilder.appearance: String by CSSDelegate()
var CssPropertyListBuilder.display: String by CSSDelegate()
var CssPropertyListBuilder.padding: String by CSSDelegate()
var CssPropertyListBuilder.paddingTop: String by CSSDelegate()
var CssPropertyListBuilder.paddingBottom: String by CSSDelegate()
var CssPropertyListBuilder.paddingLeft: String by CSSDelegate()
var CssPropertyListBuilder.paddingRight: String by CSSDelegate()
var CssPropertyListBuilder.cursor: String by CSSDelegate()
var CssPropertyListBuilder.transform: String by CSSDelegate()
var CssPropertyListBuilder.border: String by CSSDelegate()
var CssPropertyListBuilder.borderTop: String by CSSDelegate()
var CssPropertyListBuilder.borderWidth: String by CSSDelegate()
var CssPropertyListBuilder.borderStyle: String by CSSDelegate()
var CssPropertyListBuilder.borderColor: String by CSSDelegate()
var CssPropertyListBuilder.borderRadius: String by CSSDelegate()
var CssPropertyListBuilder.width: String by CSSDelegate()
var CssPropertyListBuilder.height: String by CSSDelegate()
var CssPropertyListBuilder.overflow: String by CSSDelegate()
var CssPropertyListBuilder.overflowX: String by CSSDelegate()
var CssPropertyListBuilder.overflowY: String by CSSDelegate()
var CssPropertyListBuilder.margin: String by CSSDelegate()
var CssPropertyListBuilder.marginTop: String by CSSDelegate()
var CssPropertyListBuilder.marginLeft: String by CSSDelegate()
var CssPropertyListBuilder.marginRight: String by CSSDelegate()
var CssPropertyListBuilder.marginBottom: String by CSSDelegate()
var CssPropertyListBuilder.alignItems: String by CSSDelegate()
var CssPropertyListBuilder.justifyContent: String by CSSDelegate()
var CssPropertyListBuilder.justifySelf: String by CSSDelegate()
var CssPropertyListBuilder.justifyItems: String by CSSDelegate()
var CssPropertyListBuilder.flexDirection: String by CSSDelegate()
var CssPropertyListBuilder.flexWrap: String by CSSDelegate()
var CssPropertyListBuilder.flexFlow: String by CSSDelegate()
var CssPropertyListBuilder.flexGrow: String by CSSDelegate()
var CssPropertyListBuilder.flexShrink: String by CSSDelegate()
var CssPropertyListBuilder.flexBasis: String by CSSDelegate()
var CssPropertyListBuilder.boxSizing: String by CSSDelegate()
var CssPropertyListBuilder.resize: String by CSSDelegate()
var CssPropertyListBuilder.fontSize: String by CSSDelegate()
var CssPropertyListBuilder.fontWeight: String by CSSDelegate()
var CssPropertyListBuilder.lineHeight: String by CSSDelegate()
var CssPropertyListBuilder.fontFamily: String by CSSDelegate()
var CssPropertyListBuilder.listStyle: String by CSSDelegate()
var CssPropertyListBuilder.maxWidth: String by CSSDelegate()
var CssPropertyListBuilder.maxHeight: String by CSSDelegate()
var CssPropertyListBuilder.minHeight: String by CSSDelegate()
var CssPropertyListBuilder.minWidth: String by CSSDelegate()
var CssPropertyListBuilder.borderCollapse: String by CSSDelegate()
var CssPropertyListBuilder.borderSpacing: String by CSSDelegate()
var CssPropertyListBuilder.textAlign: String by CSSDelegate()
var CssPropertyListBuilder.boxShadow: String by CSSDelegate()
var CssPropertyListBuilder.userSelect: String by CSSDelegate()
var CssPropertyListBuilder.textTransform: String by CSSDelegate()
var CssPropertyListBuilder.letterSpacing: String by CSSDelegate()
var CssPropertyListBuilder.gridTemplateColumns: String by CSSDelegate()
var CssPropertyListBuilder.gridTemplateAreas: String by CSSDelegate()
var CssPropertyListBuilder.gridArea: String by CSSDelegate()
var CssPropertyListBuilder.gridGap: String by CSSDelegate()
var CssPropertyListBuilder.gap: String by CSSDelegate()
var CssPropertyListBuilder.zIndex: String by CSSDelegate()
var CssPropertyListBuilder.objectFit: String by CSSDelegate()
var CssPropertyListBuilder.whiteSpace: String by CSSDelegate()

class CSSDelegate(val name: String? = null) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        throw WriteOnlyProperty()
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        (thisRef as CssPropertyListBuilder).add(name ?: transformName(property.name), value)
    }

    private fun transformName(name: String): String {
        val builder = StringBuilder()
        for (c in name) {
            if (c.isUpperCase()) {
                builder.append("-")
                builder.append(c.toLowerCase())
            } else {
                builder.append(c)
            }
        }
        return builder.toString()
    }
}

fun Char.isUpperCase(): Boolean = uppercaseChar() == this
fun Char.isLowerCase(): Boolean = lowercaseChar() == this

data class CssSelector(val textValue: String)

interface CssSelectorContext

val CssSelectorContext.host: CssSelector
    get() = CssSelector(":host")

val CssSelectorContext.root: CssSelector
    get() = CssSelector(":root")

val CssSelectorContext.empty: CssSelector
    get() = CssSelector("")

fun CssSelectorContext.inHost(fn: CssSelector.() -> CssSelector): CssSelector {
    return empty.fn().inHost()
}

fun CssSelector.slotted() = CssSelector("::slotted($textValue)")

fun CssSelector.inHost() = CssSelector(":host(${textValue.removePrefix(":host")})")
fun CssSelectorContext.byTag(tagName: String) = CssSelector(tagName)
fun CssSelectorContext.byClass(className: String) = CssSelector(".$className")
fun CssSelectorContext.byId(idName: String) = CssSelector("#$idName")
fun CssSelectorContext.byNamespace(namespace: String) = CssSelector("$namespace:|*")
fun CssSelectorContext.matchAny() = CssSelector("*")
fun CssSelectorContext.withNoNamespace() = CssSelector("|*")
fun CssSelector.attributePresent(
    attributeName: String
): CssSelector {
    val result = CssSelector("$textValue[$attributeName]")
    if (textValue == ":host") {
        return result.inHost()
    }
    return result
}

fun CssSelector.attributeEquals(
    attributeName: String,
    value: String,
    caseInsensitive: Boolean = false
): CssSelector {
    val result = CssSelector("$textValue[$attributeName=$value${if (caseInsensitive) " i" else ""}]")
    if (textValue == ":host") return result.inHost()
    return result
}

fun CssSelector.attributeListContains(
    attributeName: String,
    value: String,
    caseInsensitive: Boolean = false
): CssSelector {
    val result = CssSelector("$textValue[$attributeName~=$value${if (caseInsensitive) " i" else ""}]")
    if (textValue == ":host") return result.inHost()
    return result
}

fun CssSelector.attributeEqualsHyphen(
    attributeName: String,
    value: String,
    caseInsensitive: Boolean = false
): CssSelector {
    val result = CssSelector("$textValue[$attributeName|=$value${if (caseInsensitive) " i" else ""}]")
    if (textValue == ":host") return result.inHost()
    return result
}

fun CssSelector.attributeStartsWith(
    attributeName: String,
    value: String,
    caseInsensitive: Boolean = false
) = CssSelector("$textValue[$attributeName^=$value${if (caseInsensitive) " i" else ""}]")

fun CssSelector.attributeEndsWith(
    attributeName: String,
    value: String,
    caseInsensitive: Boolean = false
): CssSelector {
    val result = CssSelector("$textValue[$attributeName\$=$value${if (caseInsensitive) " i" else ""}]")
    if (textValue == ":host") return result.inHost()
    return result
}

fun CssSelector.attributeContains(
    attributeName: String,
    value: String,
    caseInsensitive: Boolean = false
): CssSelector {
    val result = CssSelector("$textValue[$attributeName*=$value${if (caseInsensitive) " i" else ""}]")
    if (textValue == ":host") return result.inHost()
    return result
}

fun CssSelector.withPseudoClass(className: String) = CssSelector("$textValue:$className")
fun CssSelector.withPseudoElement(element: String) = CssSelector("$textValue::$element")

infix fun CssSelector.adjacentSibling(other: CssSelector) = CssSelector("$textValue + ${other.textValue}")
infix fun CssSelector.anySibling(other: CssSelector) = CssSelector("$textValue ~ ${other.textValue}")
infix fun CssSelector.directChild(other: CssSelector) = CssSelector("$textValue > ${other.textValue}")
infix fun CssSelector.descendant(other: CssSelector) = CssSelector("$textValue ${other.textValue}")

infix fun CssSelector.or(other: CssSelector) = CssSelector("$textValue, ${other.textValue}")
infix fun CssSelector.and(other: CssSelector) = CssSelector("$textValue${other.textValue}")

fun List<CssSelector>.anyOf(): CssSelector = CssSelector(joinToString(", ") { it.textValue })

fun CssSelectorContext.byAnyHeader() = (1..6).map { level -> byTag("h$level") }.anyOf()

val Int.pt get() = "${this}pt"
val Int.px get() = "${this}px"
val Int.vh get() = "${this}vh"
val Int.vw get() = "${this}vw"
val Int.em get() = "${this}px"
val Int.percent get() = "${this}%"

data class CSSVar(val name: String) {
    override fun toString(): String = "var(--${name})"
}

fun CssPropertyListBuilder.variable(v: CSSVar, default: String? = null): String {
    if (default != null) {
        return "var(--${v.name}, $default)"
    } else {
        return "var(--${v.name})"
    }
}

fun CssPropertyListBuilder.setVariable(v: CSSVar, value: String) {
    add("--${v.name}", value)
}

fun CssPropertyListBuilder.setVariable(v: CSSVar, value: Rgb) {
    setVariable(v, value.toString())
}

fun boxShadow(
    offsetX: Int,
    offsetY: Int,
    blurRadius: Int = 0,
    spreadRadius: Int = 0,
    color: String? = null
): String {
    return buildString {
        append("${offsetX}px ${offsetY}px ")
        append("${blurRadius}px ${spreadRadius}px")
        if (color != null) {
            append(" ")
            append(color)
        }
    }
}

data class Rgb(val r: Int, val g: Int, val b: Int) {
    companion object {
        fun create(value: Int): Rgb {
            val r = value shr 16
            val g = (value shr 8) and (0x00FF)
            val b = (value) and (0x0000FF)
            return Rgb(r, g, b)
        }

        fun create(value: String): Rgb {
            return create(value.removePrefix("#").toInt(16))
        }
    }

    override fun toString(): String = "rgb($r, $g, $b)"
}

fun Rgb.lighten(amount: Int): Rgb {
    require(amount >= 0)
    return Rgb(min(255, r + amount), min(255, g + amount), min(255, b + amount))
}

fun Rgb.darken(amount: Int): Rgb {
    require(amount >= 0)
    return Rgb(max(0, r - amount), max(0, g - amount), max(0, b - amount))
}

class CssMounter(private val builder: CssBuilder.() -> Unit) {
    private var didMount = false

    fun mount() {
        if (didMount) return
        didMount = true
        head.appendChild(document.create.css(builder))
    }
}
