package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.img
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement

class Header {
    fun render(): HTMLElement {
        return document.create.div {
            inlineStyle {
                backgroundColor = Rgb(0, 106, 255).toString()
                color = "white"

                height = 48.px
                width = 100.vw

                display = "flex"
                alignItems = "center"

                position = "fixed"
                top = 0.px
                zIndex = "100"

                boxShadow =
                    "rgb(0 0 0 / 20%) 0px 3px 3px -2px, rgb(0 0 0 / 14%) 0px 3px 4px 0px, rgb(0 0 0 / 12%) 0px 1px 8px 0px"
            }

            img(alt = "UCloud Logo", src = "/static/logo_esc.svg") {
                inlineStyle {
                    height = 38.px
                    marginLeft = 15.px
                }
            }

            div {
                inlineStyle {
                    marginLeft = 8.px
                    fontSize = 24.px
                }

                text("UCloud")
            }
        }
    }
}
