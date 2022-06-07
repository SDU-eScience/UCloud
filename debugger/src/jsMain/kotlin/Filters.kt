package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.code
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import kotlinx.html.js.onKeyDownFunction
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.max
import kotlin.math.min

class FilterStackItem(val id: String, val title: String) {
    var filter: String = ""
    var logLevel = MessageImportance.THIS_IS_NORMAL
    var showClient = false
    var showServer = false
    var showDatabase = false
    var depth: Int = 1
}

class Filters {
    private lateinit var elem: HTMLElement

    var onChange: () -> Unit = {}

    private var stack = listOf(
        FilterStackItem("", "<init>").apply {
            showServer = true
        }
    )
    var stackIdx = 0
        private set
    val lastIndex: Int
        get() = stack.lastIndex
    val currentStack: FilterStackItem
        get() = stack[stackIdx]

    val id: String
        get() = currentStack.id

    var filter: String
        get() = currentStack.filter
        private set(value) {
            currentStack.filter = value
        }
    var logLevel: MessageImportance
        get() = stack[stackIdx].logLevel
        private set(value) {
            currentStack.logLevel = value
        }
    var showClient: Boolean
        get() = stack[stackIdx].showClient
        private set(value) {
            currentStack.showClient = value
        }
    var showServer: Boolean
        get() = stack[stackIdx].showServer
        private set(value) {
            currentStack.showServer = value
        }
    var showDatabase: Boolean
        get() = stack[stackIdx].showDatabase
        private set(value) {
            currentStack.showDatabase = value
        }

    private val stackContainer = DomRef<HTMLDivElement>()
    private val clientBox = DomRef<HTMLInputElement>()
    private val serverBox = DomRef<HTMLInputElement>()
    private val databaseBox = DomRef<HTMLInputElement>()
    private val queryBox = DomRef<HTMLInputElement>()
    private val levelBox = DomRef<HTMLSelectElement>()

    fun render(): HTMLElement {
        style.mount()
        if (this::elem.isInitialized) return elem


        elem = document.create.div(elemClass) {
            div {
                inlineStyle {
                    flexGrow = "2"
                    flexBasis = 150.px
                    whiteSpace = "nowrap"
                }

                h4 { +"Stack" }
                div {
                    inlineStyle {
                        borderRadius = 5.px
                        borderColor = Rgb(201, 211, 223).toString()
                        borderStyle = "solid"
                        borderWidth = 2.px
                        padding = 5.px
                        overflowY = "scroll"
                        overflowX = "hidden"
                        height = 100.percent
                    }
                    div {
                        inlineStyle {
                            display = "flex"
                            flexDirection = "column"
                        }

                        capture(stackContainer)
                    }
                }
            }

            div {
                inlineStyle { flexGrow = "2" }
                div {
                    h4 { +"Query" }
                    standardInput {
                        capture(queryBox)
                        onKeyDownFunction = { ev ->
                            ev as KeyboardEvent
                            ev.stopPropagation()
                            if (ev.code == "Escape") {
                                (ev.target as HTMLInputElement).blur()
                            }
                        }

                        onInputFunction = { ev ->
                            val input = ev.target as HTMLInputElement
                            filter = input.value
                            this@Filters.onChange()
                        }
                    }
                }
                div {
                    div {
                        inlineStyle {
                            display = "flex"
                            gap = 16.px
                        }
                        standardCheckbox(label = "Client") {
                            capture(clientBox)
                        }

                        standardCheckbox(label = "Server") {
                            capture(serverBox)
                        }

                        standardCheckbox(label = "Database") {
                            capture(databaseBox)
                        }
                    }
                }
            }

            div {
                h4 { +"Level" }
                standardSelect {
                    capture(levelBox)

                    onChangeFunction = { ev ->
                        val select = ev.target as HTMLSelectElement
                        logLevel = MessageImportance.valueOf(select.value)
                        this@Filters.onChange()
                    }

                    option {
                        value = MessageImportance.TELL_ME_EVERYTHING.name
                        text("Everything")
                    }

                    option {
                        value = MessageImportance.IMPLEMENTATION_DETAIL.name
                        text("Details")
                    }

                    option {
                        value = MessageImportance.THIS_IS_NORMAL.name
                        selected = true
                        text("Normal")
                    }

                    option {
                        value = MessageImportance.THIS_IS_ODD.name
                        text("Odd")
                    }

                    option {
                        value = MessageImportance.THIS_IS_WRONG.name
                        text("Wrong")
                    }

                    option {
                        value = MessageImportance.THIS_IS_DANGEROUS.name
                        text("Dangerous")
                    }
                }
            }
        }

        clientBox.find(elem).onchange = { ev ->
            showClient = (ev.target as HTMLInputElement).checked
            this@Filters.onChange()
        }
        serverBox.find(elem).onchange = { ev ->
            showServer = (ev.target as HTMLInputElement).checked
            this@Filters.onChange()
        }
        databaseBox.find(elem).onchange = { ev ->
            showDatabase = (ev.target as HTMLInputElement).checked
            this@Filters.onChange()
        }

        document.addEventListener("keydown", { event ->
            event as KeyboardEvent
            when(event.code) {
                "KeyP" -> {
                    stackIdx = min(stack.lastIndex, max(0, stackIdx - 1))
                    renderAndNotify()
                }

                "KeyO" -> {
                    stackIdx = min(stack.lastIndex, max(0, stackIdx + 1))
                    renderAndNotify()
                }

                "Slash" -> {
                    event.preventDefault()
                    queryBox.find(elem).focus()
                }

                "KeyQ" -> {
                    logLevel = MessageImportance.TELL_ME_EVERYTHING
                    renderAndNotify()
                }

                "KeyW" -> {
                    logLevel = MessageImportance.THIS_IS_NORMAL
                    renderAndNotify()
                }

                "KeyE" -> {
                    logLevel = MessageImportance.THIS_IS_ODD
                    renderAndNotify()
                }

                "KeyR" -> {
                    logLevel = MessageImportance.THIS_IS_WRONG
                    renderAndNotify()
                }

                "KeyT" -> {
                    logLevel = MessageImportance.THIS_IS_DANGEROUS
                    renderAndNotify()
                }
            }
        })

        renderStack()
        return elem
    }

    private fun renderAndNotify() {
        renderStack()
        onChange()
    }

    private fun renderStack() {
        val container = stackContainer.find(elem)
        container.innerHTML = ""
        for ((index, item) in stack.withIndex()) {
            val isActive = stackIdx == index
            container.appendChild(
                document.create.code(classes = if (isActive) "active" else null) {
                    text(item.title)

                    onClickFunction = {
                        stackIdx = index
                        renderStack()
                        this@Filters.onChange()
                    }
                }
            )
        }

        queryBox.find(elem).value = filter
        levelBox.find(elem).value = logLevel.name
        clientBox.find(elem).checked = showClient
        serverBox.find(elem).checked = showServer
        databaseBox.find(elem).checked = showDatabase
    }

    fun addStack(id: String, title: String, replaceIdx: Int? = null, doRender: Boolean = true, block: FilterStackItem.() -> Unit = {}) {
        stack = stack.subList(0, (replaceIdx ?: stackIdx) + 1) + FilterStackItem(id, title).also(block)
        stackIdx = stack.lastIndex
        if (doRender) {
            renderStack()
            onChange()
        }
    }

    fun updateSelectedStack(idx: Int) {
        stackIdx = idx
        renderStack()
        onChange()
    }

    companion object {
        private const val elemClass = "filters"
        private const val activeStackClass = "active"

        val style = CssMounter {
            val self = byClass(elemClass)
            val code = self descendant byTag("code")
            val activeCode = self descendant (byTag("code") and byClass(activeStackClass))

            self {
                display = "flex"
                flexDirection = "row"
                gap = 8.px
                height = 250.px
            }

            (self descendant byTag("h4")) {
                margin = 0.px
            }

            (self directChild byTag("div")) {
                display = "flex"
                flexShrink = "0"
                flexDirection = "column"
                overflowY = "auto"
                overflowX = "hidden"
            }

            code {
                padding = 5.px
                cursor = "pointer"
            }

            activeCode {
                backgroundColor = "#006aff"
                color = "white"
            }
        }
    }
}
