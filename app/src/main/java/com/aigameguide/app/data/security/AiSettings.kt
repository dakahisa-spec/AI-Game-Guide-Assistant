package com.aigameguide.app.data.security

import android.content.Context

class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    var model: String
        get() = prefs.getString("model", "gpt-5.6") ?: "gpt-5.6"
        set(value) = prefs.edit().putString("model", value.trim()).apply()
}
