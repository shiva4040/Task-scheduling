package com.taskscheduling.app.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChatSender { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String,
    val sender: ChatSender,
    val text: String,
    val timestamp: String,
    val associatedTask: Task? = null
)

class AssistantViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = "m0",
                sender = ChatSender.ASSISTANT,
                text = "Hello Shiva! Tell me what you'd like to do on your phone, e.g. \"Turn on dark mode\", \"Change wallpaper\", or \"Make text bigger\".",
                timestamp = timeFormat.format(Date())
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val userMsg = ChatMessage(
            id = "user_${System.currentTimeMillis()}",
            sender = ChatSender.USER,
            text = content,
            timestamp = timeFormat.format(Date())
        )
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            val matched = taskRepository.findTaskByVoiceIntent(content)
            val assistantResponse = if (matched != null) {
                ChatMessage(
                    id = "bot_${System.currentTimeMillis()}",
                    sender = ChatSender.ASSISTANT,
                    text = "I found the predefined task \"${matched.title}\" with ${matched.workflow.steps.size} coordinate actions. Would you like to run it now?",
                    timestamp = timeFormat.format(Date()),
                    associatedTask = matched
                )
            } else {
                ChatMessage(
                    id = "bot_${System.currentTimeMillis()}",
                    sender = ChatSender.ASSISTANT,
                    text = "I couldn't find a matching task for \"$content\". You can say \"Change wallpaper\", \"Make text bigger\", or create a new custom workflow in Builder.",
                    timestamp = timeFormat.format(Date())
                )
            }
            _messages.value = _messages.value + assistantResponse
        }
    }
}

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onNavigateToExecution: (String) -> Unit,
    onNavigateToBuilder: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .padding(16.dp)
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
                Text(text = "Task Assistant", color = TextPrimaryDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "Chat-driven phone automation", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    onRunTask = { onNavigateToExecution(it) },
                    onViewWorkflow = { onNavigateToBuilder(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask assistant to do something...", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = BorderSubtleDark,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    focusedContainerColor = BgCardDark,
                    unfocusedContainerColor = BgCardDark
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendMessage(input)
                        input = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(NeonCyan, RoundedCornerShape(14.dp))
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = BgDeepDark)
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onRunTask: (String) -> Unit,
    onViewWorkflow: (String) -> Unit
) {
    val isUser = message.sender == ChatSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    if (isUser) AccentIndigo else BgCardDark,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .border(
                    1.dp,
                    if (isUser) NeonCyan.copy(alpha = 0.4f) else BorderSubtleDark,
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = TextPrimaryDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Action Buttons if a task was matched!
                message.associatedTask?.let { task ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onRunTask(task.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("RUN TASK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onViewWorkflow(task.id) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("VIEW", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.timestamp,
                    color = if (isUser) Color(0xCCFFFFFF) else Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
