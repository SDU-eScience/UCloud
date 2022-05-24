package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement

class StatusCards {
    private lateinit var elem: HTMLElement
    private val card = Card().also { it.render() }
    private val card2 = Card().also { it.render() }
    private val card3 = Card().also { it.render() }
    private var count = 0

    fun render(): HTMLElement {
        style.mount()

        window.setInterval({
            card.title("Foobar: ${count++}")
        }, 1000)

        card.title("Card 1")
        card2.title("Card 2")
        card3.title("Card 3")

        elem = document.create.div(elemClass)
        elem.appendChild(card.render())
        elem.appendChild(card2.render())
        elem.appendChild(card3.render())
        return elem
    }

    companion object {
        private const val elemClass = "status-cards"

        private val style = CssMounter {
            (byClass(elemClass)) {
                height = 250.px
                display = "flex"
                flexDirection = "row"
                gap = 16.px
            }
        }
    }
}
