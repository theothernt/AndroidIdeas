package com.neilturner.perfview.data.cpu

data class TopProcessUsage(
    val pid: Int,
    val name: String,
    val cpuPercent: Float,
    val user: String,
    val state: String,
)
