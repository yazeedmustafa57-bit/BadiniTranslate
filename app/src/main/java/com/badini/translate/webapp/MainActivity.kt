package com.badini.translate.webapp

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var errorText: TextView
    private lateinit var retryBtn: Button

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private var isCameraRequest = false
    private var isImagePickerRequest = false

    companion object {
        const val WEBSITE_URL = "https://translator-site-five.vercel.app"
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(this, "Kamera-Zugriff erforderlich", Toast.LENGTH_SHORT).show()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileUploadCallback != null) {
            val results = if (result.resultCode == RESULT_OK) {
                if (isCameraRequest && cameraImageUri != null) {
                    arrayOf(cameraImageUri!!)
                } else {
                    result.data?.data?.let { arrayOf(it) }
                        ?: result.data?.clipData?.let { clip ->
                            (0 until clip.itemCount).map { clip.getItemAt(it).uri }.toTypedArray()
                        }
                }
            } else null
            fileUploadCallback!!.onReceiveValue(results)
            fileUploadCallback = null
            isCameraRequest = false
            isImagePickerRequest = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Badini Translate"

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        errorText = findViewById(R.id.errorText)
        retryBtn = findViewById(R.id.retryBtn)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.setSupportZoom(true)
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.allowFileAccess = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                findViewById<View>(R.id.errorLayout).visibility = View.GONE
                webView.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    webView.visibility = View.GONE
                    findViewById<View>(R.id.errorLayout).visibility = View.VISIBLE
                    errorText.text = "Seite konnte nicht geladen werden.\n\nBitte Internetverbindung prüfen."
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (fileUploadCallback != null) {
                    fileUploadCallback!!.onReceiveValue(null)
                }
                fileUploadCallback = callback

                val acceptTypes = fileChooserParams?.acceptTypes?.filter { it.isNotBlank() }?.toTypedArray()
                val isCapture = fileChooserParams?.isCaptureEnabled == true

                return if (isCapture) {
                    // Kamera-Foto (explizit als Capture angefordert)
                    isCameraRequest = true
                    isImagePickerRequest = false
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        openCamera()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    true
                } else if (acceptTypes?.any { it.contains("image") } == true) {
                    // Bild aus Galerie oder Kamera – Dialog anzeigen
                    isCameraRequest = false
                    isImagePickerRequest = true
                    showImagePickerDialog()
                    true
                } else {
                    // Andere Dateitypen – Standard-Dateiauswahl
                    isCameraRequest = false
                    isImagePickerRequest = false
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    fileChooserLauncher.launch(intent)
                    true
                }
            }
        }

        loadUrl()

        swipeRefresh.setOnRefreshListener { loadUrl() }
        retryBtn.setOnClickListener { loadUrl() }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Galerie", "Kamera")
        AlertDialog.Builder(this)
            .setTitle("Bild auswählen")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openGallery()
                    1 -> openCameraWithPermission()
                }
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
                isImagePickerRequest = false
            }
            .show()
    }

    private fun openGallery() {
        isCameraRequest = false
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        fileChooserLauncher.launch(intent)
    }

    private fun openCameraWithPermission() {
        isCameraRequest = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            val photoFile = createImageFile()
            cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            fileChooserLauncher.launch(intent)
        } catch (e: Exception) {
            fileChooserLauncher.launch(intent)
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: cacheDir
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun loadUrl() {
        progressBar.visibility = View.VISIBLE
        findViewById<View>(R.id.errorLayout).visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(WEBSITE_URL)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
