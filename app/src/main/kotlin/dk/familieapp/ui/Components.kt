package dk.familieapp.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF17211C)
val Muted = Color(0xFF56635B)
val Green = Color(0xFF1E5C45)
val Mint = Color(0xFFD5E8DC)
val Yellow = Color(0xFFF0B544)
val Ground = Color(0xFFF2F4EC)
val Track = Color(0xFFE3E8DC)
val Line = Color(0xFFC9D1C4)

/** Tekstfelter starter med stort bogstav. */
val SentenceCaps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

@Composable
fun FamilieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            primaryContainer = Mint,
            onPrimaryContainer = Ink,
            secondary = Green,
            onSecondary = Color.White,
            secondaryContainer = Mint,
            onSecondaryContainer = Ink,
            tertiary = Yellow,
            onTertiary = Ink,
            background = Ground,
            onBackground = Ink,
            surface = Ground,
            onSurface = Ink,
            surfaceVariant = Track,
            onSurfaceVariant = Muted,
            outline = Line,
            outlineVariant = Color(0xFFE8ECE3),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
        ),
        content = content,
    )
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

@Composable
fun ScreenHeader(subtitle: String, title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(subtitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Text(title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        }
        actions()
    }
}

@Composable
fun BackBar(label: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Ink)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** To eller tre faner i en pille, som i designet. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) Color.White else Color.Transparent)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (active) Ink else Color(0xFF47524B),
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
}

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
        Column(content = content)
    }
}

@Composable
fun YellowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Ink),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Pill(text: String, color: Color = Mint) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Ink,
        maxLines = 1,
    )
}
