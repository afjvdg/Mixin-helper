package com.example.minecraftmixinhelper.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        // 空串边界修复：原实现返回 [""], 这里返回空列表
        value?.takeIf { it.isNotEmpty() }?.split(",") ?: emptyList()
}
