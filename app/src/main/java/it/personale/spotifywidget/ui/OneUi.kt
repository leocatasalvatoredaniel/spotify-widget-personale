package it.personale.spotifywidget.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colors and sizes of One UI 9, measured on screenshots of the Samsung
 * settings on a Galaxy S24 Ultra: black (light gray) page, dark gray (white)
 * rounded groups, gray headers and the blue of Samsung for values and switches
 */
data class OneUiColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
    val accent: Color,
    val switchOff: Color,
)

private val Light = OneUiColors(
    background = Color(0xFFF3F3F5),
    card = Color(0xFFFDFCFF),
    text = Color(0xFF010101),
    secondary = Color(0xFF7E7D82),
    divider = Color(0xFFE5E4E7),
    accent = Color(0xFF377AFF),
    switchOff = Color(0xFFC6C6CA),
)

private val Dark = OneUiColors(
    background = Color(0xFF010101),
    card = Color(0xFF171719),
    text = Color(0xFFFCFBFF),
    secondary = Color(0xFF9A999E),
    divider = Color(0xFF3A3A3C),
    accent = Color(0xFF377AFF),
    switchOff = Color(0xFF5F5F62),
)

val LocalOneUi = staticCompositionLocalOf { Light }

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun OneUiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) Dark else Light
    // Material only for the ripples and the default text color
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.card, onSurface = colors.text)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.card, onSurface = colors.text)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalOneUi provides colors, content = content)
    }
}

/** Title of the page: left and bold, aligned with the text of the rows */
@Composable
fun OneUiTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, color = LocalOneUi.current.text),
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 22.dp),
    )
}

/** Gray title of a group, right below the previous group */
@Composable
fun OneUiSectionHeader(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalOneUi.current.secondary),
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** Gray explanation under a group */
@Composable
fun OneUiFooter(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = LocalOneUi.current.secondary),
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 10.dp),
    )
}

/** A rounded group of rows, separated by inset lines */
@Composable
fun OneUiCard(rows: List<@Composable () -> Unit>) {
    val colors = LocalOneUi.current
    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    thickness = 1.dp,
                    color = colors.divider,
                )
            }
            row()
        }
    }
}

/**
 * A row of a group: title, optional summary (blue when it is a value, as
 * «Come l'applicazione») and an optional switch at the end
 */
@Composable
fun OneUiRow(
    title: String,
    summary: String? = null,
    summaryIsValue: Boolean = false,
    checked: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalOneUi.current
    val clickable = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = if (checked != null) Role.Switch else Role.Button, onClick = onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(clickable)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = TextStyle(fontSize = 18.sp, color = colors.text))
            if (summary != null) {
                Text(
                    summary,
                    style = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = if (summaryIsValue) colors.accent else colors.secondary,
                    ),
                )
            }
        }
        if (checked != null) OneUiSwitch(checked)
    }
}

/** The switch of One UI 9: a short track with a white thumb inside it */
@Composable
fun OneUiSwitch(checked: Boolean) {
    val colors = LocalOneUi.current
    val track by animateColorAsState(if (checked) colors.accent else colors.switchOff, label = "track")
    val offset by animateDpAsState(if (checked) 14.5.dp else 1.5.dp, label = "thumb")
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 19.dp)
            .clip(CircleShape)
            .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Spacer(
            modifier = Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .size(16.dp)
                .background(Color.White, CircleShape),
        )
    }
}
