package com.example.data.database

import androidx.room.TypeConverter
import com.example.data.model.SellerStatus
import com.example.data.model.StockMovementType

class Converters {
    @TypeConverter
    fun fromSellerStatus(value: SellerStatus?): String? = value?.name

    @TypeConverter
    fun toSellerStatus(value: String?): SellerStatus? = value?.let {
        try {
            SellerStatus.valueOf(it)
        } catch (e: Exception) {
            SellerStatus.AKTIF
        }
    }

    @TypeConverter
    fun fromStockMovementType(value: StockMovementType?): String? = value?.name

    @TypeConverter
    fun toStockMovementType(value: String?): StockMovementType? = value?.let {
        try {
            StockMovementType.valueOf(it)
        } catch (e: Exception) {
            StockMovementType.SETORAN_AWAL
        }
    }
}
