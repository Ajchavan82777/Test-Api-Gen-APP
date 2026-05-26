package com.example

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.HistoryEntity
import com.example.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationWrapper()
                }
            }
        }
    }
}

@Composable
fun AppNavigationWrapper() {
    val context = LocalContext.current
    val appViewModel: AIVisionViewModel = viewModel()

    val currentScreen by appViewModel.currentScreen.collectAsState()
    val isGenerating by appViewModel.isGenerating.collectAsState()
    val progressText by appViewModel.generationProgressText.collectAsState()
    val progressVal by appViewModel.generationProgressValue.collectAsState()
    val errorMsg by appViewModel.errorMessage.collectAsState()

    // Base Radial / Cyberpunk Grid background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Background radial glow
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF05070A),
                            Color(0xFF030406)
                        )
                    )
                )
                // Bottom-right purple ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1BA855F7), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.9f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width * 0.9f, size.height * 0.9f),
                    radius = size.width * 0.7f
                )
                // Top-left cyan ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1300D1FF), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.1f),
                        radius = size.width * 0.6f
                    ),
                    center = Offset(size.width * 0.1f, size.height * 0.1f),
                    radius = size.width * 0.6f
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentScreen != Screen.Settings && !isGenerating) {
                    CyberBottomNavigation(
                        currentScreen = currentScreen,
                        onNavigate = { appViewModel.navigateTo(it) }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                // Main screen animator
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith
                                fadeOut(animationSpec = tween(220))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "ScreenNavigator"
                ) { screen ->
                    when (screen) {
                        is Screen.Home -> StudioHomeScreen(appViewModel)
                        is Screen.Settings -> GenerationSettingsScreen(appViewModel)
                        is Screen.Result -> ImageResultScreen(appViewModel)
                        is Screen.History -> HistoryGalleryScreen(appViewModel)
                        is Screen.Profile -> ProfileConsoleScreen(appViewModel)
                    }
                }

                // Global overlay loader during generation
                if (isGenerating) {
                    GenerativeLoaderOverlay(progressText, progressVal)
                }

                // Global error popup toast simulation
                errorMsg?.let { error ->
                    AlertDialog(
                        onDismissRequest = { appViewModel.errorMessage.value = null },
                        confirmButton = {
                            Button(
                                onClick = { appViewModel.errorMessage.value = null },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicPurple)
                            ) {
                                Text("Acknowledge", color = Color.White)
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Error icon", tint = Color.Red, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creation Interrupted", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        },
                        text = {
                            Text(error, color = TextSecondary, fontSize = 14.sp)
                        },
                        containerColor = CosmicSurface,
                        tonalElevation = 6.dp,
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 1: STUDIO HOME SCREEN
// ==========================================
@Composable
fun StudioHomeScreen(viewModel: AIVisionViewModel) {
    val prompt by viewModel.promptInput.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val presets = viewModel.presets
    val selectedPreset by viewModel.selectedPresetRef.collectAsState()
    val currentStyle by viewModel.selectedStyle.collectAsState()
    val currentRatio by viewModel.selectedAspectRatio.collectAsState()
    
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App logo & name header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Cyber aesthetic logo icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(NeonBlue, CosmicPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "AI Vision Studio Logo",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI VISION STUDIO",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = TextPrimary,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.titleMedium.copy(
                            shadow = Shadow(
                                color = NeonBlue,
                                offset = Offset(0f, 0f),
                                blurRadius = 8f
                            )
                        )
                    )
                    Text(
                        text = "Luminescent Generative Engine",
                        fontSize = 11.sp,
                        color = NeonBlue,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Quick gear settings configure icon
            IconButton(
                onClick = { viewModel.navigateTo(Screen.Settings) },
                modifier = Modifier
                    .testTag("open_settings_button")
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassCardBorderSecondary, RoundedCornerShape(12.dp))
                    .background(GlassCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configure Parameters",
                    tint = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle Switch: Text to Image vs Image to Image
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassCard)
                .border(0.5.dp, CosmicBorder, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            val isTextSelected = mode == GenerationMode.TEXT_TO_IMAGE
            
            // Text to image block
            val textBgModifier = if (isTextSelected) {
                Modifier.background(Brush.linearGradient(listOf(CosmicPurple, NeonBlue)))
            } else {
                Modifier.background(Color.Transparent)
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(textBgModifier)
                    .clickable { viewModel.mode.value = GenerationMode.TEXT_TO_IMAGE }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Text to image icon",
                        tint = if (isTextSelected) Color.White else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Text to Image",
                        color = if (isTextSelected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isTextSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Image to image block
            val isImgSelected = mode == GenerationMode.IMAGE_TO_IMAGE
            
            val imgBgModifier = if (isImgSelected) {
                Modifier.background(Brush.linearGradient(listOf(CosmicPurple, NeonBlue)))
            } else {
                Modifier.background(Color.Transparent)
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(imgBgModifier)
                    .clickable { viewModel.mode.value = GenerationMode.IMAGE_TO_IMAGE }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Image to image icon",
                        tint = if (isImgSelected) Color.White else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Image to Image",
                        color = if (isImgSelected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isImgSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Image reference card (displays when in Image to Image mode)
        AnimatedVisibility(
            visible = mode == GenerationMode.IMAGE_TO_IMAGE,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassCard)
                    .border(1.dp, GlassCardBorderSecondary, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Reference Matrix Base",
                    color = NeonBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Select an existing concept canvas to guide and blend synthesis.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Selected reference preview pane
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22000000))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = selectedPreset.url,
                        contentDescription = "Selected reference image preview",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedPreset.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(selectedPreset.author, color = TextMuted, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x3300D2FF))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("Active", color = NeonBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Grid lists of presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isPresetSelected = preset.url == selectedPreset.url
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .clickable { viewModel.selectedPresetRef.value = preset }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPresetSelected) Color(0x208B5CF6) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isPresetSelected) NeonBlue else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = preset.url,
                                contentDescription = "Preset canvas image selection",
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                preset.name,
                                color = if (isPresetSelected) NeonBlue else TextPrimary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Beautiful prompt input glass field card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(NeonBlue.copy(alpha = 0.35f), CosmicPurple.copy(alpha = 0.35f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface), // bg-[#0D1117]
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VISUAL PROMPT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonBlue,
                        letterSpacing = 1.2.sp
                    )

                    // Optional clear prompt indicator
                    if (prompt.isNotBlank()) {
                        Text(
                            text = "Clear",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { viewModel.promptInput.value = "" }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // The text box area
                TextField(
                    value = prompt,
                    onValueChange = { viewModel.promptInput.value = it },
                    placeholder = {
                        Text(
                            text = "Describe the cosmic masterpiece or retro fantasy you want to create...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("prompt_input_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() }
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Settings quick labels helper line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Style: $currentStyle",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "·",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Ratio: $currentRatio",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Floating quick gear button
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Quick tune menu",
                            tint = NeonBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AI Avatar Identity Lock controller
        AvatarIdentityLockSection(viewModel = viewModel)

        Spacer(modifier = Modifier.height(14.dp))

        // Main Glow-Gradient Generate Action trigger!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("generate_image_button")
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            NeonBlue,
                            CosmicPurple
                        )
                    )
                )
                .clickable {
                    keyboardController?.hide()
                    viewModel.generateImage()
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Generator core action trigger",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SYNTHESIZE MATRIX IN AI",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Recent Generations Row / Grid Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT GENERATIONS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
            Text(
                text = "View Gallery",
                color = NeonBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { viewModel.navigateTo(Screen.History) }
            )
        }

        // List state of recent items in view model
        val historyList by viewModel.allGenerations.collectAsState()
        
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassCard)
                    .border(0.5.dp, CosmicBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Empty art logo description",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(bottom = 6.dp)
                    )
                    Text(
                        "No generated canvases yet.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        "Input a prompt matrix above to begin.",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                historyList.take(6).forEach { gen ->
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.reopenResult(gen) }
                    ) {
                        AsyncImage(
                            model = gen.imageUrl,
                            contentDescription = "Previous generation thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xAA000000))
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = gen.style,
                                color = TextPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: DETAILS SETTINGS Screen
// ==========================================
@Composable
fun GenerationSettingsScreen(viewModel: AIVisionViewModel) {
    val selectedRatio by viewModel.selectedAspectRatio.collectAsState()
    val selectedRes by viewModel.selectedResolution.collectAsState()
    val selectedStyle by viewModel.selectedStyle.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    val negativePrompt by viewModel.negativePromptInput.collectAsState()
    val seed by viewModel.seedInput.collectAsState()
    val advancedExpanded by viewModel.advancedExpanded.collectAsState()
    val enablePromptEnhancement by viewModel.enablePromptEnhancement.collectAsState()

    val ratios = listOf("1:1", "4:5", "9:16", "16:9", "3:4")
    val resolutions = listOf("512x512", "768x1024", "1024x1024", "1080x1920", "1920x1080")
    val styles = listOf("Realistic", "Cinematic", "Anime", "3D", "Digital Art", "Logo", "Poster")
    val qualities = listOf("Standard", "HD", "Ultra HD")
    val counts = listOf(1, 2, 4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Toolbar header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(Screen.Home) },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CosmicBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Home back stack return trigger",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "ENGINE SPECIFICATIONS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Block 1: Style Selection
        Text(
            "ESTABLISHED STYLE MATRIX",
            color = NeonBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            styles.forEach { style ->
                val isSelected = style == selectedStyle
                
                val styleBgModifier = if (isSelected) {
                    Modifier.background(Brush.linearGradient(listOf(CosmicPurple, NeonBlue)))
                } else {
                    Modifier.background(GlassCard)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .then(styleBgModifier)
                        .border(1.dp, if (isSelected) NeonBlue else CosmicBorder, RoundedCornerShape(14.dp))
                        .clickable { viewModel.selectedStyle.value = style }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(style, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Block 2: Aspect Ratio
        Text(
            "RATIO GEOMETRY",
            color = NeonBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ratios.forEach { ratio ->
                val isSelected = ratio == selectedRatio
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) NeonBlue.copy(alpha = 0.12f) else GlassCard)
                        .border(1.2.dp, if (isSelected) NeonBlue else CosmicBorder, RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectedAspectRatio.value = ratio }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ratio, color = if (isSelected) NeonBlue else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Block 3: Resolution Selector
        Text(
            "RESOLUTION CAPABILITY",
            color = NeonBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassCard)
                .border(0.5.dp, CosmicBorder, RoundedCornerShape(16.dp))
                .padding(6.dp)
        ) {
            resolutions.forEach { res ->
                val isSelected = res == selectedRes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectedResolution.value = res }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = res + when (res) {
                            "1024x1024" -> " (Optimized 1K)"
                            "512x512" -> " (Speed Draft)"
                            "1920x1080" -> " (Landscape HD)"
                            else -> ""
                        },
                        color = if (isSelected) NeonBlue else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.selectedResolution.value = res },
                        colors = RadioButtonDefaults.colors(selectedColor = NeonBlue, unselectedColor = TextMuted)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Block 4: Quality & Number of Images Side-By-Side
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CONGRUENT QUALITY",
                    color = NeonBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassCard)
                        .border(0.5.dp, CosmicBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedQuality, color = TextPrimary, fontSize = 13.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "dropdown arrow", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(CosmicSurface)
                    ) {
                        qualities.forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q, color = TextPrimary) },
                                onClick = {
                                    viewModel.selectedQuality.value = q
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "IMAGE MATRIX COUNT",
                    color = NeonBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassCard)
                        .border(0.5.dp, CosmicBorder, RoundedCornerShape(12.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    counts.forEach { c ->
                        val isSelected = countSelected(selectedCount, c)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.selectedCount.value = c }
                                .background(if (isSelected) CosmicPurple else Color.Transparent)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(c.toString(), color = if (isSelected) Color.White else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // AI Prompt Enhancement Toggle Card
        Text(
            "AI SEMANTIC INTERPRETER",
            color = NeonBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCard)
                .border(1.dp, CosmicBorder, RoundedCornerShape(12.dp))
                .clickable { viewModel.setPromptEnhancement(!enablePromptEnhancement) }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI PROMPT ENHANCEMENT",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Use Gemini to automatically enrich visual detail semantics. Disable this to generate your exact prompt verbatim.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = enablePromptEnhancement,
                onCheckedChange = { viewModel.setPromptEnhancement(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonBlue,
                    checkedTrackColor = NeonBlue.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = CosmicBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Block 4: Advanced Expandable Block
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.advancedExpanded.value = !advancedExpanded }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = "Advanced tweak icons", tint = NeonBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADVANCED DEEP CONFIG", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Expand controls trigger",
                tint = NeonBlue
            )
        }

        AnimatedVisibility(
            visible = advancedExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Negative Prompt
                Text(
                    "NEGATIVE VISUAL VETO",
                    color = NeonBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = negativePrompt,
                    onValueChange = { viewModel.negativePromptInput.value = it },
                    placeholder = { Text("What to exclude (e.g. blurry, low quality, extra limbs)", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CosmicBorder,
                        focusedContainerColor = GlassCard,
                        unfocusedContainerColor = GlassCard
                    )
                )

                // Seed Custom configuration
                Text(
                    "SEED ENTROPY",
                    color = NeonBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = seed,
                    onValueChange = { viewModel.seedInput.value = it },
                    placeholder = { Text("Random (default empty)", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CosmicBorder,
                        focusedContainerColor = GlassCard,
                        unfocusedContainerColor = GlassCard
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Confirm Settings configuration Save trigger
        Button(
            onClick = { viewModel.navigateTo(Screen.Home) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CosmicPurple)
        ) {
            Text("LOCK SPECIFICATIONS AND RETURN", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun countSelected(current: Int, target: Int): Boolean {
    return current == target
}

// ==========================================
// SCREEN 3: IMAGE RESULT SCREEN
// ==========================================
@Composable
fun ImageResultScreen(viewModel: AIVisionViewModel) {
    val resultImage by viewModel.lastGeneratedImage.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (resultImage == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No generated results to load.", color = TextSecondary)
        }
        return
    }

    val item = resultImage!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Toolbar header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(Screen.Home) },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CosmicBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Studio launch exit",
                    tint = TextPrimary
                )
            }
            Text(
                text = "GENERATIVE OUTPUT",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
            IconButton(
                onClick = { viewModel.deleteGeneration(item.id); viewModel.navigateTo(Screen.Home) },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.Red.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove result card",
                    tint = Color.Red
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Huge Render Preview Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Ensure nice aspect ratio space
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, GlassCardBorder, RoundedCornerShape(24.dp))
                .background(CosmicSurfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "AI generated output picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Overlay style title
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xDD000000))
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.style.uppercase(), color = NeonBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Seed Matrix ID: ${item.seed}", color = TextSecondary, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmicPurple)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(item.aspectRatio, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive action row buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Action button: Download
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassCard)
                    .border(0.5.dp, CosmicBorder, RoundedCornerShape(14.dp))
                    .clickable { performImageDownload(item.imageUrl, context, scope) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Download render to photos", tint = NeonBlue)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Download", color = TextPrimary, fontSize = 11.sp)
            }

            // Action button: Share
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassCard)
                    .border(0.5.dp, CosmicBorder, RoundedCornerShape(14.dp))
                    .clickable { performImageShare(item.imageUrl, item.prompt, context, scope) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share visual content", tint = NeonBlue)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Share", color = TextPrimary, fontSize = 11.sp)
            }

            // Action button: Regenerate
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassCard)
                    .border(0.5.dp, CosmicBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        viewModel.promptInput.value = item.prompt
                        viewModel.generateImage()
                    }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Regenerate image trigger", tint = CosmicPurple)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Regenerate", color = TextPrimary, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Detail Prompt Specs Text values
        Text("ORIGINAL MATRIX INPUT", color = NeonBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 1.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCard)
                .padding(12.dp)
        ) {
            Text(item.prompt, color = TextPrimary, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("GEMINI AI ENHANCED PROMPT", color = CosmicPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 1.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCard)
                .padding(12.dp)
        ) {
            Text(
                text = item.enhancedPrompt.ifBlank { "Prompt enhancement skipped." },
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Technical specs indicators row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quality tag
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x3300D2FF))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Quality: ${item.quality}", fontSize = 11.sp, color = NeonBlue, fontWeight = FontWeight.SemiBold)
            }
            // Resolution tag
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x228B5CF6))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Resolution: ${item.resolution}", fontSize = 11.sp, color = CosmicPurple, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Download image action helper logic
fun performImageDownload(imageUrl: String, context: Context, scope: CoroutineScope) {
    Toast.makeText(context, "Processing output save request...", Toast.LENGTH_SHORT).show()
    scope.launch(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                val filename = "AIVision_${System.currentTimeMillis()}.png"
                val outputStream: OutputStream?
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI_Vision_Studio")
                    }
                    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = imageUri?.let { resolver.openOutputStream(it) }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                    val myDir = File(imagesDir, "AI_Vision_Studio")
                    if (!myDir.exists()) myDir.mkdirs()
                    val file = File(myDir, filename)
                    outputStream = FileOutputStream(file)
                }

                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Successfully saved render to Photos!", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: Canvas download error", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            launch(Dispatchers.Main) {
                Toast.makeText(context, "Image save stream error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// Share image action helper logic
fun performImageShare(imageUrl: String, prompt: String, context: Context, scope: CoroutineScope) {
    Toast.makeText(context, "Assembling content envelope...", Toast.LENGTH_SHORT).show()
    scope.launch(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(imageUrl).build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                
                // Save locally to cache to share via simple content uri
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val imageFile = File(cachePath, "shared_masterpiece.png")
                val stream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.close()

                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, "Witness this AI Vision masterpiece: \"$prompt\"")
                    type = "image/png"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                launch(Dispatchers.Main) {
                    try {
                        context.startActivity(Intent.createChooser(shareIntent, "Share Masterpiece"))
                    } catch (ex: Exception) {
                        Toast.makeText(context, "Error starting share window: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            // Sharing text-fallback trigger
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Witness my AI Vision masterpiece: \"$prompt\" via $imageUrl")
                type = "text/plain"
            }
            launch(Dispatchers.Main) {
                try {
                    context.startActivity(Intent.createChooser(shareIntent, "Share Masterpiece Link"))
                } catch (ex2: Exception) {
                    Toast.makeText(context, "Error sharing link: ${ex2.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}


// ==========================================
// SCREEN 4: HISTORY GALLERY SCREEN
// ==========================================
@Composable
fun HistoryGalleryScreen(viewModel: AIVisionViewModel) {
    val generationsList by viewModel.allGenerations.collectAsState()
    val searchQuery by viewModel.historySearchQuery.collectAsState()
    val activeStyle by viewModel.historyFilterStyle.collectAsState()

    val filterStyles = listOf("All", "Realistic", "Cinematic", "Anime", "3D", "Digital Art")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Title banner
        Text(
            text = "CHRONOLOGY OF ART",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Search Prompt filtering Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.historySearchQuery.value = it },
            placeholder = { Text("Search by prompt keywords...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search prompts", tint = NeonBlue) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.historySearchQuery.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search query", tint = TextMuted)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GlassCard,
                unfocusedContainerColor = GlassCard,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = NeonBlue,
                unfocusedBorderColor = CosmicBorder
            )
        )

        // Styles horizontal tab indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterStyles.forEach { style ->
                val isSelected = style == activeStyle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CosmicPurple else GlassCard)
                        .border(0.5.dp, if (isSelected) NeonBlue else CosmicBorder, RoundedCornerShape(10.dp))
                        .clickable { viewModel.historyFilterStyle.value = style }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(style, color = if (isSelected) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Complete LazyVerticalGrid of generations
        if (generationsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = "No items", tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No Matching Generations", color = TextSecondary, fontSize = 14.sp)
                    Text("Try adjusting your query filter matrix.", color = TextMuted, fontSize = 11.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().weight(1f).testTag("history_grid_layout"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(generationsList) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clickable { viewModel.reopenResult(item) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassCard),
                        border = BorderStroke(0.5.dp, CosmicBorder)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = "Thumbnail preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Gradient shadow on bottom text
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color(0xBB000000))
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = item.prompt,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.style, color = NeonBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(item.aspectRatio, color = TextMuted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: PROFILE COGNITIVE CONSOLE
// ==========================================
@Composable
fun ProfileConsoleScreen(viewModel: AIVisionViewModel) {
    val keyState by viewModel.customApiKey.collectAsState()
    val usages by viewModel.usageCounter.collectAsState()
    val modelState by viewModel.selectedModel.collectAsState()
    val models = listOf("gemini-3.5-flash (Standard)", "gemini-3.1-pro-preview (Expert Prompting)")
    var isEditingKey by remember { mutableStateOf(false) }
    var inputKey by remember { mutableStateOf(keyState) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // App identifier title
        Text(
            text = "COGNITIVE MASTER CONSOLE",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Usages count summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .border(1.dp, GlassCardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = GlassCard),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("CUMULATIVE CANVAS CREATIONS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                
                // Huge counter
                Text(
                    text = String.format("%03d", usages),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonBlue,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.displayLarge.copy(
                        shadow = Shadow(color = NeonBlue, offset = Offset(0f, 0f), blurRadius = 12f)
                    )
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = "Active signal", tint = NeonBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generative Core Status: Active & Synced", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        // Section: Gemini Security Key Config
        Text("GEMINI SECURE ACCESS KEY", color = NeonBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, CosmicBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (isEditingKey) {
                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        placeholder = { Text("Insert GEMINI_API_KEY secure secret...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditingKey = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.saveApiKey(inputKey)
                                isEditingKey = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                        ) {
                            Text("Lock Key", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val displayText = if (keyState.isBlank()) "No Custom API Key (Using Defaults)" else "•••••••••••••••••••••"
                            Text(displayText, color = if (keyState.isBlank()) TextMuted else TextPrimary, fontSize = 13.sp)
                            Text("Protects custom prompt enhancements.", color = TextMuted, fontSize = 10.sp)
                        }
                        IconButton(onClick = { isEditingKey = true; inputKey = keyState }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit API token key", tint = NeonBlue)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                // API Security Notice warning as mandated by the `android-secret-management` guidelines!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1100D2FF))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Security Warning: I have included your API keys in the generated APK file for this prototype. " +
                               "Please be aware that Android APKs can be easily decompiled, and these keys can be extracted. " +
                               "Do not share this APK file publicly or with unauthorized individuals to prevent potential misuse.",
                        color = NeonBlue.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        // Section: Model Select Area
        Text("GEMINI MODEL INTEGRATION", color = NeonBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassCard)
                .border(0.5.dp, CosmicBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            var expanded by remember { mutableStateOf(false) }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(modelState, color = TextPrimary, fontSize = 14.sp)
                        Text("Active prompting matrix engine.", color = TextMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "dropdown arrow", tint = TextSecondary)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(CosmicSurface)
                ) {
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, color = TextPrimary) },
                            onClick = {
                                viewModel.selectedModel.value = m
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Section: Diagnostics Cleanup trigger
        Text("DANGER DIAGNOSTIC SYSTEM", color = Color.Red.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = { viewModel.clearAllHistory() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("PURGE ALL GENERATION CHRONICLES", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}


// ==========================================
// SUB COMPONENT: THE ACTIVE NAVIGATION BOTTOM PILL BAR
// ==========================================
@Composable
fun CyberBottomNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    val items = listOf(
        Triple(Screen.Home, "Studio", Icons.Default.Home),
        Triple(Screen.History, "Gallery", Icons.Default.Star),
        Triple(Screen.Profile, "Console", Icons.Default.Settings)
    )

    Surface(
        color = CosmicSurface.copy(alpha = 0.85f),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(0.5.dp, CosmicBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (screen, label, icon) ->
                val isSelected = currentScreen == screen
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onNavigate(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "$label Tab icon",
                        tint = if (isSelected) NeonBlue else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        color = if (isSelected) NeonBlue else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}


// ==========================================
// SUB COMPONENT: THE CINEMATIC LOADER OVERLAY
// ==========================================
@Composable
fun GenerativeLoaderOverlay(text: String, progress: Float) {
    // Backdrop blur / dim effect
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6020208)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // Elegant pulsing futuristic halo loader ring
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(80.dp),
                    color = NeonBlue,
                    strokeWidth = 4.dp,
                    trackColor = CosmicPurple.copy(alpha = 0.2f),
                )
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Pulsing generator core aura",
                    tint = NeonBlue,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pulse progress status details
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Creating your AI image…",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Percentage glow text
            Text(
                text = "${(progress * 100).toInt()}%",
                color = CosmicPurple,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AvatarIdentityLockSection(viewModel: AIVisionViewModel) {
    val context = LocalContext.current
    val avatars by viewModel.allAvatars.collectAsState()
    val selectedAvatar by viewModel.selectedAvatar.collectAsState()

    // Creation fields
    var showCreateDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var facesList by remember { mutableStateOf<List<String>>(emptyList()) }
    var sheetPath by remember { mutableStateOf("") }
    var strengthInput by remember { mutableStateOf("Strict") } // Strict, High, Medium

    // Local result launchers
    val facesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val paths = uris.mapNotNull { uri ->
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val file = File(context.filesDir, "avatar_face_${System.currentTimeMillis()}_${(1000..9999).random()}.png")
                    val out = FileOutputStream(file)
                    stream?.copyTo(out)
                    stream?.close()
                    out.close()
                    file.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
            facesList = (facesList + paths).take(10)
        }
    }

    val sheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val stream = context.contentResolver.openInputStream(it)
                val file = File(context.filesDir, "avatar_sheet_${System.currentTimeMillis()}.png")
                val out = FileOutputStream(file)
                stream?.copyTo(out)
                stream?.close()
                out.close()
                sheetPath = file.absolutePath
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(NeonBlue.copy(alpha = 0.4f), CosmicPurple.copy(alpha = 0.4f))
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Identity lock icon",
                        tint = NeonBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI AVATAR IDENTITY LOCK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                
                // Tech badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ACTIVE GUARD",
                        color = NeonBlue,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Lock facial attributes and proportion matrices to lock consistent characters across generations.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            
            Spacer(modifier = Modifier.height(14.dp))

            // Carousel / List of loaded avatar profiles
            Text(
                text = "SELECT REGISTERED IDENTITY MATRIX",
                color = NeonBlue,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "None/Disable" selector card
                Card(
                    modifier = Modifier
                        .width(110.dp)
                        .height(115.dp)
                        .clickable { viewModel.selectAvatar(null) }
                        .border(
                            width = 1.2.dp,
                            color = if (selectedAvatar == null) NeonBlue else CosmicBorder,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedAvatar == null) CosmicSurfaceVariant else GlassCard
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "No lock",
                            tint = if (selectedAvatar == null) NeonBlue else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Lock",
                            color = if (selectedAvatar == null) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pure Prompting",
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }

                // Render dynamic list of avatars
                avatars.forEach { avatar ->
                    val isSelected = selectedAvatar?.id == avatar.id
                    val faces = avatar.faceImagesJson.split(",").filter { it.isNotBlank() }
                    
                    Card(
                        modifier = Modifier
                            .width(160.dp)
                            .height(115.dp)
                            .clickable { viewModel.selectAvatar(avatar) }
                            .border(
                                width = 1.2.dp,
                                color = if (isSelected) NeonBlue else CosmicBorder,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CosmicSurfaceVariant else GlassCard
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = avatar.name,
                                    color = if (isSelected) NeonBlue else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = NeonBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = avatar.description,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 11.sp
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Thumbnails indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    faces.take(3).forEach { f ->
                                        AsyncImage(
                                            model = f,
                                            contentDescription = "face thumb",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (faces.size > 3) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("+${faces.size - 3}", fontSize = 7.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                
                                Text(
                                    text = "Lock: ${avatar.lockStrength}",
                                    color = if (avatar.lockStrength == "Strict") CosmicPurple else NeonBlue,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row: Create Avatar and Delete Selected Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button +: Create a stunning new Identity Matrix
                Button(
                    onClick = {
                        // Reset inputs
                        nameInput = ""
                        descInput = ""
                        facesList = emptyList()
                        sheetPath = ""
                        strengthInput = "Strict"
                        showCreateDialog = true
                    },
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassCardBorderSecondary),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonBlue.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Avatar Icon",
                        tint = NeonBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEW AVATAR PROJECTION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonBlue,
                        letterSpacing = 0.5.sp
                    )
                }

                if (selectedAvatar != null) {
                    // Button: Purge current Selected Avatar Lock profile
                    Button(
                        onClick = {
                            selectedAvatar?.let {
                                viewModel.deleteAvatar(it.id)
                                Toast.makeText(context, "Purged registered matrix footprint", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(0.7f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Icon",
                            tint = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PURGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Expanded overview of chosen active lock
            selectedAvatar?.let { active ->
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassCard)
                        .border(1.dp, CosmicPurple.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(CosmicPurple)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "IDENTITY BIOMETRICS ENFORCED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicPurple,
                                letterSpacing = 0.5.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Applying: same face identity, facial structure, eye-to-nose layout, same skin color tone (#${active.lockStrength.uppercase()} setting), and same characteristic proportional age.",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Character Sheet Preview Thumbnail if exists
                            if (active.characterSheet.isNotBlank()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AsyncImage(
                                        model = active.characterSheet,
                                        contentDescription = "active character sheet",
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(0.5.dp, CosmicBorder, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("SOURCE SHEET", color = TextMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            // Descriptions
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = active.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonBlue
                                )
                                Text(
                                    text = active.description,
                                    fontSize = 9.sp,
                                    color = TextSecondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to compile standard profile specs
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Avatar key lock setup",
                        tint = NeonBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEW IDENTITY PROJECT",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Register a consistent cybernetic layout. Enter identity identifiers name, visual context guidelines, face blueprints, and character sheet references.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 1. Avatar Name String field
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Identity Name (e.g. Captain Vance)", fontSize = 11.sp) },
                        placeholder = { Text("Cyberpunk Alias...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicBorder
                        ),
                        singleLine = true
                    )

                    // 2. Avatar description
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Character Core Details (Hairstyle, skin, etc.)", fontSize = 11.sp) },
                        placeholder = { Text("Features sharp eyes, electric braids, standard aviator jacket...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicBorder
                        ),
                        maxLines = 3
                    )

                    // 3. Identity Strength Option Toggle Selector
                    Text(
                        text = "IDENTITY LOCK STRENGTH (DEFAULT: STRICT)",
                        color = NeonBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Medium", "High", "Strict").forEach { str ->
                            val chipSelected = strengthInput == str
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (chipSelected) NeonBlue.copy(alpha = 0.15f) else GlassCard)
                                    .border(
                                        1.dp,
                                        if (chipSelected) NeonBlue else CosmicBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { strengthInput = str }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = str,
                                    color = if (chipSelected) NeonBlue else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 4. Face reference setup images section
                    Text(
                        text = "FACE REFERENCE BLUEPRINTS (3 to 10 COPIES REQUIRED)",
                        color = NeonBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clickable trigger picker
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { facesLauncher.launch("image/*") }
                                .border(1.dp, NeonBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            colors = CardDefaults.cardColors(containerColor = GlassCard)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add Face Icon", tint = NeonBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CHOOSE FILES", color = NeonBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Seamless Emulator specimen pre-loader
                        Card(
                            modifier = Modifier
                                .weight(1.1f)
                                .clickable {
                                    // Prepopulate instant trial specimens
                                    facesList = listOf(
                                        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop",
                                        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400&auto=format&fit=crop",
                                        "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop",
                                        "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop"
                                    )
                                    Toast.makeText(context, "Loaded 4 high fidelity face templates!", Toast.LENGTH_SHORT).show()
                                }
                                .border(1.dp, CosmicPurple.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            colors = CardDefaults.cardColors(containerColor = GlassCard)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = "Face template icon", tint = CosmicPurple, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LOAD SPECIMENS", color = CosmicPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Render current added face thumbnails
                    if (facesList.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            facesList.forEachIndexed { idx, path ->
                                Box(modifier = Modifier.size(38.dp)) {
                                    AsyncImage(
                                        model = path,
                                        contentDescription = "loaded face image details",
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(0.5.dp, CosmicBorder, RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Remove thumbnail corner button
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.TopEnd)
                                            .background(Color.Red, RoundedCornerShape(3.dp))
                                            .clickable { facesList = facesList.filterIndexed { i, _ -> i != idx } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "remove face", tint = Color.White, modifier = Modifier.size(8.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No face references added yet. (3 Min)", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. Character sheet single entry selection
                    Text(
                        text = "CHARACTER SHEET BLUEPRINT (1 REQUIRED)",
                        color = NeonBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { sheetLauncher.launch("image/*") }
                                .border(1.dp, NeonBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            colors = CardDefaults.cardColors(containerColor = GlassCard)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add Sheet Icon", tint = NeonBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SELECT CONSOLE FILE", color = NeonBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .weight(1.1f)
                                .clickable {
                                    sheetPath = "https://images.unsplash.com/photo-1541462608141-2f52c6f1d4b6?w=600&auto=format&fit=crop"
                                    Toast.makeText(context, "Loaded master design concept rendering!", Toast.LENGTH_SHORT).show()
                                }
                                .border(1.dp, CosmicPurple.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            colors = CardDefaults.cardColors(containerColor = GlassCard)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "sheet template icon", tint = CosmicPurple, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LOAD SHEET TEMPLATE", color = CosmicPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (sheetPath.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = sheetPath,
                                contentDescription = "loaded visual character sheet blueprint target",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(0.5.dp, CosmicBorder, RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Source Character Sheet Ready", color = NeonBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.CheckCircle, contentDescription = "sheet ready checked", tint = NeonBlue, modifier = Modifier.size(14.dp))
                        }
                    } else {
                        Text("No active character sheet loaded. (1 Required)", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Validation logic
                        if (nameInput.isBlank()) {
                            Toast.makeText(context, "Verify Identity Name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (facesList.size < 3 || facesList.size > 10) {
                            Toast.makeText(context, "Face references must be 3 to 10 photos! (${facesList.size} added)", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (sheetPath.isBlank()) {
                            Toast.makeText(context, "Character Sheet required!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        viewModel.createAvatar(
                            name = nameInput,
                            description = descInput.ifBlank { "Standard identity lock footprint description configuration." },
                            faceImages = facesList,
                            characterSheet = sheetPath,
                            lockStrength = strengthInput
                        )
                        showCreateDialog = false
                        Toast.makeText(context, "Constructed identity lock projection $nameInput!", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) {
                    Text("LOCK IDENTITY MODEL", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("ABORT PROJECT", color = TextSecondary, fontSize = 11.sp)
                }
            },
            containerColor = CosmicSurface,
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
