package com.hcexport.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * True in the default Minimal design: screens keep the data and the actions
 * and drop the explanatory copy. Verbose (false) is the original design.
 */
val LocalMinimalDesign = compositionLocalOf { true }
