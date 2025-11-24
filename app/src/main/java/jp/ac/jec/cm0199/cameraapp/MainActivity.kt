package jp.ac.jec.cm0199.cameraapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
 * カメラプレビューと撮影・保存処理をすべて扱うアクティビティ。
 * - 権限確認: Runtime Permissionでカメラ利用可否を取得
 * - カメラ起動: CameraXのプレビューと撮影ユースケースをバインド
 * - 保存処理: MediaStoreへJPEGを書き出し、サムネイルを最新に更新
 */
class MainActivity : AppCompatActivity() {

    /**
     * 画面要素へアクセスするためのビューBinding。
     * onCreateで初期化し、onDestroyまで同一インスタンスを使い回す。
     */
    private lateinit var binding: ActivityMainBinding

    /**
     * 静止画撮影用のCameraXユースケース。
     * startCamera実行時に生成し、撮影ボタン押下時に利用する。
     */
    private var imageCapture: ImageCapture? = null

    /**
     * 直近で保存した画像のURI。
     * サムネイル更新や、保存結果の有無を判断する際に使用する。
     */
    private var lastSavedUri: Uri? = null

    /**
     * 権限ダイアログの結果を受け取るActivity Result API。
     * 許可ならカメラ起動、拒否なら警告表示に分岐する。
     */
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isPermissionGranted ->
            Log.d(TAG, "isPermissionGranted: $isPermissionGranted")
            if (isPermissionGranted) {
                startCamera()
            } else {
                showPermissionDeniedMessage()
            }
        }

    /**
     * 画面初期化とリスナー登録、初回権限チェックをまとめて実行する。
     * - エッジトゥエッジ適用
     * - バインディング生成とレイアウトセット
     * - ボタンリスナーを撮影処理に紐付け
     * - 権限があればCameraX起動、なければ権限リクエスト
     */
    /**
     * 画面初期化とリスナー登録を行い、権限状態に応じてCameraX起動または権限リクエストを開始する。
     *
     * ```
     * // Activity起動時に自動的に呼ばれる
     * super.onCreate(savedInstanceState)
     * ```
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnTakePicture.setOnClickListener { takePicture() }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * 要求したカメラ権限が許可済みかを判定する。
     * 撮影処理やCameraX起動前に必ず呼び出す。
     *
     * ```
     * if (hasCameraPermission()) {
     *     startCamera()
     * } else {
     *     requestPermissionsLauncher.launch(Manifest.permission.CAMERA)
     * }
     * ```
     */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * CameraXのプレビューと撮影ユースケースを初期化し、ライフサイクルに紐付ける。
     * - Preview: `binding.previewView` へ描画するSurfaceProviderを設定
     * - ImageCapture: 撮影時のラグを抑えるCAPTURE_MODE_MINIMIZE_LATENCYを指定
     * - バインド: Activityのライフサイクルに合わせてユースケースを管理
     *
     * ```
     * if (hasCameraPermission()) {
     *     startCamera()
     * }
     * ```
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also { previewUseCase ->
                    previewUseCase.surfaceProvider = binding.previewView.surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: ExecutionException) {
                Log.e(TAG, "startCamera ExecutionException: ", e)
            } catch (e: InterruptedException) {
                Log.e(TAG, "startCamera InterruptedException: ", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * プレビュー中のカメラから写真を撮影し、MediaStoreへ保存する。
     * 保存後はサムネイルを最新の画像に差し替え、利用者へフィードバックする。
     * 権限未取得の場合は撮影を行わず、権限リクエストへフォールバックする。
     * ImageCaptureがまだ初期化されていない場合も安全に抜ける。
     *
     * ```
     * binding.btnTakePicture.setOnClickListener {
     *     takePicture()
     * }
     * ```
     */
    private fun takePicture() {
        if (!hasCameraPermission()) {
            requestPermissionsLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        val captureUseCase = imageCapture
        if (captureUseCase == null) {
            Snackbar.make(
                binding.root,
                "必要な権限を許可してください",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val outputOptions = OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            createContentValues()
        ).build()

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : OnImageSavedCallback {
                override fun onImageSaved(output: OutputFileResults) {
                    val savedUri = output.savedUri
                    Log.d(TAG, "savedUri $savedUri")
                    lastSavedUri = savedUri
                    updateThumbnail(savedUri)
                    Snackbar.make(
                        binding.root,
                        "ギャラリーに保存しました",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "error ", exception)
                    Snackbar.make(
                        binding.root,
                        "保存に失敗しました",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    /**
     * MediaStoreへ保存する際のファイル情報を生成する。
     * DISPLAY_NAMEをタイムスタンプで一意化し、Pictures/CameraApp配下へ保存する。
     *
     * ```
     * val options = OutputFileOptions.Builder(
     *     contentResolver,
     *     MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
     *     createContentValues()
     * ).build()
     * ```
     */
    private fun createContentValues(): ContentValues {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")
        val fileName = LocalDateTime.now().format(dateTimeFormatter)
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
        }
    }

    /**
     * サムネイル領域を最新の保存済み画像へ差し替える。
     * URIがnullの場合はランチャーアイコンのプレースホルダーを表示する。
     *
     * ```
     * updateThumbnail(savedUri)
     * ```
     */
    private fun updateThumbnail(savedUri: Uri?) {
        if (savedUri == null) {
            binding.imageThumbnail.setImageResource(R.mipmap.ic_launcher)
            return
        }
        binding.imageThumbnail.setImageURI(savedUri)
    }

    /**
     * 権限が不足している際にスナックバーで警告を出す。
     * 権限拒否後のリトライ導線として機能する。
     *
     * ```
     * if (!hasCameraPermission()) {
     *     showPermissionDeniedMessage()
     * }
     * ```
     */
    private fun showPermissionDeniedMessage() {
        Snackbar.make(
            binding.root,
            "全ての権限を許可しないとアプリが正常に動作しません",
            Snackbar.LENGTH_LONG
        ).show()
    }

    companion object {
        /**
         * ログ出力で利用するタグ名。クラス名から取得して設定する。
         */
        private val TAG: String = MainActivity::class.java.simpleName
    }
}
