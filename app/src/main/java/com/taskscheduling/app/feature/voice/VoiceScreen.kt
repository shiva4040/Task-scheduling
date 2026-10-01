package com.taskscheduling.app.feature.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.automation.VoiceEngine
import com.taskscheduling.app.automation.VoiceState
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BgDeepDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceViewModel(
    private val voiceEngine: VoiceEngine,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.voiceState
    val recognizedText: StateFlow<String> = voiceEngine.recognizedText

    private val _matchedTask = MutableStateFlow<Task?>(null)
    val matchedTask: StateFlow<Task?> = _matchedTask.asStateFlow()

    fun startListening() {
        _matchedTask.value = null
        viewModelScope.launch {
            voiceEngine.startListening()
            val text = voiceEngine.recognizedText.value
            if (text.isNotBlank()) {
                _matchedTask.value = taskRepository.findTaskByVoiceIntent(text)
            }
        }
    }

    fun stopListening() {
        voiceEngine.stopListening()
    }

    fun selectPresetCommand(cmd: String) {
        viewModelScope.launch {
            val task = voiceEngine.processCommand(cmd)
            _matchedTask.value = task
        }
    }
}

@Composable
fun VoiceScreen(
    viewModel: VoiceViewModel,
    onNavigateToExecution: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val matchedTask by viewModel.matchedTask.collectAsState()

    // Pulse animation for mic listening
    val pulseScale = remember { Animatable(1.0f) }
    LaunchedEffect(voiceState) {
        if (voiceState == VoiceState.LISTENING) {
            pulseScale.animateTo(
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            pulseScale.snapTo(1.0f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimaryDark)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = "Voice Command", color = TextPrimaryDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "Hands-free touchless control", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Center Voice Feedback Area
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Big Pulse Mic Button
            Box(
                modifier = Modifier
                    .size((120 * pulseScale.value).dp)
                    .background(
                        when (voiceState) {
                            VoiceState.LISTENING -> AccentRose
                            VoiceState.PROCESSING -> AccentIndigo
                            VoiceState.COMPLETED -> AccentEmerald
                            else -> NeonCyan
                        },
                        CircleShape
                    )
                    .border(3.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .clickable {
                        if (voiceState == VoiceState.LISTENING) {
                            viewModel.stopListening()
                        } else {
                            viewModel.startListening()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Microphone",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // State Title
            Text(
                text = when (voiceState) {
                    VoiceState.LISTENING -> "LISTENING..."
                    VoiceState.PROCESSING -> "PROCESSING COMMAND..."
                    VoiceState.COMPLETED -> "TASK FOUND!"
                    VoiceState.ERROR -> "TRY AGAIN"
                    else -> "TAP MIC TO SPEAK"
                },
                color = when (voiceState) {
                    VoiceState.LISTENING -> AccentRose
                    VoiceState.COMPLETED -> AccentEmerald
                    else -> NeonCyan
                },
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Recognized Speech Bubble
            if (recognizedText.isNotEmpty()) {
                Text(
                    text = "\"$recognizedText\"",
                    color = TextPrimaryDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Say things like \"Change my wallpaper\", \"Make text bigger\", or \"Turn on dark mode\"",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Matched Task Card Action
            matchedTask?.let { task ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(BgCardDark, RoundedCornerShape(16.dp))
                        .border(1.dp, AccentEmerald, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = task.title, color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${task.workflow.steps.size} coordinate actions", color = TextSecondaryDark, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onNavigateToExecution(task.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RUN")
                        }
                    }
                }
            }
        }

        // Bottom Suggested Quick Voice Prompts
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("OR TAP TEST COMMAND:", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PresetVoiceChip("Wallpaper", "Change wallpaper") { viewModel.selectPresetCommand(it) }
                PresetVoiceChip("Dark Mode", "Turn on dark mode") { viewModel.selectPresetCommand(it) }
                PresetVoiceChip("Text Size", "Make text bigger") { viewModel.selectPresetCommand(it) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PresetVoiceChip(label: String, phrase: String, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .background(BgCardDark, RoundedCornerShape(12.dp))
            .border(1.dp, BorderSubtleDark, RoundedCornerShape(12.dp))
            .clickable { onSelect(phrase) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
