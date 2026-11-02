package net.digitalbc.leafreader.ui.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.digitalbc.leafreader.detection.Panel
import net.digitalbc.leafreader.ui.brand.PageCoin
import net.digitalbc.leafreader.ui.brand.Reticle
import net.digitalbc.leafreader.ui.theme.Anton
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.Cream
import net.digitalbc.leafreader.ui.theme.CreamMuted
import net.digitalbc.leafreader.ui.theme.PaperWhite
import net.digitalbc.leafreader.ui.theme.Crimson
import net.digitalbc.leafreader.ui.theme.DeepInk
import net.digitalbc.leafreader.ui.theme.DeepInkSurface
import net.digitalbc.leafreader.ui.theme.Ink
import net.digitalbc.leafreader.ui.theme.LeafAccent
import net.digitalbc.leafreader.ui.theme.LeafPrimary
import net.digitalbc.leafreader.ui.theme.Ochre
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val PanelConverter = TwoWayConverter<Panel, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
    convertFromVector = { Panel(it.v1, it.v2, it.v3, it.v4) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    pageSlideEnabled: Boolean,
    onTogglePageSlideEnabled: () -> Unit = {},
    fitMode: PageFitMode = PageFitMode.FIT_PAGE,
    onSetFitMode: (PageFitMode) -> Unit = {},
    portraitSpread: Boolean = false,
    onTogglePortraitSpread: () -> Unit = {},
    landscapeSpread: Boolean = false,
    onToggleLandscapeSpread: () -> Unit = {},
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val spreadActive = resolveSpreadLayout(isLandscape, portraitSpread, landscapeSpread)
    LaunchedEffect(spreadActive) {
        viewModel.setSpreadEnabled(spreadActive)
    }

    val pageCount = state.pageCount.coerceAtLeast(1)
    val pageIndex = state.pageIndex.coerceIn(0, pageCount - 1)
    val rtl = state.rightToLeft
    val isSpread = state.isSpread && state.secondPage != null
    val scope = rememberCoroutineScope()

    // Keep document indices with decoded images: produceState retains its previous value
    // when keys change, so unlabelled adjacent images can briefly belong to the old spread.
    val adjacentPages by produceState<Map<Int, Bitmap>>(emptyMap(), pageIndex, isSpread, pageCount) {
        value = emptyMap()
        for (index in (pageIndex - 2..pageIndex + 3)) {
            if (index in 0 until pageCount && index != pageIndex) {
                val page = viewModel.getCachedPageBitmap(index) ?: viewModel.loadPageBitmap(index)
                if (page != null) value = value + (index to page)
            }
        }
    }
    val getPageBitmap: (Int?) -> Bitmap? = { index ->
        when (index) {
            null -> null
            pageIndex -> state.page
            pageIndex + 1 -> state.secondPage ?: adjacentPages[index] ?: viewModel.getCachedPageBitmap(index)
            else -> adjacentPages[index] ?: viewModel.getCachedPageBitmap(index)
        }
    }
    val nextBitmap = getPageBitmap(pageIndex + 1)
    val prevBitmap = getPageBitmap(pageIndex - 1)

    var curlDirection by remember(pageIndex, rtl, fitMode, spreadActive, isSpread, configuration.screenWidthDp, configuration.screenHeightDp) { mutableStateOf<CurlDirection?>(null) }
    var curlProgress by remember(pageIndex, rtl, fitMode, spreadActive, isSpread, configuration.screenWidthDp, configuration.screenHeightDp) { mutableFloatStateOf(0f) }

    val canAnimateTurn: (CurlDirection) -> Boolean = { direction ->
        val mapping = computeSpreadTurnMapping(pageIndex, if (isSpread) pageIndex + 1 else null,
            pageCount, rtl, direction == CurlDirection.FORWARD,
            if (direction == CurlDirection.FORWARD) state.nextSpread else state.previousSpread)
        if (mapping != null) {
            getPageBitmap(mapping.movingBackDocIndex) != null &&
                (mapping.underneathDocIndex == null || getPageBitmap(mapping.underneathDocIndex) != null)
        } else if (direction == CurlDirection.FORWARD) nextBitmap != null else prevBitmap != null
    }
    val canTurnForward = if (isSpread) pageIndex + 2 < pageCount else pageIndex < pageCount - 1
    val canTurnBackward = pageIndex > 0

    var turnJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(pageIndex, rtl, fitMode, spreadActive, isSpread, configuration.screenWidthDp, configuration.screenHeightDp) {
        onDispose { turnJob?.cancel() }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val interruptTurn by rememberUpdatedState<() -> Unit>({
        turnJob?.cancel()
        curlDirection = null
        curlProgress = 0f
    })
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                interruptTurn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val settleTurn: (CurlDirection, Float, Boolean) -> Unit = { direction, from, complete ->
        turnJob?.cancel()
        turnJob = scope.launch {
            animate(from, if (complete) 1f else 0f,
                animationSpec = tween(if (complete) 260 else 180, easing = FastOutSlowInEasing)) { value, _ ->
                curlProgress = value
            }
            if (complete) {
                if (direction == CurlDirection.FORWARD) viewModel.nextPage() else viewModel.previousPage()
                // Hold the final sheet until decoding/navigation has published the destination.
                viewModel.state.first { it.pageIndex != pageIndex || it.error != null }
            }
            curlProgress = 0f
            curlDirection = null
        }
    }
    val turnForward: () -> Unit = {
        if (canTurnForward && curlDirection == null && canAnimateTurn(CurlDirection.FORWARD)) {
            curlDirection = CurlDirection.FORWARD
            settleTurn(CurlDirection.FORWARD, 0f, true)
        }
    }
    val turnBackward: () -> Unit = {
        if (canTurnBackward && curlDirection == null && canAnimateTurn(CurlDirection.BACKWARD)) {
            curlDirection = CurlDirection.BACKWARD
            settleTurn(CurlDirection.BACKWARD, 0f, true)
        }
    }

    // Go fully immersive (hide the status/nav bars, incl. the battery/clock icons) while reading;
    // bring the bars back with the chrome, and restore them when leaving the reader.
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    LaunchedEffect(chromeVisible, window) {
        window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(window) {
        onDispose {
            window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.error != null -> ErrorView(state.error!!)
            state.page == null -> LoadingView()
            else -> PageViewer(
                state = state,
                fitMode = fitMode,
                nextBitmap = nextBitmap,
                prevBitmap = prevBitmap,
                getPageBitmap = getPageBitmap,
                curlDirection = curlDirection,
                curlProgress = curlProgress,
                onUpdateCurl = { dir, prog ->
                    curlDirection = dir
                    curlProgress = prog
                },
                onTurnForward = turnForward,
                onTurnBackward = turnBackward,
                onSettleTurn = settleTurn,
                canAnimateTurn = canAnimateTurn,
                onToggleChrome = { chromeVisible = !chromeVisible },
                onShowFullPage = viewModel::showFullPage,
            )
        }

        // Faint reticle brackets framing the viewport (comic identity), under the chrome.
        if (state.error == null && state.page != null) {
            Reticle(
                modifier = Modifier.matchParentSize().padding(6.dp),
                color = LeafPrimary.copy(alpha = 0.55f),
                inset = 6.dp,
                length = 16.dp,
                stroke = 2.dp,
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                LeafPrimary.copy(alpha = 0.98f),
                                LeafPrimary.copy(alpha = 0.88f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 18.dp),
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Cream.copy(alpha = 0.12f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = Cream, modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title, fontFamily = Archivo, fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pageStatus(state), fontFamily = Archivo, fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp, letterSpacing = 1.4.sp, color = CreamMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpreadChip(
                        spreadEnabled = spreadActive,
                        orientation = if (isLandscape) "Landscape" else "Portrait",
                        onClick = if (isLandscape) onToggleLandscapeSpread else onTogglePortraitSpread,
                    )
                    FitModeChip(
                        fitMode = fitMode,
                        onClick = {
                            onSetFitMode(
                                if (fitMode == PageFitMode.FIT_PAGE) PageFitMode.FIT_WIDTH else PageFitMode.FIT_PAGE,
                            )
                        },
                    )
                    DirectionChip(rightToLeft = state.rightToLeft, onClick = viewModel::toggleReadingDirection)
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && state.pageCount > 1,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PageScrubber(
                pageIndex = state.pageIndex,
                pageCount = state.pageCount,
                isSpread = state.isSpread,
                rightToLeft = state.rightToLeft,
                pageSlideEnabled = pageSlideEnabled,
                onTogglePageSlideEnabled = onTogglePageSlideEnabled,
                onPrevPage = turnBackward,
                onNextPage = turnForward,
                onJumpToPage = viewModel::jumpToPage,
            )
        }
    }
}

@Composable
private fun PageScrubber(
    pageIndex: Int,
    pageCount: Int,
    isSpread: Boolean,
    rightToLeft: Boolean,
    pageSlideEnabled: Boolean,
    onTogglePageSlideEnabled: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(pageIndex.toFloat()) }
    LaunchedEffect(pageIndex) { if (!scrubbing) scrub = pageIndex.toFloat() }
    val shownPage = (if (scrubbing) scrub.roundToInt() else pageIndex) + 1
    val shownPageLabel = if (isSpread && !scrubbing && pageIndex + 1 < pageCount) {
        "${pageIndex + 1}-${pageIndex + 2}"
    } else {
        "$shownPage"
    }
    val canGoPrev = pageIndex > 0
    val canGoNext = if (isSpread) pageIndex + 1 < pageCount - 1 else pageIndex < pageCount - 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, DeepInkSurface.copy(alpha = 0.98f))))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (!net.digitalbc.leafreader.ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) Arrangement.SpaceBetween else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!net.digitalbc.leafreader.ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LeafPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.clickable { onTogglePageSlideEnabled() },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            imageVector = if (pageSlideEnabled) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.CropFree,
                            contentDescription = "Switch reading mode",
                            tint = LeafAccent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            if (pageSlideEnabled) "PAGE FLIP" else "PANEL BY PANEL",
                            fontFamily = Anton,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = PaperWhite,
                        )
                    }
                }
            }
            PageCoin(pageLabel = shownPageLabel, total = pageCount)
        }
        Spacer(Modifier.size(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onPrevPage,
                enabled = canGoPrev,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = if (rightToLeft) Icons.AutoMirrored.Filled.NavigateNext else Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = if (isSpread) "Previous pages" else "Previous page",
                    tint = if (canGoPrev) Cream else CreamMuted.copy(alpha = 0.35f),
                )
            }
            Slider(
                modifier = Modifier.weight(1f),
                value = scrub.coerceIn(0f, (pageCount - 1).toFloat()),
                onValueChange = { scrubbing = true; scrub = it },
                onValueChangeFinished = {
                    scrubbing = false
                    onJumpToPage(scrub.roundToInt())
                },
                valueRange = 0f..(pageCount - 1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Ochre,
                    activeTrackColor = Ochre,
                    inactiveTrackColor = Cream.copy(alpha = 0.2f),
                ),
            )
            IconButton(
                onClick = onNextPage,
                enabled = canGoNext,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = if (rightToLeft) Icons.AutoMirrored.Filled.NavigateBefore else Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = if (isSpread) "Next pages" else "Next page",
                    tint = if (canGoNext) Cream else CreamMuted.copy(alpha = 0.35f),
                )
            }
        }
    }
}

private enum class CurlDirection {
    FORWARD,
    BACKWARD,
}

private const val MESH_COLS = 36
private const val MESH_ROWS = 4
private const val MESH_VERT_COUNT = (MESH_COLS + 1) * (MESH_ROWS + 1) * 2



/**
 * Renders a skeuomorphic 3D page curl & unroll deformer along a vertical curved axis.
 *
 * Peels the top page from the outer margin inward around a 3D cylindrical roll, exposing
 * the reverse side of the page and casting a subtle progressive drop shadow onto the spread underneath.
 */
private fun DrawScope.drawCurlTurn(
    topBitmap: Bitmap,
    bottomBitmap: Bitmap?,
    draw: PageDraw,
    progress: Float,
    isPeelingFromRight: Boolean,
    verts: FloatArray,
    meshPaint: Paint,
) {
    if (topBitmap.isRecycled) return
    val t = progress.coerceIn(0f, 1f)
    val w = draw.scaledWidth
    val h = draw.scaledHeight
    val left = draw.left
    val top = draw.top

    // 1. Draw underneath spread revealed by the curl
    if (bottomBitmap != null && !bottomBitmap.isRecycled) {
        drawIntoCanvas { canvas ->
            val src = Rect(0, 0, bottomBitmap.width, bottomBitmap.height)
            val dst = Rect(
                left.roundToInt(),
                top.roundToInt(),
                (left + w).roundToInt(),
                (top + h).roundToInt(),
            )
            canvas.nativeCanvas.drawBitmap(bottomBitmap, src, dst, meshPaint)
        }
    }

    // 2. Geometry of the vertical cylindrical roll
    val baseR = w * 0.08f
    val r = (baseR * (0.8f + 0.4f * kotlin.math.sin(t * Math.PI.toFloat()))).coerceAtLeast(10f)

    if (isPeelingFromRight) {
        val xFold = left + w * (1f - t)
        val xApex = (xFold + r).coerceAtMost(left + w)

        // Drop shadow onto underneath page to the right of the apex
        val shadowWidth = (r * 1.8f).coerceAtLeast(12.dp.toPx())
        val shadowAlpha = (0.55f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(0f, 0.65f)
        if (shadowAlpha > 0.02f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Black.copy(alpha = shadowAlpha),
                    0.4f to Color.Black.copy(alpha = shadowAlpha * 0.45f),
                    1.0f to Color.Transparent,
                    startX = xApex,
                    endX = (xApex + shadowWidth).coerceAtMost(left + w),
                ),
                topLeft = Offset(xApex, top),
                size = Size((xApex + shadowWidth).coerceAtMost(left + w) - xApex, h),
            )
        }

        // Deform top page mesh around the vertical cylinder
        var idx = 0
        for (row in 0..MESH_ROWS) {
            val v = row.toFloat() / MESH_ROWS
            val yFlat = top + v * h

            for (col in 0..MESH_COLS) {
                val u = col.toFloat() / MESH_COLS
                val xFlat = left + u * w

                val x = if (xFlat <= xFold) {
                    xFlat
                } else {
                    val s = xFlat - xFold
                    val theta = s / r
                    if (theta <= Math.PI.toFloat()) {
                        xFold + r * kotlin.math.sin(theta)
                    } else {
                        xFold - (s - Math.PI.toFloat() * r)
                    }
                }
                verts[idx++] = x
                verts[idx++] = yFlat
            }
        }

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(
                topBitmap,
                MESH_COLS,
                MESH_ROWS,
                verts,
                0,
                null,
                0,
                meshPaint,
            )
        }

        // Crease shadow in the valley at xFold
        val creaseWidth = (r * 0.75f).coerceAtLeast(8.dp.toPx())
        val creaseAlpha = (0.4f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(0f, 0.45f)
        if (creaseAlpha > 0.02f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.7f to Color.Black.copy(alpha = creaseAlpha * 0.5f),
                    1.0f to Color.Black.copy(alpha = creaseAlpha),
                    startX = (xFold - creaseWidth).coerceAtLeast(left),
                    endX = xFold,
                ),
                topLeft = Offset((xFold - creaseWidth).coerceAtLeast(left), top),
                size = Size(xFold - (xFold - creaseWidth).coerceAtLeast(left), h),
            )
        }

        // Specular highlight along apex of the roll
        val highlightWidth = r * 0.6f
        val highlightAlpha = (0.22f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(0f, 0.25f)
        if (highlightAlpha > 0.02f && xApex - highlightWidth > left) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = highlightAlpha),
                    1.0f to Color.Transparent,
                    startX = xApex - highlightWidth,
                    endX = (xApex + highlightWidth * 0.5f).coerceAtMost(left + w),
                ),
                topLeft = Offset(xApex - highlightWidth, top),
                size = Size((xApex + highlightWidth * 0.5f).coerceAtMost(left + w) - (xApex - highlightWidth), h),
            )
        }

        // Reverse side of page tint across the curl
        val reverseAlpha = (0.28f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(0f, 0.35f)
        if (reverseAlpha > 0.02f && xApex > xFold) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color(0xFFFBF0D9).copy(alpha = reverseAlpha),
                    1.0f to Color.Transparent,
                    startX = xFold,
                    endX = xApex,
                ),
                topLeft = Offset(xFold, top),
                size = Size(xApex - xFold, h),
            )
        }
    } else {
        // Peeling from Left edge to Right
        val xFold = left + w * t
        val xApex = (xFold - r).coerceAtLeast(left)

        // Drop shadow onto underneath page to the left of the apex
        val shadowWidth = (r * 1.8f).coerceAtLeast(12.dp.toPx())
        val shadowAlpha = (0.55f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(0f, 0.65f)
        if (shadowAlpha > 0.02f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.6f to Color.Black.copy(alpha = shadowAlpha * 0.45f),
                    1.0f to Color.Black.copy(alpha = shadowAlpha),
                    startX = (xApex - shadowWidth).coerceAtLeast(left),
                    endX = xApex,
                ),
                topLeft = Offset((xApex - shadowWidth).coerceAtLeast(left), top),
                size = Size(xApex - (xApex - shadowWidth).coerceAtLeast(left), h),
            )
        }

        // Deform top page mesh around the vertical cylinder
        var idx = 0
        for (row in 0..MESH_ROWS) {
            val v = row.toFloat() / MESH_ROWS
            val yFlat = top + v * h

            for (col in 0..MESH_COLS) {
                val u = col.toFloat() / MESH_COLS
                val xFlat = left + u * w

                val x = if (xFlat >= xFold) {
                    xFlat
                } else {
                    val s = xFold - xFlat
                    val theta = s / r
                    if (theta <= Math.PI.toFloat()) {
                        xFold - r * kotlin.math.sin(theta)
                    } else {
                        xFold + (s - Math.PI.toFloat() * r)
                    }
                }
                verts[idx++] = x
                verts[idx++] = yFlat
            }
        }

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(
                topBitmap,
                MESH_COLS,
                MESH_ROWS,
                verts,
                0,
                null,
                0,
                meshPaint,
            )
        }

        // Crease shadow in the valley at xFold
        val creaseWidth = (r * 0.75f).coerceAtLeast(8.dp.toPx())
        val creaseAlpha = (0.4f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(0f, 0.45f)
        if (creaseAlpha > 0.02f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Black.copy(alpha = creaseAlpha),
                    0.3f to Color.Black.copy(alpha = creaseAlpha * 0.5f),
                    1.0f to Color.Transparent,
                    startX = xFold,
                    endX = (xFold + creaseWidth).coerceAtMost(left + w),
                ),
                topLeft = Offset(xFold, top),
                size = Size((xFold + creaseWidth).coerceAtMost(left + w) - xFold, h),
            )
        }

        // Specular highlight along apex of the roll
        val highlightWidth = r * 0.6f
        val highlightAlpha = (0.22f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(0f, 0.25f)
        if (highlightAlpha > 0.02f && xApex + highlightWidth < left + w) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = highlightAlpha),
                    1.0f to Color.Transparent,
                    startX = (xApex - highlightWidth * 0.5f).coerceAtLeast(left),
                    endX = xApex + highlightWidth,
                ),
                topLeft = Offset((xApex - highlightWidth * 0.5f).coerceAtLeast(left), top),
                size = Size(xApex + highlightWidth - (xApex - highlightWidth * 0.5f).coerceAtLeast(left), h),
            )
        }

        // Reverse side of page tint across the curl
        val reverseAlpha = (0.28f * kotlin.math.sin(t * Math.PI.toFloat())).coerceIn(0f, 0.35f)
        if (reverseAlpha > 0.02f && xFold > xApex) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    1.0f to Color(0xFFFBF0D9).copy(alpha = reverseAlpha),
                    startX = xApex,
                    endX = xFold,
                ),
                topLeft = Offset(xApex, top),
                size = Size(xFold - xApex, h),
            )
        }
    }
}

/**
 * Renders an open-book physical two-page spread curl turn.
 *
 * One physical sheet curls around the center spine:
 * - The front face displays the turning page (lifts and curls away).
 * - The reverse face displays the incoming page (settles upright and un-mirrored).
 * - The static page stays in place on its side until covered by the turning sheet.
 * - The underneath page is revealed on the active side as the turning sheet lifts away.
 */
private fun DrawScope.drawSpreadCurlTurn(
    spread: SpreadDraw,
    destinationSpread: SpreadDraw,
    staticBitmap: Bitmap,
    movingFrontBitmap: Bitmap,
    movingBackBitmap: Bitmap?,
    underneathBitmap: Bitmap?,
    mapping: SpreadTurnMapping,
    progress: Float,
    userScale: Float,
    userPanX: Float,
    userPanY: Float,
    vertsFront: FloatArray,
    vertsBack: FloatArray,
    meshPaint: Paint,
) {
    if (staticBitmap.isRecycled || movingFrontBitmap.isRecycled) return
    val t = progress.coerceIn(0f, 1f)
    val frame = lerpSpreadDraw(spread, destinationSpread, t)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val left = cx + (frame.left1 - cx) * userScale + userPanX
    val spine = cx + (frame.left2 - cx) * userScale + userPanX
    val top = cy + (frame.top1 - cy) * userScale + userPanY
    val leftWidth = frame.width1 * userScale
    val rightWidth = frame.width2 * userScale
    val height = frame.height1 * userScale
    val fromRight = mapping.isPeelingFromRight
    val width = spreadTurnSheetWidth(spread, destinationSpread, t, fromRight) * userScale
    val radius = width * 0.16f * kotlin.math.sin(t * kotlin.math.PI.toFloat()).coerceAtLeast(0f)
    val fold = computeSpreadCurlFold(width, t, radius)
    val faceBoundary = (fold + radius * kotlin.math.PI.toFloat() / 2f).coerceIn(0f, width)

    fun drawFlat(bitmap: Bitmap?, x: Float, w: Float) {
        if (bitmap == null || bitmap.isRecycled) return
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height),
                android.graphics.RectF(x, top, x + w, top + height), meshPaint)
        }
    }
    drawFlat(staticBitmap, if (fromRight) left else spine, if (fromRight) leftWidth else rightWidth)
    drawFlat(underneathBitmap, if (fromRight) spine else left, if (fromRight) rightWidth else leftWidth)

    // The cylinder's surface normal changes sign at the apex. Collapse the hidden
    // part of each face to that boundary, rather than painting two whole images
    // over the same mesh (which showed the back before the sheet had turned).
    fun drawFace(bitmap: Bitmap?, back: Boolean, vertices: FloatArray) {
        if (bitmap == null || bitmap.isRecycled) return
        var i = 0
        for (row in 0..MESH_ROWS) {
            for (col in 0..MESH_COLS) {
                val u = col.toFloat() / MESH_COLS
                vertices[i++] = computeSpreadCurlFaceX(u, width, fold, radius, spine, fromRight, back)
                vertices[i++] = top + row.toFloat() / MESH_ROWS * height
            }
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(bitmap, MESH_COLS, MESH_ROWS, vertices, 0, null, 0, meshPaint)
        }
    }
    drawFace(movingFrontBitmap, false, vertsFront)
    drawFace(movingBackBitmap, true, vertsBack)
    if (radius > 1f) {
        val apex = computeSpreadCurlX(faceBoundary, fold, radius, spine, fromRight)
        val highlight = radius * 0.35f
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.15f), Color.Transparent),
            startX = apex - highlight, endX = apex + highlight),
            Offset(apex - highlight, top), Size(highlight * 2f, height))
    }
}


@Composable
private fun PageViewer(
    state: ReaderUiState,
    fitMode: PageFitMode = PageFitMode.FIT_PAGE,
    nextBitmap: Bitmap?,
    prevBitmap: Bitmap?,
    getPageBitmap: (Int?) -> Bitmap? = { null },
    curlDirection: CurlDirection?,
    curlProgress: Float,
    onUpdateCurl: (CurlDirection?, Float) -> Unit,
    onTurnForward: () -> Unit,
    onTurnBackward: () -> Unit,
    onSettleTurn: (CurlDirection, Float, Boolean) -> Unit,
    canAnimateTurn: (CurlDirection) -> Boolean,
    onToggleChrome: () -> Unit,
    onShowFullPage: () -> Unit,
) {
    val bitmap = state.page ?: return
    val rtl = state.rightToLeft
    val scope = rememberCoroutineScope()
    val secondPage = state.secondPage
    val isSpread = state.isSpread && secondPage != null
    val pageCount = state.pageCount.coerceAtLeast(1)
    val pageIndex = state.pageIndex.coerceIn(0, pageCount - 1)

    val leftBitmap = if (isSpread && secondPage != null) {
        if (rtl) secondPage else bitmap
    } else {
        bitmap
    }
    val rightBitmap = if (isSpread && secondPage != null) {
        if (rtl) bitmap else secondPage
    } else {
        bitmap
    }
    val leftImage = remember(leftBitmap) { leftBitmap.asImageBitmap() }
    val rightImage = remember(rightBitmap) { rightBitmap.asImageBitmap() }
    val image = leftImage

    val camera = remember { Animatable(state.currentCamera, PanelConverter) }
    LaunchedEffect(state.pageIndex, state.slot) {
        camera.animateTo(state.currentCamera, tween(360, easing = FastOutSlowInEasing))
    }


    // Free pinch-to-zoom / pan, reset whenever the framed view, fit mode, or spread changes.
    var userScale by remember { mutableFloatStateOf(1f) }
    var userPanX by remember { mutableFloatStateOf(0f) }
    var userPanY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.pageIndex, state.slot, fitMode, isSpread) {
        userScale = 1f; userPanX = 0f; userPanY = 0f
    }

    val resetView: () -> Unit = {
        scope.launch {
            val s0 = userScale; val x0 = userPanX; val y0 = userPanY
            animate(0f, 1f, animationSpec = tween(240, easing = FastOutSlowInEasing)) { t, _ ->
                userScale = s0 + (1f - s0) * t
                userPanX = x0 * (1f - t)
                userPanY = y0 * (1f - t)
            }
        }
    }

    val meshPaint = remember { Paint().apply { isFilterBitmap = true } }
    val vertsFront = remember { FloatArray(MESH_VERT_COUNT) }
    val vertsBack = remember { FloatArray(MESH_VERT_COUNT) }
    val verts = vertsFront

    val viewport = androidx.compose.ui.platform.LocalConfiguration.current
    val currentCurlDirection by rememberUpdatedState(curlDirection)
    val currentCanAnimateTurn by rememberUpdatedState(canAnimateTurn)
    val currentSettleTurn by rememberUpdatedState(onSettleTurn)
    var pendingTap by remember { mutableStateOf<Job?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) pendingTap?.cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            pendingTap?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pageIndex, pageCount, rtl, fitMode, isSpread, viewport.screenWidthDp, viewport.screenHeightDp) {
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        if (currentCurlDirection != null) {
                            do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        val downPos = down.position
                        var pan = Offset.Zero
                        var zoom = 1f
                        var maxPointers = 1
                        var pastSlop = false
                        var cancelled = false
                        var isCurlDrag = false
                        var activeDirection: CurlDirection? = null
                        var curDragT = 0f
                        val slop = viewConfiguration.touchSlop
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) {
                                cancelled = true
                                break
                            }
                            maxPointers = maxOf(maxPointers, event.changes.count { it.pressed })
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            pan += panChange
                            zoom *= zoomChange

                            event.changes.firstOrNull { it.id == down.id }?.let {
                                velocityTracker.addPosition(it.uptimeMillis, it.position)
                            }

                            if (!pastSlop && (abs(zoom - 1f) > 0.02f || pan.getDistance() > slop)) {
                                pastSlop = true
                                if (maxPointers == 1 && userScale <= 1.05f && abs(pan.x) > abs(pan.y)) {
                                    val dir = if (!rtl) {
                                        if (pan.x < 0f) CurlDirection.FORWARD else CurlDirection.BACKWARD
                                    } else {
                                        if (pan.x > 0f) CurlDirection.FORWARD else CurlDirection.BACKWARD
                                    }
                                    val canTurn = when (dir) {
                                        CurlDirection.FORWARD -> (if (isSpread) pageIndex + 2 < pageCount else pageIndex < pageCount - 1)
                                        CurlDirection.BACKWARD -> pageIndex > 0
                                    }
                                    if (canTurn && currentCanAnimateTurn(dir)) {
                                        pendingTap?.cancel()
                                        isCurlDrag = true
                                        activeDirection = dir
                                        onUpdateCurl(dir, 0f)
                                    }
                                }
                            }

                            if (isCurlDrag && maxPointers > 1) {
                                onUpdateCurl(null, 0f)
                                isCurlDrag = false
                                activeDirection = null
                            }
                            if (pastSlop) {
                                pendingTap?.cancel()
                                if (isCurlDrag && activeDirection != null) {
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    val containerW = size.width.toFloat()
                                    val rawT = if (!rtl) {
                                        if (activeDirection == CurlDirection.FORWARD) -pan.x / containerW else pan.x / containerW
                                    } else {
                                        if (activeDirection == CurlDirection.FORWARD) pan.x / containerW else -pan.x / containerW
                                    }
                                    curDragT = rawT.coerceIn(0f, 1f)
                                    onUpdateCurl(activeDirection, curDragT)
                                } else {
                                    val newScale = (userScale * zoomChange).coerceIn(1f, 5f)
                                    userScale = newScale
                                    val cw = size.width.toFloat()
                                    val ch = size.height.toFloat()
                                    if (isSpread) {
                                        val spread = computeSpreadDraw(
                                            page1W = leftBitmap.width,
                                            page1H = leftBitmap.height,
                                            page2W = rightBitmap.width,
                                            page2H = rightBitmap.height,
                                            containerW = cw,
                                            containerH = ch,
                                            fitMode = fitMode,
                                        )
                                        val effectiveW = spread.totalWidth * newScale
                                        val effectiveH = spread.totalHeight * newScale
                                        val baseLeft = cw / 2f + (spread.boundLeft - cw / 2f) * newScale
                                        val baseTop = ch / 2f + (spread.boundTop - ch / 2f) * newScale
                                        userPanX = if (effectiveW > cw) clampPan(userPanX + panChange.x, baseLeft, effectiveW, cw) else 0f
                                        userPanY = if (effectiveH > ch) clampPan(userPanY + panChange.y, baseTop, effectiveH, ch) else 0f
                                    } else {
                                        val draw = computePageDraw(
                                            camera = camera.value,
                                            bitmapW = image.width,
                                            bitmapH = image.height,
                                            containerW = cw,
                                            containerH = ch,
                                            fitMode = fitMode,
                                        )
                                        val effectiveW = draw.scaledWidth * newScale
                                        val effectiveH = draw.scaledHeight * newScale
                                        val baseLeft = cw / 2f + (draw.left - cw / 2f) * newScale
                                        val baseTop = ch / 2f + (draw.top - ch / 2f) * newScale
                                        userPanX = if (effectiveW > cw) clampPan(userPanX + panChange.x, baseLeft, effectiveW, cw) else 0f
                                        userPanY = if (effectiveH > ch) clampPan(userPanY + panChange.y, baseTop, effectiveH, ch) else 0f
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (cancelled) {
                            onUpdateCurl(null, 0f)
                        } else if (!pastSlop) {
                            val w = size.width
                            val tapX = downPos.x
                            val inFlight = pendingTap
                            if (inFlight != null && inFlight.isActive) {
                                inFlight.cancel()
                                pendingTap = null
                                onShowFullPage()
                                resetView()
                            } else {
                                pendingTap = scope.launch {
                                    delay(DOUBLE_TAP_WINDOW_MS)
                                    if (userScale > 1.05f) {
                                        onToggleChrome()
                                    } else {
                                        when {
                                            tapX < w / 3f -> if (rtl) onTurnForward() else onTurnBackward()
                                            tapX > w * 2f / 3f -> if (rtl) onTurnBackward() else onTurnForward()
                                            else -> onToggleChrome()
                                        }
                                    }
                                    pendingTap = null
                                }
                            }
                        } else if (isCurlDrag && activeDirection != null) {
                            val velocity = velocityTracker.calculateVelocity().x
                            val velInTurnDir = if (!rtl) {
                                if (activeDirection == CurlDirection.FORWARD) -velocity else velocity
                            } else {
                                if (activeDirection == CurlDirection.FORWARD) velocity else -velocity
                            }
                            val settleForward = (curDragT > 0.35f || velInTurnDir > 700f) && velInTurnDir > -600f
                            currentSettleTurn(activeDirection, curDragT, settleForward)
                        }
                    }
                } finally {
                    pendingTap?.cancel()
                    onUpdateCurl(null, 0f)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isSpread) {
                val spread = computeSpreadDraw(
                    page1W = leftBitmap.width,
                    page1H = leftBitmap.height,
                    page2W = rightBitmap.width,
                    page2H = rightBitmap.height,
                    containerW = size.width,
                    containerH = size.height,
                    fitMode = fitMode,
                )

                if (curlDirection != null && curlProgress > 0.001f && userScale <= 1.05f) {
                    val isForward = curlDirection == CurlDirection.FORWARD
                    val mapping = computeSpreadTurnMapping(
                        pageIndex = pageIndex,
                        secondPageIndex = pageIndex + 1,
                        pageCount = pageCount,
                        rightToLeft = rtl,
                        isForward = isForward,
                        destination = if (isForward) state.nextSpread else state.previousSpread,
                    )
                    if (mapping != null) {
                        val staticBitmap = getPageBitmap(mapping.staticPageDocIndex)
                        val movingFrontBitmap = getPageBitmap(mapping.movingFrontDocIndex)
                        val movingBackBitmap = getPageBitmap(mapping.movingBackDocIndex)
                        val underneathBitmap = mapping.underneathDocIndex?.let { getPageBitmap(it) }

                        if (staticBitmap != null && movingFrontBitmap != null) {
                            val target = if (isForward) state.nextSpread else state.previousSpread
                            val targetFirst = getPageBitmap(target?.first)
                            val targetSecond = getPageBitmap(target?.second)
                            if (targetFirst == null) return@Canvas
                            val destinationSpread = if (targetSecond != null) {
                                val targetLeft = if (rtl) targetSecond else targetFirst
                                val targetRight = if (rtl) targetFirst else targetSecond
                                computeSpreadDraw(targetLeft.width, targetLeft.height, targetRight.width, targetRight.height,
                                    size.width, size.height, fitMode = fitMode)
                            } else {
                                val draw = computePageDraw(Panel.FULL_PAGE, targetFirst.width, targetFirst.height,
                                    size.width, size.height, fitMode = fitMode)
                                // A standalone destination occupies the landing side; the other side closes to zero.
                                val landingLeft = mapping.isPeelingFromRight
                                val spine = if (landingLeft) draw.left + draw.scaledWidth else draw.left
                                SpreadDraw(draw.left, draw.top, if (landingLeft) draw.scaledWidth else 0f, draw.scaledHeight,
                                    spine, draw.top, if (landingLeft) 0f else draw.scaledWidth, draw.scaledHeight,
                                    draw.scaledWidth, draw.scaledHeight, draw.left, draw.top)
                            }
                            drawSpreadCurlTurn(
                                spread = spread,
                                destinationSpread = destinationSpread,
                                staticBitmap = staticBitmap,
                                movingFrontBitmap = movingFrontBitmap,
                                movingBackBitmap = movingBackBitmap,
                                underneathBitmap = underneathBitmap,
                                mapping = mapping,
                                progress = curlProgress,
                                userScale = userScale,
                                userPanX = userPanX,
                                userPanY = userPanY,
                                vertsFront = vertsFront,
                                vertsBack = vertsBack,
                                meshPaint = meshPaint,
                            )
                            return@Canvas
                        }
                    }
                }

                val cx = size.width / 2f
                val cy = size.height / 2f

                val l1 = cx + (spread.left1 - cx) * userScale + userPanX
                val t1 = cy + (spread.top1 - cy) * userScale + userPanY
                val w1 = spread.width1 * userScale
                val h1 = spread.height1 * userScale

                val l2 = cx + (spread.left2 - cx) * userScale + userPanX
                val t2 = cy + (spread.top2 - cy) * userScale + userPanY
                val w2 = spread.width2 * userScale
                val h2 = spread.height2 * userScale

                drawImage(
                    image = leftImage,
                    dstOffset = IntOffset(l1.roundToInt(), t1.roundToInt()),
                    dstSize = IntSize(w1.roundToInt(), h1.roundToInt()),
                )
                drawImage(
                    image = rightImage,
                    dstOffset = IntOffset(l2.roundToInt(), t2.roundToInt()),
                    dstSize = IntSize(w2.roundToInt(), h2.roundToInt()),
                )
            } else {
                val draw = computePageDraw(
                    camera = camera.value,
                    bitmapW = image.width,
                    bitmapH = image.height,
                    containerW = size.width,
                    containerH = size.height,
                    fitMode = fitMode,
                )

                if (curlDirection != null && curlProgress > 0.001f && userScale <= 1.05f) {
                    val dir = curlDirection
                    val forwardUnderneath = nextBitmap
                    val backwardUnderneath = prevBitmap

                    when (dir) {
                        CurlDirection.FORWARD -> {
                            drawCurlTurn(
                                topBitmap = bitmap,
                                bottomBitmap = forwardUnderneath,
                                draw = draw,
                                progress = curlProgress,
                                isPeelingFromRight = !rtl,
                                verts = verts,
                                meshPaint = meshPaint,
                            )
                        }
                        CurlDirection.BACKWARD -> {
                            val unrollingPage = backwardUnderneath
                            if (unrollingPage != null) {
                                drawCurlTurn(
                                    topBitmap = unrollingPage,
                                    bottomBitmap = bitmap,
                                    draw = draw,
                                    progress = 1f - curlProgress,
                                    isPeelingFromRight = !rtl,
                                    verts = verts,
                                    meshPaint = meshPaint,
                                )
                            } else {
                                drawIntoCanvas { canvas ->
                                    val src = Rect(0, 0, bitmap.width, bitmap.height)
                                    val dst = Rect(
                                        draw.left.roundToInt(),
                                        draw.top.roundToInt(),
                                        (draw.left + draw.scaledWidth).roundToInt(),
                                        (draw.top + draw.scaledHeight).roundToInt(),
                                    )
                                    canvas.nativeCanvas.drawBitmap(bitmap, src, dst, meshPaint)
                                }
                            }
                        }
                    }
                } else {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val left = cx + (draw.left - cx) * userScale + userPanX
                    val top = cy + (draw.top - cy) * userScale + userPanY
                    val w = draw.scaledWidth * userScale
                    val h = draw.scaledHeight * userScale

                    drawImage(
                        image = image,
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Clamps pan along one axis when content is larger than the container: pans only until an edge
 * meets the container edge; when it fits within the container along that axis, stays locked at 0.
 */
private fun clampPan(pan: Float, base: Float, size: Float, container: Float, padding: Float = 0f): Float {
    if (size <= container) return 0f
    val minPan = container - padding - size - base
    val maxPan = padding - base
    return pan.coerceIn(minOf(minPan, maxPan), maxOf(minPan, maxPan))
}

/** Mode control that toggles between fit-to-page and fit-to-width. */
@Composable
private fun FitModeChip(fitMode: PageFitMode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Cream.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = if (fitMode == PageFitMode.FIT_WIDTH) Icons.Default.FitScreen else Icons.Default.AspectRatio,
            contentDescription = "Switch fit mode",
            tint = Cream,
            modifier = Modifier.size(15.dp),
        )
        Text(
            if (fitMode == PageFitMode.FIT_WIDTH) "WIDTH" else "PAGE",
            fontFamily = Archivo,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = Cream,
        )
    }
}

/** A single tap waits this long for a possible second tap before acting, enabling double-tap. */
private const val DOUBLE_TAP_WINDOW_MS = 220L

/** Reading-direction control that shows its current state (LTR/RTL) so its purpose is obvious. */
@Composable
private fun DirectionChip(rightToLeft: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Cream.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Cream, modifier = Modifier.size(15.dp))
        Text(
            if (rightToLeft) "RTL" else "LTR",
            fontFamily = Archivo,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = Cream,
        )
    }
}

/** Quick control for the current orientation's persisted layout preference. */
@Composable
private fun SpreadChip(spreadEnabled: Boolean, orientation: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Cream.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = "$orientation layout: ${if (spreadEnabled) "Two-page spread" else "Single page"}. Toggle layout",
            tint = Cream,
            modifier = Modifier.size(15.dp),
        )
        Text(
            if (spreadEnabled) "SPREAD" else "SINGLE",
            fontFamily = Archivo,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = Cream,
        )
    }
}

private fun pageStatus(state: ReaderUiState): String {
    val page = if (state.isSpread) {
        "Pages ${state.pageIndex + 1}-${state.pageIndex + 2}/${state.pageCount}"
    } else {
        "Page ${state.pageIndex + 1}/${state.pageCount}"
    }
    if (net.digitalbc.leafreader.ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) return page
    val panel = state.panelLabel
    val panelText = when {
        state.detecting -> " · detecting…"
        panel != null -> " · panel $panel/${state.panels.size}"
        else -> " · full page"
    }
    return page + panelText
}
