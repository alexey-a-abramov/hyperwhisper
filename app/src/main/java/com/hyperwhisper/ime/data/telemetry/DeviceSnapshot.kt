package com.hyperwhisper.data.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.hyperwhisper.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceSnapshot(
    val deviceModel: String,
    val osVersion: String,
    val appVersionCode: Int,
    val thermalStatus: Int?,
    val batteryPct: Int?,
    val batteryCharging: Boolean?,
    val networkType: NetworkType
)

@Singleton
class DeviceSnapshotProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        deviceModel = (Build.MODEL ?: "unknown"),
        osVersion = (Build.VERSION.RELEASE ?: "unknown"),
        appVersionCode = BuildConfig.VERSION_CODE,
        thermalStatus = thermalStatus(),
        batteryPct = batteryPct(),
        batteryCharging = batteryCharging(),
        networkType = networkType()
    )

    private fun thermalStatus(): Int? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.currentThermalStatus
        } else null
    } catch (t: Throwable) { null }

    private fun batteryPct(): Int? = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (pct == Int.MIN_VALUE) null else pct
    } catch (t: Throwable) { null }

    private fun batteryCharging(): Boolean? = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.isCharging
    } catch (t: Throwable) { null }

    private fun networkType(): NetworkType = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        if (net == null) NetworkType.NONE
        else {
            val caps = cm.getNetworkCapabilities(net)
            when {
                caps == null -> NetworkType.UNKNOWN
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                else -> NetworkType.UNKNOWN
            }
        }
    } catch (t: Throwable) { NetworkType.UNKNOWN }
}
