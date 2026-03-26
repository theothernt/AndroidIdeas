package com.neilturner.perfview.data.cpu.model

data class TopProcessUsage(
    val pid: Int,
    val name: String,
    val cpuPercent: Float,
    val ramPercent: Float,
    val ramMb: Float,
    val user: String,
    val state: String,
)
