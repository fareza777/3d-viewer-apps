package com.studio3dpro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio3dpro.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.roundToInt

val CardShape = RoundedCornerShape(16.dp)
val FieldShape = RoundedCornerShape(9.dp)
val PillShape = RoundedCornerShape(50)

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = StudioColors.BorderSoft) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun StudioPanel(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(CardShape)
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.BorderSoft, CardShape)
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = StudioColors.TextMuted
            )
            if (caption != null) {
                Spacer(Modifier.height(3.dp))
                Text(caption, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun StudioPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val onAccent = if (StudioColors.isLight) Color.White else Color(0xFF191127)
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (enabled) Brush.linearGradient(listOf(StudioColors.Accent, StudioColors.AccentDeep))
                else Brush.linearGradient(listOf(StudioColors.SurfaceHigh, StudioColors.SurfaceHigh))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) onAccent else StudioColors.TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = if (enabled) onAccent else StudioColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StudioGhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color = StudioColors.TextSecondary,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StudioColors.SurfaceRaised)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StudioIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color = if (active) StudioColors.Accent else StudioColors.TextSecondary,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) StudioColors.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (active) Modifier.border(1.dp, StudioColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else StudioColors.TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false
) {
    Row(
        modifier
            .clip(PillShape)
            .background(if (filled) color.copy(alpha = 0.14f) else StudioColors.SurfaceRaised.copy(alpha = 0.9f))
            .border(1.dp, color.copy(alpha = 0.22f), PillShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(5.dp))
        } else {
            Box(Modifier.size(5.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
fun LabeledValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = StudioColors.TextPrimary) {
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted, modifier = Modifier.weight(1f))
        Text(value, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = valueColor, maxLines = 1)
    }
}

@Composable
fun ValueField(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onEditStart: () -> Unit = {},
    onEditEnd: () -> Unit = {},
    decimals: Int = 2,
    prefix: String? = null,
    prefixColor: Color = StudioColors.TextMuted,
    suffix: String? = null
) {
    fun fmt(v: Float): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val rounded = if (abs(v - v.roundToInt()) < 0.0005f) v.roundToInt().toString()
        else String.format(java.util.Locale.US, "%.${decimals}f", v)
        return rounded
    }
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(fmt(value)) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(value, focused) {
        if (!focused) {
            val next = fmt(value)
            if (next != text) text = next
        }
    }
    Row(
        modifier
            .height(34.dp)
            .clip(FieldShape)
            .background(StudioColors.Base)
            .border(1.dp, if (focused) StudioColors.Accent.copy(alpha = 0.55f) else StudioColors.Border, FieldShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prefix != null) {
            Text(prefix, style = TextStyle(fontFamily = BodyFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold), color = prefixColor)
            Spacer(Modifier.width(5.dp))
        }
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val clean = raw.filter { it.isDigit() || it == '-' || it == '.' }.take(9)
                if (clean != text) {
                    text = clean
                    clean.toFloatOrNull()?.let(onValueChange)
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = BodyFamily, fontSize = 13.sp,
                color = StudioColors.TextPrimary, textAlign = TextAlign.Start
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { state ->
                    if (state.isFocused && !focused) {
                        focused = true
                        onEditStart()
                    } else if (!state.isFocused && focused) {
                        focused = false
                        text.toFloatOrNull()?.let(onValueChange)
                        onEditEnd()
                    }
                }
        )
        if (suffix != null) {
            Text(suffix, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
        }
    }
}

@Composable
fun StudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(value, focused) {
        if (!focused && text != value) text = value
    }
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(FieldShape)
            .background(StudioColors.Base)
            .border(1.dp, if (focused) StudioColors.Accent.copy(alpha = 0.5f) else StudioColors.Border, FieldShape)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        if (text.isEmpty()) {
            Text(placeholder, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
        }
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = TextStyle(
                fontFamily = BodyFamily,
                fontSize = 13.sp,
                color = StudioColors.TextPrimary
            ),
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )
    }
}

@Composable
fun VectorField(
    value: Vec3,
    onValueChange: (Vec3) -> Unit,
    modifier: Modifier = Modifier,
    onEditStart: () -> Unit = {},
    onEditEnd: () -> Unit = {},
    suffix: String? = null
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ValueField(value.x, { onValueChange(Vec3(it, value.y, value.z)) }, Modifier.weight(1f), onEditStart, onEditEnd, prefix = "X", prefixColor = StudioColors.AxisX, suffix = suffix)
        ValueField(value.y, { onValueChange(Vec3(value.x, it, value.z)) }, Modifier.weight(1f), onEditStart, onEditEnd, prefix = "Y", prefixColor = StudioColors.AxisY, suffix = suffix)
        ValueField(value.z, { onValueChange(Vec3(value.x, value.y, it)) }, Modifier.weight(1f), onEditStart, onEditEnd, prefix = "Z", prefixColor = StudioColors.AxisZ, suffix = suffix)
    }
}

@Composable
fun SliderField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueLabel: String = String.format(java.util.Locale.US, "%.2f", value),
    onValueChangeFinished: () -> Unit = {},
    onValueChange: (Float) -> Unit
) {
    Column(modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary, modifier = Modifier.weight(1f))
            Text(valueLabel, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = StudioColors.TextPrimary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = StudioColors.Accent,
                activeTrackColor = StudioColors.Accent,
                inactiveTrackColor = StudioColors.SurfaceHigh,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = StudioColors.TextPrimary)
            if (subtitle != null) Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (StudioColors.isLight) Color.White else Color(0xFF161020),
                checkedTrackColor = StudioColors.Accent,
                uncheckedThumbColor = StudioColors.TextMuted,
                uncheckedTrackColor = StudioColors.SurfaceHigh,
                uncheckedBorderColor = StudioColors.Border
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun SegmentedOptions(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    accent: Color = StudioColors.Accent,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(StudioColors.Base)
            .border(1.dp, StudioColors.BorderSoft, RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(27.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .then(if (active) Modifier.border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp)) else Modifier)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = if (active) accent else StudioColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StudioDropdown(
    selected: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(FieldShape)
                .background(StudioColors.Base)
                .border(1.dp, StudioColors.Border, FieldShape)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selected,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = if (enabled) StudioColors.TextPrimary else StudioColors.TextMuted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = StudioColors.TextMuted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(10.dp))
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = if (option == selected) StudioColors.Accent else StudioColors.TextPrimary
                        )
                    },
                    leadingIcon = if (option == selected) {
                        { Icon(Icons.Rounded.Check, null, tint = StudioColors.Accent, modifier = Modifier.size(14.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelect(index)
                    }
                )
            }
        }
    }
}

@Composable
fun ColorSwatch(color: Vec3, modifier: Modifier = Modifier, selected: Boolean = false) {
    val c = Color(color.x.coerceIn(0f, 1f), color.y.coerceIn(0f, 1f), color.z.coerceIn(0f, 1f))
    Box(
        modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(c)
            .border(if (selected) 2.dp else 1.dp, if (selected) StudioColors.TextPrimary else StudioColors.Border, RoundedCornerShape(6.dp))
    )
}

fun hexOf(v: Vec3): String = String.format(
    java.util.Locale.US, "#%02X%02X%02X",
    (v.x * 255).roundToInt().coerceIn(0, 255),
    (v.y * 255).roundToInt().coerceIn(0, 255),
    (v.z * 255).roundToInt().coerceIn(0, 255)
)

fun vec3OfHex(hex: String): Vec3? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    val value = clean.toLongOrNull(16) ?: return null
    return Vec3(
        ((value shr 16) and 0xFF) / 255f,
        ((value shr 8) and 0xFF) / 255f,
        (value and 0xFF) / 255f
    )
}

@Composable
fun ColorField(
    color: Vec3,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(FieldShape)
            .background(StudioColors.Base)
            .border(1.dp, StudioColors.Border, FieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorSwatch(color)
        Spacer(Modifier.width(10.dp))
        Text(hexOf(color), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = StudioColors.TextPrimary, modifier = Modifier.weight(1f))
        Text("EDIT", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = StudioColors.Accent)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = StudioColors.TextMuted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = StudioColors.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

@Composable
fun TinyBadge(text: String, color: Color = StudioColors.Mint, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text.uppercase(), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = color, fontSize = 9.5.sp)
    }
}

/** Brand mark used in the top bar, settings, onboarding and loading screen. */
@Composable
fun StudioMark(size: androidx.compose.ui.unit.Dp = 34.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.linearGradient(listOf(Color(0xFF221A2E), Color(0xFF0B0C10))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(size * 0.26f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size * 0.78f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val top = Offset(cx, h * 0.06f)
            val right = Offset(w * 0.94f, h * 0.29f)
            val left = Offset(w * 0.06f, h * 0.29f)
            val middle = Offset(cx, h * 0.52f)
            val bottomRight = Offset(w * 0.94f, h * 0.75f)
            val bottomLeft = Offset(w * 0.06f, h * 0.75f)
            val bottom = Offset(cx, h * 0.98f)
            val brass = Brush.linearGradient(
                listOf(Color(0xFFFFD08A), Color(0xFFF0A850), Color(0xFFC97F30)),
                start = left,
                end = right
            )
            drawPath(
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(middle.x, middle.y); lineTo(left.x, left.y); close()
                },
                brass
            )
            drawPath(
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(left.x, left.y); lineTo(middle.x, middle.y); lineTo(bottom.x, bottom.y); lineTo(bottomLeft.x, bottomLeft.y); close()
                },
                Color(0xFF20232C)
            )
            drawPath(
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(middle.x, middle.y); lineTo(right.x, right.y); lineTo(bottomRight.x, bottomRight.y); lineTo(bottom.x, bottom.y); close()
                },
                Color(0xFF15181F)
            )
            drawLine(
                Color(0xFFFFE3BE),
                top,
                right,
                strokeWidth = w * 0.045f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                Color(0xFFFFE3BE),
                top,
                left,
                strokeWidth = w * 0.045f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                Color(0x88FFDCAC),
                left,
                middle,
                strokeWidth = w * 0.032f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                Color(0x88FFDCAC),
                right,
                middle,
                strokeWidth = w * 0.032f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(Color(0xFF3B4050), middle, bottom, strokeWidth = w * 0.035f)
            drawLine(Color(0xAA454B5C), bottomLeft, bottom, strokeWidth = w * 0.035f)
            drawLine(Color(0xAA454B5C), bottomRight, bottom, strokeWidth = w * 0.035f)
            val gem = androidx.compose.ui.graphics.Path().apply {
                moveTo(middle.x, middle.y - h * 0.11f)
                lineTo(middle.x + w * 0.095f, middle.y)
                lineTo(middle.x, middle.y + h * 0.11f)
                lineTo(middle.x - w * 0.095f, middle.y)
                close()
            }
            drawPath(gem, Color(0xFF0D0F14))
            val inner = androidx.compose.ui.graphics.Path().apply {
                moveTo(middle.x, middle.y - h * 0.075f)
                lineTo(middle.x + w * 0.065f, middle.y)
                lineTo(middle.x, middle.y + h * 0.075f)
                lineTo(middle.x - w * 0.065f, middle.y)
                close()
            }
            drawPath(
                inner,
                Brush.linearGradient(
                    listOf(Color(0xFFB6FFF0), Color(0xFF38D9B0)),
                    start = Offset(middle.x, middle.y - h * 0.08f),
                    end = Offset(middle.x, middle.y + h * 0.08f)
                )
            )
        }
    }
}

@Composable
fun IconLabelRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = StudioColors.TextSecondary,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = tint, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun DisabledHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = StudioColors.TextMuted,
        modifier = modifier.alpha(0.8f).padding(top = 4.dp)
    )
}
