package jp.ac.jec.cm0199.cameraapp

import android.Manifest
import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCapture.OutputFileResults
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import jp.ac.jec.cm0199.cameraapp.databinding.ActivityMainBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutionException

/**
 *Android 2 FunnyCameraアプリのKotlin化は一部の学生にとっては難易度が高いかもしれない。
 * なので、Android 2 カメラ演習アプリ相当の内容をKotlin化する方向で進める.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null

    private val requestPermissions =
        registerForActivityResult(RequestMultiplePermissions()) { grantStates ->
            val isPermissionAllGranted = grantStates.entries.all { it.value }
            Log.d(TAG, "isPermissionAllGranted: $isPermissionAllGranted")
            if (isPermissionAllGranted) {
                startCamera()
            } else {
                Log.w(TAG, "全ての権限を許可しないとアプリが正常に動作しません")
                Snackbar.make(
                    window.decorView,
                    "全ての権限を許可しないとアプリが正常に動作しません",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        requestPermissions.launch(arrayOf(Manifest.permission.CAMERA))

        binding.btnTakePicture.setOnClickListener { _ -> takePicture() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()

                val viewFinder = binding.previewView
                val preview = Preview.Builder().build()
                preview.surfaceProvider = viewFinder.getSurfaceProvider()
                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: ExecutionException) {
                Log.e(TAG, "error: ", e)
            } catch (e: InterruptedException) {
                Log.e(TAG, "error: ", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePicture() {
        val imageCapture = imageCapture ?: return

        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")
        val fileName = LocalDateTime.now().format(dateTimeFormatter)
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FunnyCamera")

        val imageCollection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val outputOptions =
            OutputFileOptions.Builder(
                contentResolver,
                imageCollection,
                contentValues
            ).build()


        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : OnImageSavedCallback {
                override fun onImageSaved(output: OutputFileResults) {
                    val savedUri = output.savedUri // nullの場合がある
                    Log.d(TAG, "savedUri $savedUri")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "error ", exception)
                }
            })
    }

    companion object {
        private val TAG: String = MainActivity::class.java.getSimpleName()
    }
}