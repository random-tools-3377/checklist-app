package io.github.randomtools3377.checklist.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * The app's icon drawn at [size] pixels, for the window and the notification area: the same
 * white ring and tick on the garden green as the phone icons (see tools/make-windows-icon.py),
 * on a rounded square.
 */
fun checklistIcon(size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        // The phone drawing is on a 108 grid shown through a 72 window around its middle.
        val unit = size / 72.0
        fun x(v: Double) = (v - 18.0) * unit
        val corner = size * 0.36
        g.color = Color(0x2E, 0x6B, 0x5E)
        g.fill(RoundRectangle2D.Double(0.0, 0.0, size.toDouble(), size.toDouble(), corner, corner))
        g.color = Color.WHITE
        g.stroke = BasicStroke((5 * unit).toFloat())
        g.draw(Ellipse2D.Double(x(30.0), x(30.0), 48 * unit, 48 * unit))
        g.stroke = BasicStroke((6 * unit).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(
            Path2D.Double().apply {
                moveTo(x(43.0), x(55.0))
                lineTo(x(51.0), x(63.0))
                lineTo(x(66.0), x(46.0))
            },
        )
    } finally {
        g.dispose()
    }
    return image
}
