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
    /**
     * Encodes a [message] of dimensions [width]px times [height]px
     *
     * The returned [BufferedImage] can be converted into an image using functions in [ImageIO] or using
     * the utility function [BufferedImage.toDataURI] for data URI conversion (suitable for web-clients).
     */
    fun encode(message: String, width: Int, height: Int): BufferedImage
}

/**
 * Utility function for converting a [BufferedImage] into a data URI.
 *
 * @param format The image format (to be consumed by [ImageIO]. See [ImageIO.getWriterFormatNames]
 * @param mediaType The media type. It should match the image format.
 */
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

/**
 * Implements the [QRService] using the ZXing library
 */
class ZXingQRService : QRService {
    private val instance = QRCodeWriter()

    override fun encode(message: String, width: Int, height: Int): BufferedImage {
        val encoded = instance.encode(message, BarcodeFormat.QR_CODE, width, height)
        return MatrixToImageWriter.toBufferedImage(encoded)
    }
}
