package com.project.visionway

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.project.visionway.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class AppActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 101
    }

    lateinit var labels: List<String>
    val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY,
        Color.BLACK, Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1

    private lateinit var tts: TextToSpeech
    private var lastSpokenTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app)

        imageView = findViewById(R.id.imageView)
        textureView = findViewById(R.id.textureView)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        textureView.surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: android.graphics.SurfaceTexture, p1: Int, p2: Int) {
                // 🚨 Só abre câmera se já tiver permissão
                if (hasCameraPermission()) {
                    open_camera()
                } else {
                    requestCameraPermission()
                }
            }

            override fun onSurfaceTextureSizeChanged(p0: android.graphics.SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureDestroyed(p0: android.graphics.SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(p0: android.graphics.SurfaceTexture) {
                bitmap = textureView.bitmap ?: return
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f

                scores.forEachIndexed { index, fl ->
                    val x = index * 4
                    if (fl > 0.6) {
                        val label = labels[classes[index].toInt()]
                        paint.color = colors[index % colors.size]
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(
                            RectF(
                                locations[x + 1] * w,
                                locations[x] * h,
                                locations[x + 3] * w,
                                locations[x + 2] * h
                            ), paint
                        )
                        paint.style = Paint.Style.FILL
                        canvas.drawText(
                            "$label ${"%.2f".format(fl)}",
                            locations[x + 1] * w,
                            locations[x] * h,
                            paint
                        )

                        val now = System.currentTimeMillis()
                        if (now - lastSpokenTime > 3000) {
                            tts.speak(label, TextToSpeech.QUEUE_FLUSH, null, null)
                            lastSpokenTime = now
                        }
                    }
                }
                imageView.setImageBitmap(mutable)
            }
        }

        tts = TextToSpeech(this, this)

        // Solicita permissão só se necessário
        if (!hasCameraPermission()) {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        if (!hasCameraPermission()) return

        cameraManager.openCamera(cameraManager.cameraIdList[0],
            object : CameraDevice.StateCallback() {
                override fun onOpened(p0: CameraDevice) {
                    cameraDevice = p0
                    val surfaceTexture = textureView.surfaceTexture
                    val surface = Surface(surfaceTexture)
                    val captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequest.addTarget(surface)

                    cameraDevice.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(p0: CameraCaptureSession) {
                                p0.setRepeatingRequest(captureRequest.build(), null, handler)
                            }

                            override fun onConfigureFailed(p0: CameraCaptureSession) {}
                        },
                        handler
                    )
                }

                override fun onDisconnected(p0: CameraDevice) {}
                override fun onError(p0: CameraDevice, p1: Int) {}
            }, handler
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                // 👉 Agora sim, abre a câmera
                if (textureView.isAvailable) {
                    open_camera()
                } else {
                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            open_camera()
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = false
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            } else {
                // Permissão negada → exibe alerta e fecha ou continua sem câmera
            }
        }
    }


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("pt", "BR")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        tts.stop()
        tts.shutdown()
    }
}