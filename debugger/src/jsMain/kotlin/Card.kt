import kotlinx.browser.document
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLHeadingElement

class Card {
    private lateinit var root: HTMLElement
    private lateinit var content: HTMLElement
    private lateinit var title: HTMLHeadingElement

    fun render(): HTMLElement {
        style.mount()
        root = document.create.div(className) {
            div {
                inlineStyle {
                    borderTop = "5px solid #70b"
                }
            }

            div(innerClass) {
                h3 { }
                div(contentClass)
            }
        }

        content = (root.querySelector(".$contentClass") as HTMLElement?)!!
        title = (root.querySelector("h3") as HTMLHeadingElement?)!!

        return root
    }

    fun title(title: String) {
        this.title.textContent = title
    }

    fun content(element: HTMLElement) {
        content.innerHTML = ""
        content.appendChild(element)
    }

    companion object {
        private const val className = "card"
        private const val contentClass = "content"
        private const val innerClass = "inner"

        private val style = CssMounter {
            head.append(document.create.css {
                (byClass(className)) {
                    height = "auto"
                    boxShadow = "rgb(0 0 0 / 20%) 0px 3px 3px -2px, rgb(0 0 0 / 14%) 0px 3px 4px 0px, rgb(0 0 0 / 12%) 0px 1px 8px 0px"
                    border = "0px solid rgb(201, 211, 223)"
                    borderRadius = 6.px
                    width = 100.percent
                    overflow = "hidden"
                }

                (byClass(className) directChild byClass(innerClass)) {
                    padding = "4px 16px"
                    height = "calc(100% - 5px)"
                }
            })
        }
    }
}
