package com.genericnotes.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.genericnotes.app.hwdn.HwdnExtension

@Composable
internal fun FilePanel(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF4F4F4),
        contentColor = Color(0xFF111111),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF111111)),
                cursorBrush = SolidColor(Color(0xFF111111)),
                modifier = Modifier.width(156.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (fileName.isBlank()) {
                            Text(
                                text = "Untitled Note",
                                color = Color(0xFF777777),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Text(
                text = HwdnExtension,
                color = Color(0xFF555555),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF111111),
                ),
            ) {
                Icon(
                    imageVector = SaveIcon,
                    contentDescription = "Save",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
