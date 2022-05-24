package dk.sdu.cloud.debug

import org.w3c.dom.HTMLElement
import kotlin.js.Promise

external interface IBigJsonViewerDom {
    fun fromData(data: String): Promise<BigJsonViewer>
}

external interface BigJsonViewer {
    fun getRootElement(): HTMLElement
}

external val BigJsonViewerDom: IBigJsonViewerDom
