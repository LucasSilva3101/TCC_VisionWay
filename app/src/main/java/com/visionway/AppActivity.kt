package com.project.visionway

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AppActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VisionWay"
        private const val CAMERA_PERMISSION_REQUEST = 101
        private const val YOLO_IMG_SIZE = 640
        private const val MAX_DETECTIONS = 300
        private const val CONF_THRESHOLD = 0.05f
        private const val IOU_THRESHOLD = 0.45f
        private const val MIN_FRAME_INTERVAL_MS = 80L
    }

    // UI / Câmera
    private lateinit var imageView: ImageView
    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var handler: Handler
    private var previewSize: Size? = null

    // Desenho
    private val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY,
        Color.BLACK, Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    private val paint = Paint().apply { isAntiAlias = true }

    // TFLite
    private lateinit var tflite: Interpreter
    private lateinit var labels: List<String>
    private var inputIsNHWC = true
    private var inputDataType: DataType = DataType.FLOAT32

    // Controle
    private val isProcessing = AtomicBoolean(false)
    private var lastFrameTs = 0L

    // TTS (somente Google)
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var lastSpokenTime = 0L
    private var lastAppliedVoice: String? = null

    // Reagir a mudanças na voz salva
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == VoicePrefs.KEY_VOICE_NAME || key == VoicePrefs.KEY_VOICE_GENDER) {
            applyGoogleVoice(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app)

        imageView = findViewById(R.id.imageView)
        textureView = findViewById(R.id.textureView)
        imageView.visibility = View.GONE

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        // Labels/modelo (em /assets)
        labels = FileUtil.loadLabels(this, "labels.txt")
        initTFLite("best_float32.tflite")

        val ht = HandlerThread("videoThread").apply { start() }
        handler = Handler(ht.looper)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                if (hasCamPerm()) openCamera() else reqPerm()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                val now = System.currentTimeMillis()
                if (now - lastFrameTs < MIN_FRAME_INTERVAL_MS) return
                if (!isProcessing.compareAndSet(false, true)) return
                lastFrameTs = now

                val frame = textureView.bitmap ?: run {
                    isProcessing.set(false); return
                }

                handler.post {
                    try {
                        val prep = preprocessToYolo(frame, YOLO_IMG_SIZE, inputIsNHWC, inputDataType)
                        val inputBuffer = prep.buffer
                        val scale = prep.scale
                        val padX = prep.padX
                        val padY = prep.padY

                        val outShape = tflite.getOutputTensor(0).shape()
                        var desenhou = false

                        if (outShape.size == 3 && outShape[0] == 1 && outShape[2] == 6) {
                            val n = min(outShape[1], MAX_DETECTIONS)
                            val output = Array(1) { Array(n) { FloatArray(6) } }
                            tflite.run(inputBuffer, output)
                            drawDetections(frame, output[0], scale, padX, padY)
                            desenhou = true
                        } else {
                            val nc = labels.size
                            val dim1 = outShape.getOrNull(1) ?: 0
                            val dim2 = outShape.getOrNull(2) ?: 0

                            val preds: List<DetRaw> = if (dim1 == 4 + nc) {
                                val output3d = Array(1) { Array(dim1) { FloatArray(dim2) } }
                                tflite.run(inputBuffer, output3d)
                                postprocessYoloNoNmsChannelsFirst(output3d[0], nc, CONF_THRESHOLD)
                            } else if (dim2 == 4 + nc) {
                                val output3d = Array(1) { Array(dim1) { FloatArray(dim2) } }
                                tflite.run(inputBuffer, output3d)
                                postprocessYoloNoNmsDetLast(output3d[0], nc, CONF_THRESHOLD)
                            } else {
                                emptyList()
                            }

                            val kept = nms(preds, IOU_THRESHOLD, MAX_DETECTIONS)
                            val finalList = if (kept.isEmpty() && preds.isNotEmpty()) {
                                listOf(preds.maxBy { it.conf })
                            } else kept

                            val detsForDraw = Array(finalList.size) { FloatArray(6) }
                            for (i in finalList.indices) {
                                val d = finalList[i]
                                val xc = (d.x1 + d.x2) / 2f
                                val yc = (d.y1 + d.y2) / 2f
                                val bw = (d.x2 - d.x1)
                                val bh = (d.y2 - d.y1)
                                detsForDraw[i][0] = xc
                                detsForDraw[i][1] = yc
                                detsForDraw[i][2] = bw
                                detsForDraw[i][3] = bh
                                detsForDraw[i][4] = d.conf
                                detsForDraw[i][5] = d.cls.toFloat()
                            }

                            drawDetections(frame, detsForDraw, scale, padX, padY)
                            desenhou = true
                        }

                        if (!desenhou) {
                            runOnUiThread {
                                if (imageView.visibility != View.VISIBLE) imageView.visibility = View.VISIBLE
                                imageView.setImageBitmap(frame)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro na inferência: ${e.message}", e)
                    } finally {
                        isProcessing.set(false)
                    }
                }
            }
        }

        // TTS: sempre no engine do Google
        tts = TextToSpeech(this, this, TtsUtils.GOOGLE_TTS_PKG)

        VoicePrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)

        if (!hasCamPerm()) reqPerm()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { VoicePrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
        try { tts.stop(); tts.shutdown() } catch (_: Exception) {}
    }

    // ======= TFLite =======
    private fun initTFLite(assetModelName: String) {
        val options = Interpreter.Options()
        val modelBuffer = try {
            FileUtil.loadMappedFile(this, assetModelName)
        } catch (e: Exception) {
            assets.open(assetModelName).use { input ->
                val bytes = input.readBytes()
                val bb = ByteBuffer.allocateDirect(bytes.size)
                bb.order(ByteOrder.nativeOrder())
                bb.put(bytes); bb.rewind(); bb
            }
        }
        tflite = Interpreter(modelBuffer, options)

        val in0 = tflite.getInputTensor(0)
        val inShape = in0.shape()
        inputDataType = in0.dataType()
        inputIsNHWC = inShape.size == 4 && inShape[3] == 3
        Log.d(TAG, "Input shape=${inShape.contentToString()} type=$inputDataType NHWC=$inputIsNHWC")
    }

    data class PreprocessResult(
        val buffer: ByteBuffer,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    private fun preprocessToYolo(
        src: Bitmap,
        inputSize: Int,
        wantNHWC: Boolean,
        dataType: DataType
    ): PreprocessResult {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val r = min(inputSize.toFloat() / w, inputSize.toFloat() / h)
        val newW = (w * r).toInt()
        val newH = (h * r).toInt()

        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)

        val letterboxed = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(letterboxed)
        c.drawColor(Color.rgb(114, 114, 114))
        val dx = ((inputSize - newW) / 2f)
        val dy = ((inputSize - newH) / 2f)
        c.drawBitmap(resized, dx, dy, null)

        val pixels = IntArray(inputSize * inputSize)
        letterboxed.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        when (dataType) {
            DataType.FLOAT32 -> {
                val bb = if (wantNHWC) {
                    ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
                } else {
                    ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4)
                }
                bb.order(ByteOrder.nativeOrder())

                if (wantNHWC) {
                    var idx = 0
                    for (y in 0 until inputSize) {
                        for (x in 0 until inputSize) {
                            val px = pixels[idx++]
                            bb.putFloat(((px shr 16) and 0xFF) / 255f)
                            bb.putFloat(((px shr 8) and 0xFF) / 255f)
                            bb.putFloat((px and 0xFF) / 255f)
                        }
                    }
                } else {
                    for (y in 0 until inputSize) for (x in 0 until inputSize) {
                        val px = pixels[y * inputSize + x]; bb.putFloat(((px shr 16) and 0xFF) / 255f)
                    }
                    for (y in 0 until inputSize) for (x in 0 until inputSize) {
                        val px = pixels[y * inputSize + x]; bb.putFloat(((px shr 8) and 0xFF) / 255f)
                    }
                    for (y in 0 until inputSize) for (x in 0 until inputSize) {
                        val px = pixels[y * inputSize + x]; bb.putFloat((px and 0xFF) / 255f)
                    }
                }
                bb.rewind()
                return PreprocessResult(bb, r, dx, dy)
            }

            DataType.UINT8, DataType.INT8 -> {
                val bb = if (wantNHWC) {
                    ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3)
                } else {
                    ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize)
                }
                bb.order(ByteOrder.nativeOrder())

                if (wantNHWC) {
                    var idx = 0
                    for (y in 0 until inputSize) for (x in 0 until inputSize) {
                        val px = pixels[idx++]
                        bb.put(((px shr 16) and 0xFF).toByte())
                        bb.put(((px shr 8) and 0xFF).toByte())
                        bb.put((px and 0xFF).toByte())
                    }
                } else {
                    for (y in 0 until inputSize) for (x in 0 until inputSize) {
                        val px = pixels[y * inputSize + x]; bb.put(((px shr 16) and 0xFF).toByte())
                    }
                    for (y in 0 until inputSize) for (x in 0 until inputSize) {
                        val px = pixels[y * inputSize + x]; bb.put(((px shr 8) and 0xFF).toByte())
                    }
                    for (y in 0 until inputSize) for (x in 0 until inputSize) {
                        val px = pixels[y * inputSize + x]; bb.put((px and 0xFF).toByte())
                    }
                }
                bb.rewind()
                return PreprocessResult(bb, r, dx, dy)
            }

            else -> error("Tipo de input TFLite não suportado: $dataType")
        }
    }

    // ======= Postprocess & NMS =======
    data class DetRaw(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val conf: Float, val cls: Int
    )

    private fun sigmoid(x: Float): Float = (1f / (1f + kotlin.math.exp(-x)))

    private fun postprocessYoloNoNmsChannelsFirst(
        out: Array<FloatArray>, // [4+nc][N]
        numClasses: Int,
        confThr: Float
    ): List<DetRaw> {
        val channels = 4 + numClasses
        require(out.size == channels) { "Esperado ${channels} canais, veio ${out.size}" }
        val n = out[0].size

        val xs = out[0]; val ys = out[1]; val ws = out[2]; val hs = out[3]
        val results = ArrayList<DetRaw>(64)

        for (i in 0 until n) {
            var bestC = -1
            var bestP = -Float.MAX_VALUE
            for (c in 0 until numClasses) {
                var p = out[4 + c][i]
                if (p < 0f || p > 1f) p = sigmoid(p)
                if (p > bestP) { bestP = p; bestC = c }
            }

            val x = xs[i]; val y = ys[i]; val w = ws[i]; val h = hs[i]
            val usePixels = (w > 1.5f || h > 1.5f || x > 1.5f || y > 1.5f)
            val xn = if (usePixels) x / YOLO_IMG_SIZE else x
            val yn = if (usePixels) y / YOLO_IMG_SIZE else y
            val wn = if (usePixels) w / YOLO_IMG_SIZE else w
            val hn = if (usePixels) h / YOLO_IMG_SIZE else h

            val x1 = xn - wn / 2f
            val y1 = yn - hn / 2f
            val x2 = xn + wn / 2f
            val y2 = yn + hn / 2f

            if (bestP >= confThr) results.add(DetRaw(x1, y1, x2, y2, bestP, bestC))
        }
        return results
    }

    private fun postprocessYoloNoNmsDetLast(
        out: Array<FloatArray>, // [N][4+nc]
        numClasses: Int,
        confThr: Float
    ): List<DetRaw> {
        val channels = 4 + numClasses
        require(out.isNotEmpty() && out[0].size == channels) { "Esperado 4+nc colunas" }

        val results = ArrayList<DetRaw>(64)
        for (i in out.indices) {
            val row = out[i]
            var x = row[0]; var y = row[1]; var w = row[2]; var h = row[3]

            var bestC = -1
            var bestP = -Float.MAX_VALUE
            for (c in 0 until numClasses) {
                var p = row[4 + c]
                if (p < 0f || p > 1f) p = sigmoid(p)
                if (p > bestP) { bestP = p; bestC = c }
            }

            val usePixels = (w > 1.5f || h > 1.5f || x > 1.5f || y > 1.5f)
            if (usePixels) { x /= YOLO_IMG_SIZE; y /= YOLO_IMG_SIZE; w /= YOLO_IMG_SIZE; h /= YOLO_IMG_SIZE }

            val x1 = x - w / 2f
            val y1 = y - h / 2f
            val x2 = x + w / 2f
            val y2 = y + h / 2f

            if (bestP >= confThr) results.add(DetRaw(x1, y1, x2, y2, bestP, bestC))
        }
        return results
    }

    private fun nms(dets: List<DetRaw>, iouThr: Float, maxDet: Int): List<DetRaw> {
        if (dets.isEmpty()) return emptyList()
        val sorted = dets.sortedByDescending { it.conf }.toMutableList()
        val kept = ArrayList<DetRaw>(sorted.size)
        while (sorted.isNotEmpty() && kept.size < maxDet) {
            val best = sorted.removeAt(0)
            kept.add(best)
            val it = sorted.iterator()
            while (it.hasNext()) {
                val other = it.next()
                if (iou(best, other) > iouThr) it.remove()
            }
        }
        return kept
    }

    private fun iou(a: DetRaw, b: DetRaw): Float {
        val interX1 = maxOf(a.x1, b.x1)
        val interY1 = maxOf(a.y1, b.y1)
        val interX2 = minOf(a.x2, b.x2)
        val interY2 = minOf(a.y2, b.y2)
        val interW = maxOf(0f, interX2 - interX1)
        val interH = maxOf(0f, interY2 - interY1)
        val inter = interW * interH

        val areaA = maxOf(0f, a.x2 - a.x1) * maxOf(0f, a.y2 - a.y1)
        val areaB = maxOf(0f, b.x2 - b.x1) * maxOf(0f, b.y2 - b.y1)
        val uni = areaA + areaB - inter + 1e-6f
        return inter / uni
    }

    private fun drawDetections(
        frameBitmap: Bitmap,
        dets: Array<FloatArray>,
        scale: Float,
        padX: Float,
        padY: Float
    ) {
        val mutable = frameBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val h = mutable.height.toFloat()
        val w = mutable.width.toFloat()

        paint.textSize = h / 15f
        paint.strokeWidth = h / 85f

        for (i in dets.indices) {
            val d = dets[i]
            val conf = d[4]
            if (conf < CONF_THRESHOLD) continue

            val clsId = d[5].toInt().coerceIn(0, labels.lastIndex)
            val label = labels[clsId]

            val xc = d[0]; val yc = d[1]; val bw = d[2]; val bh = d[3]
            val x1_l = (xc - bw / 2f)
            val y1_l = (yc - bh / 2f)
            val x2_l = (xc + bw / 2f)
            val y2_l = (yc + bh / 2f)

            val rect = deLetterbox(
                x1Norm = x1_l, y1Norm = y1_l, x2Norm = x2_l, y2Norm = y2_l,
                srcW = w, srcH = h, inputSize = YOLO_IMG_SIZE,
                scale = scale, padX = padX, padY = padY
            )

            val x1 = rect.left.coerceIn(0f, w - 1f)
            val y1 = rect.top.coerceIn(0f, h - 1f)
            val x2 = rect.right.coerceIn(0f, w - 1f)
            val y2 = rect.bottom.coerceIn(0f, h - 1f)
            if (x2 <= x1 || y2 <= y1) continue

            paint.color = colors[i % colors.size]
            paint.style = Paint.Style.STROKE
            canvas.drawRect(RectF(x1, y1, x2, y2), paint)

            paint.style = Paint.Style.FILL
            val text = "$label ${"%.2f".format(conf)}"
            canvas.drawText(text, x1, max(0f, y1 - 10f), paint)

            val t = System.currentTimeMillis()
            if (t - lastSpokenTime > 3000) {
                try {
                    applyGoogleVoice(force = false)
                    tts.speak(label, TextToSpeech.QUEUE_FLUSH, null, null)
                } catch (_: Exception) {}
                lastSpokenTime = t
            }
        }

        runOnUiThread {
            if (imageView.visibility != View.VISIBLE) imageView.visibility = View.VISIBLE
            imageView.setImageBitmap(mutable)
        }
    }

    private fun deLetterbox(
        x1Norm: Float, y1Norm: Float, x2Norm: Float, y2Norm: Float,
        srcW: Float, srcH: Float,
        inputSize: Int, scale: Float, padX: Float, padY: Float
    ): RectF {
        val x1i = x1Norm * inputSize
        val y1i = y1Norm * inputSize
        val x2i = x2Norm * inputSize
        val y2i = y2Norm * inputSize

        val x1p = (x1i - padX) / scale
        val y1p = (y1i - padY) / scale
        val x2p = (x2i - padX) / scale
        val y2p = (y2i - padY) / scale

        return RectF(x1p, y1p, x2p, y2p)
    }

    // ===== Câmera 2 =====
    private fun hasCamPerm() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun reqPerm() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == CAMERA_PERMISSION_REQUEST && g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) {
            if (textureView.isAvailable) openCamera()
        }
    }

    private fun chooseBack(cm: CameraManager): String? {
        for (id in cm.cameraIdList) {
            val facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return cm.cameraIdList.firstOrNull()
    }

    private fun getBestSize(cm: CameraManager, cameraId: String, w: Int, h: Int): Size {
        val map = cm.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val choices = map.getOutputSizes(SurfaceTexture::class.java)
        val targetRatio = w.toFloat() / h
        return choices.minBy { s ->
            val r = s.width.toFloat() / s.height
            val ratioScore = abs(r - targetRatio) * 1000
            val areaScore = abs(s.width * s.height - w * h) / 1000f
            ratioScore + areaScore
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (!hasCamPerm()) return
        val camId = chooseBack(cameraManager) ?: return

        previewSize = getBestSize(
            cameraManager,
            camId,
            textureView.width.coerceAtLeast(640),
            textureView.height.coerceAtLeast(480)
        )

        cameraManager.openCamera(camId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                val st = textureView.surfaceTexture ?: return
                val ps = previewSize!!
                st.setDefaultBufferSize(ps.width, ps.height)
                val surface = Surface(st)

                val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }

                device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(req.build(), null, handler)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, handler)
            }
            override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
            override fun onError(device: CameraDevice, error: Int) { device.close(); cameraDevice = null }
        }, handler)
    }

    override fun onResume() {
        super.onResume()
        applyGoogleVoice(force = false)
        if (textureView.isAvailable && hasCamPerm()) openCamera()
    }

    override fun onPause() {
        super.onPause()
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
    }

    // ===== TTS (Google) =====
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try { tts.language = Locale("pt", "BR") } catch (_: Exception) {}
            ttsReady = true
            applyGoogleVoice(force = true)
        }
    }

    /** Aplica a voz salva do Google TTS; fallback para primeira pt-BR, depois pitch. */
    private fun applyGoogleVoice(force: Boolean) {
        if (!ttsReady) return

        val desired = VoicePrefs.getVoiceName(this)
        if (!force && lastAppliedVoice == desired) return

        try {
            tts.stop()
            var applied = false

            if (!desired.isNullOrBlank()) {
                val match = tts.voices?.firstOrNull { it.name == desired }
                if (match != null) {
                    tts.voice = match
                    tts.setPitch(1.0f)
                    tts.setSpeechRate(1.0f)
                    applied = true
                }
            }

            if (!applied) {
                val firstPt = tts.voices?.firstOrNull { v ->
                    v.locale?.language?.equals("pt", true) == true &&
                            (v.locale?.country.isNullOrBlank() || v.locale.country.equals("BR", true))
                }
                if (firstPt != null) {
                    tts.voice = firstPt
                    tts.setPitch(1.0f)
                    tts.setSpeechRate(1.0f)
                    applied = true
                }
            }

            if (!applied) {
                val gen = VoicePrefs.getGender(this)
                tts.setPitch(if (gen == VoicePrefs.GENDER_MALE) 0.9f else 1.1f)
                tts.setSpeechRate(1.0f)
            }

            lastAppliedVoice = desired
        } catch (_: Exception) {}
    }
}