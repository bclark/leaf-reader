package net.digitalbc.leafreader.ui.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
    val isCompact = !isLandscape && configuration.screenWidthDp < 600
    val spreadActive = resolveSpreadLayout(isLandscape, portraitSpread, landscapeSpread)
    LaunchedEffect(spreadActive) {
        viewModel.setSpreadEnabled(spreadActive)
    }

    val pageCount = state.pageCount.coerceAtLeast(1)
    val pageIndex = state.pageIndex.coerceIn(0, pageCount - 1)
    val rtl = state.rightToLeft
    val isSpread = state.isSpread && state.secondPage != null
    val scope = rememberCoroutineScope()

    val adjacentPages = state.adjacentPages
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
        if (canTurnForward && curlDirection == null && !state.preparingSpread) {
            if (!canAnimateTurn(CurlDirection.FORWARD)) viewModel.nextPage()
            else {
                curlDirection = CurlDirection.FORWARD
                settleTurn(CurlDirection.FORWARD, 0f, true)
            }
        }
    }
    val turnBackward: () -> Unit = {
        if (canTurnBackward && curlDirection == null && !state.preparingSpread) {
            if (!canAnimateTurn(CurlDirection.BACKWARD)) viewModel.previousPage()
            else {
                curlDirection = CurlDirection.BACKWARD
                settleTurn(CurlDirection.BACKWARD, 0f, true)
            }
        }
    }

    // Go fully immersive (hide the status/nav bars, incl. the battery/clock icons) while reading;
    // bring the bars back with the chrome, and restore them when leaving the reader.
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    val lightNavigationBar = MaterialTheme.colorScheme.background.luminance() > 0.5f
    LaunchedEffect(chromeVisible, window) {
        window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightNavigationBars = false
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(window) {
        onDispose {
            window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightNavigationBars = lightNavigationBar
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.error != null -> ErrorView(state.error!!, viewModel::retry, onBack)
            state.page == null -> LoadingView(onBack)
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
                onDismiss = onBack,
            )
        }

        if ((state.preparingSpread || state.loading && state.page != null) && state.error == null) {
            Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), color = MaterialTheme.colorScheme.surface) {
                Text(if (state.preparingSpread) "Preparing two-page layout…" else "Opening page…", modifier = Modifier.padding(12.dp))
            }
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
            Column(
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
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 18.dp),
            ) {
                if (isCompact) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
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
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReaderActionChips(
                            spreadActive = spreadActive,
                            isLandscape = isLandscape,
                            onToggleLandscapeSpread = onToggleLandscapeSpread,
                            onTogglePortraitSpread = onTogglePortraitSpread,
                            fitMode = fitMode,
                            onSetFitMode = onSetFitMode,
                            rightToLeft = state.rightToLeft,
                            onToggleReadingDirection = viewModel::toggleReadingDirection,
                            showFitMode = false,
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
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
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        ReaderActionChips(
                            spreadActive = spreadActive,
                            isLandscape = isLandscape,
                            onToggleLandscapeSpread = onToggleLandscapeSpread,
                            onTogglePortraitSpread = onTogglePortraitSpread,
                            fitMode = fitMode,
                            onSetFitMode = onSetFitMode,
                            rightToLeft = state.rightToLeft,
                            onToggleReadingDirection = viewModel::toggleReadingDirection,
                        )
                    }
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



/** Single-page cylindrical bend: unmodified artwork on both faces, with a soft cast shadow. */
private fun DrawScope.drawCurlTurn(
    topBitmap: Bitmap,
    bottomBitmap: Bitmap?,
    draw: PageDraw,
    bottomDraw: PageDraw?,
    progress: Float,
    isPeelingFromRight: Boolean,
    vertsFront: FloatArray,
    vertsBack: FloatArray,
    meshPaint: Paint,
) {
    if (topBitmap.isRecycled) return
    val t = progress.coerceIn(0f, 1f)
    val w = draw.scaledWidth
    val h = draw.scaledHeight
    val left = draw.left
    val top = draw.top
    if (w <= 0f || h <= 0f) return

    if (bottomBitmap != null && !bottomBitmap.isRecycled && bottomDraw != null) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                bottomBitmap, Rect(0, 0, bottomBitmap.width, bottomBitmap.height),
                android.graphics.RectF(bottomDraw.left, bottomDraw.top,
                    bottomDraw.left + bottomDraw.scaledWidth, bottomDraw.top + bottomDraw.scaledHeight),
                meshPaint,
            )
        }
    }

    val apex = left + computeSinglePageCurlX(
        if (isPeelingFromRight) 1f else 0f, w, t, isPeelingFromRight, back = false,
    )
    val shadowWidth = (w * 0.06f).coerceAtLeast(4f)
    val shadowAlpha = (0.20f * kotlin.math.sin(t * kotlin.math.PI.toFloat())).coerceAtLeast(0f)
    val shadowStart = if (isPeelingFromRight) apex else (apex - shadowWidth).coerceAtLeast(left)
    val shadowEnd = if (isPeelingFromRight) (apex + shadowWidth).coerceAtMost(left + w) else apex
    if (shadowAlpha > 0.02f && shadowEnd > shadowStart) {
        val dark = Color.Black.copy(alpha = shadowAlpha)
        val mid = Color.Black.copy(alpha = shadowAlpha * 0.45f)
        drawRect(
            brush = if (isPeelingFromRight) Brush.horizontalGradient(
                0f to dark, 0.4f to mid, 1f to Color.Transparent,
                startX = shadowStart, endX = shadowEnd,
            ) else Brush.horizontalGradient(
                0f to Color.Transparent, 0.6f to mid, 1f to dark,
                startX = shadowStart, endX = shadowEnd,
            ),
            topLeft = Offset(shadowStart, top),
            size = Size(shadowEnd - shadowStart, h),
        )
    }

    // Collapse each hidden face to the cylinder horizon to avoid overlapping artwork.
    fun drawFace(back: Boolean, vertices: FloatArray) {
        var i = 0
        for (row in 0..MESH_ROWS) {
            for (col in 0..MESH_COLS) {
                vertices[i++] = left + computeSinglePageCurlX(
                    col.toFloat() / MESH_COLS, w, t, isPeelingFromRight, back,
                )
                vertices[i++] = top + row.toFloat() / MESH_ROWS * h
            }
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(
                topBitmap, MESH_COLS, MESH_ROWS, vertices, 0, null, 0, meshPaint,
            )
        }
    }
    drawFace(back = false, vertsFront)
    drawFace(back = true, vertsBack)
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
    onDismiss: () -> Unit,
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

    var dismissOffset by remember { mutableStateOf(Offset.Zero) }
    var dismissReturnJob by remember { mutableStateOf<Job?>(null) }
    var dismissCommitted by remember { mutableStateOf(false) }

    val returnDismissedPage: () -> Unit = {
        dismissReturnJob?.cancel()
        val from = dismissOffset
        dismissReturnJob = scope.launch {
            animate(1f, 0f, animationSpec = spring(
                dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow,
            )) { fraction, _ ->
                dismissOffset = from * fraction
            }
            dismissOffset = Offset.Zero
        }
    }

    val meshPaint = remember { Paint().apply { isFilterBitmap = true } }
    val vertsFront = remember { FloatArray(MESH_VERT_COUNT) }
    val vertsBack = remember { FloatArray(MESH_VERT_COUNT) }

    val viewport = androidx.compose.ui.platform.LocalConfiguration.current
    val currentCurlDirection by rememberUpdatedState(curlDirection)
    val currentCanAnimateTurn by rememberUpdatedState(canAnimateTurn)
    val currentSettleTurn by rememberUpdatedState(onSettleTurn)
    val currentDismiss by rememberUpdatedState(onDismiss)
    var pendingTap by remember { mutableStateOf<Job?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                pendingTap?.cancel()
                dismissReturnJob?.cancel()
                dismissOffset = Offset.Zero
                dismissCommitted = false
            }
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
                        if (currentCurlDirection != null || dismissCommitted) {
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
                        var isDismissDrag = false
                        var dismissCandidate = false
                        var dismissStart = Offset.Zero
                        var dismissOrigin = Offset.Zero
                        val dismissEngageDistance = ReaderDismissGesture.ENGAGE_DISTANCE_DP.dp.toPx()
                        val containerHeight = size.height.toFloat()
                        val contentHeight = if (isSpread) {
                            computeSpreadDraw(leftBitmap.width, leftBitmap.height,
                                rightBitmap.width, rightBitmap.height, size.width.toFloat(), containerHeight,
                                fitMode = fitMode).totalHeight
                        } else {
                            computePageDraw(camera.value, image.width, image.height,
                                size.width.toFloat(), containerHeight, fitMode = fitMode).scaledHeight
                        }
                        // Tall fit-to-width pages must remain scrollable; zoomed content keeps panning.
                        val canDismiss = userScale <= 1.05f && contentHeight * userScale <= containerHeight + 1f
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
                                if (maxPointers == 1 && canDismiss && ReaderDismissGesture.isDownward(pan.x, pan.y)) {
                                    dismissCandidate = true
                                } else if (maxPointers == 1 && userScale <= 1.05f && abs(pan.x) > abs(pan.y)) {
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

                            if (dismissCandidate && maxPointers == 1 && !isDismissDrag &&
                                pan.y >= dismissEngageDistance && ReaderDismissGesture.isDownward(pan.x, pan.y)) {
                                dismissReturnJob?.cancel()
                                dismissOrigin = dismissOffset
                                // The first part of the swipe establishes intent without moving the artwork.
                                dismissStart = pan * (dismissEngageDistance / pan.y)
                                isDismissDrag = true
                            }
                            if (maxPointers > 1) dismissCandidate = false
                            if (maxPointers > 1 && isDismissDrag) {
                                isDismissDrag = false
                                returnDismissedPage()
                            }
                            if (isCurlDrag && maxPointers > 1) {
                                onUpdateCurl(null, 0f)
                                isCurlDrag = false
                                activeDirection = null
                            }
                            if (pastSlop) {
                                pendingTap?.cancel()
                                if (isDismissDrag) {
                                    val dragged = dismissOrigin + (pan - dismissStart) * ReaderDismissGesture.DRAG_RESISTANCE
                                    dismissOffset = Offset(dragged.x, dragged.y.coerceAtLeast(0f))
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (isCurlDrag && activeDirection != null) {
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

                        if (dismissCommitted) return@awaitEachGesture
                        if (cancelled) {
                            if (dismissOffset != Offset.Zero) returnDismissedPage()
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
                        } else if (isDismissDrag) {
                            val velocity = velocityTracker.calculateVelocity()
                            if (ReaderDismissGesture.shouldDismiss(dismissOffset.y, containerHeight, velocity.y)) {
                                dismissCommitted = true
                                val from = dismissOffset
                                // Continue along the release direction, including a diagonal flick.
                                val slope = if (velocity.y > 0f) velocity.x / velocity.y
                                    else from.x / from.y.coerceAtLeast(1f)
                                val travelY = containerHeight * 1.3f
                                val target = from + Offset(slope.coerceIn(-2f, 2f) * travelY, travelY)
                                dismissReturnJob = scope.launch {
                                    animate(0f, 1f, animationSpec = tween(280, easing = LinearOutSlowInEasing)) { t, _ ->
                                        dismissOffset = from + (target - from) * t
                                    }
                                    currentDismiss()
                                }
                            } else {
                                returnDismissedPage()
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
                    dismissReturnJob?.cancel()
                    if (!dismissCommitted) dismissOffset = Offset.Zero
                    onUpdateCurl(null, 0f)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer {
            // Transform only the artwork: gesture coordinates and reader controls stay stable.
            val progress = ReaderDismissGesture.progress(dismissOffset.y, size.height)
            translationX = dismissOffset.x
            translationY = dismissOffset.y
            rotationZ = (dismissOffset.x / size.width.coerceAtLeast(1f) * 14f).coerceIn(-10f, 10f)
            scaleX = 1f - progress * 0.08f
            scaleY = scaleX
        }) {
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
                                bottomDraw = forwardUnderneath?.let {
                                    computePageDraw(Panel.FULL_PAGE, it.width, it.height,
                                        size.width, size.height, fitMode = fitMode)
                                },
                                progress = curlProgress,
                                isPeelingFromRight = !rtl,
                                vertsFront = vertsFront,
                                vertsBack = vertsBack,
                                meshPaint = meshPaint,
                            )
                        }
                        CurlDirection.BACKWARD -> {
                            val unrollingPage = backwardUnderneath
                            if (unrollingPage != null) {
                                drawCurlTurn(
                                    topBitmap = unrollingPage,
                                    bottomBitmap = bitmap,
                                    draw = computePageDraw(Panel.FULL_PAGE, unrollingPage.width, unrollingPage.height,
                                        size.width, size.height, fitMode = fitMode),
                                    bottomDraw = draw,
                                    progress = 1f - curlProgress,
                                    isPeelingFromRight = !rtl,
                                    vertsFront = vertsFront,
                                    vertsBack = vertsBack,
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
private fun LoadingView(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text("Opening comic…", modifier = Modifier.padding(16.dp))
        androidx.compose.material3.TextButton(onClick = onBack) { Text("Go back") }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        Row {
            androidx.compose.material3.TextButton(onClick = onRetry) { Text("Retry") }
            androidx.compose.material3.TextButton(onClick = onBack) { Text("Go back") }
        }
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

@Composable
private fun ReaderActionChips(
    spreadActive: Boolean,
    isLandscape: Boolean,
    onToggleLandscapeSpread: () -> Unit,
    onTogglePortraitSpread: () -> Unit,
    fitMode: PageFitMode,
    onSetFitMode: (PageFitMode) -> Unit,
    rightToLeft: Boolean,
    onToggleReadingDirection: () -> Unit,
    modifier: Modifier = Modifier,
    showFitMode: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpreadChip(
            spreadEnabled = spreadActive,
            orientation = if (isLandscape) "Landscape" else "Portrait",
            onClick = if (isLandscape) onToggleLandscapeSpread else onTogglePortraitSpread,
        )
        if (showFitMode) {
            FitModeChip(
                fitMode = fitMode,
                onClick = {
                    onSetFitMode(
                        if (fitMode == PageFitMode.FIT_PAGE) PageFitMode.FIT_WIDTH else PageFitMode.FIT_PAGE,
                    )
                },
            )
        }
        DirectionChip(rightToLeft = rightToLeft, onClick = onToggleReadingDirection)
    }
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
