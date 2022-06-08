package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.html.classes
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.max

class ServiceSelector(private val client: Client) {
    lateinit var elem: HTMLElement

    sealed class Node {
        abstract val title: String
        abstract val path: String

        data class Internal(
            override val title: String,
            override val path: String,
            val children: ArrayList<Node> = ArrayList(),
        ) : Node()
        data class Leaf(
            val service: ServiceMetadata,
            override val path: String,
        ) : Node() {
            override val title: String = service.path.substringAfterLast('/')
        }
    }

    private val root = Node.Internal("", "", ArrayList())
    private var activeNode: Node? = null

    fun render(): HTMLElement {
        style.mount()
        if (this::elem.isInitialized) return elem
        elem = document.create.div(elemClass)

        document.addEventListener("keydown", { event ->
            event as KeyboardEvent

            when (event.code) {
                "KeyU" -> moveSelectionTo(1)
                "KeyI" -> moveSelectionTo(-1)
            }
        })
        return elem
    }

    private fun rerender() {
        elem.innerHTML = ""
        renderService(root, 0)
    }

    private fun moveSelectionTo(offset: Int) {
        val active = activeNode
        val currentIndex = if (active == null) 0 else findIndexOf(active)
        val newNode = findNodeByIndex(max(1, currentIndex + offset))
        if (newNode != null) activeNode = newNode
        rerender()
        if (newNode is Node.Leaf) {
            client.send(ClientToServer.OpenService(newNode.service.id))
        }
    }

    private fun findNodeByIndex(index: Int, currentNode: Node = root, count: Ref<Int> = Ref(0)): Node? {
        if (index == count.current) return currentNode
        count.current += 1
        if (currentNode is Node.Internal) {
            for (child in currentNode.children) {
                val res = findNodeByIndex(index, child, count)
                if (res != null) return res
            }
        }
        return null
    }

    private fun findIndexOf(target: Node, currentNode: Node = root, count: Ref<Int> = Ref(0)): Int {
        if (target == currentNode) return count.current
        count.current += 1
        if (currentNode is Node.Internal) {
            for (child in currentNode.children) {
                val res = findIndexOf(target, child, count)
                if (res != -1) return count.current
            }
        }
        return -1
    }

    private fun renderService(node: Node, depth: Int) {
        if (depth != 0) {
            val rowElement = document.create.div {
                classes = setOf(nodeClass)
                if (activeNode == node) classes += activeClass
                inlineStyle {
                    marginLeft = (depth * 8).px
                    if (node is Node.Leaf) {
                        cursor = "pointer"
                    } else {
                        fontWeight = "700"
                    }
                }

                text(node.title)
            }

            if (node is Node.Leaf) {
                rowElement.addEventListener("click", {
                    client.send(ClientToServer.OpenService(node.service.id))
                    activeNode = node
                    rerender()
                })
            }

            elem.append(rowElement)
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
            val path = components.subList(0, i + 1).joinToString("/")
            val currentNode = node
            val isLeaf = i == components.lastIndex
            val childIdx = node.children.indexOfFirst { child -> child.title == components[i] }
            if ((childIdx == -1 || isLeaf) || (!isLeaf && childIdx != -1 && node.children[childIdx] !is Node.Internal)) {
                if (isLeaf) {
                    if (childIdx != -1) node.children[childIdx] = Node.Leaf(service, path)
                    else node.children.add(Node.Leaf(service, path))
                } else {
                    val newNode = Node.Internal(components[i], path)
                    node.children.add(newNode)
                    node = newNode
                }

                currentNode.children.sortBy { it.title }
            } else {
                node = node.children[childIdx] as Node.Internal
            }
        }

        rerender()
    }

    companion object {
        const val elemClass = "service-selector"
        const val activeClass = "active"
        const val nodeClass = "node"

        val style = CssMounter {
            val self = byClass(elemClass)
            val node = self descendant byClass(nodeClass)
            val activeNode = self descendant (byClass(nodeClass) and byClass(activeClass))

            self {
                display = "flex"
                flexDirection = "column"
                gap = 8.px
            }

            node {
                borderBottom = "3px solid transparent"
                width = "fit-content"
            }

            activeNode {
                borderBottom = "3px solid #006aff"
            }
        }
    }
}
