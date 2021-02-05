package com.ajnsnewmedia.kitchenstories.homeconnect.model.programs

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SpecificAvailableProgram(
    val key: String,
    val name: String,
    val options: List<ProgramOptions>
)

@JsonClass(generateAdapter = true)
data class ProgramOptions(
    val key: String,
    val name: String,
    val type: String,
    val unit: String,
    val constraints: ProgramConstraints
)

@JsonClass(generateAdapter = true)
data class ProgramConstraints(
    val min: Int,
    val max: Int,
    val stepSize: Int,
    val allowedValues: List<String>
)