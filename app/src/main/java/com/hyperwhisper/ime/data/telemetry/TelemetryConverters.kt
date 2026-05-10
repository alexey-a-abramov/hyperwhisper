package com.hyperwhisper.data.telemetry

import androidx.room.TypeConverter

class TelemetryConverters {
    @TypeConverter fun sessionTypeToString(t: SessionType): String = t.name
    @TypeConverter fun sessionTypeFromString(s: String): SessionType =
        runCatching { SessionType.valueOf(s) }.getOrDefault(SessionType.CLOUD)

    @TypeConverter fun coldStartKindToString(t: ColdStartKind): String = t.name
    @TypeConverter fun coldStartKindFromString(s: String): ColdStartKind =
        runCatching { ColdStartKind.valueOf(s) }.getOrDefault(ColdStartKind.WARM)

    @TypeConverter fun networkTypeToString(t: NetworkType): String = t.name
    @TypeConverter fun networkTypeFromString(s: String): NetworkType =
        runCatching { NetworkType.valueOf(s) }.getOrDefault(NetworkType.UNKNOWN)
}
