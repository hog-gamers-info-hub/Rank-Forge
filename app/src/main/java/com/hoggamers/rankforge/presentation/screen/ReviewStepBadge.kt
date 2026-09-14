package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ReviewStepBadge(
    number: Int,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    numberColor: Color = Color.White,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(borderColor?.let { Modifier.border(1.dp, it, shape) } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = numberColor,
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
