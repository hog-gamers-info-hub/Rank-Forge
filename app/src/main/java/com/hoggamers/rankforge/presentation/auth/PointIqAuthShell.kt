package com.hoggamers.rankforge.presentation.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoggamers.rankforge.R

private val PointIqNavy = Color(0xFF071B3E)
private val PointIqBody = Color(0xFF40536F)
private val PointIqMuted = Color(0xFF7A8BA4)
private val PointIqBlue = Color(0xFF176AF7)
private val PointIqCyan = Color(0xFF17C9F2)
private val PointIqBackgroundTop = Color(0xFFFDFEFF)
private val PointIqBackgroundBottom = Color(0xFFF4FAFF)

@Composable
internal fun PointIqAuthShell(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PointIqBackgroundTop, PointIqBackgroundBottom),
                ),
            ),
    ) {
        PointIqBackgroundDecoration()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            PointIqBrandHeader(
                icon = painterResource(R.drawable.pointiq_brand_mark),
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = PointIqNavy,
                modifier = titleModifier,
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PointIqBody,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            content()
        }
    }
}

@Composable
private fun PointIqBrandHeader(
    icon: Painter,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                val brandText = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = PointIqNavy,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append(stringResource(R.string.pointiq_brand_point))
                    }
                    withStyle(
                        SpanStyle(
                            color = PointIqBlue,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append(stringResource(R.string.pointiq_brand_iq))
                    }
                }

                Text(
                    text = brandText,
                    fontSize = 24.sp,
                    lineHeight = 27.sp,
                )
                Text(
                    text = stringResource(R.string.pointiq_brand_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = PointIqMuted,
                )
            }
        }
    }
}

@Composable
private fun PointIqBackgroundDecoration() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(
            x = size.width - 6.dp.toPx(),
            y = 100.dp.toPx(),
        )
        val color = PointIqCyan.copy(alpha = 0.08f)
        val strokeWidth = 1.dp.toPx()

        drawCircle(
            color = color,
            radius = 56.dp.toPx(),
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = color,
            radius = 34.dp.toPx(),
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = color,
            radius = 13.dp.toPx(),
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(center.x - 72.dp.toPx(), center.y),
            end = Offset(center.x + 72.dp.toPx(), center.y),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(center.x, center.y - 72.dp.toPx()),
            end = Offset(center.x, center.y + 72.dp.toPx()),
            strokeWidth = strokeWidth,
        )
    }
}
