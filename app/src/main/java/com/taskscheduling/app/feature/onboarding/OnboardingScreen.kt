package com.taskscheduling.app.feature.onboarding

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentViolet
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BgDeepDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark

data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val badge: String,
    val emoji: String
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slides = listOf(
        OnboardingSlide(
            title = "Meet Task Scheduling",
            subtitle = "Control your phone through simple commands without physically touching the screen.",
            badge = "TOUCHLESS CONTROL",
            emoji = "📱"
        ),
        OnboardingSlide(
            title = "Watch Every Action",
            subtitle = "See every touch, ripple, swipe path, and movement in real-time right on the screen.",
            badge = "VISUAL TRACKING",
            emoji = "🎯"
        ),
        OnboardingSlide(
            title = "Build Your Own Workflows",
            subtitle = "Create repeatable phone action sequences with an interactive coordinate picker.",
            badge = "WORKFLOW BUILDER",
            emoji = "⚙️"
        ),
        OnboardingSlide(
            title = "Designed For Everyone",
            subtitle = "Empower elderly users, people with motor challenges, and anyone wanting hands-free ease.",
            badge = "ACCESSIBILITY FIRST",
            emoji = "♿"
        ),
        OnboardingSlide(
            title = "Ready?",
            subtitle = "Start using Task Scheduling to navigate your phone effortlessly.",
            badge = "VERSION 1.0 READY",
            emoji = "🚀"
        )
    )

    var currentSlideIndex by remember { mutableIntStateOf(0) }
    val slide = slides[currentSlideIndex]

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Skip Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onFinishOnboarding) {
                Text("Skip", color = TextSecondaryDark, fontSize = 14.sp)
            }
        }

        // Center Content Box
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(AccentIndigo.copy(alpha = 0.2f), CircleShape)
                    .border(2.dp, NeonCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = slide.emoji, fontSize = 52.sp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .background(Color(0x2638BDF8), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = slide.badge,
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = slide.title,
                color = TextPrimaryDark,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = slide.subtitle,
                color = TextSecondaryDark,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Slide indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slides.indices.forEach { idx ->
                    Box(
                        modifier = Modifier
                            .size(if (idx == currentSlideIndex) 24.dp else 8.dp, 8.dp)
                            .background(
                                if (idx == currentSlideIndex) NeonCyan else Color(0x33FFFFFF),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }

        // Bottom Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentSlideIndex > 0) {
                OutlinedButton(
                    onClick = { currentSlideIndex-- },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            if (currentSlideIndex < slides.size - 1) {
                Button(
                    onClick = { currentSlideIndex++ },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BgDeepDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onFinishOnboarding,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Get Started", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
