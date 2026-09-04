package com.sujon.remotlinkssm.pairing

import org.json.JSONObject

/** Physical, user-initiated QR bootstrap for the Controller's public identity. */
data class DeviceIdentityQr(
    val deviceId: String,
    val deviceName: String,
    val publicKey: String
) {
    fun toPayload(): String = JSONObject()
        .put("v", 1)
        .put("type", "remotelink_identity")
        .put("deviceId", deviceId)
        .put("deviceName", deviceName)
        .put("publicKey", publicKey)
        .toString()

    companion object {
        fun fromPayload(payload: String): DeviceIdentityQr {
            val json = JSONObject(payload)
            require(json.optString("type") == "remotelink_identity") { "Invalid RemoteLink identity QR" }
            require(json.optInt("v") == 1) { "Unsupported identity QR version" }
            return DeviceIdentityQr(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                publicKey = json.getString("publicKey")
            )
        }
    }
}
