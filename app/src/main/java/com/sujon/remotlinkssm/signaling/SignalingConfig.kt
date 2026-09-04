package com.sujon.remotlinkssm.signaling

import android.content.Context

/** Keeps the signaling endpoint configurable; no server URL is hard-coded. */
class SignalingConfig(context: Context) {
    private val prefs = context.getSharedPreferences("signaling_config", Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value.trim()).apply()

    companion object {
        private const val KEY_ENDPOINT = "wss_endpoint"
    }
}
