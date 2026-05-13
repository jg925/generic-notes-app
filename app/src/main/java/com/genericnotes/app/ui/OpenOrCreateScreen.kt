package com.genericnotes.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.genericnotes.app.hwdn.RecentHwdnFile

@Composable
internal fun OpenOrCreateScreen(
    onOpenExisting: () -> Unit,
    onCreateNew: () -> Unit,
    recentFiles: List<RecentHwdnFile>,
    onOpenRecent: (RecentHwdnFile) -> Unit,
    onOpenSettings: () -> Unit,
    accentColor: Color,
) {
    var showAppInfo by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Start a note",
                    color = accentColor,
                    style = MaterialTheme.typography.headlineMedium,
                )
                StylusHoverTooltipBox(
                    tooltipText = "settings",
                    containerColor = accentColor,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = accentColor),
                    ) {
                        Icon(
                            imageVector = SettingsIcon,
                            contentDescription = "settings",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                StylusHoverTooltipBox(
                    tooltipText = "about and licenses",
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    IconButton(
                        onClick = { showAppInfo = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFF4F4F4),
                            contentColor = Color(0xFF111111),
                        ),
                    ) {
                        Icon(
                            imageVector = InfoIcon,
                            contentDescription = "about and licenses",
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onOpenExisting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White,
                ),
            ) {
                Text("Open existing .hwdn")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onCreateNew,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
            ) {
                Text("Create new")
            }
            if (recentFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                RecentFilesSection(
                    recentFiles = recentFiles,
                    onOpenRecent = onOpenRecent,
                    accentColor = accentColor,
                )
            }
        }
    }

    if (showAppInfo) {
        AppInfoDialog(onDismissRequest = { showAppInfo = false })
    }
}

@Composable
private fun RecentFilesSection(
    recentFiles: List<RecentHwdnFile>,
    onOpenRecent: (RecentHwdnFile) -> Unit,
    accentColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Recently opened",
            color = Color(0xFF555555),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        recentFiles.forEach { recentFile ->
            RecentFileButton(
                recentFile = recentFile,
                onClick = { onOpenRecent(recentFile) },
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun RecentFileButton(
    recentFile: RecentHwdnFile,
    onClick: () -> Unit,
    accentColor: Color,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = recentFile.displayName,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = recentFile.locationHint,
                color = Color(0xFF666666),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
