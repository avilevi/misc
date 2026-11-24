package com.hcexport.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hcexport.DesignStyle
import com.hcexport.Prefs
import com.hcexport.ui.SegmentedControl
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle

@Composable
fun AppearanceSettingsPage() {
    val context = LocalContext.current
    var style by remember { mutableStateOf(Prefs.getDesignStyle(context)) }

    SettingsPageList {
        item {
            SettingsNote("How much the journal explains. Minimal shows the facts and nothing else.")
        }

        item { SettingsSectionHeader("Journal entry") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "Minimal keeps one workout card, the plan and the numbers. " +
                        "Verbose adds the labels and the explanatory text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    options = DesignStyle.entries,
                    selected = style,
                    label = { it.label },
                    onSelect = {
                        style = it
                        Prefs.setDesignStyle(context, it)
                    },
                    accent = Periwinkle,
                )
            }
        }
    }
}
