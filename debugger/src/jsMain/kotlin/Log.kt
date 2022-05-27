package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Log(private val filters: Filters) {
    private val messages = FilteredList<DebugMessage>(1024 * 32)
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

        messages.filterFunction = f@{ message ->
            val allowedByType = when (message) {
                is DebugMessage.ClientRequest -> filters.showClient
                is DebugMessage.ClientResponse -> filters.showClient
                is DebugMessage.DatabaseConnection -> filters.showDatabase
                is DebugMessage.DatabaseQuery -> filters.showDatabase
                is DebugMessage.DatabaseResponse -> filters.showDatabase
                is DebugMessage.DatabaseTransaction -> filters.showDatabase
                is DebugMessage.Log -> true
                is DebugMessage.ServerRequest -> filters.showServer
                is DebugMessage.ServerResponse -> filters.showServer
            }
            if (!allowedByType) return@f false

            val allowedByLevel = message.importance.ordinal >= filters.logLevel.ordinal
            if (!allowedByLevel) return@f false

            val allowedByQuery = when (message) {
                is DebugMessage.ClientRequest -> (message.call ?: "").contains(filters.filter, true)
                is DebugMessage.ClientResponse -> (message.call ?: "").contains(filters.filter, true)
                is DebugMessage.DatabaseConnection -> false
                is DebugMessage.DatabaseQuery -> false
                is DebugMessage.DatabaseResponse -> false
                is DebugMessage.DatabaseTransaction -> false
                is DebugMessage.Log -> message.message.contains(filters.filter, true)
                is DebugMessage.ServerRequest -> (message.call ?: "").contains(filters.filter, true)
                is DebugMessage.ServerResponse -> (message.call ?: "").contains(filters.filter, true)
            }
            if (!allowedByQuery) return@f false

            val allowedByStack = when (filters.id) {
                "" -> message.context.depth == 1
                else -> {
                    message.context.path.contains(filters.id)
                }
            }
            if (!allowedByStack) return@f false

            return@f true
        }

        filters.onChange = {
            messages.reapplyFilter()
            rerender()
        }

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
            clear()
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

        return elem
    }

    private fun findFirstVisibleRow(): Int {
        return max(0, floor(scrollingElem.scrollTop / rowSize).toInt() - 10)
    }

    private fun rerender() {
        scrollLockButton.disabled = scrollLock
        container.style.height = max(rows.size * rowSize, this.messages.filtered.size * rowSize).px

        val firstIdx = findFirstVisibleRow()
        val lastIdx = min(messages.filtered.size, firstIdx + rows.size) - 1
        val count = lastIdx - firstIdx + 1
        val cacheOffset = max(0, rows.size - count)

        if (messages.filtered.size < rows.size) {
            for ((index, row) in rows.withIndex()) {
                row.clear()
                row.elem.style.top = (index * rowSize).px
            }
        }

        for (idx in firstIdx..lastIdx) {
            val message = messages.filtered[idx]
            val cacheRow = rows[cacheOffset + idx - firstIdx]
            cacheRow.update(message, messages.filtered.getOrNull(idx - 1))
            cacheRow.elem.style.top = (idx * rowSize).px
            cacheRow.elem.style.paddingLeft = ((message.context.depth - filters.currentStack.depth) * 8).px
            cacheRow.elem.onclick = {
                dataViewer.update(message)
            }

            cacheRow.elem.ondblclick = {
                filters.addStack(message.context.id, LogRow.message(message)) {
                    logLevel = MessageImportance.IMPLEMENTATION_DETAIL
                    showServer = true
                    showClient = true
                    showDatabase = true
                    depth = message.context.depth
                }
            }
        }
    }

    fun clear() {
        messages.clear()
        scrollingElem.scrollTop = 0.0
        scrollLock = false
        rerender()
    }

    fun addMessages(messages: List<DebugMessage>) {
        this.messages.addAll(messages)
        if (scrollLock) {
            ignoreNextScrollEvent = true
            scrollingElem.scrollTop = this.messages.filtered.size * rowSize.toDouble()
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
                userSelect = "none"
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

    fun update(row: DebugMessage, previous: DebugMessage?) {
        elem.style.background = backgroundColor(row.importance)
        elem.style.color = foregroundColor(row.importance)

        timestamp.textContent =
            if (previous == null) "0s"
            else (row.timestamp - previous.timestamp).toDuration(DurationUnit.MILLISECONDS).toString()
        message.textContent = message(row)
        response.textContent = response(row)
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

        fun message(row: DebugMessage): String {
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
    }
}
