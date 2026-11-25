package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalLapakColors

private data class TutorialStep(
    val stepTitle: String,
    val description: String
)

private data class TutorialFlow(
    val number: Int,
    val shortTab: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val steps: List<TutorialStep>
)

@Composable
fun TutorialGuideDialog(
    onDismiss: () -> Unit
) {
    val lapakColors = LocalLapakColors.current

    val flows = remember {
        listOf(
            TutorialFlow(
                number = 1,
                shortTab = "Alur 1",
                title = "Persiapan Awal & Daftarkan Penyetor",
                summary = "Siapkan identitas lapak, tarif komisi, lalu daftarkan dapur penyetor pertama Anda.",
                icon = Icons.Default.Storefront,
                steps = listOf(
                    TutorialStep(
                        "1. Isi Profil Lapak",
                        "Buka menu Pengaturan > Profil Lapak. Masukkan Nama Lapak, Nomor WhatsApp, Alamat, serta pilih Ikon dan Warna Header sesuai selera Anda."
                    ),
                    TutorialStep(
                        "2. Atur Tarif Biaya Admin / Komisi",
                        "Di Pengaturan > Biaya Admin / Komisi Lapak, tentukan potongan standar lapak: bisa Nominal Tetap per item (misal Rp 2.000) atau Persentase (%) dari harga jual."
                    ),
                    TutorialStep(
                        "3. Daftarkan Penyetor",
                        "Masuk ke tab Penyetor di bawah, lalu tekan tombol (+) di kanan bawah. Isi nama dapur penyetor, nomor WA, dan atur tarif khusus jika penyetor tersebut berbeda."
                    ),
                    TutorialStep(
                        "4. Bagikan Link Web Penyetor ke Penjual",
                        "Pada kartu penyetor, tekan tombol Link Web Penyetor lalu klik Kirim Link via WhatsApp. Penjual cukup membuka link tersebut di browser HP tanpa perlu instal aplikasi!"
                    )
                )
            ),
            TutorialFlow(
                number = 2,
                shortTab = "Alur 2",
                title = "Terima Titipan Harian & Update Stok",
                summary = "Dua cara mudah menerima titipan pagi/harian: otomatis dari Web Penyetor atau input manual di HP Admin.",
                icon = Icons.Default.Inventory2,
                steps = listOf(
                    TutorialStep(
                        "Cara A (Otomatis): Penjual Kirim Lewat Web Penyetor",
                        "Penjual membuka link WA miliknya, tambah/edit produk (uang bersih terhitung otomatis), isi kolom 'Stok Kiriman Hari Ini', lalu tekan 'Kirim & Simpan ke Lapak'."
                    ),
                    TutorialStep(
                        "Stok Otomatis Masuk & Muncul Notifikasi",
                        "Begitu penjual mengirim data, aplikasi Admin otomatis menerima produk baru/stok tambahan, mencatatnya sebagai 'Kiriman via Web Penyetor', dan menampilkan banner notifikasi."
                    ),
                    TutorialStep(
                        "Cara B (Manual): Tombol + Terima Titipan",
                        "Jika penjual langsung mengantar tanpa web, cukup tekan tombol '+ Terima Titipan' di Dashboard (atau 'Tambah Stok' di halaman Penyetor), pilih dapur, produk, dan jumlah titipannya."
                    )
                )
            ),
            TutorialFlow(
                number = 3,
                shortTab = "Alur 3",
                title = "Promosi & Layani Pembeli di Kasir",
                summary = "Bagikan poster cantik ke pelanggan tanpa menampilkan nama dapur, lalu layani transaksi cepat di Kasir.",
                icon = Icons.Default.PointOfSale,
                steps = listOf(
                    TutorialStep(
                        "1. Bagikan Poster Menu ke Grup Pembeli",
                        "Buka tab Jualan, lalu tekan tombol 'Promo Pelanggan'. Pilih warna background/foto galeri, atur info yang tampil, lalu tekan 'Bagikan Gambar ke WhatsApp'. Nama penyetor aman tersembunyi!"
                    ),
                    TutorialStep(
                        "2. Kirim Info Sisa Stok ke Grup Penyetor",
                        "Jika ingin memberi tahu penjual sisa dagangannya, tekan tombol 'Info ke Penyetor' di halaman Jualan, pilih 'Semua Dapur' atau 'Per Penyetor', lalu kirim teks ke grup WA penjual."
                    ),
                    TutorialStep(
                        "3. Masukkan Pesanan & Bayar di Kasir",
                        "Di halaman Jualan, ketuk '+ Ke Kasir' pada produk yang dibeli pelanggan. Bilah ringkasan akan muncul di bawah — tekan 'Buka Kasir >', pilih Tunai/QRIS/Transfer, lalu klik Bayar."
                    ),
                    TutorialStep(
                        "4. Stok Berkurang & Struk Siap Dibagikan",
                        "Saat transaksi selesai, stok otomatis berkurang, hak penyetor & untung lapak tercatat otomatis, dan struk pelanggan siap dicetak atau dikirim ke WA (tanpa nama dapur)."
                    )
                )
            ),
            TutorialFlow(
                number = 4,
                shortTab = "Alur 4",
                title = "Tutup Lapak & Bayar Setoran Penjual",
                summary = "Pantau hasil jualan harian secara transparan dan catat pelunasan uang setoran ke masing-masing penyetor.",
                icon = Icons.Default.AccountBalanceWallet,
                steps = listOf(
                    TutorialStep(
                        "1. Cek Ringkasan di Dashboard",
                        "Lihat 4 kartu warna di Dashboard: Total Omzet, Untung Lapak, Setoran ke Penjual, dan Biaya Operasional. Anda bisa filter per Hari Ini, 7 Hari, atau Bulan Ini."
                    ),
                    TutorialStep(
                        "2. Catat Biaya Operasional (Opsional)",
                        "Jika ada pengeluaran seperti plastik kresek atau kebersihan, catat lewat Pengaturan > Biaya Operasional agar Untung Lapak bersih selalu akurat."
                    ),
                    TutorialStep(
                        "3. Bayar Setoran ke Penjual",
                        "Tekan tombol cepat 'Bayar Setoran' di Dashboard (atau di kartu Penyetor). Pilih penyetor, masukkan nominal pembayaran, lalu simpan untuk mencetak/kirim Bukti Setoran via WA."
                    ),
                    TutorialStep(
                        "4. Cek Transparansi di Riwayat & Cadangkan Data",
                        "Gunakan tab Riwayat untuk memeriksa ulang struk atau membatalkan transaksi keliru. Lakukan ekspor di Pengaturan > Cadangan & Pemulihan secara berkala agar data aman."
                    )
                )
            )
        )
    }

    var selectedIndex by remember { mutableStateOf(0) }
    val activeFlow = flows[selectedIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, lapakColors.cardBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Panduan Penggunaan Lapak Kita",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "4 Alur Mudah untuk Pemula",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup Panduan")
                    }
                }

                // 4 Balanced Flow Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    flows.forEachIndexed { idx, flow ->
                        val selected = idx == selectedIndex
                        Surface(
                            onClick = { selectedIndex = idx },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tutorial_tab_${idx + 1}")
                        ) {
                            Text(
                                text = flow.shortTab,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 9.dp),
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = lapakColors.cardBorder)

                // Active Flow Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = lapakColors.omzetCardBg,
                        border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    activeFlow.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Alur ${activeFlow.number}: ${activeFlow.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = lapakColors.omzetCardAccent
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = activeFlow.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    activeFlow.steps.forEach { step ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, lapakColors.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = step.stepTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = lapakColors.cardBorder)

                // Footer Navigation Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedIndex > 0) {
                        OutlinedButton(
                            onClick = { selectedIndex-- },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text("Sebelumnya")
                        }
                    }
                    Button(
                        onClick = {
                            if (selectedIndex < flows.lastIndex) selectedIndex++ else onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = if (selectedIndex < flows.lastIndex) "Alur Berikutnya >" else "Selesai & Mulai Pakai",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
