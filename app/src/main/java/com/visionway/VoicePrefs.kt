package com.project.visionway

import android.content.Context
import android.content.SharedPreferences

object VoicePrefs {
    private const val PREFS = "visionway_prefs"

    const val KEY_VOICE_NAME   = "voice_name"   // nome exato da Voice (Google TTS)
    const val KEY_VOICE_GENDER = "voice_gender" // opcional: fallback
    const val GENDER_MALE = "male"
    const val GENDER_FEMALE = "female"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setVoiceName(ctx: Context, voice: String?) =
        prefs(ctx).edit().putString(KEY_VOICE_NAME, voice).apply()
    fun getVoiceName(ctx: Context): String? =
        prefs(ctx).getString(KEY_VOICE_NAME, null)

    fun setGender(ctx: Context, gender: String) =
        prefs(ctx).edit().putString(KEY_VOICE_GENDER, gender).apply()
    fun getGender(ctx: Context): String =
        prefs(ctx).getString(KEY_VOICE_GENDER, GENDER_MALE) ?: GENDER_MALE
}