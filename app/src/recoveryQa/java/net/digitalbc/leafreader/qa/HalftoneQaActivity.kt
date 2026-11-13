package net.digitalbc.leafreader.qa

import android.os.Bundle
import android.os.Trace
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import net.digitalbc.leafreader.data.settings.ThemeMode
import net.digitalbc.leafreader.ui.brand.halftone
import net.digitalbc.leafreader.ui.theme.ComicReaderTheme
import net.digitalbc.leafreader.ui.theme.LeafSecondary
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Finite, synthetic drawing attribution only; never compiled into owner/release variants. */
class HalftoneQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "net.digitalbc.leafreader.recoveryqa")
        val mode = intent.getStringExtra("mode") ?: "direct"
        check(mode in setOf("direct", "none", "offscreen", "opaque", "path", "tile"))
        val dark = intent.getBooleanExtra("dark", false)
        val animate = intent.getBooleanExtra("animate", true)
        val resize = intent.getBooleanExtra("resize", false)
        enableEdgeToEdge()
        Trace.beginSection("LeafHalftone.start")
        try {
            setContent {
                ComicReaderTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Fixture(mode, dark, animate, resize)
                }
            }
        } finally { Trace.endSection() }
    }
}

@Composable
private fun Fixture(mode: String, dark: Boolean, animate: Boolean, resize: Boolean) {
    val frame = remember { mutableIntStateOf(0) }
    LaunchedEffect(animate) {
        if (animate) withTimeoutOrNull(40_000) {
            while (isActive) {
                withFrameNanos { }
                frame.intValue++
            }
        }
    }
    val background = MaterialTheme.colorScheme.background
    val marker = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(background).semantics {
        contentDescription = "Leaf halftone "+mode+" dark="+dark+" animate="+animate+" resize="+resize
    }) {
        Column(Modifier.fillMaxSize()) {
            val header = if (resize) Modifier.layout { measurable, constraints ->
                val phase = frame.intValue % 240
                val extra = (if (phase < 120) phase else 240 - phase) / 2f
                val height = (144.dp + extra.dp).roundToPx()
                val child = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(child.width, child.height) { child.place(0, 0) }
            } else Modifier.height(144.dp)
            Box(Modifier.fillMaxWidth().then(header), contentAlignment = Alignment.Center) {
                Text("Leaf render diagnostic", style = MaterialTheme.typography.titleLarge)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val layer = if (mode == "offscreen" || mode == "opaque") Modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                } else Modifier
                val paper = if (mode == "opaque") Modifier.background(background) else Modifier
                val dots = remember(mode) { when (mode) {
                    "none" -> Modifier
                    "path" -> Modifier.dotPath(LeafSecondary)
                    "tile" -> Modifier.dotTile(LeafSecondary)
                    else -> Modifier.halftone(LeafSecondary, alpha = 0.05f)
                }
                }
                Box(Modifier.matchParentSize().then(layer).then(paper).then(dots))
            }
        }
        // Invalidates a separate draw node, leaving the static wash unchanged.
        Canvas(Modifier.matchParentSize()) {
            drawRect(marker, Offset((frame.intValue * 7f) % size.width, 72.dp.toPx()), Size(1f, 1f))
        }
    }
}

/** Diagnostic alternative only, retaining the production default dot geometry. */
private fun Modifier.dotPath(color: Color) = drawWithCache {
    val spacing = 9.dp.toPx()
    val radius = 1.6.dp.toPx()
    val dots = Path()
    var y = 0f
    while (y < size.height + spacing) {
        var x = 0f
        while (x < size.width + spacing) {
            dots.addOval(Rect(x - radius, y - radius, x + radius, y + radius))
            x += spacing
        }
        y += spacing
    }
    onDrawBehind { drawPath(dots, color, alpha = 0.05f) }
}


/** QA-only bounded texture hypothesis. Fallback preserves paint for unsupported densities. */
private fun Modifier.dotTile(color: Color): Modifier {
    var cachedSpacing = Float.NaN
    var brush: ShaderBrush? = null
    return drawWithCache {
        val spacing = 9.dp.toPx()
        val radius = 1.6.dp.toPx()
        val periods = (1..32).firstOrNull {
            val width = spacing * it
            width.roundToInt() in 1..512 && abs(width - width.roundToInt()) < 0.0001f
        }
        if (periods == null) return@drawWithCache onDrawBehind {
            var y = 0f
            while (y < size.height + spacing) {
                var x = 0f
                while (x < size.width + spacing) {
                    drawCircle(color, radius, Offset(x, y), alpha = 0.05f)
                    x += spacing
                }
                y += spacing
            }
        }
        if (cachedSpacing != spacing) {
            val edge = (spacing * periods).roundToInt()
            val bitmap = android.graphics.Bitmap.createBitmap(edge, edge, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
            }
            // Include boundary circles so their clipped halves wrap correctly.
            for (row in 0..periods) for (column in 0..periods) {
                canvas.drawCircle(column * spacing, row * spacing, radius, paint)
            }
            brush = ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
            cachedSpacing = spacing
        }
        val dots = checkNotNull(brush)
        onDrawBehind {
            // Keep the original overflowing top/left row and last circle extent.
            val right = ceil(size.width / spacing) * spacing + radius
            val bottom = ceil(size.height / spacing) * spacing + radius
            drawRect(dots, Offset(-radius, -radius), Size(right + radius, bottom + radius), alpha = 0.05f)
        }
    }
}
