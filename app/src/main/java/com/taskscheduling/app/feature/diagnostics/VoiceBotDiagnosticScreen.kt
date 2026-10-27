package com.taskscheduling.app.feature.diagnostics

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BgDeepDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark
import com.taskscheduling.app.voice.LanguageMode

@Composable
fun VoiceBotDiagnosticScreen(
    viewModel: VoiceBotDiagnosticViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemOverview by viewModel.systemOverview.collectAsState()
    val audioCapture by viewModel.audioCapture.collectAsState()
    val speechModel by viewModel.speechModel.collectAsState()
    val longSpeech by viewModel.longSpeech.collectAsState()
    val botPipeline by viewModel.floatingBotPipeline.collectAsState()
    val cmdPipeline by viewModel.commandPipeline.collectAsState()
    val testLab by viewModel.testLabState.collectAsState()
    val filteredEvents by viewModel.filteredEvents.collectAsState()
    val reportPreview by viewModel.reportPreviewText.collectAsState()
    val actionConfirmation by viewModel.actionConfirmation.collectAsState()

    // SAF Document Creation Launchers
    val saveTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.saveReportToUri(uri, isJson = false)
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.saveReportToUri(uri, isJson = true)
        }
    }

    // Path & Language selection state
    var selectedPath by remember { mutableStateOf("DIAGNOSTIC_PATH") }
    var selectedLanguage by remember { mutableStateOf(LanguageMode.ENGLISH) }

    // Advanced accordion expansion states
    var expandAudioDetails by remember { mutableStateOf(false) }
    var expandModelDetails by remember { mutableStateOf(false) }
    var expandRecordingDetails by remember { mutableStateOf(false) }
    var expandPipelineDetails by remember { mutableStateOf(false) }
    var expandEventsTimeline by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .verticalScroll(rememberScrollState())
    ) {
        // ---------------------------------------------------------------------
        // Top App Bar
        // ---------------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimaryDark)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Diagnostic Center",
                    color = TextPrimaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Voice capture, speech model & floating bot diagnostics",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = { viewModel.refreshDiagnostics() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan)
            }
            Button(
                onClick = { viewModel.prepareReportPreview() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Preview", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        }

        // Action Confirmation Toast / Banner
        actionConfirmation?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(Color(0xFF064E3B), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(msg, color = Color(0xFFA7F3D0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = { viewModel.clearActionConfirmation() },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFA7F3D0), modifier = Modifier.size(14.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -----------------------------------------------------------------
            // PART B: MAIN SCREEN (Items 1-7)
            // -----------------------------------------------------------------

            // Items 1, 2, 3: System Status, Mic Status, Model Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item 1: System Status
                    val sysLabel = when {
                        testLab.isRecording -> "Recording"
                        testLab.isTranscribing -> "Processing"
                        testLab.outcomeClassification == TestOutcomeClassification.NO_AUDIO_CAPTURED ||
                                testLab.outcomeClassification == TestOutcomeClassification.INFERENCE_TIMEOUT_OR_FAILURE -> "Error"
                        else -> "Ready"
                    }
                    val sysDotColor = when (sysLabel) {
                        "Ready" -> AccentEmerald
                        "Recording" -> AccentRose
                        "Processing" -> AccentAmber
                        else -> Color(0xFFEF4444)
                    }
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("SYSTEM", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(sysDotColor, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(sysLabel, color = TextPrimaryDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Item 2: Microphone Status
                    val isBotListening = systemOverview.floatingBotState == "LISTENING"
                    val micLabel = when {
                        !systemOverview.micPermissionGranted -> "Permission Required"
                        isBotListening || audioCapture.recorderState == "RECORDING" -> "In Use"
                        else -> "Available"
                    }
                    val micColor = when (micLabel) {
                        "Available" -> AccentEmerald
                        "In Use" -> AccentAmber
                        else -> AccentRose
                    }
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("MICROPHONE", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        Text(micLabel, color = micColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Item 3: Speech Model Status
                    val modelLabel = when {
                        speechModel.modelState == "LOADING" -> "Loading"
                        speechModel.modelFileExists -> "Ready"
                        else -> "Failed"
                    }
                    val modelColor = when (modelLabel) {
                        "Ready" -> AccentEmerald
                        "Loading" -> AccentAmber
                        else -> AccentRose
                    }
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("MODEL", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        Text(modelLabel, color = modelColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Microphone Conflict Warning (if mic is held by floating bot)
            if (systemOverview.floatingBotState == "LISTENING") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1A0E)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Microphone Active in Assistant", color = Color(0xFFFCD34D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Floating bot is currently listening. Stop or release it to avoid contention.", color = Color(0xFFFDE68A), fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.releaseAssistantMic() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Release Mic", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Item 4: Last Test Result
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LAST TEST RESULT",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        // Outcome Classification Badge
                        val outcomeBadgeColor = when (testLab.outcomeClassification) {
                            TestOutcomeClassification.SUCCESSFUL_TRANSCRIPT -> AccentEmerald
                            TestOutcomeClassification.AUDIO_CAPTURED_EMPTY_TRANSCRIPT,
                            TestOutcomeClassification.STOPPED_PREMATURELY,
                            TestOutcomeClassification.AUDIO_NEARLY_SILENT -> AccentAmber
                            TestOutcomeClassification.NO_AUDIO_CAPTURED,
                            TestOutcomeClassification.INFERENCE_TIMEOUT_OR_FAILURE,
                            TestOutcomeClassification.ENGINE_BUSY,
                            TestOutcomeClassification.PERMISSION_DENIED -> AccentRose
                            else -> Color(0xFF64748B)
                        }
                        Text(
                            text = testLab.outcomeClassification.displayName,
                            color = outcomeBadgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Transcript or error detail
                    val displayResultText = when {
                        testLab.recognizedText.isNotBlank() -> "\"${testLab.recognizedText}\""
                        testLab.errorDetails != null -> "Error: ${testLab.errorDetails}"
                        testLab.isRecording -> "Listening to audio..."
                        testLab.isTranscribing -> "Decoding with Whisper offline model..."
                        testLab.testId.isBlank() -> "No test run yet. Select a path below and press Start Test."
                        else -> "No speech recognized."
                    }
                    Text(
                        text = displayResultText,
                        color = if (testLab.recognizedText.isNotBlank()) Color.White else Color(0xFF94A3B8),
                        fontSize = 15.sp,
                        fontWeight = if (testLab.recognizedText.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = 20.sp
                    )

                    // Concise technical metadata
                    if (testLab.testId.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val durText = if (testLab.recordingDurationMs > 0) "${testLab.recordingDurationMs}ms" else "Unavailable"
                            val infText = if (testLab.inferenceTimeMs > 0) "${testLab.inferenceTimeMs}ms" else "Unavailable"
                            Text("Duration: $durText (${testLab.capturedSampleCount} samples)", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("Inference: $infText", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Test Setup & Controls (Items 5 & 6)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TEST CONTROLS",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    // Path Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedPath == "DIAGNOSTIC_PATH",
                            onClick = { selectedPath = "DIAGNOSTIC_PATH" },
                            label = { Text("Diagnostic Path", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedPath == "FLOATING_BOT_PATH",
                            onClick = { selectedPath = "FLOATING_BOT_PATH" },
                            label = { Text("Floating Bot Path", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = AccentEmerald
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Language Selector (English Only Enforced)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = { selectedLanguage = LanguageMode.ENGLISH },
                            label = { Text("English (Strictly Enforced)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF334155),
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    // Active Recording or Transcribing state
                    if (testLab.isRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.stopLabTestAndTranscribe() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Text("Stop & Transcribe", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Button(
                                onClick = { viewModel.cancelLabTest() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    } else if (testLab.isTranscribing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NeonCyan, strokeWidth = 2.dp)
                            Text("Transcribing audio...", color = NeonCyan, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { viewModel.cancelLabTest() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Items 5 & 6: Start Test & Floating Bot Test buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.startLabTest(
                                        language = selectedLanguage,
                                        callerLabel = selectedPath
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Start Test", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.startLabTest(
                                        language = selectedLanguage,
                                        callerLabel = "FLOATING_BOT_PATH"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Floating Bot Test", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Item 7: Copy Report, Save Report, and Share Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "REPORT ACTIONS",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Copy Report
                        Button(
                            onClick = { viewModel.copyReportToClipboard() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("Copy", fontSize = 11.sp, color = NeonCyan)
                        }

                        // Save Report (TXT)
                        Button(
                            onClick = {
                                val defaultName = VoiceBotDiagnosticReportExporter.generateDefaultFilename("txt")
                                saveTxtLauncher.launch(defaultName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentEmerald)
                            Spacer(Modifier.width(4.dp))
                            Text("Save .txt", fontSize = 11.sp, color = AccentEmerald)
                        }

                        // Save Report (JSON)
                        Button(
                            onClick = {
                                val defaultName = VoiceBotDiagnosticReportExporter.generateDefaultFilename("json")
                                saveJsonLauncher.launch(defaultName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFF59E0B))
                            Spacer(Modifier.width(4.dp))
                            Text("Save .json", fontSize = 11.sp, color = Color(0xFFF59E0B))
                        }

                        // Share Report
                        Button(
                            onClick = {
                                viewModel.exportReport { shareIntent ->
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Report"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Share", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // ADVANCED DETAILS (Collapsible Expandable Accordions)
            // -----------------------------------------------------------------
            Text(
                text = "ADVANCED DETAILS & TELEMETRY",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Accordion 1: Audio Details & Calibration
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expandAudioDetails = !expandAudioDetails },
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audio Details & Calibration", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (expandAudioDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    AnimatedVisibility(visible = expandAudioDetails) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricRow("Source (Req/Act):", "${audioCapture.requestedSource} / ${audioCapture.actualSource}")
                            MetricRow("Sample Rate / Channels:", "${audioCapture.sampleRate} Hz / ${audioCapture.channelCount} (MONO)")
                            MetricRow("Encoding / Buffer Size:", "${audioCapture.pcmEncoding} / ${audioCapture.bufferSizeBytes} bytes")
                            MetricRow("Captured Samples / Bytes:", "${audioCapture.totalSamplesCaptured} / ${audioCapture.totalCapturedBytes} B")
                            MetricRow("Actual Duration:", "${audioCapture.actualRecordingDurationMs} ms")
                            MetricRow("Effective Audio Duration:", "${audioCapture.effectiveAudioDurationMs} ms")
                            MetricRow("Audio RMS Level:", "%.4f".format(audioCapture.rmsLevel))
                            MetricRow("Peak Amplitude:", "%.4f".format(audioCapture.peakAmplitude))
                            MetricRow("Silence Ratio (<0.005):", "%.1f%%".format(audioCapture.estimatedSilencePercentage))
                            MetricRow("Clipped Samples (>=0.99):", "%.2f%%".format(audioCapture.clippedSamplesPercentage))
                            MetricRow("Buffer Overruns:", "${audioCapture.bufferOverrunsCount}")
                            MetricRow("Read Errors (Last Code):", "${audioCapture.readErrorsCount} (${audioCapture.lastReadErrorCode})")
                        }
                    }
                }
            }

            // Accordion 2: Speech Model Details
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expandModelDetails = !expandModelDetails },
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speech Model & Runtime", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (expandModelDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    AnimatedVisibility(visible = expandModelDetails) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricRow("Engine Implementation:", speechModel.engineImplementation)
                            MetricRow("Loaded Model File:", "${speechModel.modelFilename} (${speechModel.modelSizeBytes / (1024 * 1024)} MB)")
                            MetricRow("File Readable:", "${speechModel.modelFileExists} / ${speechModel.modelFileReadable}")
                            MetricRow("Quantization Type:", speechModel.quantization)
                            MetricRow("Worker Threads:", "${speechModel.configuredThreadCount} threads (Helio G99 Cortex-A76)")
                            MetricRow("Model Load Latency:", "${speechModel.modelLoadDurationMs} ms")
                            MetricRow("Language Mode:", "${speechModel.requestedLanguageCode} (Auto: ${speechModel.isAutoLanguageDetection})")
                            MetricRow("Translation Mode:", if (speechModel.isTranslationEnabled) "TRANSLATE" else "OFFLINE SOURCE (translate=false)")
                            MetricRow("Inference Lock Owner:", speechModel.activeInferenceOwner)
                            MetricRow("Last Native Error:", speechModel.nativeError ?: "None")
                        }
                    }
                }
            }

            // Accordion 3: Recording & Endpointing Metrics
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expandRecordingDetails = !expandRecordingDetails },
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recording & Endpointing", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (expandRecordingDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    AnimatedVisibility(visible = expandRecordingDetails) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricRow("Frames Received / Empty:", "${audioCapture.framesReceivedCount} / ${audioCapture.emptyFramesCount}")
                            MetricRow("Stopped Normally:", "${audioCapture.stoppedNormally}")
                            MetricRow("Stopped Prematurely:", "${audioCapture.stoppedBeforeSpeechFinished}")
                            MetricRow("Sentence Truncated by Limit:", "${longSpeech.isTruncatedByLimit}")
                            MetricRow("Buffer Dispatch Status:", audioCapture.bufferStatus)
                            MetricRow("Failure Stage Cause:", audioCapture.failureCause.name)
                        }
                    }
                }
            }

            // Accordion 4: Floating-Bot 13-Stage Pipeline
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expandPipelineDetails = !expandPipelineDetails },
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Floating-Bot 13-Stage Pipeline", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (expandPipelineDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    AnimatedVisibility(visible = expandPipelineDetails) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            botPipeline.stages.forEach { stage ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stage.stageName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(stage.details, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    val stageColor = when (stage.status) {
                                        "SUCCESS" -> AccentEmerald
                                        "PENDING" -> AccentAmber
                                        "SKIPPED" -> Color(0xFF64748B)
                                        else -> AccentRose
                                    }
                                    Text(stage.status, color = stageColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }

            // Accordion 5: Errors & Event Timeline
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expandEventsTimeline = !expandEventsTimeline },
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Errors & Event Timeline (${filteredEvents.size} events)", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (expandEventsTimeline) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    AnimatedVisibility(visible = expandEventsTimeline) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { viewModel.clearLogs() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF371B1B)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Clear Logs", fontSize = 10.sp, color = AccentRose)
                                }
                            }
                            filteredEvents.take(30).forEach { event ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    val sevColor = when (event.severity) {
                                        DiagnosticSeverity.ERROR -> AccentRose
                                        DiagnosticSeverity.WARNING -> AccentAmber
                                        else -> NeonCyan
                                    }
                                    Box(modifier = Modifier.size(6.dp).background(sevColor, CircleShape).padding(top = 4.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(event.timestampFormatted, color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                            Text(event.eventName, color = sevColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(event.description, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                        if (event.suggestedCheck != null) {
                                            Text("↳ ${event.suggestedCheck}", color = Color(0xFFFCD34D), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Export Report Preview Dialog
    reportPreview?.let { previewText ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissReportPreview() },
            title = {
                Text(
                    text = "Review Diagnostic Report",
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = previewText,
                        color = Color(0xFFCBD5E1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.exportReport { shareIntent ->
                            context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Report"))
                            viewModel.dismissReportPreview()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Text("Save & Share Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissReportPreview() }) {
                    Text("Close", color = TextSecondaryDark)
                }
            },
            containerColor = BgCardDark
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

// -----------------------------------------------------------------------------
// SECTION 1: Live Status & System Overview
// -----------------------------------------------------------------------------
@Composable
private fun LiveStatusSection(
    systemOverview: SystemOverviewState,
    audioCapture: AudioCaptureTelemetry,
    speechModel: SpeechModelTelemetry,
    botPipeline: FloatingBotPipelineTelemetry,
    onRefresh: () -> Unit,
    onCancelRecording: () -> Unit,
    onCancelInference: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Controls Row
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENGINE RECOVERY CONTROLS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Engine: ${speechModel.modelState}",
                        fontSize = 11.sp,
                        color = if (speechModel.modelState == "READY") AccentEmerald else AccentRose,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCancelRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF371B1B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel Recording", fontSize = 11.sp, color = AccentRose)
                    }
                    Button(
                        onClick = onCancelInference,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel Inference", fontSize = 11.sp, color = NeonCyan)
                    }
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Failure Stage Alert (if any)
        if (audioCapture.failureCause != AudioFailureCause.NONE) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF371B1B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRose, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FAILURE DIAGNOSIS: ${audioCapture.failureCause.title}",
                            color = AccentRose,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = audioCapture.failureCause.explanation,
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp
                        )
                        if (audioCapture.failureDetails.isNotBlank()) {
                            Text(
                                text = "Details: ${audioCapture.failureDetails}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live Metric Cards Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBadge(
                label = "MICROPHONE",
                value = if (systemOverview.micPermissionGranted) "GRANTED" else "DENIED",
                statusColor = if (systemOverview.micPermissionGranted) AccentEmerald else AccentRose,
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "RECORDER",
                value = audioCapture.recorderState.replace("RECORDSTATE_", ""),
                statusColor = if (audioCapture.recorderState.contains("RECORDING")) AccentEmerald else NeonCyan,
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "BOT LISTENING",
                value = if (botPipeline.isListening) "ACTIVE" else "IDLE",
                statusColor = if (botPipeline.isListening) AccentEmerald else Color.Gray,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBadge(
                label = "AUDIO SIGNAL",
                value = if (systemOverview.hasMeasurableAudio) "MEASURABLE" else "NEAR SILENT",
                statusColor = if (systemOverview.hasMeasurableAudio) AccentEmerald else AccentRose,
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "RMS LEVEL",
                value = "%.4f".format(audioCapture.rmsLevel),
                statusColor = if (audioCapture.rmsLevel >= 0.005f) AccentEmerald else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "PEAK AMPLITUDE",
                value = "%.4f".format(audioCapture.peakAmplitude),
                statusColor = if (audioCapture.peakAmplitude > 0.005f) AccentEmerald else AccentRose,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBadge(
                label = "WHISPER MODEL",
                value = speechModel.modelFilename,
                statusColor = if (speechModel.modelState == "READY") AccentEmerald else AccentRose,
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "LANGUAGE",
                value = systemOverview.selectedLanguage,
                statusColor = NeonCyan,
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "ACCESSIBILITY",
                value = if (systemOverview.isAccessibilityConnected) "CONNECTED" else "OFFLINE",
                statusColor = if (systemOverview.isAccessibilityConnected) AccentEmerald else AccentRose,
                modifier = Modifier.weight(1f)
            )
        }

        // Last Recognition Result Card
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LAST RECOGNITION RESULT",
                    fontSize = 11.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (systemOverview.lastRecognitionResult.isNotBlank()) "\"${systemOverview.lastRecognitionResult}\"" else "No speech recognized yet",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (systemOverview.lastRecognitionResult.isNotBlank()) Color.White else Color.Gray
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Duration: ${systemOverview.lastTranscriptionDurationMs}ms",
                        fontSize = 11.sp,
                        color = TextSecondaryDark
                    )
                    Text(
                        text = "Workflow: ${systemOverview.lastWorkflowStatus}",
                        fontSize = 11.sp,
                        color = TextSecondaryDark
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SECTION 2: Audio & Model Diagnostics
// -----------------------------------------------------------------------------
@Composable
private fun AudioAndModelDiagnosticsSection(
    audioCapture: AudioCaptureTelemetry,
    speechModel: SpeechModelTelemetry,
    longSpeech: LongSpeechTelemetry
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section: Real Audio Capture Telemetry
        DiagnosticCategoryCard(title = "AUDIO SIGNAL & BUFFER STATISTICS") {
            DetailRow("Requested Audio Source", audioCapture.requestedSource)
            DetailRow("Actual Audio Source", audioCapture.actualSource)
            DetailRow("Sample Rate / Channels", "${audioCapture.sampleRate} Hz / ${if (audioCapture.channelCount == 1) "Mono" else "Stereo"}")
            DetailRow("PCM Format", audioCapture.pcmEncoding)
            DetailRow("Buffer Size", "${audioCapture.bufferSizeBytes} bytes")
            DetailRow("Total Samples Captured", "${audioCapture.totalSamplesCaptured} samples (${audioCapture.totalCapturedBytes} B)")
            DetailRow("Recording Duration", "${audioCapture.actualRecordingDurationMs} ms (Effective: ${audioCapture.effectiveAudioDurationMs} ms)")
            DetailRow("RMS Level", "%.4f".format(audioCapture.rmsLevel))
            DetailRow("Peak Amplitude", "%.4f".format(audioCapture.peakAmplitude))
            DetailRow("Min / Max Sample", "${"%.4f".format(audioCapture.minSampleValue)} / ${"%.4f".format(audioCapture.maxSampleValue)}")
            DetailRow("Near-Zero Samples (< 0.001)", "%.2f%%".format(audioCapture.nearZeroSamplesPercentage))
            DetailRow("Clipped Samples (>= 0.99)", "%.2f%%".format(audioCapture.clippedSamplesPercentage))
            DetailRow("Estimated Silence (< 0.005)", "%.2f%%".format(audioCapture.estimatedSilencePercentage))
            DetailRow("Buffer Dispatch Status", audioCapture.bufferStatus)

            Text(
                text = "Note: Silence estimation from amplitude is not the same as reliable speech detection.",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Section: Whisper Speech Model & Native Bridge
        DiagnosticCategoryCard(title = "SPEECH MODEL & NATIVE JNI BRIDGE") {
            DetailRow("Engine Implementation", speechModel.engineImplementation)
            DetailRow("Native Whisper Loaded", if (speechModel.nativeLibraryLoaded) "YES (libwhisper.so)" else "NO")
            DetailRow("Model Filename", speechModel.modelFilename)
            DetailRow("Model Size", "${speechModel.modelSizeBytes / (1024 * 1024)} MB")
            DetailRow("Model File Exists / Readable", "${speechModel.modelFileExists} / ${speechModel.modelFileReadable}")
            DetailRow("Model Ready State", speechModel.modelState)
            DetailRow("Initialization Latency", "${speechModel.modelLoadDurationMs} ms")
            DetailRow("Quantization Type", speechModel.quantization)
            DetailRow("Configured Thread Count", "${speechModel.configuredThreadCount} threads (Optimized ARM Cortex-A76)")
            DetailRow("Inference Parameters", speechModel.inferenceParameters)
            DetailRow("Language Code", "${speechModel.requestedLanguageCode} (Auto: ${speechModel.isAutoLanguageDetection})")
            DetailRow("Translation Mode", if (speechModel.isTranslationEnabled) "TRANSLATE" else "FALSE (Offline source preservation)")
            DetailRow("Inference Lock Owner", speechModel.activeInferenceOwner)
            DetailRow("Native Error", speechModel.nativeError ?: "None")
        }

        // Section: Long Speech & Endpointing
        DiagnosticCategoryCard(title = "LONG-SPEECH & ENDPOINTING THRESHOLDS") {
            DetailRow("Max Recording Duration Limit", "${longSpeech.configuredMaxRecordingMs} ms (10 seconds ceiling)")
            DetailRow("Endpoint Silence Timeout", "${longSpeech.configuredEndpointSilenceMs} ms (1200ms EN / 1600ms HI)")
            DetailRow("Total Utterance Duration", "${longSpeech.totalDurationMs} ms")
            DetailRow("Buffers Collected", "${longSpeech.buffersCollected}")
            DetailRow("Truncated by 10s Limit", if (longSpeech.isTruncatedByLimit) "YES (Utterance was cut off)" else "NO")
            DetailRow("Stopped by Silence Detector", if (longSpeech.stoppedBySilenceDetection) "YES" else "NO")
            DetailRow("Stopped by Word Pause", if (longSpeech.stoppedByWordPause) "YES (Natural pause triggered endpoint)" else "NO")
            DetailRow("Buffer Reached Whisper", if (longSpeech.transcriptionReceivedFullBuffer) "YES" else "NO")

            if (longSpeech.stageTimeline.isNotEmpty()) {
                Text(
                    text = "STAGE TIMELINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier.padding(top = 8.dp)
                )
                longSpeech.stageTimeline.forEach { stage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "• ${stage.stageName}", fontSize = 11.sp, color = TextPrimaryDark)
                        Text(text = "${stage.durationMs}ms [${stage.status}]", fontSize = 11.sp, color = NeonCyan)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SECTION 3: English, Hindi, and Hinglish Test Lab
// -----------------------------------------------------------------------------
@Composable
private fun TestLabSection(
    testLab: TestLabRunState,
    onStartTest: (LanguageMode, String, String, String) -> Unit,
    onStopTest: () -> Unit,
    onCancelTest: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(LanguageMode.ENGLISH) }
    var selectedCaller by remember { mutableStateOf("DIAGNOSTIC_PATH") }
    var expectedTextInput by remember { mutableStateOf("") }
    var selectedGuidedPhrase by remember { mutableStateOf("") }

    val guidedPhrases = when (selectedLanguage) {
        LanguageMode.ENGLISH -> listOf(
            "Hello.",
            "Turn on dark mode.",
            "Open settings and search for display options."
        )
        LanguageMode.HINDI -> listOf(
            "नमस्ते।",
            "डार्क मोड चालू करो।",
            "सेटिंग्स खोलो और डिस्प्ले विकल्प खोजो।"
        )
        LanguageMode.HINGLISH -> listOf(
            "Dark mode on karo.",
            "Settings kholo aur display options search karo."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Test Configuration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "1. SELECT LANGUAGE MODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LanguageMode.values().forEach { mode ->
                        FilterChip(
                            selected = selectedLanguage == mode,
                            onClick = {
                                selectedLanguage = mode
                                selectedGuidedPhrase = ""
                            },
                            label = { Text(mode.displayName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentIndigo,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Text(
                    text = "2. SELECT CALLER PIPELINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedCaller == "DIAGNOSTIC_PATH",
                        onClick = { selectedCaller = "DIAGNOSTIC_PATH" },
                        label = { Text("Diagnostic Path", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedCaller == "FLOATING_BOT_PATH",
                        onClick = { selectedCaller = "FLOATING_BOT_PATH" },
                        label = { Text("Floating Bot Path", fontSize = 11.sp) }
                    )
                }

                Text(
                    text = "3. GUIDED TEST PHRASES (OPTIONAL)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace
                )
                guidedPhrases.forEach { phrase ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selectedGuidedPhrase == phrase) AccentIndigo.copy(alpha = 0.3f) else Color(0xFF0F172A),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (selectedGuidedPhrase == phrase) AccentIndigo else BorderSubtleDark,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedGuidedPhrase = phrase
                                expectedTextInput = phrase
                            }
                            .padding(10.dp)
                    ) {
                        Text(text = phrase, fontSize = 12.sp, color = TextPrimaryDark)
                    }
                }

                OutlinedTextField(
                    value = expectedTextInput,
                    onValueChange = { expectedTextInput = it },
                    label = { Text("Expected text for comparison (optional)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFCBD5E1),
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderSubtleDark
                    )
                )

                // Recording Control Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!testLab.isRecording && !testLab.isTranscribing) {
                        Button(
                            onClick = {
                                onStartTest(selectedLanguage, selectedCaller, selectedGuidedPhrase, expectedTextInput)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Test Recording")
                        }
                    } else if (testLab.isRecording) {
                        Button(
                            onClick = onStopTest,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop & Transcribe")
                        }
                        Button(
                            onClick = onCancelTest,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel")
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Transcribing with Whisper...")
                        }
                    }
                }
            }
        }

        // Test Outcome & Result Card
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEST OUTCOME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = testLab.outcomeClassification.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (testLab.outcomeClassification) {
                            TestOutcomeClassification.SUCCESSFUL_TRANSCRIPT -> AccentEmerald
                            TestOutcomeClassification.INCONCLUSIVE -> Color.Gray
                            else -> AccentRose
                        }
                    )
                }

                DetailRow("Recognized Text", if (testLab.recognizedText.isNotBlank()) "\"${testLab.recognizedText}\"" else "None")
                DetailRow("Expected Text", if (testLab.expectedText.isNotBlank()) "\"${testLab.expectedText}\"" else "None")
                DetailRow("Language Code", testLab.languageMode.code)
                DetailRow("Audio Duration", "${testLab.recordingDurationMs} ms")
                DetailRow("RMS / Peak", "${"%.4f".format(testLab.rmsLevel)} / ${"%.4f".format(testLab.peakAmplitude)}")
                DetailRow("Silence Ratio", "${"%.1f".format(testLab.silencePercentage)}%")
                DetailRow("Whisper Inference (Pure Decode)", "${testLab.inferenceTimeMs} ms")
                DetailRow("Total Latency (Submission to Result)", "${testLab.totalTimeMs} ms")

                if (testLab.errorDetails != null) {
                    Text(
                        text = "Error: ${testLab.errorDetails}",
                        fontSize = 11.sp,
                        color = AccentRose,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = "Commands are NEVER executed from Test Lab transcripts.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SECTION 4: Bot & Command Pipeline
// -----------------------------------------------------------------------------
@Composable
private fun BotAndCommandPipelineSection(
    botPipeline: FloatingBotPipelineTelemetry,
    commandPipeline: CommandPipelineTelemetry,
    longSpeech: LongSpeechTelemetry
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Component Sharing Matrix
        DiagnosticCategoryCard(title = "COMPONENT SHARING & ARCHITECTURE IDENTITY") {
            IdentityMatrixRow("Recorder Implementation", "AudioRecord 16kHz Mono", botPipeline.sharedRecorderImplementation)
            IdentityMatrixRow("Audio Source", "DEFAULT", botPipeline.sharedAudioSource)
            IdentityMatrixRow("PCM Conversion", "16-bit Signed Float", botPipeline.sharedPcmFormat)
            IdentityMatrixRow("Audio Preprocessing", "prepareAudioForWhisper", botPipeline.sharedPreprocessing)
            IdentityMatrixRow("Whisper Model Instance", "Process-wide Singleton Instance", botPipeline.sharedModelInstance)
            IdentityMatrixRow("Transcription Entry Point", "SpeechRecognitionService.transcribe", botPipeline.sharedTranscriptionEntryPoint)
            IdentityMatrixRow("Inference Mutex Lock", "inferenceLock", botPipeline.sharedInferenceLock)
            IdentityMatrixRow("Language Configuration", "LanguageMode (en / hi / auto)", botPipeline.sharedLanguageConfig)
        }

        // 13-Stage Floating Bot Pipeline
        DiagnosticCategoryCard(title = "FLOATING BOT PIPELINE STAGES") {
            val allStages = listOf(
                "Bot Activated",
                "Microphone Started",
                "Audio Buffers Received",
                "Speech Endpoint Reached",
                "Audio Finalized",
                "Recognition Requested",
                "Engine Accepted Job",
                "Inference Started",
                "Transcript Returned",
                "Command Resolver Called",
                "Command Matched",
                "Confirmation Displayed",
                "Workflow Started"
            )

            allStages.forEachIndexed { idx, stageName ->
                val executed = botPipeline.stages.find { it.stageName == stageName }
                val isCurrent = botPipeline.currentStage == stageName
                val isFailed = botPipeline.failureStage == stageName

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                when {
                                    isFailed || executed?.status == "FAILED" -> AccentRose
                                    executed?.status == "EMPTY_TRANSCRIPT" -> AccentAmber
                                    executed?.status == "SKIPPED" -> Color.Gray
                                    executed?.status == "CANCELLED" -> Color.DarkGray
                                    executed?.status == "SUCCESS" -> AccentEmerald
                                    executed != null -> AccentEmerald
                                    isCurrent -> NeonCyan
                                    else -> Color.DarkGray
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${idx + 1}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stageName,
                            fontSize = 12.sp,
                            fontWeight = if (executed != null || isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (executed != null || isCurrent) Color.White else Color.Gray
                        )
                        if (executed != null) {
                            val durationText = executed.durationMs?.let { " (${it}ms)" } ?: ""
                            Text(
                                text = "${executed.status}$durationText ${executed.details}".trim(),
                                fontSize = 10.sp,
                                color = when (executed.status) {
                                    "FAILED" -> AccentRose
                                    "EMPTY_TRANSCRIPT" -> AccentAmber
                                    "SKIPPED" -> Color.Gray
                                    "CANCELLED" -> Color.LightGray
                                    else -> NeonCyan
                                }
                            )
                        }
                    }
                }
            }
        }

        // Command Resolver & Safety Pipeline
        DiagnosticCategoryCard(title = "COMMAND RESOLVER & SAFETY POLICIES") {
            DetailRow("Raw Recognized Text", if (commandPipeline.exactRecognizedText.isNotBlank()) "\"${commandPipeline.exactRecognizedText}\"" else "None")
            DetailRow("Normalized Text", if (commandPipeline.normalizedText.isNotBlank()) "\"${commandPipeline.normalizedText}\"" else "None")
            DetailRow("Resolver Language Mode", commandPipeline.resolverLanguageMode)
            DetailRow("Resolver Entry Time", commandPipeline.resolverEntryTimeFormatted.ifBlank { "N/A" })
            DetailRow("Resolver Output", commandPipeline.resolverResult.ifBlank { "None" })
            DetailRow("Matched Command / Workflow", commandPipeline.matchedCommandOrWorkflowId.ifBlank { "None" })
            DetailRow("Confirmation Required", if (commandPipeline.isConfirmationRequired) "YES (Strict Safety Policy)" else "NO")
            DetailRow("Confirmation Displayed", if (commandPipeline.isConfirmationDisplayed) "YES" else "NO")
            DetailRow("User Action", commandPipeline.userConfirmationAction)
            DetailRow("Workflow Status", commandPipeline.workflowExecutionStatus)
            if (commandPipeline.workflowError != null) {
                DetailRow("Workflow Error", commandPipeline.workflowError, isError = true)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SECTION 5: Event Timeline & Error Logs
// -----------------------------------------------------------------------------
@Composable
private fun EventTimelineSection(
    events: List<DiagnosticEvent>,
    selectedComponent: DiagnosticComponent?,
    selectedSeverity: DiagnosticSeverity?,
    searchQuery: String,
    onSelectComponent: (DiagnosticComponent?) -> Unit,
    onSelectSeverity: (DiagnosticSeverity?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search & Clear Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search logs", fontSize = 11.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFCBD5E1),
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = BorderSubtleDark
                )
            )
            Button(
                onClick = onClearLogs,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF371B1B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Clear", fontSize = 11.sp, color = AccentRose)
            }
        }

        // Severity Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedSeverity == null,
                onClick = { onSelectSeverity(null) },
                label = { Text("All (${events.size})", fontSize = 11.sp) }
            )
            DiagnosticSeverity.values().forEach { sev ->
                FilterChip(
                    selected = selectedSeverity == sev,
                    onClick = { onSelectSeverity(if (selectedSeverity == sev) null else sev) },
                    label = { Text(sev.name, fontSize = 11.sp) }
                )
            }
        }

        // Component Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedComponent == null,
                onClick = { onSelectComponent(null) },
                label = { Text("All Components", fontSize = 11.sp) }
            )
            DiagnosticComponent.values().forEach { comp ->
                FilterChip(
                    selected = selectedComponent == comp,
                    onClick = { onSelectComponent(if (selectedComponent == comp) null else comp) },
                    label = { Text(comp.name.replace("_", " "), fontSize = 11.sp) }
                )
            }
        }

        // Events List
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No diagnostic events match current filters.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                events.forEach { event ->
                    EventCard(event)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: DiagnosticEvent) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCardDark),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (event.severity == DiagnosticSeverity.ERROR) AccentRose else BorderSubtleDark
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "[${event.severity}]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (event.severity) {
                            DiagnosticSeverity.ERROR -> AccentRose
                            DiagnosticSeverity.WARNING -> Color(0xFFFCD34D)
                            DiagnosticSeverity.INFO -> NeonCyan
                            DiagnosticSeverity.DEBUG -> Color.Gray
                        }
                    )
                    Text(
                        text = event.eventName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = event.timestampFormatted,
                    fontSize = 10.sp,
                    color = TextSecondaryDark,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = event.description,
                fontSize = 11.sp,
                color = Color(0xFFCBD5E1)
            )

            if (event.suggestedCheck != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                        .padding(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Suggested Check: ${event.suggestedCheck}",
                        fontSize = 10.sp,
                        color = NeonCyan
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// HELPER COMPONENTS
// -----------------------------------------------------------------------------
@Composable
private fun MetricBadge(
    label: String,
    value: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BgCardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = label, fontSize = 9.sp, color = TextSecondaryDark, fontWeight = FontWeight.Bold)
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiagnosticCategoryCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                fontFamily = FontFamily.Monospace
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = TextSecondaryDark)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isError) AccentRose else Color.White
        )
    }
}

@Composable
private fun IdentityMatrixRow(
    componentName: String,
    configuration: String,
    isShared: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = componentName, fontSize = 11.sp, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            Text(text = configuration, fontSize = 10.sp, color = TextSecondaryDark)
        }
        Text(
            text = if (isShared) "✓ SHARED" else "✗ SEPARATE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isShared) AccentEmerald else AccentRose
        )
    }
}
