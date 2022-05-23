import dk.sdu.cloud.debug.ServiceMetadata
import kotlinx.browser.document
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.js.style

val body inline get() = document.body!!
val head inline get() = document.head!!

fun main() {
    cssReset()
    head.append(
        document.create.css {
            importGoogleFont("Inter", listOf(100, 400, 700))
            importGoogleFont("JetBrains Mono", listOf(100, 400, 700))

            (byTag("html")) {
                fontFamily = "'Inter', sans-serif"
            }

            (byTag("h3")) {
                cursor = "inherit"
                fontSize = 24.px
                margin = 0.px
                fontWeight = "400"
            }
        }
    )

    val header = Header()
    body.appendChild(header.render())

    val sidebar = Sidebar().also { it.render() }
    val serviceSelector = ServiceSelector().also { it.render() }
    sidebar.elem.appendChild(serviceSelector.elem)
    run {
        serviceSelector.addService(ServiceMetadata("ucloud-core-aa", "UCloud/Core"))
        serviceSelector.addService(ServiceMetadata("ucloud-core-bb", "UCloud/Core"))
        serviceSelector.addService(ServiceMetadata("im-server-aa", "IM/Server"))
        serviceSelector.addService(ServiceMetadata("im-user-1000-aa", "IM/User/1000"))
        serviceSelector.addService(ServiceMetadata("im-user-1001-aa", "IM/User/1001"))
    }
    body.appendChild(sidebar.elem)

    val content = document.create.div {
        inlineStyle {
            position = "fixed"
            top = 48.px
            left = 190.px

            height = "calc(100vh - 48px)"
            width = "calc(100vw - 190px)"

            overflow = "hidden"

            paddingTop = 16.px
            paddingLeft = 16.px
            paddingRight = 16.px

            display = "flex"
            gap = 16.px
            flexDirection = "column"
        }
    }

    val statusCards = StatusCards()
    content.appendChild(statusCards.render())

    val filters = Filters()
    content.appendChild(filters.render())

    val log = Log()
    content.appendChild(log.render())

    body.appendChild(content)
}
