package com.example.clickdevice.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppThemeMode(val storageValue: String, val displayName: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    fun resolveDarkTheme(systemDarkTheme: Boolean): Boolean {
        return when (this) {
            System -> systemDarkTheme
            Light -> false
            Dark -> true
        }
    }

    companion object {
        fun fromStorageValue(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: System
        }
    }
}

object AppThemePreference {
    private const val PREFS_NAME = "app_theme_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private var loaded = false

    var themeMode by mutableStateOf(AppThemeMode.System)
        private set

    fun ensureLoaded(context: Context) {
        if (loaded) return
        themeMode = AppThemeMode.fromStorageValue(
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, AppThemeMode.System.storageValue)
        )
        loaded = true
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        ensureLoaded(context)
        if (themeMode == mode) return
        themeMode = mode
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
    }
}
