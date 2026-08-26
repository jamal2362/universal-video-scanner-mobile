package com.jamal2367.uvsmobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The static HDR metadata a title carries.
 *
 * The scanner reports every value the stream does not carry as 0, so a zero
 * here means "not present" rather than "measured as nothing" - which is why the
 * screen leaves those rows out instead of printing `0 cd/m²`.
 */
@Serializable
data class HdrMetadata(
    @SerialName("hdr10_mdl_max") val hdr10MdlMax: Double = 0.0,
    @SerialName("hdr10_mdl_min") val hdr10MdlMin: Double = 0.0,
    @SerialName("hdr10_max_cll") val hdr10MaxCll: Double = 0.0,
    @SerialName("hdr10_max_fall") val hdr10MaxFall: Double = 0.0,
    @SerialName("rpu_mdl_max") val rpuMdlMax: Double = 0.0,
    @SerialName("rpu_mdl_min") val rpuMdlMin: Double = 0.0,
    @SerialName("rpu_max_cll") val rpuMaxCll: Double = 0.0,
    @SerialName("rpu_max_fall") val rpuMaxFall: Double = 0.0,
    @SerialName("l5_left") val l5Left: Double = 0.0,
    @SerialName("l5_right") val l5Right: Double = 0.0,
    @SerialName("l5_top") val l5Top: Double = 0.0,
    @SerialName("l5_bottom") val l5Bottom: Double = 0.0,
) {
    val hasBaseLayer: Boolean
        get() = hdr10MdlMax > 0 || hdr10MdlMin > 0 || hdr10MaxCll > 0 || hdr10MaxFall > 0

    val hasRpu: Boolean
        get() = rpuMdlMax > 0 || rpuMdlMin > 0 || rpuMaxCll > 0 || rpuMaxFall > 0

    val hasActiveArea: Boolean
        get() = l5Left > 0 || l5Right > 0 || l5Top > 0 || l5Bottom > 0

    val isEmpty: Boolean
        get() = !hasBaseLayer && !hasRpu && !hasActiveArea
}
