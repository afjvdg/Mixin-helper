package com.example.minecraftmixinhelper.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String? =
        list?.takeIf { it.isNotEmpty() }?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}