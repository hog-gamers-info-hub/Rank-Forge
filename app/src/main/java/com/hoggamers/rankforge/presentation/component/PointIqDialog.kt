package com.hoggamers.rankforge.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val PointIqDialogSurface = Color(0xFF071B3E)
private val PointIqDialogBorder = Color(0xFF176AF7)
private val PointIqDialogDestructiveBorder = Color(0xFFD85C6A)
private val PointIqDialogTitle = Color(0xFFF6F8FF)
private val PointIqDialogBody = Color(0xFF91AFE0)
private val PointIqDialogAction = Color(0xFF17C9F2)
private val PointIqDialogSecondaryAction = Color(0xFF91AFE0)
private val PointIqDialogDestructiveAction = Color(0xFFFF6B6B)

@Composable
fun PointIqConfirmationDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    destructive: Boolean = false,
    confirmModifier: Modifier = Modifier,
    dismissModifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .border(
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (destructive) {
                            PointIqDialogDestructiveBorder
                        } else {
                            PointIqDialogBorder
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                ),
            shape = RoundedCornerShape(12.dp),
            color = PointIqDialogSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = title,
                    color = PointIqDialogTitle,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 12.dp),
                    color = PointIqDialogBody,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (onDismiss != null && dismissLabel != null) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = dismissEnabled,
                            modifier = dismissModifier,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = PointIqDialogSecondaryAction,
                            ),
                        ) {
                            Text(
                                text = dismissLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    TextButton(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        modifier = confirmModifier,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (destructive) {
                                PointIqDialogDestructiveAction
                            } else {
                                PointIqDialogAction
                            },
                        ),
                    ) {
                        Text(
                            text = confirmLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
