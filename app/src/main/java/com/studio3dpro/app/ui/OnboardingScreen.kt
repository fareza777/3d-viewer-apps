package com.studio3dpro.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio3dpro.app.StudioHaptics
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private data class OnboardPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val chips: List<String>
)

private val pages = listOf(
    OnboardPage(
        eyebrow = "MODEL · SCULPT",
        title = "A full 3D studio\nin your hand.",
        body = "Block out a character, refine the silhouette and paint PBR materials without a desktop round-trip.",
        chips = listOf("Vertex · Edge · Face", "Extrude · Bevel · Boolean", "PBR materials")
    ),
    OnboardPage(
        eyebrow = "RIG · ANIMATE",
        title = "Rig with your thumbs.",
        body = "A 24-bone humanoid with automatic skinning, weight painting, IK/FK, constraints and a pose library.",
        chips = listOf("Auto-rig", "Weight paint", "IK / FK")
    ),
    OnboardPage(
        eyebrow = "EXPORT · SHIP",
        title = "Straight into\nyour game engine.",
        body = "One tap writes a skinned GLB with every clip — plus OBJ, STL and BVH for the rest of your pipeline.",
        chips = listOf("Skinned GLB", "Unity-ready clips", "OBJ · STL · BVH")
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onExploreDemo: () -> Unit,
    onStartEmpty: () -> Unit,
    onSkip: () -> Unit
) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == pages.lastIndex
    val onAccent = if (StudioColors.isLight) Color.White else Color(0xFF161020)

    Column(
        Modifier
            .fillMaxSize()
            .background(StudioColors.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioMark(28.dp)
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    "3D STUDIO PRO",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.Accent
                )
                Text(
                    "Model · Rig · Animate",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextSecondary
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "SKIP",
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable {
                        StudioHaptics.tick()
                        onSkip()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) { index ->
            val page = pages[index]
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                OnboardingStage(index, Modifier.fillMaxWidth().weight(0.56f).padding(top = 14.dp))
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(18.dp).height(1.dp).background(StudioColors.Accent))
                    Spacer(Modifier.width(8.dp))
                    Text(page.eyebrow, style = MaterialTheme.typography.labelSmall, color = StudioColors.Accent)
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    page.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = StudioColors.TextPrimary,
                    lineHeight = 32.sp
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = StudioColors.TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    page.chips.forEach { chip ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioColors.SurfaceRaised)
                                .border(1.dp, StudioColors.BorderSoft, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = StudioColors.Mint, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(chip, style = MaterialTheme.typography.labelSmall, color = StudioColors.TextSecondary)
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(pages.size) { index ->
                        val active = index == pager.currentPage
                        Box(
                            Modifier
                                .width(if (active) 24.dp else 8.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(if (active) StudioColors.Accent else StudioColors.Border)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pager.currentPage + 1} / ${pages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextMuted
                )
            }
            Spacer(Modifier.height(14.dp))
            if (!isLast) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(StudioColors.Accent, StudioColors.AccentDeep)
                            )
                        )
                        .clickable {
                            StudioHaptics.tick()
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Continue", style = MaterialTheme.typography.titleMedium, color = onAccent)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ArrowForward, null, tint = onAccent, modifier = Modifier.size(16.dp))
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(StudioColors.Accent, StudioColors.AccentDeep)
                            )
                        )
                        .clickable {
                            StudioHaptics.tick()
                            onExploreDemo()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = onAccent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Explore the ATLAS demo", style = MaterialTheme.typography.titleMedium, color = onAccent)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Start with an empty scene",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioColors.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            StudioHaptics.tick()
                            onStartEmpty()
                        }
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun OnboardingStage(index: Int, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF191426), Color(0xFF0A0B10), Color(0xFF07080B))
                )
            )
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(26.dp))
    ) {
        when (index) {
            0 -> CubeStage(Modifier.fillMaxSize())
            1 -> RigStage(Modifier.fillMaxSize())
            else -> ExportStage(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CubeStage(modifier: Modifier) {
    val motion = rememberInfiniteTransition(label = "orbit")
    val phase = motion.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Restart),
        label = "phase"
    ).value
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.66f
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0x554B2E7A), Color(0x000A0B10)),
                center = Offset(w * 0.5f, h * 0.42f),
                radius = w * 0.62f
            )
        )
        repeat(7) { i ->
            val t = i / 6f
            val y = horizon + (h - horizon) * t * t
            drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        repeat(9) { i ->
            val t = (i - 4) / 4f
            drawLine(
                Color(0x18FFFFFF),
                Offset(w * 0.5f + t * w * 0.12f, horizon),
                Offset(w * 0.5f + t * w * 0.95f, h),
                strokeWidth = 1f
            )
        }

        val cx = w * 0.5f
        val cy = h * 0.43f
        val s = w * 0.19f
        val top = Offset(cx, cy - s * 1.05f)
        val left = Offset(cx - s, cy - s * 0.48f)
        val right = Offset(cx + s, cy - s * 0.48f)
        val middle = Offset(cx, cy + s * 0.08f)
        val bottomLeft = Offset(cx - s, cy + s * 0.64f)
        val bottomRight = Offset(cx + s, cy + s * 0.64f)
        val bottom = Offset(cx, cy + s * 1.12f)

        val orbitRx = s * 1.85f
        val orbitRy = s * 0.52f
        val ox = cx + cos(phase) * orbitRx
        val oy = cy + sin(phase) * orbitRy
        drawOval(
            color = Color(0x33B9A7FF),
            topLeft = Offset(cx - orbitRx, cy - orbitRy),
            size = Size(orbitRx * 2f, orbitRy * 2f),
            style = Stroke(width = 1.4f)
        )
        drawCircle(Color(0xFFFFB574), radius = 3.4f, center = Offset(ox, oy))

        drawPath(
            Path().apply {
                moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(middle.x, middle.y); lineTo(left.x, left.y); close()
            },
            Brush.linearGradient(listOf(Color(0xFFF6CE9B), Color(0xFFE0A45F), Color(0xFFB3712D)))
        )
        drawPath(
            Path().apply {
                moveTo(left.x, left.y); lineTo(middle.x, middle.y); lineTo(bottom.x, bottom.y); lineTo(bottomLeft.x, bottomLeft.y); close()
            },
            Color(0xFF1A1D25)
        )
        drawPath(
            Path().apply {
                moveTo(middle.x, middle.y); lineTo(right.x, right.y); lineTo(bottomRight.x, bottomRight.y); lineTo(bottom.x, bottom.y); close()
            },
            Color(0xFF101319)
        )
        val outline = Path().apply {
            moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottom.x, bottom.y); lineTo(bottomLeft.x, bottomLeft.y); lineTo(left.x, left.y); close()
            moveTo(top.x, top.y); lineTo(middle.x, middle.y)
            moveTo(left.x, left.y); lineTo(middle.x, middle.y); lineTo(right.x, right.y)
        }
        drawPath(outline, Color(0xFFF3F1EC), style = Stroke(width = 2.4f, join = androidx.compose.ui.graphics.StrokeJoin.Round, cap = StrokeCap.Round))
        drawCircle(Color(0xFF8BE9D0), radius = 5.4f, center = middle)
        drawCircle(Color(0xFF07080B), radius = 8.4f, center = middle, style = Stroke(width = 2f))
        drawCircle(Color(0x558BE9D0), radius = 16f, center = middle)
    }
}

@Composable
private fun RigStage(modifier: Modifier) {
    val motion = rememberInfiniteTransition(label = "rig")
    val pulse = motion.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse"
    ).value
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0x44206A5B), Color(0x000A0B10)),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = w * 0.55f
            )
        )
        repeat(7) { i ->
            val y = h * 0.68f + i * h * 0.045f
            drawLine(Color(0x16FFFFFF), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        data class Joint(val x: Float, val y: Float)
        val cx = w * 0.5f
        val head = Joint(cx, h * 0.24f)
        val neck = Joint(cx, h * 0.32f)
        val chest = Joint(cx, h * 0.42f)
        val hips = Joint(cx, h * 0.54f)
        val lShoulder = Joint(cx - w * 0.13f, h * 0.37f)
        val lElbow = Joint(cx - w * 0.22f, h * 0.47f)
        val lHand = Joint(cx - w * 0.25f, h * 0.57f)
        val rShoulder = Joint(cx + w * 0.13f, h * 0.37f)
        val rElbow = Joint(cx + w * 0.23f, h * 0.44f)
        val rHand = Joint(cx + w * 0.27f, h * 0.34f)
        val lHip = Joint(cx - w * 0.075f, h * 0.56f)
        val lKnee = Joint(cx - w * 0.09f, h * 0.70f)
        val lFoot = Joint(cx - w * 0.10f, h * 0.84f)
        val rHip = Joint(cx + w * 0.075f, h * 0.56f)
        val rKnee = Joint(cx + w * 0.10f, h * 0.68f)
        val rFoot = Joint(cx + w * 0.12f, h * 0.83f)

        fun bone(a: Joint, b: Joint, color: Color, width: Float) {
            drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = width, cap = StrokeCap.Round)
        }
        val boneColor = Color(0xFF6F7C9A)
        // Neck joins the head ring so the rig reads as one connected skeleton.
        bone(head, neck, boneColor, 3f)
        bone(neck, chest, boneColor, 3f); bone(chest, hips, boneColor, 3f)
        bone(neck, lShoulder, boneColor, 2.4f); bone(lShoulder, lElbow, boneColor, 2.4f); bone(lElbow, lHand, boneColor, 2.4f)
        bone(neck, rShoulder, boneColor, 2.4f)
        bone(rShoulder, rElbow, Color(0xFFFFB574), 4f)
        bone(rElbow, rHand, Color(0xFFFFB574), 4f)
        bone(hips, lHip, boneColor, 2.8f); bone(lHip, lKnee, boneColor, 2.8f); bone(lKnee, lFoot, boneColor, 2.8f)
        bone(hips, rHip, boneColor, 2.8f); bone(rHip, rKnee, boneColor, 2.8f); bone(rKnee, rFoot, boneColor, 2.8f)
        bone(lFoot, Joint(lFoot.x + w * 0.035f, lFoot.y + h * 0.024f), boneColor, 2.4f)
        bone(rFoot, Joint(rFoot.x - w * 0.02f, rFoot.y + h * 0.024f), boneColor, 2.4f)

        drawCircle(Color(0x33FFB574), radius = 20f * pulse, center = Offset(rElbow.x, rElbow.y))
        listOf(head, neck, chest, hips, lShoulder, lElbow, lHand, rShoulder, rElbow, rHand, lHip, lKnee, lFoot, rHip, rKnee, rFoot).forEach { j ->
            drawCircle(Color(0xFF8BE9D0), radius = 3.2f, center = Offset(j.x, j.y))
            drawCircle(Color(0xFF07080B), radius = 5.2f, center = Offset(j.x, j.y), style = Stroke(width = 1f))
        }
        drawCircle(Color(0xFFF3F1EC), radius = h * 0.055f, center = Offset(head.x, head.y), style = Stroke(width = 2f))
        drawCircle(Color(0xFF8BE9D0), radius = 4.6f, center = Offset(hips.x, hips.y))
    }
}

@Composable
private fun ExportStage(modifier: Modifier) {
    Box(modifier.padding(18.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF2111318))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(11.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFE0A45F), Color(0xFFB3712D)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("GLB", style = MaterialTheme.typography.labelLarge, color = Color(0xFF1B1208), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ATLAS_Studio_Demo",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFF3F1EC)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "24 bones · 3 clips · 1 material",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8B8D97)
                        )
                    }
                    Icon(Icons.Rounded.Check, null, tint = Color(0xFF8BE9D0), modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Walk", "Run", "Idle").forEach { clip ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0x3327C2A3))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(clip, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8BE9D0))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Unity-ready", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B8D97))
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(1.dp).height(18.dp).background(Color(0x44FFFFFF)))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("UNITY", "UNREAL").forEach {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB9BAC3))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("OBJ", "STL", "BVH").forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6F7380),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x1AFFFFFF))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
