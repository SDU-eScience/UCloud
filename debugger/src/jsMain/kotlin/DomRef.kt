package dk.sdu.cloud.debug

import kotlinx.html.CommonAttributeGroupFacade
import org.w3c.dom.Element
import org.w3c.dom.ParentNode

class DomRef<E : Element> {
    var id: Int? = null
    private var cached: E? = null

    fun findOrNull(root: ParentNode): E? {
        if (id == null) return null
        if (cached != null) return cached
        return (root.querySelector("[data-selector-id=\"id-$id\"]") as? E).also { cached = it }
    }

    fun find(root: ParentNode): E {
        return findOrNull(root) ?: error("Could not find element: $id")
    }

    companion object {
        var domRefIdGenerator = 0
    }
}

fun <E : Element> CommonAttributeGroupFacade.capture(ref: DomRef<E>) {
    val generatedId = DomRef.domRefIdGenerator++
    attributes["data-selector-id"] = "id-$generatedId"
    ref.id = generatedId
}