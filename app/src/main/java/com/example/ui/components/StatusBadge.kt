package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SellerStatus
import com.example.ui.theme.LocalLapakColors

@Composable
fun StatusBadge(
    status: SellerStatus,
    modifier: Modifier = Modifier
) {
    val lapakColors = LocalLapakColors.current
    val (bgColor, textColor, label) = when (status) {
        SellerStatus.AKTIF -> Triple(lapakColors.statusActiveBg, lapakColors.statusActiveText, "Aktif")
        SellerStatus.LIBUR -> Triple(lapakColors.statusLeaveBg, lapakColors.statusLeaveText, "Libur")
        SellerStatus.NONAKTIF -> Triple(lapakColors.statusInactiveBg, lapakColors.statusInactiveText, "Nonaktif")
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
