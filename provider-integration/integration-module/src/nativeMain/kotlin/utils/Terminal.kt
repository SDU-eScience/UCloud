package dk.sdu.cloud.utils

class TerminalMessageDsl {
    private val builder: StringBuilder = StringBuilder()
    private var color = 0
    private var backgroundColor = 0

    fun inline(message: String) {
        builder.append(message)
    }

    fun line(message: String) {
        builder.appendLine(message)
    }

    fun line() {
        builder.appendLine()
    }

    fun startBold() {
        sgr(1)
    }

    fun endBold() {
        sgr(22)
    }

    fun bold(block: TerminalMessageDsl.() -> Unit) {
        startBold()
        block()
        endBold()
    }

    fun startItalics() {
        sgr(3)
    }

    fun endItalics() {
        sgr(23)
    }

    fun italics(block: TerminalMessageDsl.() -> Unit) {
        startItalics()
        block()
        endItalics()
    }

    fun code(block: TerminalMessageDsl.() -> Unit) {
        green(block)
    }

    fun setColor(colorId: Int) {
        color = colorId
        sgr(30 + colorId)
    }

    fun setBackgroundColor(colorId: Int) {
        backgroundColor = colorId
        sgr(40 + colorId)
    }

    fun foreground(colorId: Int, block: TerminalMessageDsl.() -> Unit) {
        val oldColor = color
        setColor(colorId)
        block()
        setColor(oldColor)
    }

    fun black(block: TerminalMessageDsl.() -> Unit) {
        foreground(0, block)
    }

    fun red(block: TerminalMessageDsl.() -> Unit) {
        foreground(1, block)
    }

    fun green(block: TerminalMessageDsl.() -> Unit) {
        foreground(2, block)
    }

    fun yellow(block: TerminalMessageDsl.() -> Unit) {
        foreground(3, block)
    }

    fun blue(block: TerminalMessageDsl.() -> Unit) {
        foreground(4, block)
    }

    fun magenta(block: TerminalMessageDsl.() -> Unit) {
        foreground(5, block)
    }

    fun cyan(block: TerminalMessageDsl.() -> Unit) {
        foreground(6, block)
    }

    fun white(block: TerminalMessageDsl.() -> Unit) {
        foreground(7, block)
    }

    fun background(colorId: Int, block: TerminalMessageDsl.() -> Unit) {
        val oldColor = backgroundColor
        setBackgroundColor(colorId)
        block()
        setBackgroundColor(oldColor)
    }

    fun blackBg(block: TerminalMessageDsl.() -> Unit) {
        background(0, block)
    }

    fun redBg(block: TerminalMessageDsl.() -> Unit) {
        background(1, block)
    }

    fun greenBg(block: TerminalMessageDsl.() -> Unit) {
        background(2, block)
    }

    fun yellowBg(block: TerminalMessageDsl.() -> Unit) {
        background(3, block)
    }

    fun blueBg(block: TerminalMessageDsl.() -> Unit) {
        background(4, block)
    }

    fun magentaBg(block: TerminalMessageDsl.() -> Unit) {
        background(5, block)
    }

    fun cyanBg(block: TerminalMessageDsl.() -> Unit) {
        background(6, block)
    }

    fun whiteBg(block: TerminalMessageDsl.() -> Unit) {
        background(7, block)
    }

    fun reset() {
        sgr(0)
    }

    fun sgr(n: Int) {
        builder.append("\u001B[${n}m")
    }

    fun print() {
        reset() // NOTE(Dan): Make sure we reset back to normal style after printing.
        println(builder)
    }
}

fun sendTerminalMessage(block: TerminalMessageDsl.() -> Unit) {
    TerminalMessageDsl().apply(block).print()
}
