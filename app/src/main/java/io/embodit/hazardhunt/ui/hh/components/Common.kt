package io.embodit.hazardhunt.ui.hh.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.hazardhunt.R
import io.embodit.hazardhunt.domain.model.Category
import io.embodit.hazardhunt.ui.hh.debug.probe
import io.embodit.hazardhunt.ui.theme.HhColors

internal val PanelPadding = 16.dp

/** Glass root of a panel (design: Material.Regular root): clipped shape, glass, padding. */
@Composable
internal fun Glass(
    width: Dp,
    height: Dp,
    corner: Dp = 28.dp,
    material: Material = Material.Regular,
    padding: Dp = PanelPadding,
    spacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(corner)).backgroundMaterial(enable = true, style = material).padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}

/** Type roles of the design (`typography`): PicoTheme scale, tabular figures where numbers change. */
internal object HhType {
    val titleLarge: TextStyle
        @Composable get() = PicoTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    val titleMedium: TextStyle
        @Composable get() = PicoTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")

    val headline: TextStyle
        @Composable get() = PicoTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")

    val bodyLarge: TextStyle
        @Composable get() = PicoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)

    val bodyMedium: TextStyle
        @Composable get() = PicoTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

    val labelLarge: TextStyle
        @Composable get() = PicoTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")

    val labelMedium: TextStyle
        @Composable get() = PicoTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
}

/** An icon key (mute, more, edit, back, close, previous / next, - / +): at least 48 dp wide (taste B19). */
@Composable
internal fun IconKey(name: String, iconRes: Int, description: String, enabled: Boolean = true, width: Dp = 48.dp, height: Dp = 40.dp, onClick: () -> Unit) {
    val content = if (enabled) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelQuaternary
    IconButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.probe(name, onClick),
        colors = IconButtonDefaults.iconButtonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = content),
        size = IconButtonDefaults.iconButtonSize(width = width, height = height),
        enabled = enabled,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = description, modifier = Modifier.size(22.dp), tint = content)
    }
}

/**
 * A worded button, capsule, 48 dp high: [primary] = the light fill with ink text (the main action), otherwise the
 * tertiary fill with glass text. Text never clips: it shrinks one step before it would ellipsize.
 */
@Composable
internal fun WordButton(
    name: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 96.dp,
    height: Dp = 48.dp,
    iconRes: Int? = null,
    style: TextStyle? = null,
) {
    val container = if (primary) PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.fillTertiary
    val content =
        when {
            !enabled -> PicoTheme.colorScheme.labelQuaternary
            primary -> HhColors.PlateInk
            else -> PicoTheme.colorScheme.labelPrimary
        }
    Button(
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.55f).probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = height, minWidth = minWidth),
        shape = CircleShape,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        if (iconRes != null) {
            Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style ?: HhType.titleMedium)
    }
}

/** A small capsule label: tertiary fill, primary text (design: chips use labelPrimary, never a status colour). */
@Composable
internal fun Tag(text: String, modifier: Modifier = Modifier, iconRes: Int? = null) {
    Row(
        modifier = modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(PicoTheme.colorScheme.fillTertiary).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (iconRes != null) Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(14.dp), tint = PicoTheme.colorScheme.labelPrimary)
        Text(text = text, color = PicoTheme.colorScheme.labelPrimary, style = HhType.labelLarge, maxLines = 1, softWrap = false)
    }
}

/**
 * A category chip (design `spatialUiMap.categoryChips`): rounded 20, labelLarge; selected = interaction fill. The long
 * chips (104 dp) carry the category icon; the short ones (76 dp) are text only and drop to labelMedium for labels
 * longer than five characters ("Signs/PPE"), so four Chinese characters fit without clipping (emulator check).
 */
@Composable
internal fun CategoryChip(category: Category, label: String, selected: Boolean, width: Dp, onClick: () -> Unit) {
    val container = if (selected) PicoTheme.colorScheme.interaction else PicoTheme.colorScheme.fillTertiary
    val content = if (selected) HhColors.PlateInk else PicoTheme.colorScheme.labelPrimary
    val withIcon = width >= 100.dp
    Button(
        onClick = onClick,
        modifier = Modifier.width(width).probe("cat_${category.name.lowercase()}", onClick),
        size = ButtonDefaults.Regular.copy(height = 40.dp, minWidth = width),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        if (withIcon) {
            Icon(painter = painterResource(categoryIcon(category)), contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = label, color = content, style = if (!withIcon && label.length > 5) HhType.labelMedium else HhType.labelLarge, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}

internal fun categoryIcon(c: Category): Int =
    when (c) {
        Category.FLOOR -> R.drawable.ic_cat_floor
        Category.ELEC -> R.drawable.ic_cat_elec
        Category.FIRE -> R.drawable.ic_cat_fire
        Category.CHEM -> R.drawable.ic_cat_chem
        Category.MACH -> R.drawable.ic_cat_mach
        Category.STORE -> R.drawable.ic_cat_store
        Category.PPE -> R.drawable.ic_cat_ppe
    }

/** A SpatialUI segment control; selected = interaction at 26 %. */
@Composable
internal fun Segments(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit, width: Dp, height: Dp = 36.dp, small: Boolean = false) {
    val itemWidth = width / items.size
    SegmentControl(modifier = Modifier.width(width).height(height), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        items.forEachIndexed { i, (name, text) ->
            SegmentItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                modifier = Modifier.width(itemWidth).height(height).probe(name) { onSelect(i) },
                textStyle = if (small) HhType.labelMedium else HhType.labelLarge,
                colors =
                    SegmentControlDefaults.colors(
                        itemBackgroundColor = Color.Transparent,
                        itemContentColor = PicoTheme.colorScheme.labelPrimary,
                        selectedItemBackgroundColor = PicoTheme.colorScheme.interaction.copy(alpha = 0.26f),
                        selectedItemContentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                title = {
                    // design-style: inherited-content-color SegmentItem
                    Text(text = text, style = if (small) HhType.labelMedium else HhType.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                },
            )
        }
    }
}

/** A numbered circle (flag number in the list): ink on hazard orange, 6.3:1 (white would be 2.6:1). */
@Composable
internal fun NumberDot(n: Int) {
    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(HhColors.HazardOrange), contentAlignment = Alignment.Center) {
        Text(text = n.toString(), color = HhColors.PlateInk, style = HhType.labelMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum, lnum"), maxLines = 1)
    }
}

/** A status dot on an ink plate (design: status colours sit on plateInk, not on the glass). */
@Composable
internal fun VerdictDot(color: Color) {
    Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(HhColors.PlateInk), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
    }
}

internal fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceIn(0, 59 * 60 + 59)
    return "%02d:%02d".format(s / 60, s % 60)
}

@Suppress("unused")
private val unusedPadding = Modifier.padding(0.dp)
