package com.studio3dpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.studio3dpro.engine.math.Vec3
import kotlin.math.roundToInt

/**
 * A precise HSV picker. Sliders are used instead of a free-form canvas so the
 * control stays usable with one hand and with accessibility services; the
 * swatch, hex field and live PBR preview all update together.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Vec3,
    onDismiss: () -> Unit,
    onPick: (Vec3) -> Unit
) {
    val hsv = remember {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (initial.x * 255).roundToInt().coerceIn(0, 255),
                (initial.y * 255).roundToInt().coerceIn(0, 255),
                (initial.z * 255).roundToInt().coerceIn(0, 255)
            ),
            out
        )
        out
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hexText by remember { mutableStateOf(hexOf(initial)) }
    val color = Color.hsv(hue, saturation, value)
    val current = Vec3(color.red, color.green, color.blue)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, StudioColors.Border, RoundedCornerShape(22.dp)),
            color = StudioColors.SurfaceRaised,
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                        Spacer(Modifier.height(3.dp))
                        Text("Spectral mixer", style = MaterialTheme.typography.titleLarge, color = StudioColors.TextPrimary)
                    }
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    )
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.hsv(0f, saturation, value),
                                    Color.hsv(60f, saturation, value),
                                    Color.hsv(120f, saturation, value),
                                    Color.hsv(180f, saturation, value),
                                    Color.hsv(240f, saturation, value),
                                    Color.hsv(300f, saturation, value),
                                    Color.hsv(360f, saturation, value)
                                )
                            )
                        )
                        .border(1.dp, StudioColors.Border, RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.height(10.dp))
                SliderField(
                    "Hue", hue, 0f..360f,
                    valueLabel = "${hue.roundToInt()}°",
                    onValueChange = {
                        hue = it
                        hexText = hexOf(Color.hsv(hue, saturation, value).let { c -> Vec3(c.red, c.green, c.blue) })
                    }
                )
                SliderField(
                    "Saturation", saturation, 0f..1f,
                    valueLabel = "${(saturation * 100).roundToInt()}%",
                    onValueChange = {
                        saturation = it
                        hexText = hexOf(Color.hsv(hue, saturation, value).let { c -> Vec3(c.red, c.green, c.blue) })
                    }
                )
                SliderField(
                    "Brightness", value, 0f..1f,
                    valueLabel = "${(value * 100).roundToInt()}%",
                    onValueChange = {
                        value = it
                        hexText = hexOf(Color.hsv(hue, saturation, value).let { c -> Vec3(c.red, c.green, c.blue) })
                    }
                )
                Spacer(Modifier.height(10.dp))
                Text("HEX", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                Spacer(Modifier.height(5.dp))
                StudioTextField(hexText, { text ->
                    hexText = text
                    vec3OfHex(text)?.let { picked ->
                        val out = FloatArray(3)
                        android.graphics.Color.colorToHSV(
                            android.graphics.Color.rgb(
                                (picked.x * 255).roundToInt(),
                                (picked.y * 255).roundToInt(),
                                (picked.z * 255).roundToInt()
                            ),
                            out
                        )
                        hue = out[0]
                        saturation = out[1]
                        value = out[2]
                    }
                }, placeholder = "#RRGGBB")
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioGhostButton("Cancel", Modifier.weight(1f), onClick = onDismiss)
                    StudioPrimaryButton("Apply color", Modifier.weight(1f), onClick = {
                        onPick(vec3OfHex(hexText) ?: current)
                    })
                }
            }
        }
    }
}
