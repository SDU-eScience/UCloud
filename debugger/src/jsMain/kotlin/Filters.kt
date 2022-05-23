import kotlinx.browser.document
import kotlinx.html.div
import kotlinx.html.dom.create
import org.w3c.dom.HTMLElement

class Filters {
    private lateinit var elem: HTMLElement
    fun render(): HTMLElement {
        style.mount()
        if (this::elem.isInitialized) return elem
        elem = document.create.div(elemClass)
        return elem
    }

    companion object {
        private const val elemClass = "filters"

        val style = CssMounter {
            (byClass(elemClass)) {
                height = 250.px
            }
        }
    }
}
