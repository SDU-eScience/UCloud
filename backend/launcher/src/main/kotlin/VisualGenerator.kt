package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

private val canvas by lazy {
    BufferedImage(1300, 30_000, BufferedImage.TYPE_INT_ARGB)
}

private val actor by lazy {
    ImageIO.read(File("./doc-assets/actor.png"))
}

private val providerIcon by lazy {
    ImageIO.read(File("./doc-assets/provider_recipient.png"))
}

private val ucloudIcon by lazy {
    ImageIO.read(File("./doc-assets/ucloud_recipient.png"))
}

private fun registerFont(path: String) {
    Font.createFont(
        Font.TRUETYPE_FONT,
        UseCase::class.java.getResourceAsStream(path)
    ).also { font ->
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
    }
}

private val primaryFont by lazy {
    registerFont("/fonts/inter/Inter-Regular.ttf")
    registerFont("/fonts/inter/Inter-Bold.ttf")

    Font("Inter", Font.PLAIN, 16)
}

private val monospaceFont by lazy {
    registerFont("/fonts/jetbrains-mono/JetBrainsMono-Regular.ttf")
    Font("Jetbrains Mono", Font.PLAIN, 16)
}

private val monospaceFontBold by lazy {
    registerFont("/fonts/jetbrains-mono/JetBrainsMono-Bold.ttf")
    Font("Jetbrains Mono", Font.BOLD, 16)
}

private val outputDir by lazy {
    File("../../docs/diagrams").also { it.mkdirs() }
}

fun UseCase.visual(): String {
    val img = canvas
    val graphics = img.createGraphics()

    graphics.color = Color.BLACK
    graphics.background = Color.WHITE
    graphics.clearRect(0, 0, img.width, img.height)

    var y = 0
    with(graphics) {
        font = primaryFont

        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val fontMetrics = getFontMetrics(font)
        val monospaceMetricsBold = getFontMetrics(monospaceFontBold)

        val actorWidth = 110
        val actorHeight = 140
        val recipientBuffer = 5
        val arrowSize = 30
        val messageWidth = (canvas.width - (actorWidth * 2) - (arrowSize * 2))

        for (node in nodes) {
            when (node) {
                is UseCaseNode.Actor -> {
                    // Do nothing
                }
                is UseCaseNode.Call<*, *, *> -> {
                    run {
                        font = monospaceFontBold
                        drawRectangleWithShadow(actorWidth + arrowSize, y, messageWidth, monospaceMetricsBold.height + 16)
                        drawString(node.call.fullName, actorWidth + arrowSize + 10, y + monospaceMetricsBold.height + 5)
                        y += monospaceMetricsBold.height + 16 + 16
                        font = primaryFont
                    }

                    run {
                        drawRectangleWithShadow(0, y, actorWidth, actorHeight)
                        drawTextCentered(node.actor.name, 5, y + 130, 105)
                        drawImage(actor, 34, y + 13, null)
                    }

                    val recipientX = canvas.width - actorWidth - recipientBuffer
                    run {
                        drawRectangleWithShadow(recipientX, y, actorWidth, actorHeight)
                        val isUCloud = if (node.call.containerRef.javaClass.simpleName.endsWith("Provider")) {
                            false
                        } else {
                            true
                        }
                        val recipient = if (isUCloud) "UCloud" else "Provider"
                        val icon = if (isUCloud) ucloudIcon else providerIcon

                        drawImage(icon, recipientX + 1, y + 1, null)

                        val oldColor = color
                        color = Color.WHITE
                        drawTextCentered(recipient, recipientX, y + 130, 105)
                        color = oldColor
                    }

                    run {
                        // Request line
                        drawLine(actorWidth, y + 40, actorWidth + arrowSize, y + 40)
                        drawArrow(actorWidth + arrowSize, y + 40, ArrowDirection.RIGHT)

                        // Received-by line
                        drawLine(actorWidth + arrowSize + messageWidth, y + 40, canvas.width - 115, y + 40)
                        drawArrow(canvas.width - 115, y + 40, ArrowDirection.RIGHT)
                    }

                    val baseY = y

                    run {
                        val visualization = visualizeValue(node.request)
                        drawVisualization(visualization, actorWidth + arrowSize, y, messageWidth, 0)
                        y += max(actorHeight, estimateHeight(visualization)) + 30
                    }

                    run {
                        drawLine(
                            recipientX + (actorWidth / 2),
                            baseY + actorHeight,
                            recipientX + (actorWidth / 2),
                            y + 30
                        )
                        drawLine(
                            recipientX + (actorWidth / 2),
                            y + 30,
                            actorWidth + arrowSize + messageWidth,
                            y + 30
                        )
                        drawArrow(
                            actorWidth + arrowSize + messageWidth + 4,
                            y + 30,
                            ArrowDirection.LEFT
                        )

                        drawLine(
                            actorWidth / 2, y + 30,
                            actorWidth + arrowSize, y + 30
                        )
                        drawLine(
                            actorWidth / 2, baseY + actorHeight,
                            actorWidth / 2, y + 30,
                        )
                        drawArrow(
                            actorWidth / 2, baseY + actorHeight,
                            ArrowDirection.UP
                        )
                    }

                    y += 10

                    run {
                        val visualization = visualizeValue(node.response)
                        drawVisualization(visualization, actorWidth + arrowSize, y, messageWidth, 0)
                        y += estimateHeight(visualization) + 30
                    }
                }
                is UseCaseNode.Comment -> {
                    // y += drawTextWrapped(node.comment, 140, y, messageWidth)
                    node.comment.lines().forEach { line ->
                        y += (fontMetrics.height * 1.3).toInt()
                        // NOTE(Dan): 165 is a rough attempt to center contents based on how wide it is in the
                        // source-code
                        drawString(line, actorWidth + arrowSize + 165, y)
                    }

                    y += fontMetrics.height * 2
                }
                is UseCaseNode.SourceCode -> {
                    // Do nothing
                }
                is UseCaseNode.Subscription<*, *, *> -> {
                    // Do nothing
                }
            }
        }
    }

    ImageIO.write(img.getSubimage(0, 0, canvas.width, max(1, y)), "png", File(outputDir, this.id + ".png"))
    return "![](/docs/diagrams/$id.png)"
}

private val colors = listOf(
    Color.BLACK,
    Color(0x006aff),
    Color(0xc0000f),
    Color(0xe98c33),
    Color(0x00c05a),
    Color(0x9065d1),
    Color(0x70b000),
    Color(0x839a7),
    Color(0x0c95b7)
)

enum class ArrowDirection {
    UP,
    RIGHT,
    DOWN,
    LEFT
}

fun Graphics2D.drawArrow(
    x: Int,
    y: Int,
    direction: ArrowDirection,
    arrowSize: Int = 5
) {
    when (direction) {
        ArrowDirection.UP -> {
            drawLine(x - arrowSize, y + arrowSize, x, y)
            drawLine(x + arrowSize, y + arrowSize, x, y)
        }
        ArrowDirection.RIGHT -> {
            drawLine(x - arrowSize, y + arrowSize, x, y)
            drawLine(x - arrowSize, y - arrowSize, x, y)
        }
        ArrowDirection.DOWN -> {
            drawLine(x - arrowSize, y - arrowSize, x, y)
            drawLine(x + arrowSize, y - arrowSize, x, y)
        }
        ArrowDirection.LEFT -> {
            drawLine(x + arrowSize, y + arrowSize, x, y)
            drawLine(x + arrowSize, y - arrowSize, x, y)
        }
    }
}

fun Graphics2D.drawRectangleWithShadow(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    borderColor: Color = Color.BLACK,
    shadowColor: Color = Color(0xbbbbbb),
    shadowOffsetX: Int = 4,
    shadowOffsetY: Int = 4
) {
    val previousColor = color
    color = shadowColor
    fillRect(x + shadowOffsetX, y + shadowOffsetY, width, height)
    color = borderColor
    clearRect(x, y, width, height)
    drawRect(x, y, width, height)
    color = previousColor
}

fun Graphics2D.drawTextCentered(
    text: String,
    x: Int,
    y: Int,
    width: Int
) {
    val metrics = getFontMetrics(font)
    val expectedWidth = metrics.stringWidth(text)
    if (expectedWidth < width) {
        drawString(text, x + (width - expectedWidth) / 2, y)
    } else {
        drawString(text, x, y)
    }
}

private const val titlePaddingY = 5
private const val cardPropertyPaddingY = 10
private const val cardMargin = 10
private const val propertyPadding = 10

fun Graphics2D.drawVisualization(
    visualization: DocVisualization,
    x: Int,
    y: Int,
    maxWidth: Int,
    depth: Int,
) {
    val previousColor = color
    color = colors[depth % colors.size]
    val previousFont = font
    font = monospaceFont
    val metrics = getFontMetrics(font)

    when (visualization) {
        is DocVisualization.Card -> {
            val height = estimateHeight(visualization)
            val width = maxWidth

            drawRectangleWithShadow(
                x,
                y,
                width,
                height,
                borderColor = color,
                shadowColor = Color(color.red, color.green, color.blue, 125)
            )

            var offsetX = cardMargin
            var offsetY = cardMargin

            font = monospaceFontBold
            drawString(visualization.title, offsetX + x, y + metrics.height + 5)
            offsetY += metrics.height + titlePaddingY

            font = monospaceFont
            for (line in visualization.lines) {
                var maxHeight = 0
                for (stat in line.stats) {
                    var statValue = stat.value
                    if (statValue is DocVisualization.Placeholder) {
                        stat.value = replacePlaceholderVisualization(statValue.value)
                        statValue = stat.value
                    }

                    val key = if (stat.name.isEmpty()) "" else "${stat.name}: "
                    drawString(key, x + offsetX, y + offsetY + metrics.height)
                    offsetX += metrics.stringWidth(key)

                    val extraSpace = if (statValue is DocVisualization.Card) {
                        metrics.height + cardPropertyPaddingY
                    } else {
                        0
                    }

                    offsetY += extraSpace

                    if (statValue is DocVisualization.Card) offsetX = cardMargin

                    val newDepth = if (statValue is DocVisualization.Card) depth + 1 else depth
                    drawVisualization(statValue, x + offsetX, y + offsetY, maxWidth - cardMargin * 2, newDepth)

                    offsetY -= extraSpace

                    maxHeight = max(maxHeight, estimateHeight(statValue) + extraSpace)
                }
                offsetY += maxHeight + propertyPadding
                offsetX = cardMargin
            }

            for (child in visualization.children) {
                val newDepth = if (child is DocVisualization.Card) depth + 1 else depth
                drawVisualization(child, x + offsetX, y + offsetY, maxWidth - cardMargin * 2, newDepth)
                offsetY += estimateHeight(child) + propertyPadding
            }
        }
        is DocVisualization.Inline -> {
            drawString(visualization.value, x, y + metrics.height)
        }

        is DocVisualization.Placeholder -> {
            drawVisualization(replacePlaceholderVisualization(visualization.value), x, y, maxWidth, depth)
        }
    }
    font = previousFont
    color = previousColor
}

fun Graphics2D.estimateHeight(
    visualization: DocVisualization,
): Int {
    if (visualization.estimatedHeight != -1) return visualization.estimatedHeight
    val metrics = getFontMetrics(font)

    return when (visualization) {
        is DocVisualization.Card -> {
            val lineHeight = visualization.lines.sumByLong { line ->
                // HACK(Dan): This shouldn't be here, but it was the easiest place to make sure that placeholders are
                // gone before we estimate the height.
                line.stats.forEach {
                    val value = it.value
                    if (value is DocVisualization.Placeholder) {
                        it.value = replacePlaceholderVisualization(value.value)
                    }
                }

                val hasCard = line.stats.any { it.value is DocVisualization.Card }
                val extraSpacing = if (hasCard) metrics.height + cardPropertyPaddingY else 0
                (line.stats.maxOf { estimateHeight(it.value) } ) + cardPropertyPaddingY + extraSpacing.toLong()
            }.toInt()

            val childrenHeight = visualization.children.sumByLong { card ->
                estimateHeight(card) + propertyPadding.toLong()
            }.toInt()

            val paddingY = cardMargin * 2
            val titleSize = metrics.height + titlePaddingY + 5

            lineHeight + childrenHeight + paddingY + titleSize
        }

        is DocVisualization.Inline -> {
            metrics.height
        }

        is DocVisualization.Placeholder -> {
            estimateHeight(replacePlaceholderVisualization(visualization.value))
        }
    }.also { visualization.estimatedHeight = it }
}
