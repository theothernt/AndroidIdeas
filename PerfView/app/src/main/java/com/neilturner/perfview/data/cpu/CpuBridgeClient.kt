package com.neilturner.perfview.data.cpu

fun interface CpuBridgeClient {
    @Throws(Exception::class)
    suspend fun fetchCpuPercent(): Float
}
