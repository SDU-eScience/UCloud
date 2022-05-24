package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.create
import org.w3c.dom.HTMLElement

class Filters {
    private lateinit var elem: HTMLElement
    fun render(): HTMLElement {
        style.mount()
        if (this::elem.isInitialized) return elem
        elem = document.create.div(elemClass) {
            standardInput {}

            div {
                select {
                    option(content = "Everything")
                    option(content = "Details")
                    option(content = "Normal")
                    option(content = "Odd")
                    option(content = "Wrong")
                    option(content = "Dangerous")
                }
            }

            div {
                label {
                    checkBoxInput {}
                    text("Client")
                }
                label {
                    checkBoxInput {}
                    text("Server")
                }

                label {
                    checkBoxInput {}
                    text("Database")
                }
            }
        }
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
