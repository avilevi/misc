package com.hcexport

import android.content.Context
import androidx.core.content.ContextCompat

object SourceBrands {

    data class BrandInfo(
        val displayName: String,
        val iconResId: Int,
        val brandColor: Int,
    )

    private val knownBrands = listOf(
        BrandSpec("samsung", "Samsung Health", R.drawable.ic_source_samsung_health, 0xFF0D99FF.toInt()),
        BrandSpec("fitbit",  "Fitbit",          R.drawable.ic_source_fitbit,         0xFF00B0B9.toInt()),
        BrandSpec("garmin",  "Garmin Connect",  R.drawable.ic_source_garmin,         0xFF0082C3.toInt()),
        BrandSpec("polar",   "Polar Flow",      R.drawable.ic_source_polar,          0xFFE60000.toInt()),
        BrandSpec("strava",  "Strava",          R.drawable.ic_source_strava,         0xFFFC4C02.toInt()),
        BrandSpec("whoop",   "WHOOP",           R.drawable.ic_source_whoop,          0xFF00ADEF.toInt()),
        BrandSpec("circular","Circular",        R.drawable.ic_source_circular,       0xFF1A1A1A.toInt()),
        BrandSpec("lyfta",   "Lyfta",           R.drawable.ic_source_lyfta,          0xFF8B5CF6.toInt()),
        BrandSpec("withings","Withings",        R.drawable.ic_source_withings,       0xFF4A90D9.toInt()),
        BrandSpec("huawei",  "Huawei Health",   R.drawable.ic_source_huawei,         0xFFCF0A2C.toInt()),
        BrandSpec("fitness", "Google Fit",      R.drawable.ic_source_google_fit,     0xFF4285F4.toInt()),
    )

    fun forPackage(pkg: String): BrandInfo {
        val spec = knownBrands.find { pkg.contains(it.keyword, ignoreCase = true) }
        return if (spec != null) {
            BrandInfo(spec.displayName, spec.iconResId, spec.brandColor)
        } else {
            val fallback = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            BrandInfo(fallback, R.drawable.ic_source_generic, 0xFF64748B.toInt())
        }
    }

    fun displayName(pkg: String): String = forPackage(pkg).displayName

    fun iconResId(pkg: String): Int = forPackage(pkg).iconResId

    private data class BrandSpec(
        val keyword: String,
        val displayName: String,
        val iconResId: Int,
        val brandColor: Int,
    )
}
