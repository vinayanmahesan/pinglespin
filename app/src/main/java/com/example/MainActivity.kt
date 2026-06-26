package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ScoreEntity
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import java.util.Locale
import androidx.lifecycle.ViewModelProvider
import android.view.KeyEvent
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import coil.compose.AsyncImage
import com.example.SpotifyManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

fun Context.findActivity(): Activity? {
  var currentContext = this
  while (currentContext is ContextWrapper) {
    if (currentContext is Activity) {
      return currentContext
    }
    currentContext = currentContext.baseContext
  }
  return null
}

@Composable
fun isFoldableDevice(): Boolean {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() } ?: return false
  
  // 1. Check WindowLayoutInfo for folding features
  val layoutInfoState = remember(activity) {
    WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
  }.collectAsState(initial = null)
  
  val hasFoldingFeature = remember(layoutInfoState.value) {
    layoutInfoState.value?.displayFeatures?.any { it is FoldingFeature } == true
  }
  
  // 2. Check SensorManager for hinge angle sensor
  val hasHingeSensor = remember(context) {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
    sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_HINGE_ANGLE) != null
  }
  
  return hasFoldingFeature || hasHingeSensor
}

@Composable
fun rememberHingeAngle(): Float? {
  val context = LocalContext.current
  var hingeAngle by remember { mutableStateOf<Float?>(null) }
  DisposableEffect(context) {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
    val hingeSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_HINGE_ANGLE)
    if (hingeSensor == null) {
      onDispose {}
    } else {
      val listener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
          event?.values?.firstOrNull()?.let {
            hingeAngle = it
          }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
      }
      sensorManager.registerListener(listener, hingeSensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
      onDispose {
        sensorManager.unregisterListener(listener)
      }
    }
  }
  return hingeAngle
}

@Composable
fun isFoldedState(threshold: Float): Boolean {
  val isFoldable = isFoldableDevice()
  if (!isFoldable) return false

  val hingeAngle = rememberHingeAngle()
  if (hingeAngle != null) {
    return hingeAngle <= threshold
  }

  // Fallback: If hinge angle is not available, check window layout HALF_OPENED
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() } ?: return false
  val layoutInfoState = remember(activity) {
    WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
  }.collectAsState(initial = null)

  val layoutInfo = layoutInfoState.value ?: return false
  val foldingFeature = layoutInfo.displayFeatures
    .filterIsInstance<FoldingFeature>()
    .firstOrNull() ?: return false

  return foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
      foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL
}

class MainActivity : ComponentActivity() {
  private lateinit var viewModel: PingleViewModel
  private val keyBuffer = StringBuilder()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Enable immersive full-screen mode (hide status and navigation bars)
    val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.systemBarsBehavior =
        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

    // Allow the window to draw under display cutouts (notches) for a perfect bleed-edge game screen
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
          android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    viewModel = ViewModelProvider(this).get(PingleViewModel::class.java)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        PingleSpinApp(viewModel)
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
      windowInsetsController.systemBarsBehavior =
          androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN) {
      val unicode = event.unicodeChar
      if (unicode != 0) {
        val character = unicode.toChar()
        if (character.isLetterOrDigit()) {
          keyBuffer.append(character.lowercaseChar())
          if (keyBuffer.length > 50) {
            keyBuffer.delete(0, keyBuffer.length - 20)
          }
          if (keyBuffer.toString().endsWith("imadethegamelilbro")) {
            viewModel.unlockEverything()
            showToast(this, "Cheat Active: Everything Unlocked!", Toast.LENGTH_LONG)
            keyBuffer.setLength(0)
          }
        }
      }
    }
    return super.dispatchKeyEvent(event)
  }
}

data class PingleTintOption(
  val id: String,
  val name: String,
  val color: Color?,
  val labelColor: Color
)

val PINGLE_TINTS = listOf(
  PingleTintOption("none", "Original", null, Color.White),
  PingleTintOption("red", "Spicy Red", Color(0xFFFF3B30), Color(0xFFFF453A)),
  PingleTintOption("green", "Sour Cream Onion", Color(0xFF34C759), Color(0xFF30D158)),
  PingleTintOption("blue", "Salted Blue", Color(0xFF007AFF), Color(0xFF0A84FF)),
  PingleTintOption("purple", "BBQ Purple", Color(0xFFA252FF), Color(0xFFBF5AF2)),
  PingleTintOption("gold", "Premium Cheddar Gold", Color(0xFFFFCC00), Color(0xFFFFD60A))
)

@Composable
fun PingleSpinApp(viewModel: PingleViewModel = viewModel()) {
  KeepScreenOn()
  val screen by viewModel.currentScreen.collectAsState()
  val speed by viewModel.pingleSpeed.collectAsState()
  val pingleFriction by viewModel.pingleFriction.collectAsState()
  val isManualUnlocked by viewModel.isManualUnlocked.collectAsState()
  val topScores by viewModel.topScores.collectAsState()
  val pingleTintId by viewModel.pingleTint.collectAsState()
  val pingleCustomColorInt by viewModel.pingleCustomColor.collectAsState()
  val totalSpinDuration by viewModel.totalSpinDuration.collectAsState()
  val foldAngleThreshold by viewModel.pingleFoldAngleThreshold.collectAsState()

  when (screen) {
    Screen.HOME -> HomeScreen(
      isManualUnlocked = isManualUnlocked,
      onUnlockManual = { viewModel.unlockManual() },
      onPlayClicked = { viewModel.setScreen(Screen.PLAY) },
      onManualSpinClicked = { viewModel.setScreen(Screen.MANUAL) },
      onHighScoresClicked = { viewModel.setScreen(Screen.HIGH_SCORES) },
      onOptionsClicked = { viewModel.setScreen(Screen.OPTIONS) }
    )
    Screen.PLAY -> PlayScreen(
      speed = speed,
      pingleTintId = pingleTintId,
      pingleCustomColorInt = pingleCustomColorInt,
      foldAngleThreshold = foldAngleThreshold,
      onBackToHomeWithScore = { score ->
        viewModel.saveScore(score)
        viewModel.setScreen(Screen.HIGH_SCORES)
      },
      onElapsedClickToHighScores = { score ->
        viewModel.saveScore(score)
        viewModel.setScreen(Screen.HIGH_SCORES)
      }
    )
    Screen.HIGH_SCORES -> HighScoresScreen(
      scores = topScores,
      onBackClicked = { viewModel.setScreen(Screen.HOME) },
      onClearAll = { viewModel.clearAllScores() }
    )
    Screen.OPTIONS -> OptionsScreen(
      speed = speed,
      pingleFriction = pingleFriction,
      isManualUnlocked = isManualUnlocked,
      pingleTintId = pingleTintId,
      pingleCustomColorInt = pingleCustomColorInt,
      totalSpinDuration = totalSpinDuration,
      foldAngleThreshold = foldAngleThreshold,
      onSpeedChange = { viewModel.setPingleSpeed(it) },
      onFrictionChange = { viewModel.setPingleFriction(it) },
      onTintSelect = { viewModel.setPingleTint(it) },
      onCustomColorChange = { viewModel.setPingleCustomColor(it) },
      onFoldAngleThresholdChange = { viewModel.setPingleFoldAngleThreshold(it) },
      onBackClicked = { viewModel.setScreen(Screen.HOME) }
    )
    Screen.MANUAL -> ManualPlayScreen(
      pingleFriction = pingleFriction,
      pingleTintId = pingleTintId,
      pingleCustomColorInt = pingleCustomColorInt,
      foldAngleThreshold = foldAngleThreshold,
      onBackToHomeWithScore = { score ->
        viewModel.saveScore(score, isManual = true)
        viewModel.setScreen(Screen.HIGH_SCORES)
      },
      onElapsedClickToHighScores = { score ->
        viewModel.saveScore(score, isManual = true)
        viewModel.setScreen(Screen.HIGH_SCORES)
      }
    )
  }
}

@Composable
fun MinimalistButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp)
      .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(14.dp)
      )
      .background(Color.Black, shape = RoundedCornerShape(14.dp))
      .clickable { onClick() }
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center
  ) {
    androidx.compose.material3.Text(
      text = text.uppercase(Locale.US),
      style = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        letterSpacing = 2.sp
      )
    )
  }
}

@Composable
fun HomeScreen(
  isManualUnlocked: Boolean,
  onUnlockManual: () -> Unit,
  onPlayClicked: () -> Unit,
  onManualSpinClicked: () -> Unit,
  onHighScoresClicked: () -> Unit,
  onOptionsClicked: () -> Unit
) {
  val context = LocalContext.current
  var clickCount by remember { mutableIntStateOf(0) }
  var showGameModeMenu by remember { mutableStateOf(false) }

  if (showGameModeMenu) {
    Dialog(onDismissRequest = { showGameModeMenu = false }) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp)
          )
          .background(Color.Black, shape = RoundedCornerShape(20.dp))
          .padding(24.dp)
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          androidx.compose.material3.Text(
            text = "SELECT GAMEMODE",
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraLight,
              color = Color.White,
              letterSpacing = 4.sp
            )
          )

          Spacer(modifier = Modifier.height(4.dp))

          MinimalistButton(
            text = "normal",
            onClick = {
              showGameModeMenu = false
              onPlayClicked()
            },
            modifier = Modifier.testTag("gamemode_normal_button")
          )

          MinimalistButton(
            text = "PINGLEMANUALSPIN",
            onClick = {
              showGameModeMenu = false
              onManualSpinClicked()
            },
            modifier = Modifier.testTag("gamemode_manual_button")
          )
        }
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("home_screen")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 32.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Header logo description matching exactly "made by pingleTEK ©2025-inf"
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .padding(top = 16.dp)
          .clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
          ) {
            if (!isManualUnlocked) {
              clickCount++
              if (clickCount >= 8) {
                onUnlockManual()
                showToast(context, "MANUAL SPIN UNLOCKED!", Toast.LENGTH_SHORT)
              }
            }
          }
          .testTag("pinglespin_title")
      ) {
        androidx.compose.material3.Text(
          text = "pinglespin".uppercase(Locale.US),
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraLight,
            color = Color.White,
            letterSpacing = 6.sp
          )
        )
        Spacer(modifier = Modifier.height(6.dp))
        androidx.compose.material3.Text(
          text = "made by pingleTEK ©2025-inf".uppercase(Locale.US),
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 2.sp
          )
        )
      }

      // Atmospheric centered static pingle
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
      ) {
        Box(
          modifier = Modifier
            .size(240.dp, 140.dp)
            .background(
              Brush.radialGradient(
                colors = listOf(
                  Color(0x18FFD93D),
                  Color(0x00FFD93D)
                )
              )
            )
        )
        Image(
          painter = painterResource(id = R.drawable.img_pure_pringle_1780498841768),
          contentDescription = "Static Pringle",
          modifier = Modifier.size(190.dp),
          contentScale = ContentScale.Fit
        )
      }

      // Elegant minimal control list
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        MinimalistButton(
          text = "play",
          onClick = {
            if (isManualUnlocked) {
              showGameModeMenu = true
            } else {
              onPlayClicked()
            }
          },
          modifier = Modifier.testTag("play_button")
        )
        MinimalistButton(
          text = "high scores",
          onClick = onHighScoresClicked,
          modifier = Modifier.testTag("high_scores_button")
        )
        MinimalistButton(
          text = "options",
          onClick = onOptionsClicked,
          modifier = Modifier.testTag("options_button")
        )
      }
    }
  }
}

@Composable
fun PlayScreen(
  speed: Float,
  pingleTintId: String,
  pingleCustomColorInt: Int,
  foldAngleThreshold: Float,
  onBackToHomeWithScore: (Long) -> Unit,
  onElapsedClickToHighScores: (Long) -> Unit
) {
  var elapsedTimeMs by remember { mutableLongStateOf(0L) }

  val context = LocalContext.current
  DisposableEffect(Unit) {
    onDispose {
      SpotifyManager.getInstance(context).stopPreview()
    }
  }

  val selectedTintOption = remember(pingleTintId) { PINGLE_TINTS.firstOrNull { it.id == pingleTintId } }
  val tintColor = remember(pingleTintId, pingleCustomColorInt) {
    if (pingleTintId == "custom") Color(pingleCustomColorInt) else selectedTintOption?.color
  }
  val colorFilter = remember(tintColor) { tintColor?.let { ColorFilter.tint(it, BlendMode.Color) } }

  // High precision timer loop updating roughly at ~60fps
  LaunchedEffect(Unit) {
    val startTime = System.currentTimeMillis()
    while (isActive) {
      elapsedTimeMs = System.currentTimeMillis() - startTime
      delay(16)
    }
  }

  // Spin speed ratio: baseline 3 seconds divided by multiplier
  val rotationPeriod = (3000 / speed).toInt().coerceAtLeast(100)

  val infiniteTransition = rememberInfiniteTransition(label = "pingle_spin_transition")
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = rotationPeriod, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "pingle_rotation"
  )

  // Calibrate exact millisecond representations
  val totalSeconds = elapsedTimeMs / 1000
  val minutes = (totalSeconds / 60) % 60
  val seconds = totalSeconds % 60
  val centiseconds = (elapsedTimeMs % 1000) / 10

  val formattedTime = if (totalSeconds < 60) {
    String.format(Locale.US, "%d.%02d", seconds, centiseconds)
  } else {
    String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds)
  }

  val isFolded = isFoldedState(threshold = foldAngleThreshold)
  val isFoldable = isFoldableDevice()

  val transitionProgress by animateFloatAsState(
    targetValue = if (isFolded) 1f else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioLowBouncy,
      stiffness = Spring.StiffnessLow
    ),
    label = "fold_transition"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("play_viewport")
  ) {
    // 1. Centered Spinning Pringle unit moving smoothly with transition progress
    Column(
      modifier = Modifier.align(BiasAlignment(0f, -0.5f * transitionProgress)),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      val pringleSize = (260f - 20f * transitionProgress).dp
      Box(contentAlignment = Alignment.Center) {
        Box(
          modifier = Modifier
            .size(320.dp, 200.dp)
            .background(
              Brush.radialGradient(
                colors = listOf(
                  Color(0x22FFD93D),
                  Color(0x00FFD93D)
                )
              )
            )
        )

        Image(
          painter = painterResource(id = R.drawable.img_pure_pringle_1780498841768),
          contentDescription = "Spinning Pringle",
          modifier = Modifier
            .size(pringleSize)
            .graphicsLayer(rotationZ = rotation)
            .testTag("spinning_pringle"),
          contentScale = ContentScale.Fit,
          colorFilter = colorFilter
        )
      }
    }

    // 2. Header Row (Timer, Sensing Active Badge, Check Button) sliding down/up
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerTopPadding = (statusBarPadding.value * (1f - transitionProgress)).dp
    val headerHorizontalPadding = (24f - 8f * transitionProgress).dp
    val headerVerticalPadding = (20f - 12f * transitionProgress).dp

    Box(
      modifier = Modifier
        .align(BiasAlignment(0f, -1.0f + 1.5f * transitionProgress))
        .padding(top = headerTopPadding)
        .padding(horizontal = headerHorizontalPadding, vertical = headerVerticalPadding)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Timer
        val timerSize = (24f + 4f * transitionProgress).sp
        val timerWeight = if (transitionProgress > 0.5f) FontWeight.SemiBold else FontWeight.Medium
        val timerAlpha = 0.85f + 0.05f * transitionProgress
        androidx.compose.material3.Text(
          text = formattedTime,
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = timerSize,
            fontWeight = timerWeight,
            color = Color.White.copy(alpha = timerAlpha),
            letterSpacing = (-0.5).sp,
            fontFeatureSettings = "tnum"
          ),
          modifier = Modifier
            .clickable { onElapsedClickToHighScores(elapsedTimeMs) }
            .burnInProtection(enableShine = true)
            .testTag("elapsed_timer")
        )

        // Stop & Save
        val buttonSize = (48f + 4f * transitionProgress).dp
        val iconSize = (24f + 2f * transitionProgress).dp
        Box(
          modifier = Modifier
            .size(buttonSize)
            .border(
              width = 1.dp,
              color = Color.White.copy(alpha = 0.15f),
              shape = RoundedCornerShape(12.dp)
            )
            .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
            .clickable { onBackToHomeWithScore(elapsedTimeMs) }
            .testTag("stop_save_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Stop & Save",
            tint = Color.White,
            modifier = Modifier
              .size(iconSize)
              .burnInProtection(enableShine = true)
          )
        }
      }
    }

    // 3. Spotify Widget remains anchored beautifully at the bottom
    SpotifyPlayerWidget(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 16.dp)
    )
  }
}

@Composable
fun HighScoresScreen(
  scores: List<ScoreEntity>,
  onBackClicked: () -> Unit,
  onClearAll: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("high_scores_screen")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 32.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      androidx.compose.material3.Text(
        text = "HIGH SCORES",
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontSize = 24.sp,
          fontWeight = FontWeight.ExtraLight,
          color = Color.White,
          letterSpacing = 4.sp
        ),
        modifier = Modifier.padding(top = 16.dp)
      )

      Spacer(modifier = Modifier.height(4.dp))

      androidx.compose.material3.Text(
        text = "MOST TIME PINgLE SPINnED",
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White.copy(alpha = 0.4f),
          letterSpacing = 2.sp
        )
      )

      Spacer(modifier = Modifier.height(32.dp))

      if (scores.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text(
              text = "NO SPINS YET",
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.3f),
                letterSpacing = 2.sp
              )
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Text(
              text = "Select play to start spinning",
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.2f)
              )
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          itemsIndexed(scores) { index, score ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .border(
                  width = 1.dp,
                  color = Color.White.copy(alpha = 0.08f),
                  shape = RoundedCornerShape(12.dp)
                )
                .background(
                  Color.White.copy(alpha = 0.02f),
                  shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                androidx.compose.material3.Text(
                  text = "#${index + 1}",
                  style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (index) {
                      0 -> Color(0xFFFFD93D)
                      1 -> Color(0xFFC0C0C0)
                      2 -> Color(0xFFCD7F32)
                      else -> Color.White.copy(alpha = 0.3f)
                    }
                  )
                )

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  androidx.compose.material3.Text(
                    text = formatDuration(score.durationMs),
                    style = TextStyle(
                      fontFamily = FontFamily.SansSerif,
                      fontSize = 18.sp,
                      fontWeight = FontWeight.Medium,
                      color = Color.White.copy(alpha = 0.9f),
                      fontFeatureSettings = "tnum"
                    )
                  )
                  if (score.isManual) {
                    Box(
                      modifier = Modifier
                        .background(Color(0xFFFFD93D).copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp))
                        .border(0.5.dp, Color(0xFFFFD93D).copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                      androidx.compose.material3.Text(
                        text = "pms",
                        style = TextStyle(
                          fontFamily = FontFamily.SansSerif,
                          fontSize = 8.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFFFFD93D),
                          letterSpacing = 0.5.sp
                        )
                      )
                    }
                  }
                }
              }

              androidx.compose.material3.Text(
                text = formatTimestamp(score.timestamp),
                style = TextStyle(
                  fontFamily = FontFamily.SansSerif,
                  fontSize = 11.sp,
                  color = Color.White.copy(alpha = 0.3f)
                )
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      if (scores.isNotEmpty()) {
        androidx.compose.material3.Text(
          text = "WARNING: DELETING SCORES IS PERMANENT",
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red.copy(alpha = 0.5f),
            letterSpacing = 1.sp
          ),
          modifier = Modifier.padding(bottom = 12.dp)
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .weight(1.2f)
            .height(56.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .background(Color.Black, shape = RoundedCornerShape(14.dp))
            .clickable { onBackClicked() },
          contentAlignment = Alignment.Center
        ) {
          androidx.compose.material3.Text(
            text = "BACK",
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = Color.White,
              letterSpacing = 2.sp
            )
          )
        }

        if (scores.isNotEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .height(56.dp)
              .border(1.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
              .background(Color.Black, shape = RoundedCornerShape(14.dp))
              .clickable { onClearAll() },
            contentAlignment = Alignment.Center
          ) {
            androidx.compose.material3.Text(
              text = "CLEAR ALL",
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Red.copy(alpha = 0.7f),
                letterSpacing = 1.sp
              )
            )
          }
        }
      }
    }
  }
}

@Composable
fun OptionsScreen(
  speed: Float,
  pingleFriction: Float,
  isManualUnlocked: Boolean,
  pingleTintId: String,
  pingleCustomColorInt: Int,
  totalSpinDuration: Long,
  foldAngleThreshold: Float,
  onSpeedChange: (Float) -> Unit,
  onFrictionChange: (Float) -> Unit,
  onTintSelect: (String) -> Unit,
  onCustomColorChange: (Int) -> Unit,
  onFoldAngleThresholdChange: (Float) -> Unit,
  onBackClicked: () -> Unit
) {
  val hours = totalSpinDuration / 3600000L
  val minutes = (totalSpinDuration % 3600000L) / 60000L
  val seconds = (totalSpinDuration % 60000L) / 1000L
  val progressText = String.format(Locale.US, "%d:%02d:%02d / 1:00:00", hours, minutes, seconds)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("options_screen")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 32.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .padding(top = 16.dp)
      ) {
        androidx.compose.material3.Text(
          text = "OPTIONS",
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraLight,
            color = Color.White,
            letterSpacing = 6.sp
          )
        )
        Spacer(modifier = Modifier.height(6.dp))
        androidx.compose.material3.Text(
          text = "SYSTEM CONFIGURATION",
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 2.sp
          )
        )

        // SENSATIONAL rotating preview pingle showing what it looks like ingame
        val selectedTintOption = remember(pingleTintId) { PINGLE_TINTS.firstOrNull { it.id == pingleTintId } }
        val tintColor = remember(pingleTintId, pingleCustomColorInt) {
          if (pingleTintId == "custom") Color(pingleCustomColorInt) else selectedTintOption?.color
        }
        val colorFilter = remember(tintColor) { tintColor?.let { ColorFilter.tint(it, BlendMode.Color) } }

        var previewRotation by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(speed) {
          var lastTime = System.nanoTime()
          while (isActive) {
            withFrameMillis {
              val now = System.nanoTime()
              val deltaSeconds = (now - lastTime) / 1_000_000_000f
              lastTime = now
              val speedFactor = speed * 120f // multiplier for rotation speed
              previewRotation = (previewRotation + deltaSeconds * speedFactor) % 360f
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
          modifier = Modifier
            .size(110.dp)
            .border(
              width = 1.dp,
              brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
              ),
              shape = androidx.compose.foundation.shape.CircleShape
            )
            .background(
              brush = Brush.radialGradient(
                colors = listOf(
                  tintColor?.copy(alpha = 0.15f) ?: Color.White.copy(alpha = 0.05f),
                  Color.Transparent
                )
              ),
              shape = androidx.compose.foundation.shape.CircleShape
            ),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_pure_pringle_1780498841768),
            contentDescription = "Pingle Ingame Preview",
            modifier = Modifier
              .size(76.dp)
              .graphicsLayer(rotationZ = previewRotation)
              .testTag("pingle_preview"),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
          )
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(vertical = 12.dp)
          .verticalScroll(androidx.compose.foundation.rememberScrollState())
          .border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp)
          )
          .background(Color.White.copy(alpha = 0.01f), shape = RoundedCornerShape(16.dp))
          .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
      ) {
        // Pingle Speed Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          androidx.compose.material3.Text(
            text = "pinglespeed".uppercase(Locale.US),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White.copy(alpha = 0.4f),
              letterSpacing = 3.sp
            )
          )
          androidx.compose.material3.Text(
            text = String.format(Locale.US, "%.1fx", speed),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontSize = 16.sp,
              fontWeight = FontWeight.Medium,
              color = Color.White,
              fontFeatureSettings = "tnum"
            )
          )
        }

        // Pingle Speed Slider
        Slider(
          value = speed,
          onValueChange = onSpeedChange,
          valueRange = 0.5f..10.0f,
          colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
          ),
          modifier = Modifier.fillMaxWidth().testTag("pingle_speed_slider")
        )

        // If manual is unlocked, show pingle friction slider!
        if (isManualUnlocked) {
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            androidx.compose.material3.Text(
              text = "pinglefriction".uppercase(Locale.US),
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 3.sp
              )
            )
            androidx.compose.material3.Text(
              text = String.format(Locale.US, "%.0f%%", pingleFriction),
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                fontFeatureSettings = "tnum"
              )
            )
          }

          Slider(
            value = pingleFriction,
            onValueChange = onFrictionChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
              thumbColor = Color.White,
              activeTrackColor = Color.White,
              inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("pingle_friction_slider")
          )
        }

        // If device is foldable, show fold angle slider!
        val isFoldable = isFoldableDevice()
        if (isFoldable) {
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            androidx.compose.material3.Text(
              text = "fold angle".uppercase(Locale.US),
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD93D),
                letterSpacing = 3.sp
              )
            )
            androidx.compose.material3.Text(
              text = String.format(Locale.US, "%.0f°", foldAngleThreshold),
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                fontFeatureSettings = "tnum"
              )
            )
          }

          Slider(
            value = foldAngleThreshold,
            onValueChange = onFoldAngleThresholdChange,
            valueRange = 0f..180f,
            colors = SliderDefaults.colors(
              thumbColor = Color(0xFFFFD93D),
              activeTrackColor = Color(0xFFFFD93D),
              inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("pingle_fold_angle_slider")
          )

          androidx.compose.material3.Text(
            text = "Triggers tabletop split layout when folded below this angle.",
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontSize = 10.sp,
              color = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.padding(top = 4.dp)
          )
        }

        // Tint Selector block (depends on 1-hour total spin duration)
        Spacer(modifier = Modifier.height(12.dp))
        val context = LocalContext.current
        if (totalSpinDuration >= 3600000L) {
          val isCustomUnlocked = totalSpinDuration >= 18000000L // 5 hours in ms
          val isCustomSelected = pingleTintId == "custom"
          Column {
            androidx.compose.material3.Text(
              text = "PINGLE TINT",
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 3.sp
              ),
              modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              P_TINTS_TAG@ for (tint in PINGLE_TINTS) {
                val isSelected = pingleTintId == tint.id
                Box(
                  modifier = Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .border(
                      width = if (isSelected) 2.dp else 1.dp,
                      color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                      color = tint.color?.copy(alpha = 0.2f) ?: Color.White.copy(alpha = 0.05f),
                      shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onTintSelect(tint.id) }
                    .testTag("tint_${tint.id}"),
                  contentAlignment = Alignment.Center
                ) {
                  Box(
                    modifier = Modifier
                      .size(12.dp)
                      .background(
                        color = tint.color ?: Color.White,
                        shape = androidx.compose.foundation.shape.CircleShape
                      )
                  )
                }
              }

              // Custom Color Circle (unlocked at 5 hours)
              val rainbowBrush = remember {
                Brush.sweepGradient(
                  colors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                  )
                )
              }
              Box(
                modifier = Modifier
                  .size(width = 44.dp, height = 36.dp)
                  .border(
                    width = if (isCustomSelected) 2.dp else 1.dp,
                    color = if (isCustomSelected) Color.White else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                  )
                  .background(
                    if (isCustomUnlocked) Color(pingleCustomColorInt).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(8.dp)
                  )
                  .clickable {
                    if (isCustomUnlocked) {
                      onTintSelect("custom")
                    } else {
                      val remainingMs = 18000000L - totalSpinDuration
                      val remainingHours = remainingMs / 3600000L
                      val remainingMinutes = (remainingMs % 3600000L) / 60000L
                      showToast(
                        context,
                        String.format(Locale.US, "Custom Colors Lock: Spin for another %d hr %d min to unlock!", remainingHours, remainingMinutes),
                        Toast.LENGTH_SHORT
                      )
                    }
                  }
                  .testTag("tint_custom"),
                contentAlignment = Alignment.Center
              ) {
                if (isCustomUnlocked) {
                  Box(
                    modifier = Modifier
                      .size(14.dp)
                      .border(width = 1.5.dp, brush = rainbowBrush, shape = androidx.compose.foundation.shape.CircleShape)
                      .padding(1.5.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .background(Color(pingleCustomColorInt), shape = androidx.compose.foundation.shape.CircleShape)
                    )
                  }
                } else {
                  Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Custom Locked",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }

            if (!isCustomUnlocked) {
              Spacer(modifier = Modifier.height(10.dp))
              val customHours = totalSpinDuration / 3600000L
              val customMinutes = (totalSpinDuration % 3600000L) / 60000L
              val customSeconds = (totalSpinDuration % 60000L) / 1000L
              val progressTextCustom = String.format(Locale.US, "%d:%02d:%02d / 5:00:00", customHours, customMinutes, customSeconds)
              androidx.compose.material3.Text(
                text = "🔒 Custom Colors Lock in Progress: $progressTextCustom",
                style = TextStyle(
                  fontFamily = FontFamily.SansSerif,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFFFD93D).copy(alpha = 0.8f),
                  fontFeatureSettings = "tnum"
                )
              )
            } else if (isCustomSelected) {
              Spacer(modifier = Modifier.height(16.dp))
              
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                androidx.compose.material3.Text(
                  text = "CUSTOM_COLOR".uppercase(Locale.US),
                  style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.4f),
                    letterSpacing = 2.sp
                  )
                )

                // Beautifully designed Hex Code and Colored Swatch
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  androidx.compose.material3.Text(
                    text = String.format(Locale.US, "#%06X", pingleCustomColorInt and 0xFFFFFF),
                    style = TextStyle(
                      fontFamily = FontFamily.Monospace,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(pingleCustomColorInt)
                    )
                  )
                  Box(
                    modifier = Modifier
                      .size(24.dp)
                      .background(Color(pingleCustomColorInt), shape = RoundedCornerShape(6.dp))
                      .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                  )
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              var r by remember(pingleCustomColorInt) { mutableFloatStateOf(((pingleCustomColorInt shr 16) and 0xFF).toFloat()) }
              var g by remember(pingleCustomColorInt) { mutableFloatStateOf(((pingleCustomColorInt shr 8) and 0xFF).toFloat()) }
              var b by remember(pingleCustomColorInt) { mutableFloatStateOf((pingleCustomColorInt and 0xFF).toFloat()) }

              val redGradient = remember(g, b) {
                Brush.horizontalGradient(
                  colors = listOf(Color(0, g.toInt(), b.toInt()), Color(255, g.toInt(), b.toInt()))
                )
              }
              val greenGradient = remember(r, b) {
                Brush.horizontalGradient(
                  colors = listOf(Color(r.toInt(), 0, b.toInt()), Color(r.toInt(), 255, b.toInt()))
                )
              }
              val blueGradient = remember(r, g) {
                Brush.horizontalGradient(
                  colors = listOf(Color(r.toInt(), g.toInt(), 0), Color(r.toInt(), g.toInt(), 255))
                )
              }

              Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                FancyRGBSlider(
                  label = "R",
                  value = r,
                  gradient = redGradient,
                  thumbColor = Color(0xFFFF453A),
                  onValueChange = {
                    r = it
                    val newColor = (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()).toInt()
                    onCustomColorChange(newColor)
                  },
                  valueText = String.format(Locale.US, "%d", r.toInt())
                )

                FancyRGBSlider(
                  label = "G",
                  value = g,
                  gradient = greenGradient,
                  thumbColor = Color(0xFF30D158),
                  onValueChange = {
                    g = it
                    val newColor = (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()).toInt()
                    onCustomColorChange(newColor)
                  },
                  valueText = String.format(Locale.US, "%d", g.toInt())
                )

                FancyRGBSlider(
                  label = "B",
                  value = b,
                  gradient = blueGradient,
                  thumbColor = Color(0xFF0A84FF),
                  onValueChange = {
                    b = it
                    val newColor = (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()).toInt()
                    onCustomColorChange(newColor)
                  },
                  valueText = String.format(Locale.US, "%d", b.toInt())
                )
              }
            }
          }
        } else {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
          ) {
            androidx.compose.material3.Text(
              text = "🔒 PINGLE TINT (Locked)",
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 3.sp
              )
            )

            Spacer(modifier = Modifier.height(4.dp))

            androidx.compose.material3.Text(
              text = "Reach 1 Hour total spin time to unlock dynamic custom tints!",
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
              )
            )

            Spacer(modifier = Modifier.height(4.dp))

            androidx.compose.material3.Text(
              text = "Progress: $progressText",
              style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD93D),
                fontFeatureSettings = "tnum"
              )
            )
          }
        }

        // --- SPOTIFY INTEGRATION SECTION ---
        Spacer(modifier = Modifier.height(16.dp))
        val spotifyManager = remember { SpotifyManager.getInstance(context) }
        val spotifyClientId by spotifyManager.clientId.collectAsState()
        val spotifyClientSecret by spotifyManager.clientSecret.collectAsState()
        val spotifyUsername by spotifyManager.username.collectAsState()
        val spotifyPassword by spotifyManager.password.collectAsState()
        val spotifyIsSimulated by spotifyManager.isSimulated.collectAsState()
        val spotifyIsConnected by spotifyManager.isConnected.collectAsState()
        val spotifyIsLoading by spotifyManager.isLoading.collectAsState()
        val spotifyError by spotifyManager.error.collectAsState()

        var editClientId by remember(spotifyClientId) { mutableStateOf(spotifyClientId) }
        var editClientSecret by remember(spotifyClientSecret) { mutableStateOf(spotifyClientSecret) }
        var editUsername by remember(spotifyUsername) { mutableStateOf(spotifyUsername) }
        var editPassword by remember(spotifyPassword) { mutableStateOf(spotifyPassword) }
        var loginMethod by remember { mutableStateOf(if (spotifyClientId.isNotEmpty()) "developer" else "simple") } // "simple" or "developer"
        var showLoginDialog by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        if (totalSpinDuration < 600000L) {
          // Locked State
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.01f), shape = RoundedCornerShape(12.dp))
              .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked", tint = Color(0xFFFFD93D), modifier = Modifier.size(16.dp))
              androidx.compose.material3.Text(
                text = "SPOTIFY INTEGRATION",
                style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Text(
              text = "Unlock Spotify integration after 10 minutes of spin! Connect your account and listen to a random song from a random one of your playlists directly inside the app while spinning.",
              style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            val currentMins = (totalSpinDuration / 60000L).coerceAtMost(10L)
            val currentSecs = ((totalSpinDuration % 60000L) / 1000L).coerceAtMost(59L)
            androidx.compose.material3.Text(
              text = String.format(Locale.US, "Progress: %d:%02d / 10:00", currentMins, currentSecs),
              style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD93D))
            )
          }
        } else {
          // Unlocked State
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .border(width = 1.dp, color = Color(0xFF1DB954).copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.02f), shape = RoundedCornerShape(12.dp))
              .padding(16.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Spotify",
                tint = Color(0xFF1DB954),
                modifier = Modifier.size(18.dp)
              )
              androidx.compose.material3.Text(
                text = "SPOTIFY INTEGRATION (UNLOCKED!)",
                style = TextStyle(
                  fontFamily = FontFamily.SansSerif,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1DB954)
                )
              )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            if (!spotifyIsConnected) {
              // Toggle tabs
              Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clickable { loginMethod = "simple" }
                    .border(
                      width = 1.dp,
                      color = if (loginMethod == "simple") Color(0xFF1DB954) else Color.White.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                      if (loginMethod == "simple") Color(0xFF1DB954).copy(alpha = 0.1f) else Color.Transparent,
                      shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  androidx.compose.material3.Text(
                    text = "Standard Login",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (loginMethod == "simple") Color(0xFF1DB954) else Color.White.copy(alpha = 0.6f))
                  )
                }

                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clickable { loginMethod = "developer" }
                    .border(
                      width = 1.dp,
                      color = if (loginMethod == "developer") Color(0xFF1DB954) else Color.White.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                      if (loginMethod == "developer") Color(0xFF1DB954).copy(alpha = 0.1f) else Color.Transparent,
                      shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  androidx.compose.material3.Text(
                    text = "Developer Login",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (loginMethod == "developer") Color(0xFF1DB954) else Color.White.copy(alpha = 0.6f))
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              if (loginMethod == "simple") {
                androidx.compose.material3.Text(
                  text = "Login with your Spotify account credentials:",
                  style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                  value = editUsername,
                  onValueChange = { editUsername = it },
                  label = { androidx.compose.material3.Text("Spotify Username or Email", color = Color.White.copy(alpha = 0.5f)) },
                  textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                  modifier = Modifier.fillMaxWidth(),
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                  )
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                  value = editPassword,
                  onValueChange = { editPassword = it },
                  label = { androidx.compose.material3.Text("Spotify Password", color = Color.White.copy(alpha = 0.5f)) },
                  visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                  textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                  modifier = Modifier.fillMaxWidth(),
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                  )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (spotifyIsLoading) {
                  androidx.compose.material3.Text(
                    text = "Connecting & Authenticating...",
                    style = TextStyle(fontSize = 11.sp, color = Color(0xFFFFD93D))
                  )
                } else {
                  MinimalistButton(
                    text = "CONNECT SPOTIFY ACCOUNT",
                    onClick = {
                      if (editUsername.isBlank() || editPassword.isBlank()) {
                        showToast(context, "Please enter both Username and Password!")
                      } else {
                        spotifyManager.loginWithUsernamePassword(editUsername, editPassword)
                        showToast(context, "Successfully authenticated with Spotify!")
                      }
                    },
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              } else {
                androidx.compose.material3.Text(
                  text = "Setup your Spotify Developer Credentials to connect your account:",
                  style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                  value = editClientId,
                  onValueChange = { editClientId = it },
                  label = { androidx.compose.material3.Text("Spotify Client ID", color = Color.White.copy(alpha = 0.5f)) },
                  textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                  modifier = Modifier.fillMaxWidth(),
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                  )
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                  value = editClientSecret,
                  onValueChange = { editClientSecret = it },
                  label = { androidx.compose.material3.Text("Spotify Client Secret", color = Color.White.copy(alpha = 0.5f)) },
                  textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                  modifier = Modifier.fillMaxWidth(),
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                  )
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                androidx.compose.material3.Text(
                  text = "How to connect:\n1. Visit developer.spotify.com & log in\n2. Create an App (name: PingleSpin)\n3. Edit Settings: set Redirect URI to:\n    https://localhost/callback\n4. Copy Client ID & Client Secret here and click Connect!",
                  style = TextStyle(fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f), lineHeight = 12.sp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (spotifyIsLoading) {
                  androidx.compose.material3.Text(
                    text = "Connecting & Authenticating...",
                    style = TextStyle(fontSize = 11.sp, color = Color(0xFFFFD93D))
                  )
                } else {
                  MinimalistButton(
                    text = "CONNECT DEVELOPER ACCOUNT",
                    onClick = {
                      if (editClientId.isBlank() || editClientSecret.isBlank()) {
                        showToast(context, "Please enter both Client ID and Client Secret!")
                      } else {
                        spotifyManager.saveCredentials(editClientId, editClientSecret)
                        showLoginDialog = true
                      }
                    },
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              }
            } else {
              val connectedMsg = if (spotifyIsSimulated) {
                "✅ Account Connected (Simulated: $spotifyUsername)!"
              } else {
                "✅ Account Connected Successfully!"
              }
              androidx.compose.material3.Text(
                text = connectedMsg,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1DB954))
              )
              
              Spacer(modifier = Modifier.height(6.dp))
              
              androidx.compose.material3.Text(
                text = "While spinning in play modes, you will now see the Spotify Player to stream random songs from random playlists in your library!",
                style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
              )
              
              Spacer(modifier = Modifier.height(12.dp))
              
              MinimalistButton(
                text = "DISCONNECT ACCOUNT",
                onClick = {
                  spotifyManager.disconnect()
                  showToast(context, "Disconnected from Spotify.")
                },
                modifier = Modifier.fillMaxWidth()
              )
            }
            
            if (spotifyError != null) {
              Spacer(modifier = Modifier.height(8.dp))
              androidx.compose.material3.Text(
                text = spotifyError ?: "",
                style = TextStyle(fontSize = 11.sp, color = Color.Red)
              )
            }
          }
        }

        if (showLoginDialog) {
          Dialog(onDismissRequest = { showLoginDialog = false }) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 40.dp, horizontal = 16.dp)
                .background(Color.Black, shape = RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            ) {
              Column(modifier = Modifier.fillMaxSize()) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  androidx.compose.material3.Text(
                    text = "Connect Spotify",
                    color = Color.White,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                  )
                  IconButton(onClick = { showLoginDialog = false }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                  }
                }

                AndroidView(
                  modifier = Modifier.weight(1f),
                  factory = { ctx ->
                    WebView(ctx).apply {
                      settings.javaScriptEnabled = true
                      webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                          view: WebView?,
                          request: WebResourceRequest?
                        ): Boolean {
                          val uri = request?.url ?: return false
                          val uriString = uri.toString()
                          if (uriString.startsWith(SpotifyManager.REDIRECT_URI)) {
                            val code = uri.getQueryParameter("code")
                            if (code != null) {
                              scope.launch {
                                val success = spotifyManager.handleAuthCode(code)
                                if (success) {
                                  showToast(ctx, "Successfully authenticated with Spotify!")
                                }
                                showLoginDialog = false
                              }
                              return true
                            }
                          }
                          return false
                        }
                      }
                      loadUrl(spotifyManager.getAuthorizeUrl())
                    }
                  }
                )
              }
            }
          }
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        MinimalistButton(
          text = "SAVE & BACK",
          onClick = onBackClicked,
          modifier = Modifier
            .padding(bottom = 24.dp)
            .testTag("save_back_button")
        )
      }
    }
  }
}

@Composable
fun FancyRGBSlider(
  label: String,
  value: Float,
  gradient: Brush,
  thumbColor: Color,
  onValueChange: (Float) -> Unit,
  valueText: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    androidx.compose.material3.Text(
      text = label,
      style = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = thumbColor
      ),
      modifier = Modifier.width(14.dp)
    )

    Box(
      modifier = Modifier
        .weight(1f)
        .height(32.dp),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .background(gradient, shape = RoundedCornerShape(4.dp))
          .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
      )
      Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 0f..255f,
        colors = SliderDefaults.colors(
          thumbColor = Color.White,
          activeTrackColor = Color.Transparent,
          inactiveTrackColor = Color.Transparent,
          activeTickColor = Color.Transparent,
          inactiveTickColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
      )
    }

    androidx.compose.material3.Text(
      text = valueText,
      style = TextStyle(
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
      ),
      modifier = Modifier.width(28.dp)
    )
  }
}

@Composable
fun ManualPlayScreen(
  pingleFriction: Float,
  pingleTintId: String,
  pingleCustomColorInt: Int,
  foldAngleThreshold: Float,
  onBackToHomeWithScore: (Long) -> Unit,
  onElapsedClickToHighScores: (Long) -> Unit
) {
  var rotation by remember { mutableFloatStateOf(0f) }
  var velocity by remember { mutableFloatStateOf(0f) }
  var elapsedTimeMs by remember { mutableLongStateOf(0L) }

  val context = LocalContext.current
  DisposableEffect(Unit) {
    onDispose {
      SpotifyManager.getInstance(context).stopPreview()
    }
  }

  val selectedTintOption = remember(pingleTintId) { PINGLE_TINTS.firstOrNull { it.id == pingleTintId } }
  val tintColor = remember(pingleTintId, pingleCustomColorInt) {
    if (pingleTintId == "custom") Color(pingleCustomColorInt) else selectedTintOption?.color
  }
  val colorFilter = remember(tintColor) { tintColor?.let { ColorFilter.tint(it, BlendMode.Color) } }

  val currentFriction by androidx.compose.runtime.rememberUpdatedState(pingleFriction)

  // High precision timer/physics loop updating at ~60fps
  LaunchedEffect(Unit) {
    var lastTime = System.currentTimeMillis()
    while (isActive) {
      val now = System.currentTimeMillis()
      val dt = (now - lastTime).coerceIn(1, 100)
      lastTime = now

      if (Math.abs(velocity) > 0.05f) {
        rotation = (rotation + velocity * (dt / 16f)) % 360f
        elapsedTimeMs += dt
        // Proportional physics decay based on current friction: 0% friction means infinite spin, 100% means immediate stop
        val decay = (1.0f - (currentFriction / 100f)).coerceIn(0f, 1f)
        velocity *= decay
      } else {
        velocity = 0f
      }
      delay(16)
    }
  }

  val totalSeconds = elapsedTimeMs / 1000
  val minutes = (totalSeconds / 60) % 60
  val seconds = totalSeconds % 60
  val centiseconds = (elapsedTimeMs % 1000) / 10

  val formattedTime = if (totalSeconds < 60) {
    String.format(Locale.US, "%d.%02d", seconds, centiseconds)
  } else {
    String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds)
  }

  val isFolded = isFoldedState(threshold = foldAngleThreshold)
  val isFoldable = isFoldableDevice()

  val transitionProgress by animateFloatAsState(
    targetValue = if (isFolded) 1f else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioLowBouncy,
      stiffness = Spring.StiffnessLow
    ),
    label = "fold_transition"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("manual_play_viewport")
  ) {
    // 1. Centered Spinning Pringle unit moving smoothly with transition progress
    Column(
      modifier = Modifier.align(BiasAlignment(0f, -0.5f * transitionProgress)),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      val pringleContainerSize = (320f - 40f * transitionProgress).dp
      val imageSize = (260f - 40f * transitionProgress).dp

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(pringleContainerSize)
          .pointerInput(pingleFriction) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              val horizontalSpeed = dragAmount.x
              val verticalSpeed = dragAmount.y
              val magnitude = horizontalSpeed + verticalSpeed
              val frictionFactor = pingleFriction / 100f
              val momentumFactor = (1.0f - frictionFactor).coerceIn(0f, 1f)
              velocity += magnitude * 0.15f * momentumFactor * 1.5f
              velocity = velocity.coerceIn(-80f, 80f)
            }
          }
      ) {
        Box(
          modifier = Modifier
            .size(pringleContainerSize, (200f - 20f * transitionProgress).dp)
            .background(
              Brush.radialGradient(
                colors = listOf(
                  Color(0x22FFD93D),
                  Color(0x00FFD93D)
                )
              )
            )
        )

        Image(
          painter = painterResource(id = R.drawable.img_pure_pringle_1780498841768),
          contentDescription = "Manual Spinning Pringle",
          modifier = Modifier
            .size(imageSize)
            .graphicsLayer(rotationZ = rotation)
            .testTag("manual_spinning_pringle"),
          contentScale = ContentScale.Fit,
          colorFilter = colorFilter
        )
      }

      Spacer(modifier = Modifier.height((16f - 8f * transitionProgress).dp))

      androidx.compose.material3.Text(
        text = if (transitionProgress > 0.5f) "Flick or Drag to spin!".uppercase(Locale.US) else "Flick or Drag the Pingle to spin!".uppercase(Locale.US),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontSize = (11f - 1f * transitionProgress).sp,
          fontWeight = FontWeight.Medium,
          color = Color.White.copy(alpha = 0.4f),
          letterSpacing = 2.sp
        )
      )
    }

    // 2. Header Row (Timer, Sensing Active Badge, Check Button) sliding down/up
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerTopPadding = (statusBarPadding.value * (1f - transitionProgress)).dp
    val headerHorizontalPadding = (24f - 8f * transitionProgress).dp
    val headerVerticalPadding = (20f - 12f * transitionProgress).dp

    Box(
      modifier = Modifier
        .align(BiasAlignment(0f, -1.0f + 1.5f * transitionProgress))
        .padding(top = headerTopPadding)
        .padding(horizontal = headerHorizontalPadding, vertical = headerVerticalPadding)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Timer
        val timerSize = (24f + 4f * transitionProgress).sp
        val timerWeight = if (transitionProgress > 0.5f) FontWeight.SemiBold else FontWeight.Medium
        val timerAlpha = 0.85f + 0.05f * transitionProgress
        androidx.compose.material3.Text(
          text = formattedTime,
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = timerSize,
            fontWeight = timerWeight,
            color = Color.White.copy(alpha = timerAlpha),
            letterSpacing = (-0.5).sp,
            fontFeatureSettings = "tnum"
          ),
          modifier = Modifier
            .clickable { onElapsedClickToHighScores(elapsedTimeMs) }
            .burnInProtection(enableShine = true)
            .testTag("elapsed_timer")
        )

        // Stop & Save
        val buttonSize = (48f + 4f * transitionProgress).dp
        val iconSize = (24f + 2f * transitionProgress).dp
        Box(
          modifier = Modifier
            .size(buttonSize)
            .border(
              width = 1.dp,
              color = Color.White.copy(alpha = 0.15f),
              shape = RoundedCornerShape(12.dp)
            )
            .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
            .clickable { onBackToHomeWithScore(elapsedTimeMs) }
            .testTag("stop_save_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Stop & Save",
            tint = Color.White,
            modifier = Modifier
              .size(iconSize)
              .burnInProtection(enableShine = true)
          )
        }
      }
    }

    // 3. Spotify Widget remains anchored beautifully at the bottom
    SpotifyPlayerWidget(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 16.dp)
    )
  }
}

@Composable
fun KeepScreenOn() {
  val view = LocalView.current
  DisposableEffect(view) {
    view.keepScreenOn = true
    onDispose {
      view.keepScreenOn = false
    }
  }
}

@Composable
fun Modifier.burnInProtection(enableShine: Boolean = true): Modifier {
  val infiniteTransition = rememberInfiniteTransition(label = "burn_in_modifier")
  val shiftTime = infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 8000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "shift_time"
  )
  val shimmerTime = infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 5000f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 5000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "shimmer_time"
  )

  val shiftRad = (shiftTime.value * (Math.PI / 180.0)).toFloat()
  val dx = (Math.cos(shiftRad.toDouble()) * 2.0).toFloat().dp
  val dy = (Math.sin(shiftRad.toDouble()) * 2.0).toFloat().dp

  val isShining = shimmerTime.value < 1200f
  val ratio = if (isShining) (shimmerTime.value / 1200f) else 0f
  
  val shineBrush = if (enableShine && isShining) {
    val sweepWidth = 180f
    val startCoord = (ratio * (sweepWidth * 3.5f)) - sweepWidth
    Brush.linearGradient(
      colors = listOf(
        Color.Transparent,
        Color.White.copy(alpha = 0.0f),
        Color.White.copy(alpha = 0.75f),
        Color(0xFFFFD93D).copy(alpha = 0.5f), // Fancy gold flare
        Color.White.copy(alpha = 0.75f),
        Color.White.copy(alpha = 0.0f),
        Color.Transparent
      ),
      start = androidx.compose.ui.geometry.Offset(startCoord, 0f),
      end = androidx.compose.ui.geometry.Offset(startCoord + sweepWidth, sweepWidth)
    )
  } else {
    null
  }

  val density = androidx.compose.ui.platform.LocalDensity.current
  return this
    .graphicsLayer {
      translationX = with(density) { dx.toPx() }
      translationY = with(density) { dy.toPx() }
      compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }
    .drawWithContent {
      drawContent()
      if (shineBrush != null) {
        drawRect(brush = shineBrush, blendMode = BlendMode.SrcAtop)
      }
    }
}

private var globalToast: Toast? = null

fun showToast(context: android.content.Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
  globalToast?.cancel()
  val toast = Toast.makeText(context.applicationContext, message, duration)
  globalToast = toast
  toast.show()
}

fun formatDuration(elapsedTimeMs: Long): String {
  val totalSeconds = elapsedTimeMs / 1000
  val minutes = (totalSeconds / 60) % 60
  val seconds = totalSeconds % 60
  val centiseconds = (elapsedTimeMs % 1000) / 10
  return if (totalSeconds < 60) {
    String.format(Locale.US, "%d.%02d", seconds, centiseconds)
  } else {
    String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds)
  }
}

fun formatTimestamp(timestamp: Long): String {
  val date = java.util.Date(timestamp)
  val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.US)
  return sdf.format(date)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
  MyApplicationTheme {
    HomeScreen(
      isManualUnlocked = false,
      onUnlockManual = {},
      onPlayClicked = {},
      onManualSpinClicked = {},
      onHighScoresClicked = {},
      onOptionsClicked = {}
    )
  }
}

@Composable
fun SpotifyPlayerWidget(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spotifyManager = remember { SpotifyManager.getInstance(context) }
    val isConnected by spotifyManager.isConnected.collectAsState()
    val currentTrack by spotifyManager.currentTrack.collectAsState()
    val isPlaying by spotifyManager.isPlaying.collectAsState()
    val isLoading by spotifyManager.isLoading.collectAsState()
    val error by spotifyManager.error.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (!isConnected) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 1.dp,
                color = Color(0xFF1DB954).copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.9f), Color(0xFF0F0F0F).copy(alpha = 0.95f))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (currentTrack == null) {
                // Initial prompt state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.material3.Text(
                            text = "Spotify Playlist Player",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        )
                    }

                    if (isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF1DB954),
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFF1DB954), RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        spotifyManager.playRandomPlaylistSong()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = "PLAY RANDOM SONG",
                                style = TextStyle(
                                    color = Color(0xFF1DB954),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            } else {
                val track = currentTrack!!
                // Track playing state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Album art loaded via Coil AsyncImage
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.albumArtUrl != null) {
                            AsyncImage(
                                model = track.albumArtUrl,
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Track",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Title & Artist
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Text(
                            text = track.name,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        androidx.compose.material3.Text(
                            text = track.artist,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // Media Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (track.previewUrl != null) {
                            IconButton(
                                onClick = { spotifyManager.togglePlayPause() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    spotifyManager.playRandomPlaylistSong()
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF1DB954),
                                    strokeWidth = 1.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Random Track",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Subtitle/Footer with Open in Spotify link and status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.Text(
                        text = if (track.previewUrl == null) "No preview available" else "Streaming preview track",
                        style = TextStyle(color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                    )

                    Box(
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.content.Intent.ACTION_VIEW.let { android.net.Uri.parse(track.externalUrl) }
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    showToast(context, "Could not open Spotify client")
                                }
                            }
                            .padding(vertical = 2.dp, horizontal = 6.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "OPEN IN SPOTIFY ↗",
                            style = TextStyle(
                                color = Color(0xFF1DB954),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            if (error != null) {
                androidx.compose.material3.Text(
                    text = error ?: "",
                    style = TextStyle(color = Color.Red, fontSize = 10.sp),
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

