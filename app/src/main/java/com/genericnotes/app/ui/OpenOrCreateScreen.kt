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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
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
            Text(
                text = "Start a note",
                color = Color(0xFF111111),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onOpenExisting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111111),
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
            ) {
                Text("Create new")
            }
            if (recentFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                RecentFilesSection(
                    recentFiles = recentFiles,
                    onOpenRecent = onOpenRecent,
                )
            }
        }
    }
}

@Composable
private fun RecentFilesSection(
    recentFiles: List<RecentHwdnFile>,
    onOpenRecent: (RecentHwdnFile) -> Unit,
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
            )
        }
    }
}

@Composable
private fun RecentFileButton(
    recentFile: RecentHwdnFile,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = recentFile.displayName,
                color = Color(0xFF111111),
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
