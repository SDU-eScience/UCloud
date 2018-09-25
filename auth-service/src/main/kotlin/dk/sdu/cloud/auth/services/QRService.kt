package dk.sdu.cloud.auth.services

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

/**
 * A service for generating QR-codes
 */
interface QRService {
    fun encode(message: String, width: Int, height: Int): BufferedImage
}

fun BufferedImage.toDataURI(format: String = "PNG", mediaType: String = "image/png"): String {
    return StringBuilder().apply {
        append("data:")
        append(mediaType)
        append(";base64,")
        val array = ByteArrayOutputStream().use { outStream ->
            ImageIO.write(this@toDataURI, format, outStream)
            outStream.toByteArray()
        }

        append(Base64.getEncoder().encodeToString(array))
    }.toString()
}

class ZXingQRService : QRService {
    private val instance = QRCodeWriter()

    override fun encode(message: String, width: Int, height: Int): BufferedImage {
        val encoded = instance.encode(message, BarcodeFormat.QR_CODE, width, height)
        return MatrixToImageWriter.toBufferedImage(encoded)
    }
}