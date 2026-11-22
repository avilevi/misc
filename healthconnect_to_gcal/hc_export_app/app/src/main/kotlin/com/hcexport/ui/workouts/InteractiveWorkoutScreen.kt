package com.hcexport.ui.workouts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hcexport.R
import com.hcexport.Workout
import com.hcexport.ui.AiWorkoutPanel
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.PageHeader
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.rememberAiWorkoutState
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.launch

/**
 * The AI workout on its own screen, opened from the Workouts tab. The journal
 * entry embeds the same panel, so this is a frame around it rather than a
 * second presentation of the workout.
 */
@Composable
fun InteractiveWorkoutScreen(
    date: String,
    typeKey: String,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberAiWorkoutState(
        date = date,
        typeKey = typeKey,
        autoGenerate = true,
        lookupWorkout = true,
    )

    // Values land on disk before the screen goes away, whatever else happens.
    BackHandler {
        scope.launch {
            runCatching { state.save() }
            onBack()
        }
    }

    HcBackground(accent = Teal) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "AI workout",
                eyebrow = "Preview",
                onBack = onBack,
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = 4.dp,
                    bottom = 36.dp,
                ),
            ) {
                item {
                    if (!state.loading && state.workout == null && state.plan == null) {
                        EmptyState(
                            iconRes = R.drawable.ic_calendar,
                            title = "Workout not found",
                            message = "This workout is not in the local database.",
                            accent = Teal,
                        )
                    } else {
                        AiWorkoutPanel(
                            state = state,
                            onOpenAiSettings = onOpenAiSettings,
                            readOnly = true,
                            original = { OriginalWorkoutCard(state.workout) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginalWorkoutCard(workout: Workout?) {
    val content = workout?.content ?: return
    HcCard(contentPadding = PaddingValues(18.dp)) {
        SectionLabel("Original workout", color = Slate)
        Spacer(Modifier.height(8.dp))
        Text(
            content,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = Mist,
        )
    }
}
