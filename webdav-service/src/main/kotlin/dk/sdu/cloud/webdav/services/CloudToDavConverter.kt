package dk.sdu.cloud.webdav.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.createdAt
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.modifiedAt
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.api.sensitivityLevel
import dk.sdu.cloud.file.api.size
import dk.sdu.cloud.webdav.WebDavProperty
import org.w3c.dom.Element
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class CloudToDavConverter() {
    fun writeFileProps(file: StorageFile, root: Element) = with(root) {
        if (
            file.sensitivityLevel == SensitivityLevel.SENSITIVE ||
            file.sensitivityLevel == SensitivityLevel.CONFIDENTIAL
        ) {
            return
        }

        appendNewElement("d:response") {
            appendNewElement("d:href") {
                textContent = "/" + file.path.split("/").filter { it.isNotEmpty() }.drop(2).joinToString("/")
            }

            appendNewElement("d:propstat") {
                appendNewElement("d:prop") {
                    appendNewElement("d:resourcetype") {
                        if (file.fileType == FileType.DIRECTORY) {
                            appendNewElement("d:collection")
                        }
                    }

                    if (file.fileType != FileType.DIRECTORY) {
                        appendNewElement("d:${WebDavProperty.ContentLength.title}") {
                            textContent = file.size.toString()
                        }
                    }

                    appendNewElement("d:${WebDavProperty.CreationDate.title}") {
                        textContent =
                            formatter.format(ZonedDateTime.ofInstant(Date(file.createdAt).toInstant(), zoneId))
                    }

                    appendNewElement("d:${WebDavProperty.LastModified.title}") {
                        textContent =
                            formatter.format(ZonedDateTime.ofInstant(Date(file.modifiedAt).toInstant(), zoneId))
                    }

                    appendNewElement("d:${WebDavProperty.DisplayName.title}") {
                        textContent = file.path.fileName()
                    }
                }

                appendNewElement("d:status") {
                    textContent = "HTTP/1.1 200 OK"
                }
            }
        }
    }

    private val zoneId = ZoneId.of("GMT")
    private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")
}
