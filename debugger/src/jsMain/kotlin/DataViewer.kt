package dk.sdu.cloud.debug

import dk.sdu.cluod.debug.defaultMapper
import kotlinx.browser.document
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.serialization.encodeToString
import org.w3c.dom.HTMLElement

class DataViewer {
    lateinit var elem: HTMLElement

    fun render(): HTMLElement {
        style.mount()
        if (this::elem.isInitialized) return elem
        elem = document.create.div(elemClass) {

        }
        return elem
    }

    fun update(row: DebugMessage) {
        BigJsonViewerDom.fromData(defaultMapper.encodeToString(row)).then {
            elem.innerHTML = ""
            val node = it.getRootElement()
            elem.appendChild(node)
            node.asDynamic().openAll(2)
        }
    }

    companion object {
        private const val elemClass = "data-viewer"
        val style = CssMounter {
            (byClass(elemClass)) {

            }
        }
    }
}
