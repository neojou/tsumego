package com.neojou.tsumego

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import com.neojou.tools.ui.menu.MyTopMenuBar
import com.neojou.tools.ui.menu.MyTopMenuItem

/**
 * Log tag used by [Tsumego] for logging UI events.
 */
private const val TAG = "Tsumego"

/**
 * Main content modes for the shell area below the toolbar.
 */
private enum class MainContent {
    /** Default placeholder until a feature is chosen. */
    Home,

    /** Daily candlestick + volume chart (viewport pan/zoom). */
    KChart,
}

/**
 * Primary application shell.
 *
 * Hosts a product-configured [MyTopMenuBar] and content area.
 * - Database → Input / View / Export / Import
 * - K Chart → View / Settings（均線 + KD + MACD 參數）
 */
@Composable
fun Tsumego() {
    var showAbout by remember { mutableStateOf(false) }

    // Product-specific menu tree only; [MyTopMenuBar] stays app-agnostic.
    // Rebuilt each composition so callbacks always see current shell state.
    val topMenus = listOf(
        MyTopMenuItem(
            id = "about",
            label = "About",
            onClick = { showAbout = true },
        ),
    )

    LaunchedEffect(Unit) {
        MyLog.add(TAG, "Enter", LogLevel.DEBUG)
    }

    Scaffold(
        topBar = {
            MyTopMenuBar(items = topMenus)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("tsumego")
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = AppVersion.APP_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = AppVersion.APP_NAME_EN,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Text(
                    text = "版本 ${AppVersion.DISPLAY}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = AppVersion.SUMMARY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("關閉")
                }
            }
        }
    }
}
