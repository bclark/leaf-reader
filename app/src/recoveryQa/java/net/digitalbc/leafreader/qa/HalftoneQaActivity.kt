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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
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

/** Finite, synthetic drawing attribution only; never compiled into owner/release variants. */
class HalftoneQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "net.digitalbc.leafreader.recoveryqa")
        val mode = intent.getStringExtra("mode") ?: "direct"
        check(mode in setOf("direct", "none", "offscreen", "opaque", "path"))
        val dark = intent.getBooleanExtra("dark", false)
        val animate = intent.getBooleanExtra("animate", true)
        enableEdgeToEdge()
        Trace.beginSection("LeafHalftone.start")
        try {
            setContent {
                ComicReaderTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Fixture(mode, dark, animate)
                }
            }
        } finally { Trace.endSection() }
    }
}

@Composable
private fun Fixture(mode: String, dark: Boolean, animate: Boolean) {
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
        contentDescription = "Leaf halftone "+mode+" dark="+dark+" animate="+animate
    }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(144.dp), contentAlignment = Alignment.Center) {
                Text("Leaf render diagnostic", style = MaterialTheme.typography.titleLarge)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val layer = if (mode == "offscreen" || mode == "opaque") Modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                } else Modifier
                val paper = if (mode == "opaque") Modifier.background(background) else Modifier
                val dots = when (mode) {
                    "none" -> Modifier
                    "path" -> Modifier.dotPath(LeafSecondary)
                    else -> Modifier.halftone(LeafSecondary, alpha = 0.05f)
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
