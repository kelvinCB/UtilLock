package app.utillock.android.challenge

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.utillock.android.BuildConfig
import app.utillock.android.UtilLockApplication
import app.utillock.android.data.BackendClient
import app.utillock.android.data.ProtectionRepository
import app.utillock.android.model.LogicChallenge
import app.utillock.android.ui.theme.UtilLockTheme
import app.utillock.android.ui.tr
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ChallengeActivity : ComponentActivity() {
    private val container by lazy { (application as UtilLockApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UtilLockTheme {
                ChallengeRoute(
                    backend = container.backendClient,
                    repository = container.protectionRepository,
                    onSuccess = { finish() },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ChallengeRoute(
    backend: BackendClient,
    repository: ProtectionRepository,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var challenge by remember { mutableStateOf<LogicChallenge?>(null) }
    var loading by remember { mutableStateOf(true) }
    var evaluating by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(3) }
    var pauseMinutes by remember { mutableIntStateOf(15) }
    var retryAt by remember { mutableLongStateOf(0L) }
    val waitFeedback = tr(
        "La validación local estará disponible cuando termine la espera.",
        "Local validation will be available after the wait.",
    )
    val localSuccess = tr("Respuesta verificada en el dispositivo.", "Answer verified on this device.")
    val localFailure = tr(
        "No pude encontrar la respuesta correcta en la foto.",
        "I could not find the correct answer in the photo.",
    )
    val cloudFallback = tr(
        "La evaluación en línea no respondió. Cambiamos a validación local.",
        "Online evaluation did not respond. Switching to local validation.",
    )
    val exhaustedFeedback = tr(
        "Tres intentos usados. Generamos otro ejercicio; espera 5 minutos.",
        "Three attempts used. A new exercise was generated; wait 5 minutes.",
    )

    suspend fun loadChallenge() {
        loading = true
        feedback = null
        val state = repository.snapshot()
        val cloudAllowed = backend.configured && (state.premium || state.freeAiGrantsUsed < 4)
        val remote = if (cloudAllowed) backend.startChallenge() else null
        challenge = remote ?: LocalChallengeEngine.create().copy(
            localAvailableAtEpochMs = when {
                BuildConfig.DEBUG || state.premium || state.freeAiGrantsUsed < 4 -> 0
                else -> System.currentTimeMillis() + 15 * 60_000L
            },
        )
        attempts = challenge?.attemptsRemaining ?: 3
        loading = false
    }

    LaunchedEffect(Unit) { loadChallenge() }

    fun evaluate(file: File) {
        evaluating = true
        feedback = null
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main).launch {
            val current = challenge ?: return@launch
            val verdict = if (current.remote) {
                backend.evaluateChallenge(current.id, file)
            } else {
                val allowedAt = maxOf(current.localAvailableAtEpochMs, retryAt)
                if (System.currentTimeMillis() < allowedAt) {
                    app.utillock.android.model.ChallengeVerdict(
                        accepted = false,
                        feedback = waitFeedback,
                        attemptsRemaining = attempts,
                    )
                } else {
                    val recognized = recognizeText(context, file)
                    val expected = LocalChallengeEngine.normalizeAnswer(current.expectedAnswer.orEmpty())
                    val tokens = Regex("-?\\d+(?:[.,]\\d+)?|verdadero|falso|true|false")
                        .findAll(recognized.lowercase())
                        .map { LocalChallengeEngine.normalizeAnswer(it.value.replace(',', '.')) }
                        .toSet()
                    val accepted = expected.isNotBlank() && expected in tokens
                    app.utillock.android.model.ChallengeVerdict(
                        accepted = accepted,
                        feedback = if (accepted) localSuccess else localFailure,
                        attemptsRemaining = if (accepted) attempts else attempts - 1,
                    )
                }
            }
            file.delete()
            evaluating = false
            if (verdict == null && current.remote) {
                val state = repository.snapshot()
                challenge = LocalChallengeEngine.create().copy(
                    localAvailableAtEpochMs = if (state.premium || state.freeAiGrantsUsed < 4) 0 else System.currentTimeMillis() + 15 * 60_000L,
                )
                feedback = cloudFallback
                return@launch
            }
            val result = verdict ?: return@launch
            attempts = result.attemptsRemaining
            feedback = result.feedback
            if (result.accepted) {
                repository.grantPause(pauseMinutes)
                if (current.remote && !repository.snapshot().premium) repository.recordAiGrant()
                onSuccess()
            } else if (result.attemptsRemaining <= 0) {
                retryAt = System.currentTimeMillis() + 5 * 60_000L
                challenge = LocalChallengeEngine.create().copy(localAvailableAtEpochMs = retryAt)
                attempts = 3
                feedback = exhaustedFeedback
            }
        }
    }

    ChallengeScreen(
        challenge = challenge,
        loading = loading,
        evaluating = evaluating,
        feedback = feedback,
        attempts = attempts,
        pauseMinutes = pauseMinutes,
        onPauseMinutes = { pauseMinutes = it },
        onPhoto = ::evaluate,
        onCancel = onCancel,
    )
}

@Composable
private fun ChallengeScreen(
    challenge: LogicChallenge?,
    loading: Boolean,
    evaluating: Boolean,
    feedback: String?,
    attempts: Int,
    pauseMinutes: Int,
    onPauseMinutes: (Int) -> Unit,
    onPhoto: (File) -> Unit,
    onCancel: () -> Unit,
) {
    if (loading || challenge == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var cameraOpen by remember { mutableStateOf(false) }
    val waitMs = challenge.localAvailableAtEpochMs - System.currentTimeMillis()
    val canCapture = waitMs <= 0

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(tr("Pausa consciente", "Mindful pause"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            tr(
                "Resuelve en papel. La foto se procesa una sola vez y no se conserva después.",
                "Solve it on paper. The photo is processed once and is not retained afterward.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(18.dp)) {
                Text(challenge.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(challenge.pseudocode, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(tr("Duración de la pausa", "Pause duration"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 30).forEach { minutes ->
                AssistChip(
                    onClick = { onPauseMinutes(minutes) },
                    label = { Text("$minutes min${if (pauseMinutes == minutes) " ✓" else ""}") },
                )
            }
        }
        Text("${tr("Intentos restantes", "Attempts left")}: $attempts", color = MaterialTheme.colorScheme.onSurfaceVariant)
        feedback?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
        if (!canCapture) {
            Spacer(Modifier.height(16.dp))
            Text(
                "${tr("La alternativa local se habilita en", "Local fallback unlocks in")} ${((waitMs + 59_999) / 60_000)} min.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(18.dp))
        if (cameraOpen && canCapture) {
            CameraCapture(
                enabled = !evaluating,
                onCaptured = {
                    cameraOpen = false
                    onPhoto(it)
                },
                onError = { cameraOpen = false },
            )
        } else {
            Button(
                onClick = { cameraOpen = true },
                enabled = canCapture && !evaluating,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (evaluating) CircularProgressIndicator(modifier = Modifier.height(22.dp))
                else {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Text(tr("  Tomar foto de mi respuesta", "  Take a photo of my answer"))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(tr("Cancelar", "Cancel")) }
    }
}

@Composable
private fun CameraCapture(enabled: Boolean, onCaptured: (File) -> Unit, onError: (Throwable) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Text(tr("Se necesita permiso de cámara para fotografiar tu solución.", "Camera permission is required to photograph your solution."), textAlign = TextAlign.Center)
        return
    }

    Column {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        }.onFailure(onError)
                    }, ContextCompat.getMainExecutor(viewContext))
                }
            },
            modifier = Modifier.fillMaxWidth().height(360.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = enabled,
            onClick = {
                val raw = File(context.cacheDir, "challenge-${System.currentTimeMillis()}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(raw).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            runCatching { resizeForUpload(raw) }.onSuccess(onCaptured).onFailure {
                                raw.delete()
                                onError(it)
                            }
                        }
                        override fun onError(exception: ImageCaptureException) {
                            raw.delete()
                            onError(exception)
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Text(tr("  Usar esta foto", "  Use this photo"))
        }
    }
}

private fun resizeForUpload(raw: File): File {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(raw.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) sample *= 2
    val bitmap = BitmapFactory.decodeFile(raw.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("No se pudo leer la foto")
    val destination = File(raw.parentFile, "${raw.nameWithoutExtension}-ready.jpg")
    FileOutputStream(destination).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    bitmap.recycle()
    raw.delete()
    return destination
}

private suspend fun recognizeText(context: android.content.Context, file: File): String = withContext(Dispatchers.IO) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(file))
    suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener { continuation.resume("") }
            .addOnCompleteListener { recognizer.close() }
    }
}
