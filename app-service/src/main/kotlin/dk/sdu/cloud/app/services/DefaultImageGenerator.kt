package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.Application
import io.ktor.http.ContentType
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.absoluteValue

class DefaultImageGenerator {
    private val colors = listOf(
        Color(0xE57373),
        Color(0xEF5350),
        Color(0xF44336),
        Color(0xE53935),
        Color(0xD32F2F),
        Color(0xC62828),
        Color(0xB71C1C),
        Color(0xEC407A),
        Color(0xE91E63),
        Color(0xD81B60),
        Color(0xC2185B),
        Color(0xAD1457),
        Color(0x880E4F),
        Color(0xFF80AB),
        Color(0xFF4081),
        Color(0xF50057),
        Color(0xC51162),
        Color(0xBA68C8),
        Color(0xAB47BC),
        Color(0x9C27B0),
        Color(0x8E24AA),
        Color(0x7B1FA2),
        Color(0x6A1B9A),
        Color(0x4A148C),
        Color(0xEA80FC),
        Color(0xE040FB),
        Color(0xD500F9),
        Color(0xAA00FF),
        Color(0x9575CD),
        Color(0x7E57C2),
        Color(0x673AB7),
        Color(0x5E35B1),
        Color(0x512DA8),
        Color(0x4527A0),
        Color(0x311B92),
        Color(0x7986CB),
        Color(0x5C6BC0),
        Color(0x3F51B5),
        Color(0x3949AB),
        Color(0x303F9F),
        Color(0x283593),
        Color(0x1A237E),
        Color(0x64B5F6),
        Color(0x42A5F5),
        Color(0x2196F3),
        Color(0x1E88E5),
        Color(0x1976D2),
        Color(0x1565C0),
        Color(0x0D47A1),
        Color(0x4FC3F7),
        Color(0x29B6F6),
        Color(0x03A9F4),
        Color(0x039BE5),
        Color(0x0288D1),
        Color(0x0277BD),
        Color(0x01579B),
        Color(0x4DD0E1),
        Color(0x26C6DA),
        Color(0x00BCD4),
        Color(0x00ACC1),
        Color(0x0097A7),
        Color(0x00838F),
        Color(0x006064),
        Color(0x4DB6AC),
        Color(0x26A69A),
        Color(0x009688),
        Color(0x00897B),
        Color(0x00796B),
        Color(0x00695C),
        Color(0x004D40),
        Color(0x81C784),
        Color(0x66BB6A),
        Color(0x4CAF50),
        Color(0x43A047),
        Color(0x388E3C),
        Color(0x2E7D32),
        Color(0x1B5E20),
        Color(0xAED581),
        Color(0x9CCC65),
        Color(0x8BC34A),
        Color(0x7CB342),
        Color(0x689F38),
        Color(0x558B2F),
        Color(0x33691E),
        Color(0xDCE775),
        Color(0xD4E157),
        Color(0xCDDC39),
        Color(0xC0CA33),
        Color(0xAFB42B),
        Color(0x9E9D24),
        Color(0x827717),
        Color(0xFFF176),
        Color(0xFFEE58),
        Color(0xFFEB3B),
        Color(0xFDD835),
        Color(0xFBC02D),
        Color(0xF9A825),
        Color(0xF57F17),
        Color(0xFFD54F),
        Color(0xFFCA28),
        Color(0xFFC107),
        Color(0xFFB300),
        Color(0xFFA000),
        Color(0xFF8F00),
        Color(0xFF6F00),
        Color(0xFFB74D),
        Color(0xFFA726),
        Color(0xFF9800),
        Color(0xFB8C00),
        Color(0xF57C00),
        Color(0xEF6C00),
        Color(0xE65100),
        Color(0xFF8A65),
        Color(0xFF7043),
        Color(0xFF5722),
        Color(0xF4511E),
        Color(0xE64A19),
        Color(0xD84315),
        Color(0xBF360C),
        Color(0xA1887F),
        Color(0x8D6E63),
        Color(0x795548),
        Color(0x6D4C41),
        Color(0x5D4037),
        Color(0x4E342E),
        Color(0x3E2723),
        Color(0xE0E0E0),
        Color(0xBDBDBD),
        Color(0x9E9E9E),
        Color(0x757575),
        Color(0x616161),
        Color(0x424242),
        Color(0x212121),
        Color(0x90A4AE),
        Color(0x78909C),
        Color(0x607D8B),
        Color(0x546E7A),
        Color(0x455A64),
        Color(0x37474F)
    )

    val images: List<Pair<ContentType, ByteArray>> by lazy {
        val baseImage = ImageIO.read(javaClass.classLoader.getResourceAsStream("assets/circuit.png"))
        val width = baseImage.width
        val height = baseImage.height

        colors
            .map { c ->
                val result = baseImage.copy()
                with(result.createGraphics()) {
                    color = Color(c.red, c.green, c.blue, 150)
                    fillRect(0, 0, width, height)
                    dispose()
                }

                result
            }
            .map {
                val byteStream = ByteArrayOutputStream()
                ImageIO.write(it, "png", byteStream)

                ContentType.Image.PNG to byteStream.toByteArray()
            }
    }

    private fun Pair<ContentType, ByteArray>.toDataURI(): String = StringBuilder().apply {
        append("data:")
        append(first.toString())
        append(";base64,")
        append(Base64.getEncoder().encodeToString(second))
    }.toString()

    fun processApplication(application: Application): Application =
        if (application.imageUrl == "") {
            application.copy(
                imageUrl = images[application.description.info.name.hashCode().absoluteValue % images.size].toDataURI()
            )
        } else {
            application
        }
}

fun main(args: Array<String>) {
    File("/tmp/file.png").writeBytes(DefaultImageGenerator().images.first().second)
}

private fun BufferedImage.copy(): BufferedImage {
    val b = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = b.graphics
    g.drawImage(this, 0, 0, null)
    g.dispose()
    return b
}
