package com.hcexport.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Card
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.CardLift
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.HairlineSoft
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Obsidian
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal

val NumberFeatures = "tnum"

/** The app canvas: deep ink with two very soft coloured glows. */
@Composable
fun HcBackground(
    accent: Color = Teal,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Obsidian)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 130.dp, y = (-170).dp)
                .size(400.dp)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.17f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-150).dp, y = 140.dp)
                .size(340.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Periwinkle.copy(alpha = 0.07f), Color.Transparent)
                    )
                )
        )
        content()
    }
}

@Composable
fun PageHeader(
    title: String,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(
                icon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow) },
                onClick = onBack,
                border = false,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Slate) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/** A layered, quietly glowing card. `accent = null` gives the neutral surface. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HcCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = accent?.copy(alpha = 0.24f) ?: Hairline
    val backgroundBrush = if (accent != null) {
        Brush.linearGradient(
            listOf(accent.copy(alpha = 0.13f), CardHigh, Card)
        )
    } else {
        Brush.linearGradient(listOf(CardHigh, Card))
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, borderColor, shape)
            .then(
                when {
                    onClick != null && onLongClick != null -> Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            )
            .padding(contentPadding),
        content = content,
    )
}

/** The three large destination buttons on Home. */
@Composable
fun HubCard(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    meta: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.17f), CardHigh.copy(alpha = 0.96f), Card)
                )
            )
            .border(1.dp, accent.copy(alpha = 0.26f), shape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = accent.copy(alpha = 0.08f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(104.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.08f))
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(25.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Snow,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!meta.isNullOrBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Tag(meta, accent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
fun IconAction(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: Boolean = true,
    background: Color = CardHigh,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (border) Modifier.border(1.dp, Hairline, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.82f)))
                else Brush.horizontalGradient(listOf(CardLift, CardHigh))
            )
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) Abyss else Slate,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Snow,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(CardHigh)
            .border(1.dp, Hairline, RoundedCornerShape(15.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) accent else Slate,
        )
    }
}

/** Pill segmented control used for Week / Month / Day, 1RM / 2RM / 3RM, etc. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Card)
            .border(1.dp, Hairline, RoundedCornerShape(15.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val container by animateColorAsState(
                if (isSelected) accent.copy(alpha = 0.20f) else Color.Transparent,
                tween(180),
                label = "segmentContainer",
            )
            val contentColor by animateColorAsState(
                if (isSelected) accent else Mist,
                tween(180),
                label = "segmentContent",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(container)
                    .then(
                        if (isSelected) Modifier.border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(11.dp))
                        else Modifier
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
fun HcDivider(modifier: Modifier = Modifier, color: Color = HairlineSoft) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
fun EmptyState(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 38.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, accent.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(27.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Snow)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(13.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(actionText, style = MaterialTheme.typography.titleSmall, color = accent)
            }
        }
    }
}

@Composable
fun LoadingLine(
    text: String = "Loading…",
    accent: Color = Teal,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = accent,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Slate)
    }
}

@Composable
fun SyncBar(
    title: String,
    subtitle: String,
    accent: Color,
    syncing: Boolean,
    onSync: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(CardHigh, Card)))
            .border(1.dp, Hairline, shape)
            .padding(start = 15.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp).padding(7.dp),
                color = accent,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.13f))
                    .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(50))
                    .clickable(onClick = onSync)
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sync", style = MaterialTheme.typography.labelLarge, color = accent)
            }
        }
    }
}

/** A large numeric headline that never jitters as values change. */
@Composable
fun NumberText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Snow,
    size: Int = 34,
    weight: FontWeight = FontWeight.SemiBold,
) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = size.sp,
            lineHeight = (size + 3).sp,
            fontWeight = weight,
            fontFeatureSettings = NumberFeatures,
        ),
        color = color,
        maxLines = 1,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Delete",
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardHigh,
        titleContentColor = Snow,
        textContentColor = Mist,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (destructive) com.hcexport.ui.theme.Danger else Teal,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate, style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}

/** Vertical rhythm used across the screens. */
val ScreenPadding = 20.dp
val CardGap = 12.dp
