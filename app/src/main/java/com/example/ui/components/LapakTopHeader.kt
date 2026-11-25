package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.HeaderBgColorKey
import com.example.data.model.ShopIconKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class HeaderGradientPalette(
    val key: String,
    val label: String,
    val lightStart: Color,
    val lightEnd: Color,
    val darkStart: Color,
    val darkEnd: Color
)

object LapakHeaderPalettes {
    val all: List<HeaderGradientPalette> = listOf(
        HeaderGradientPalette(
            key = HeaderBgColorKey.SOFT_EMERALD,
            label = "Soft Emerald",
            lightStart = Color(0xFF2D7A5E),
            lightEnd = Color(0xFF1F5C46),
            darkStart = Color(0xFF16382C),
            darkEnd = Color(0xFF0F1412)
        ),
        HeaderGradientPalette(
            key = HeaderBgColorKey.OCEAN_TEAL,
            label = "Ocean Teal",
            lightStart = Color(0xFF226F82),
            lightEnd = Color(0xFF164F5E),
            darkStart = Color(0xFF14343D),
            darkEnd = Color(0xFF0E1619)
        ),
        HeaderGradientPalette(
            key = HeaderBgColorKey.WARM_TERRACOTTA,
            label = "Terracotta",
            lightStart = Color(0xFFB85A3A),
            lightEnd = Color(0xFF8C3E24),
            darkStart = Color(0xFF3E2219),
            darkEnd = Color(0xFF17110F)
        ),
        HeaderGradientPalette(
            key = HeaderBgColorKey.ROYAL_SLATE,
            label = "Royal Slate",
            lightStart = Color(0xFF355070),
            lightEnd = Color(0xFF243852),
            darkStart = Color(0xFF1B2736),
            darkEnd = Color(0xFF10151C)
        ),
        HeaderGradientPalette(
            key = HeaderBgColorKey.BERRY_PLUM,
            label = "Warm Berry",
            lightStart = Color(0xFF7A3E5D),
            lightEnd = Color(0xFF592A43),
            darkStart = Color(0xFF331B28),
            darkEnd = Color(0xFF160F13)
        )
    )

    fun resolve(key: String?): HeaderGradientPalette {
        return all.firstOrNull { it.key == key } ?: all.first()
    }
}

fun resolveShopIconVector(iconKey: String?): ImageVector {
    return when (iconKey) {
        ShopIconKey.BAKERY -> Icons.Default.BakeryDining
        ShopIconKey.CAFE -> Icons.Default.LocalCafe
        ShopIconKey.BASKET -> Icons.Default.ShoppingBasket
        ShopIconKey.RESTAURANT -> Icons.Default.Restaurant
        else -> Icons.Default.Storefront
    }
}

fun copyPickedImageToInternal(context: Context, sourceUri: Uri, targetFileName: String): String? {
    return try {
        val dir = File(context.filesDir, "lapak_media").apply { mkdirs() }
        val outFile = File(dir, targetFileName)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        outFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

fun loadLocalBitmap(context: Context, pathOrUri: String?, maxDim: Int = 720): Bitmap? {
    if (pathOrUri.isNullOrBlank()) return null
    return try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val isFile = pathOrUri.startsWith("/")
        if (isFile) {
            if (!File(pathOrUri).exists()) return null
            BitmapFactory.decodeFile(pathOrUri, boundsOpts)
        } else {
            context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
        }

        val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
        var sample = 1
        while (maxSide / sample > maxDim) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        if (isFile) {
            BitmapFactory.decodeFile(pathOrUri, decodeOpts)
        } else {
            context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun rememberLocalBitmap(pathOrUri: String?, maxDim: Int = 600): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(pathOrUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pathOrUri) {
        bitmap = withContext(Dispatchers.IO) {
            loadLocalBitmap(context, pathOrUri, maxDim)
        }
    }
    return bitmap
}

@Composable
fun ShopAvatarBadge(
    iconKey: String,
    logoUri: String?,
    size: Dp = 42.dp,
    containerColor: Color = Color.White.copy(alpha = 0.18f),
    iconTint: Color = Color.White,
    borderColor: Color = Color.White.copy(alpha = 0.32f),
    modifier: Modifier = Modifier
) {
    val customBitmap = if (iconKey == ShopIconKey.CUSTOM_PHOTO && !logoUri.isNullOrBlank()) {
        rememberLocalBitmap(logoUri, maxDim = 256)
    } else null

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(size * 0.30f)),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = "Logo Lapak",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = resolveShopIconVector(iconKey),
                contentDescription = "Ikon Lapak",
                tint = iconTint,
                modifier = Modifier.size(size * 0.56f)
            )
        }
    }
}

@Composable
fun HeaderActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
fun LapakTopHeader(
    title: String,
    subtitle: String? = null,
    paletteKey: String = HeaderBgColorKey.SOFT_EMERALD,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = remember(paletteKey) { LapakHeaderPalettes.resolve(paletteKey) }
    val gradientColors = if (isDark) {
        listOf(palette.darkStart, palette.darkEnd)
    } else {
        listOf(palette.lightStart, palette.lightEnd)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(gradientColors))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                if (showBackButton && onBackClick != null) {
                    HeaderActionIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        onClick = onBackClick
                    )
                    Spacer(Modifier.width(12.dp))
                }

                if (leadingContent != null) {
                    leadingContent()
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
        }
    }
}
