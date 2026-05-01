package com.example.trymov.data.local

import androidx.room.TypeConverter
import com.example.trymov.model.WatchStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AppTypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun watchStatusToString(value: WatchStatus?): String? = value?.name

    @TypeConverter
    fun stringToWatchStatus(value: String?): WatchStatus? = value?.let { WatchStatus.valueOf(it) }

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? {
        if (value == null) return null
        return gson.toJson(value)
    }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? {
        if (value == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}
