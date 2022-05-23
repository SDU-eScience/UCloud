import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement

class StatusCards {
    private lateinit var elem: HTMLElement
    private val card = Card()
    private var count = 0

    fun render(): HTMLElement {
        style.mount()

        window.setInterval({
            card.title("Foobar: ${count++}")
        }, 1000)

        elem = document.create.div(elemClass)
        elem.appendChild(card.render())

        BigJsonViewerDom.fromData("""
            {
                "test": 123,
                "someArray": [1, 2, 3, 4, 5]
            }
        """.trimIndent()).then {
            elem.appendChild(it.getRootElement())
        }
        return elem
    }

    companion object {
        private const val elemClass = "status-cards"

        private val style = CssMounter {
            (byClass(elemClass)) {
                height = 250.px
            }
        }
    }
}
