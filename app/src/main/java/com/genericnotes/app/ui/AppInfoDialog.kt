package com.genericnotes.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private const val GitHubRepositoryUrl = "https://github.com/jg925/generic-notes-app"

@Composable
internal fun AppInfoDialog(
    onDismissRequest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .heightIn(max = 620.dp),
            color = Color.White,
            contentColor = Color(0xFF111111),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "About Generic Notes",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "A handwritten digital notes app to give you freedom with your files.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Generic Notes is open source and open for contributions. The source code, issues, and pull requests are hosted on GitHub.",
                        color = Color(0xFF444444),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Visit Repo: ",
                            color = Color(0xFF444444),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = { uriHandler.openUri(GitHubRepositoryUrl) },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF111111),
                            ),
                        ) {
                            Text("generic-notes-app")
                        }
                    }

                    NoticeSection(
                        title = "Copyright",
                        body = "Copyright (c) 2026 Jinny Gui",
                    )
                    NoticeSection(
                        title = "App license",
                        body = "Generic Notes is distributed under the MIT License. The full license text is included in the repository LICENSE file.",
                    )
                    NoticeSection(
                        title = "Open-source notices",
                        body = "This app uses Kotlin and AndroidX Jetpack Compose libraries, including Activity Compose, Foundation, Material 3, UI, UI Graphics, and UI Tooling Preview. These components are distributed under the Apache License 2.0.",
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF111111),
                        ),
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeSection(
    title: String,
    body: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = Color(0xFF111111),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = body,
            color = Color(0xFF444444),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
