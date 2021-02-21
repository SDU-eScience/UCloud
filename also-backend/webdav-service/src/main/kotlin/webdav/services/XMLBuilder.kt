package dk.sdu.cloud.webdav.services

import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private val docBuilderFactory = DocumentBuilderFactory.newInstance()
private val transformer = TransformerFactory.newInstance().newTransformer()

fun newDocument(rootTag: String, handler: Element.() -> Unit): Document {
    return docBuilderFactory.newDocumentBuilder().newDocument().appendNewElement(rootTag) {
        setAttribute("xmlns:d", "DAV:")
        setAttribute("xmlns:z", "urn:schemas-microsoft-com:")
        handler()
    }
}

fun Document.convertDocumentToString(): String {
    val writer = StringWriter()
    transformer.transform(DOMSource(this), StreamResult(writer))
    return writer.toString().also {
        log.debug(it)
    }
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.webdav.services.XMLBuilderKt")

fun Document.appendNewElement(tag: String, handler: Element.() -> Unit = {}): Document {
    appendChild(createElement(tag).apply(handler))
    return this
}

fun Element.appendNewElement(tag: String, handler: Element.() -> Unit = {}) {
    appendChild(ownerDocument.createElement(tag).apply(handler))
}
