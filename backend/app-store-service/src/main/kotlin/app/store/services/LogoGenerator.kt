package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.accounting.util.AsyncCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.io.use
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// The following colors used in the replace slot are reserved for custom filters.
// They are all some weird version of magenta which will never be used anyway.
const val InvertColor = 0xff40ff
const val GrayscaleInvertColor = 0xff41ff
const val DeleteColor = 0xff42ff
const val DarkenColor5 = 0xff43ff
const val DarkenColor10 = 0xff44ff
const val DarkenColor15 = 0xff45ff

const val DarkBackground = 0x282c2f
const val LightBackground = 0xf8f8f9

object LogoGenerator {
    private val mutex = Mutex()
    private val cache = AtomicReference<Map<String, ByteArray>>(emptyMap())

    suspend fun generateLogoWithText(
        cacheKey: String,
        title: String,
        input: ByteArray,
        placeTextUnderLogo: Boolean,
        backgroundColor: Int,
        colorReplacements: Map<Int, Int>,
    ): ByteArray {
        val currentCache = cache.get()
        val cached = currentCache[cacheKey]
        if (cached != null) return cached

        mutex.withLock {
            val inputImage = ImageIO.read(ByteArrayInputStream(input))

            val resizeHeight = 256
            val fontScalingFactor = inputImage.height / resizeHeight.toFloat()
            val resizeCanvas =
                BufferedImage(inputImage.width, inputImage.height, BufferedImage.TYPE_INT_ARGB)
            val gs = resizeCanvas.graphics
            gs.drawImage(inputImage, 0, 0, resizeCanvas.width, resizeCanvas.height, null)

            // keys are argb colors, values are the number of times we have seen the color
            val histogram = HashMap<Int, Int>()
            var firstX = Int.MAX_VALUE
            var firstY = Int.MAX_VALUE
            var lastX = Int.MIN_VALUE
            var lastY = Int.MIN_VALUE

            // rgba format
            val newImageData = IntArray(resizeCanvas.width * resizeCanvas.height * 4)

            val hasAlpha = resizeCanvas.alphaRaster != null
            val imageData = resizeCanvas.raster.dataBuffer
            for (i in 0 until imageData.size) {
                val color = imageData.getElem(i)
                var r = (color shr 16) and 0xFF
                var g = (color shr 8) and 0xFF
                var b = (color shr 0) and 0xFF
                var a = if (hasAlpha) (color shr 24) and 0xFF else 255

                val origRgb = (r shl 16) or (g shl 8) or b
                for ((find, replace) in colorReplacements) {
                    if (colorDistance(origRgb, find) < 80) {
                        when (replace) {
                            InvertColor -> {
                                val inverted = Rgb(r, g, b).invert()
                                r = inverted.r
                                g = inverted.g
                                b = inverted.b
                            }

                            GrayscaleInvertColor -> {
                                val grayscaleInverted = Rgb(r, g, b).grayscale().invert()
                                r = grayscaleInverted.r
                                g = grayscaleInverted.g
                                b = grayscaleInverted.b
                            }

                            DarkenColor5 -> {
                                val darken = Rgb(r, g, b).shade(0.05)
                                r = darken.r
                                g = darken.g
                                b = darken.b
                            }

                            DarkenColor10 -> {
                                val darken = Rgb(r, g, b).shade(0.1)
                                r = darken.r
                                g = darken.g
                                b = darken.b
                            }

                            DarkenColor15 -> {
                                val darken = Rgb(r, g, b).shade(0.15)
                                r = darken.r
                                g = darken.g
                                b = darken.b
                            }

                            DeleteColor -> {
                                a = 0
                            }

                            else -> {
                                val rR = (replace shr 16) and 0xFF
                                val rG = (replace shr 8) and 0xFF
                                val rB = replace and 0xFF

                                r = rR
                                g = rG
                                b = rB
                            }
                        }
                    }
                }

                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                newImageData[i * 4] = r
                newImageData[i * 4 + 1] = g
                newImageData[i * 4 + 2] = b
                newImageData[i * 4 + 3] = a
                if (a >= 200) histogram[argb] = (histogram[argb] ?: 0) + 1

                if (argb != 0xFFFFFFFF.toInt() && a > 150) {
                    val x = i % resizeCanvas.width
                    val y = i / resizeCanvas.width

                    if (x < firstX) firstX = x
                    if (y < firstY) firstY = y
                    if (x > lastX) lastX = x
                    if (y > lastY) lastY = y
                }
            }

            val modifiedImage = BufferedImage(resizeCanvas.width, resizeCanvas.height, BufferedImage.TYPE_INT_ARGB)
            (modifiedImage.graphics as Graphics2D).setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )
            modifiedImage.raster.setPixels(0, 0, resizeCanvas.width, resizeCanvas.height, newImageData)

            val croppedImage = BufferedImage(lastX - firstX, lastY - firstY, BufferedImage.TYPE_INT_ARGB)
            (croppedImage.graphics as Graphics2D).setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )

            croppedImage.graphics.drawImage(
                modifiedImage,
                0,
                0,
                croppedImage.width,
                croppedImage.height,
                firstX,
                firstY,
                lastX,
                lastY,
                null
            )

            val backgroundRgb = Rgb.fromInt(backgroundColor)
            var bestColor: Rgb = backgroundRgb.invert()
            var maxCount = -1
            for ((color, count) in histogram) {
                val rgb = Rgb.fromInt(color)
                if (rgb.contrast(backgroundRgb) >= 2.2 && count > maxCount) {
                    bestColor = rgb
                    maxCount = count
                }
            }

            // Snap grays to either white or black for better consistency across the entire store
            fun colorLooksGray(color: Rgb): Boolean {
                val (r, g, b) = color
                val maxComponent = listOf(r, g, b).max()
                val minComponent = listOf(r, g, b).min()
                return maxComponent - minComponent < 30
            }

            if (colorLooksGray(bestColor)) {
                if (backgroundColor == DarkBackground) {
                    bestColor = Rgb(255, 255, 255)
                } else if (backgroundColor == LightBackground) {
                    bestColor = Rgb(0, 0, 0)
                }
            }

            val imageWidth = (lastX - firstX) + 1
            val imageHeight = (lastY - firstY) + 1

            var imageWithText: BufferedImage? = null

            if (placeTextUnderLogo) {
                var size = 60f * fontScalingFactor
                val fontMetrics = gs.getFontMetrics(primaryFont.deriveFont(size))
                var textWidth = fontMetrics.stringWidth(title)
                if (title == "") {
                    textWidth = 0
                    size = 0f
                }

                imageWithText = BufferedImage(
                    max(textWidth, imageWidth),
                    (imageHeight + size + fontMetrics.descent).toInt(),
                    BufferedImage.TYPE_INT_ARGB
                )

                val g = imageWithText.graphics
                (g as Graphics2D).setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )

                val logoPadding = if (textWidth > imageWidth) (textWidth - imageWidth) / 2f else 0f
                val textPadding = if (imageWidth > textWidth) (imageWidth - textWidth) / 2f else 0f

                g.font = primaryFont.deriveFont(size)
                g.color = bestColor.toColor()

                g.drawImage(croppedImage, logoPadding.toInt(), 0, null)
                g.drawString(title, textPadding.toInt(), (imageHeight + size * 1.1f).toInt())
            } else {
                var paddingX = (30 * fontScalingFactor).toInt()
                val sizes = listOf(120f, 110f, 100f, 90f, 80f, 70f, 60f, 50f).map { it * fontScalingFactor }
                for (size in sizes) {
                    val fontMetrics = gs.getFontMetrics(primaryFont.deriveFont(size))
                    val titleWords = title.split(" ")
                    val currentLine = StringBuilder()
                    var maxWidth = 0
                    val linesOfText = ArrayList<String>()
                    if (titleWords.size == 2) {
                        linesOfText.addAll(titleWords)
                        for (word in titleWords) {
                            val width = fontMetrics.stringWidth(word)
                            if (width > maxWidth) maxWidth = width
                        }
                    } else {
                        for (word in titleWords) {
                            currentLine.append(word)
                            currentLine.append(" ")

                            val trim = currentLine.toString().trim()
                            val newWidth = fontMetrics.stringWidth(trim)
                            if (newWidth > maxWidth) maxWidth = newWidth
                            if (newWidth > 250) {
                                linesOfText.add(trim)
                                currentLine.clear()
                            }
                        }
                    }

                    if (currentLine.isNotEmpty()) linesOfText.add(currentLine.toString())

                    val realSize = fontMetrics.descent + fontMetrics.ascent + 1

                    var textHeight = (((linesOfText.size - 1) * realSize) + realSize).toInt()
                    var textWidth = maxWidth
                    if (title == "") {
                        textHeight = 0
                        textWidth = 0
                        paddingX = 0
                    }

                    if (linesOfText.size == 1 && textHeight > imageHeight * 0.55) continue
                    if (linesOfText.size > 1 && textHeight > imageHeight * 0.8) continue

                    imageWithText = BufferedImage(
                        imageWidth + paddingX + textWidth,
                        max(imageHeight, textHeight),
                        BufferedImage.TYPE_INT_ARGB
                    )

                    val g = imageWithText.graphics
                    (g as Graphics2D).setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                    )

                    g.drawImage(croppedImage, 0, 0, null)

                    g.font = primaryFont.deriveFont(size)
                    g.color = bestColor.toColor()

                    val paddingY =
                        if (textHeight > imageHeight) 0 else ((imageHeight / 2.0) - (textHeight / 2.0)).toInt()

                    var textY = size + paddingY
                    for (line in linesOfText) {
                        g.drawString(line, imageWidth + paddingX, textY.toInt())
                        textY += size * 1.1f
                    }

                    break // size loop
                }
            }

            val result = ByteArrayOutputStream().use { outs ->
                ImageIO.write(imageWithText ?: croppedImage, "PNG", outs)
                outs.toByteArray()
            }

            while (true) {
                val current = cache.get()
                val new = current + (cacheKey to result)
                if (cache.compareAndSet(current, new)) break
            }

            return result
        }
    }
}

private fun registerFont(path: String) {
    val data = LogoGenerator::class.java.classLoader.resources(path).toList().firstOrNull()?.readBytes()
        ?: error("Could not load $path")

    Font.createFont(
        Font.TRUETYPE_FONT,
        ByteArrayInputStream(data)
    ).also { font ->
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
    }
}

private val primaryFont by lazy {
    registerFont("fonts/inter/Inter-Regular.ttf")
    registerFont("fonts/inter/Inter-Bold.ttf")

    Font("Inter", Font.PLAIN, 16)
}


data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun toColor(): Color {
        return Color(r, g, b)
    }

    fun grayscale(): Rgb {
        val average = ((r + g + b) / 3.toDouble()).toInt()
        return Rgb(average, average, average)
    }

    fun invert(): Rgb {
        return Rgb(255 - r, 255 - g, 255 - b)
    }

    fun contrast(other: Rgb): Double {
        val lum1 = luminance()
        val lum2 = other.luminance()
        val brightest = max(lum1, lum2)
        val darkest = min(lum1, lum2)
        return (brightest + 0.05) / (darkest + 0.05)
    }

    fun luminance(): Double {
        fun transformComponent(component: Int): Double {
            val v = component.toDouble() / 255.0
            return if (v < 0.03928) {
                v / 12.92
            } else {
                ((v + 0.055) / 1.055).pow(LUM_GAMMA)
            }
        }

        return transformComponent(r) * LUM_RED + transformComponent(g) * LUM_GREEN + transformComponent(b) * LUM_BLUE
    }

    fun mix(target: Rgb, percent: Double): Rgb {
        check(percent in 0.0..1.0)
        val (r1, g1, b1) = this
        val (r2, g2, b2) = target
        val nR = min(255, (r1 + (r2 - r1) * percent).toInt())
        val nG = min(255, (g1 + (g2 - g1) * percent).toInt())
        val nB = min(255, (b1 + (b2 - b1) * percent).toInt())
        return Rgb(nR, nG, nB)
    }

    fun shade(percentage: Double): Rgb {
        return mix(BLACK, percentage)
    }

    fun tint(percentage: Double): Rgb {
        return mix(WHITE, percentage)
    }

    companion object {
        const val LUM_RED = 0.2126
        const val LUM_GREEN = 0.7152
        const val LUM_BLUE = 0.0722
        const val LUM_GAMMA = 2.4
        val BLACK = Rgb(0, 0, 0)
        val WHITE = Rgb(255, 255, 255)

        fun fromInt(value: Int): Rgb {
            return Rgb((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)
        }
    }
}

private fun extractRgb(color: Int): Rgb {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return Rgb(r, g, b)
}

private fun colorDistance(color1: Int, color2: Int): Double {
    val (r1, g1, b1) = extractRgb(color1)
    val (r2, g2, b2) = extractRgb(color2)
    return sqrt(
        (((r2 - r1) * (r2 - r1)) +
                ((g2 - g1) * (g2 - g1)) +
                ((b2 - b1) * (b2 - b1))).toDouble()
    )
}

fun main() = runBlocking {
    println(Rgb(255, 255, 255).invert())
    println(Rgb(0, 0, 0).invert())
    println(Rgb(0, 0, 0).invert())
    val darkBackground = 0x282c2f
    val lightBackground = 0xf8f8f9
    val logo = File("/tmp/whisper.png").readBytes()
    val sparkReplace = mapOf(0x3c3a3e to InvertColor)
    val tenxReplace = mapOf(0x153057 to GrayscaleInvertColor)
    val jupyterReplace = mapOf(
        0x4e4e4e to InvertColor,
        0x9e9e9e to InvertColor,
    )
    val newLogo = LogoGenerator.generateLogoWithText(
        "",
        "",
        logo,
        placeTextUnderLogo = false,
        darkBackground,
        jupyterReplace,
    )

    File("/tmp/spark_out.png").writeBytes(newLogo)
}
