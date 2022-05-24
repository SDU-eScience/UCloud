package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement

class Sidebar {
    lateinit var elem: HTMLElement
    fun render(): HTMLElement {
        if (this::elem.isInitialized) return elem
        elem = document.create.div {
            inlineStyle {
                position = "fixed"
                top = 48.px

                width = 190.px
                height = "calc(100vh - 48px)"

                fontSize = 18.px
                paddingTop = 16.px
                paddingBottom = 16.px

                backgroundColor = Rgb(245, 247, 249).toString()
            }
        }
        return elem
    }
}
