import dk.sdu.cloud.debug.ServiceMetadata
import kotlinx.browser.document
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement

class ServiceSelector {
    lateinit var elem: HTMLElement

    sealed class Node {
        abstract val title: String

        data class Internal(override val title: String, val children: ArrayList<Node> = ArrayList()) : Node()
        data class Leaf(val service: ServiceMetadata) : Node() {
            override val title: String = service.path.substringAfterLast('/')
        }
    }

    private val root = Node.Internal("", ArrayList())

    fun render(): HTMLElement {
        style.mount()
        if (this::elem.isInitialized) return elem
        elem = document.create.div(elemClass)
        return elem
    }

    private fun rerender() {
        elem.innerHTML = ""
        renderService(root, 0)
    }

    private fun renderService(node: Node, depth: Int) {
        println("Rendering $node")
        if (depth != 0) {
            elem.append(document.create.div {
                inlineStyle {
                    marginLeft = (depth * 8).px
                    if (node is Node.Leaf) {
                        cursor = "pointer"
                    } else {
                        fontWeight = "700"
                    }
                }

                text(node.title)
            })
        }

        if (node is Node.Internal) {
            for (child in node.children) {
                renderService(child, depth + 1)
            }
        }
    }

    fun addService(service: ServiceMetadata) {
        val components = service.path.split("/")
        var node = root
        for (i in components.indices) {
            val isLeaf = i == components.lastIndex
            val childIdx = node.children.indexOfFirst { child -> child.title == components[i] }
            if ((childIdx == -1 || isLeaf) || (!isLeaf && childIdx != -1 && node.children[childIdx] !is Node.Internal)) {
                if (isLeaf) {
                    if (childIdx != -1) node.children[childIdx] = Node.Leaf(service)
                    else node.children.add(Node.Leaf(service))
                } else {
                    val newNode = Node.Internal(components[i])
                    node.children.add(newNode)
                    node = newNode
                }

                node.children.sortBy { it.title }
            } else {
                node = node.children[childIdx] as Node.Internal
            }
        }

        rerender()
    }

    companion object {
        const val elemClass = "service-selector"

        val style = CssMounter {
            (byClass(elemClass)) {
                display = "flex"
                flexDirection = "column"
                gap = 8.px

            }
        }
    }
}
