import dk.sdu.cloud.debug.DebugContext
import dk.sdu.cloud.debug.DebugMessage
import dk.sdu.cloud.debug.MessageImportance
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

class Log {
    private val messages = ArrayList<DebugMessage>()
    private val rows = Array(100) { LogRow().also { it.render() } }
    private val dataViewer = DataViewer()

    private lateinit var elem: HTMLElement
    private lateinit var scrollingElem: HTMLElement
    private lateinit var container: HTMLElement
    private lateinit var scrollLockButton: HTMLButtonElement
    private lateinit var viewerContainer: HTMLElement
    private var scrollLock = false
    private var ignoreNextScrollEvent = false

    fun render(): HTMLElement {
        style.mount()
        if (this::scrollingElem.isInitialized) return scrollingElem
        elem = document.create.div(elemClass) {
            div(controlsClass) {
                standardButton(clearButtonClass) {
                    text("Clear")
                }

                standardButton(scrollButtonClass) {
                    text("Scroll to bottom")
                }
            }

            div(scrollingContainerClass) {
                div(containerClass)
            }

            div(viewerClass)
        }

        scrollingElem = elem.querySelector(".$scrollingContainerClass") as HTMLElement

        viewerContainer = elem.querySelector(".$viewerClass") as HTMLElement
        viewerContainer.append(dataViewer.render())

        scrollLockButton = elem.querySelector(".$scrollButtonClass") as HTMLButtonElement
        scrollLockButton.addEventListener("click", {
            scrollLock = !scrollLock
            rerender()
        })

        (elem.querySelector(".$clearButtonClass") as HTMLButtonElement).addEventListener("click", {
            messages.clear()
            scrollingElem.scrollTop = 0.0
            scrollLock = false
            rerender()
        })

        container = scrollingElem.querySelector(".$containerClass") as HTMLElement
        for (row in rows) {
            container.appendChild(row.elem)
        }

        scrollingElem.addEventListener("scroll", {
            if (ignoreNextScrollEvent) {
                ignoreNextScrollEvent = false
                return@addEventListener
            }

            scrollLock = false
            rerender()
        })

        val ctx = DebugContext.Job("foo")
        var count = 0
        addMessages((0 until 1000).map { DebugMessage.Log(ctx, "Hello, World: ${count++}") })
        window.setInterval({
            addMessages((0 until 1).map { DebugMessage.Log(ctx, "Hello, World: ${count++}") })
        }, 50)

        return elem
    }

    private fun findFirstVisibleRow(): Int {
        return floor(scrollingElem.scrollTop / rowSize).toInt()
    }

    private fun rerender() {
        scrollLockButton.disabled = scrollLock
        container.style.height = (this.messages.size * rowSize).px

        val firstIdx = findFirstVisibleRow()
        val lastIdx = min(messages.size, firstIdx + rows.size) - 1
        val count = lastIdx - firstIdx + 1
        val cacheOffset = max(0, rows.size - count)

        if (messages.size < rows.size) {
            for ((index, row) in rows.withIndex()) {
                row.clear()
                row.elem.style.top = (index * rowSize).px
            }
        }

        for (idx in firstIdx..lastIdx) {
            val message = messages[idx]
            val cacheRow = rows[cacheOffset + idx - firstIdx]
            cacheRow.update(message, messages.getOrNull(idx - 1))
            cacheRow.elem.style.top = (idx * rowSize).px
            cacheRow.elem.onclick = {
                dataViewer.update(message)
            }
        }
    }

    fun addMessages(messages: List<DebugMessage>) {
        this.messages.addAll(messages)
        if (scrollLock) {
            ignoreNextScrollEvent = true
            scrollingElem.scrollTop = this.messages.size * rowSize.toDouble()
        }
        rerender()
    }

    companion object {
        private const val scrollingContainerClass = "scrolling"
        private const val containerClass = "container"
        private const val elemClass = "log-viewer"
        private const val viewerClass = "viewer"
        private const val controlsClass = "controls"
        private const val scrollButtonClass = "scroll-lock"
        private const val clearButtonClass = "clear"

        private const val rowSize = 32

        private val style = CssMounter {
            (byClass(elemClass)) {
                display = "flex"
                flexDirection = "row"
                gap = 8.px
            }

            (byClass(elemClass) directChild byClass(scrollingContainerClass)) {
                height = "calc(100vh - 48px - 16px - 250px - 16px - 250px - 16px)"
                overflowY = "scroll"
                backgroundColor = Rgb(245, 247, 249).toString()
                flexBasis = 500.px
                flexGrow = "1"
            }

            (byClass(elemClass) directChild byClass(scrollingContainerClass) directChild byClass(containerClass)) {
                position = "relative"
            }

            (byClass(elemClass) directChild byClass(viewerClass)) {
                width = 400.px
                height = 100.percent
            }

            (byClass(elemClass) directChild byClass(controlsClass)) {
                display = "flex"
                flexDirection = "column"
            }
        }
    }
}

class LogRow {
    lateinit var elem: HTMLElement
    private lateinit var timestamp: HTMLElement
    private lateinit var message: HTMLElement
    private lateinit var response: HTMLElement

    fun render(): HTMLElement {
        style.mount()
        if (this::elem.isInitialized) return elem

        elem = document.create.div(rowClass) {
            div(timestampClass)
            div(messageClass)
            div(responseClass)
        }

        timestamp = elem.querySelector(".$timestampClass") as HTMLElement
        message = elem.querySelector(".$messageClass") as HTMLElement
        response = elem.querySelector(".$responseClass") as HTMLElement

        return elem
    }

    fun clear() {
        elem.style.background = "white"
        elem.style.color = "black"
        timestamp.textContent = ""
        message.textContent = ""
        response.textContent = ""
    }

    @OptIn(ExperimentalTime::class)
    fun update(row: DebugMessage, previous: DebugMessage?) {
        elem.style.background = backgroundColor(row.importance)
        elem.style.color = foregroundColor(row.importance)

        timestamp.textContent =
            if (previous == null) "0s"
            else (row.timestamp - previous.timestamp).toDuration(DurationUnit.MILLISECONDS).toString()
        message.textContent = message(row)
        response.textContent = response(row)
    }

    private fun backgroundColor(importance: MessageImportance): String {
        return when (importance) {
            MessageImportance.TELL_ME_EVERYTHING -> "white"
            MessageImportance.IMPLEMENTATION_DETAIL -> "#B7ADED"
            MessageImportance.THIS_IS_NORMAL -> "#A8E3B3"
            MessageImportance.THIS_IS_ODD -> "#F6BF85"
            MessageImportance.THIS_IS_WRONG -> "#ED8C79"
            MessageImportance.THIS_IS_DANGEROUS -> "#ED6572"
        }
    }

    private fun foregroundColor(importance: MessageImportance): String {
        return when (importance) {
            MessageImportance.TELL_ME_EVERYTHING -> "black"
            MessageImportance.IMPLEMENTATION_DETAIL -> "black"
            MessageImportance.THIS_IS_NORMAL -> "black"
            MessageImportance.THIS_IS_ODD -> "black"
            MessageImportance.THIS_IS_WRONG -> "white"
            MessageImportance.THIS_IS_DANGEROUS -> "white"
        }
    }

    private fun message(row: DebugMessage): String {
        return when (row) {
            is DebugMessage.ClientRequest -> row.call ?: "Client: Unknown call"
            is DebugMessage.ClientResponse -> row.call ?: "Client: Unknown call"
            is DebugMessage.DatabaseConnection -> {
                if (row.isOpen) "DB open"
                else "DB close"
            }
            is DebugMessage.DatabaseQuery -> "DB query"
            is DebugMessage.DatabaseResponse -> "DB response"
            is DebugMessage.DatabaseTransaction -> {
                when (row.event) {
                    DebugMessage.DBTransactionEvent.OPEN -> "DB transaction open"
                    DebugMessage.DBTransactionEvent.COMMIT -> "DB commit"
                    DebugMessage.DBTransactionEvent.ROLLBACK -> "DB rollback"
                }
            }
            is DebugMessage.Log -> row.message
            is DebugMessage.ServerRequest -> row.call ?: "Server: Unknown call"
            is DebugMessage.ServerResponse -> row.call ?: "Server: Unknown call"
        }
    }

    private fun response(row: DebugMessage): String {
        return when (row) {
            is DebugMessage.ClientRequest -> ""
            is DebugMessage.ClientResponse -> row.responseCode.toString()
            is DebugMessage.DatabaseConnection -> ""
            is DebugMessage.DatabaseQuery -> ""
            is DebugMessage.DatabaseResponse -> ""
            is DebugMessage.DatabaseTransaction -> ""
            is DebugMessage.Log -> ""
            is DebugMessage.ServerRequest -> ""
            is DebugMessage.ServerResponse -> row.responseCode.toString()
        }
    }

    companion object {
        private const val rowClass = "log-row"
        private const val messageClass = "message"
        private const val timestampClass = "timestamp"
        private const val responseClass = "response"
        private const val rowHeight = 32

        private val style = CssMounter {
            (byClass(rowClass)) {
                fontFamily = "'JetBrains Mono', monospace"
                cursor = "pointer"
                height = rowHeight.px
                width = 100.percent
                overflow = "hidden"
                position = "absolute"

                display = "flex"
                gap = 16.px
            }

            (byClass(rowClass) directChild byClass(messageClass)) {
                flexGrow = "3"
            }

            (byClass(rowClass) directChild byClass(timestampClass)) {
                flexGrow = "0"
                flexBasis = 80.px
            }

            (byClass(rowClass) directChild byClass(responseClass)) {
                flexGrow = "1"
            }
        }
    }
}
