package app.utillock.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.utillock.android.ui.theme.UtilLockGradients

/** Circular badge with a gradient fill — used to give every icon a bit of "premium glow". */
@Composable
fun GlowIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    brush: Brush = UtilLockGradients.primaryButton,
    size: Dp = 46.dp,
    iconTint: Color = Color.White,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .then(
                if (contentDescription == null) Modifier else Modifier.semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
            )
            .size(size)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Gradient-filled primary button — the app's signature call-to-action look. */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    brush: Brush = UtilLockGradients.primaryButton,
    height: Dp = 58.dp,
    contentColor: Color = Color.White,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(brush)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = contentColor, strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = alpha))
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text,
                color = contentColor.copy(alpha = alpha),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Small rounded status indicator — a colored dot plus label, e.g. ACTIVE / READY. */
@Composable
fun StatusPill(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "statusColor",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** A card with a subtle gradient background and soft border — the base of every "premium" surface. */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    brush: Brush = UtilLockGradients.heroSoft,
    shape: Shape = MaterialTheme.shapes.large,
    borderColor: Color = Color.White.copy(alpha = 0.06f),
    content: ColumnScopeBox,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, borderColor, shape),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

private typealias ColumnScopeBox = @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit

/** Section header used above lists: a bold title, optional subtitle, optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** Simple centered empty state for lists with nothing in them yet. */
@Composable
fun EmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A pill-style segmented control — replaces plain AssistChip rows for duration pickers. */
@Composable
fun SegmentedControl(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val background by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "segmentBg",
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "segmentFg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = content, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A clickable row used for permission / capability toggles: icon badge, title+desc, trailing state. */
@Composable
fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIconBadge(
                icon = icon,
                brush = if (enabled) UtilLockGradients.successGlow else UtilLockGradients.primaryButton,
                size = 42.dp,
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.size(8.dp))
            if (enabled) {
                StatusPill("OK", active = true)
            } else {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A ring that fills clockwise as [progress] (0f..1f) increases, with [center] content overlaid.
 * Used on the dashboard hero card to visualize the remaining quick-block time.
 */
@Composable
fun CountdownRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = Color.White.copy(alpha = 0.14f),
    progressBrushColors: List<Color> = listOf(Color.White, Color.White.copy(alpha = 0.6f)),
    center: @Composable () -> Unit,
) {
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(500), label = "ringProgress")
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(diameter)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                brush = Brush.sweepGradient(progressBrushColors),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        center()
    }
}

@Composable
fun PillIndicatorSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
