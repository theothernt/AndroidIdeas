package com.neilturner.perfview.data.cpu

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpCpuBridgeClient(
    private val endpointUrl: String,
) : CpuBridgeClient {
    override suspend fun fetchCpuPercent(): Float = withContext(Dispatchers.IO) {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2_000
            readTimeout = 2_000
            setRequestProperty("Accept", "text/plain")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("Bridge server responded with HTTP $responseCode")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            body.toFloatOrNull()
                ?.coerceIn(0f, 100f)
                ?: error("Bridge server returned invalid CPU data")
        } finally {
            connection.disconnect()
        }
    }
}
