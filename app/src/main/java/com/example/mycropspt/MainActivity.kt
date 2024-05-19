package com.example.mycropspt

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.example.mycropspt.databinding.ActivityMainBinding
import com.example.mycropspt.ui.theme.LoadingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min


class MainActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        const val TAG = "TFLite - ODT"
        const val REQUEST_IMAGE_CAPTURE: Int = 1
        private const val MAX_FONT_SIZE = 96F
    }

    private lateinit var captureImageFab: Button
    private lateinit var resetImageFab: Button
    private lateinit var inputImageView: ImageView
    private lateinit var imgSampleOne: ImageView
    private lateinit var imgSampleTwo: ImageView
    private lateinit var imgSampleThree: ImageView
    private lateinit var tvPlaceholder: TextView
    private lateinit var currentPhotoPath: String
    private lateinit var cameraFeed: PreviewView
    private lateinit var binding: ActivityMainBinding
    private var outputDirectory: File? = null
    private lateinit var cameraExecutor:ExecutorService

    private var imageCapture: ImageCapture?=null

    override fun onCreate(savedInstanceState: Bundle?) {



        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
        /*splash screen
        Thread.sleep(3000)
        installSplashScreen()*/

        captureImageFab = findViewById(R.id.captureImageFab)
        resetImageFab = findViewById(R.id.resetImageFab)
        inputImageView = findViewById(R.id.imageView)
        imgSampleOne = findViewById(R.id.imgSampleOne)
        imgSampleTwo = findViewById(R.id.imgSampleTwo)
        imgSampleThree = findViewById(R.id.imgSampleThree)
        tvPlaceholder = findViewById(R.id.tvPlaceholder)
        cameraFeed = findViewById(R.id.viewFinder)

        captureImageFab.setOnClickListener(this)
        resetImageFab.setOnClickListener(this)
        imgSampleOne.setOnClickListener(this)
        imgSampleTwo.setOnClickListener(this)
        imgSampleThree.setOnClickListener(this)

        resetImageFab.isEnabled = false
        resetImageFab.isClickable = false
        inputImageView.visibility = View.INVISIBLE

        if(allPermissionGranted()){
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, Constants.REQUIRED_PERMISSIONS, Constants.REQUEST_CODE_PERMISSIONS
            )
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()){
                startCamera()
            } else {
                finish()
            }
        }
    }
    private fun allPermissionGranted() =
        Constants.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun getOutputDirectory(): File? {

        return getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    }
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile: File? = try {
            createImageFile()
        } catch (e: IOException) {
            Log.e(TAG, e.message.toString())
            null
        }
        // Continue only if the File was successfully created
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this@MainActivity,
                "com.example.mycropspt.fileprovider",
                it
            )
        }

        val outputOption = photoFile?.let {
            ImageCapture
                .OutputFileOptions
                .Builder(it)
                .build()
        }

        outputOption?.let {
            imageCapture.takePicture(
                it, ContextCompat.getMainExecutor(this),
                object: ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = Uri.fromFile(photoFile)
                        val msg = "Photo Saved"

                        Toast.makeText(
                            this@MainActivity,
                            "$msg $savedUri",
                            Toast.LENGTH_LONG
                        ).show()

                        setViewAndDetect(getCapturedImage())
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(Constants.TAG, "onError: ${exception.message}", exception)
                    }

                }
            )
        }
    }
    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also { mPreview->
                    mPreview.setSurfaceProvider(
                        binding.viewFinder.surfaceProvider
                    )
                }
            imageCapture  = ImageCapture.Builder()
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception){
                Log.d("TAG", "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE &&
            resultCode == RESULT_OK
        ) {
            setViewAndDetect(getCapturedImage())
        }
    }


    /**
     * onClick(v: View?)
     *      Detect touches on the UI components
     */
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.captureImageFab -> {
                captureImageFab.isEnabled = false
                captureImageFab.isClickable = false
                resetImageFab.isEnabled = true
                resetImageFab.isClickable = true
                inputImageView.visibility = View.VISIBLE
                cameraFeed.visibility = View.INVISIBLE
                try {
                    takePhoto()
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, e.message.toString())
                }
            }
            R.id.resetImageFab -> {
                inputImageView.setImageResource(R.drawable.green_loading_bar)
                captureImageFab.isEnabled = true
                captureImageFab.isClickable = true
                resetImageFab.isEnabled = false
                resetImageFab.isClickable = false
                inputImageView.visibility = View.INVISIBLE
                cameraFeed.visibility = View.VISIBLE
            }
            R.id.imgSampleOne -> {
                setViewAndDetect(getSampleImage(R.drawable.img_meal_one))
                captureImageFab.isEnabled = false
                captureImageFab.isClickable = false
                resetImageFab.isEnabled = true
                resetImageFab.isClickable = true
                inputImageView.visibility = View.VISIBLE
                cameraFeed.visibility = View.INVISIBLE
            }
            R.id.imgSampleTwo -> {
                setViewAndDetect(getSampleImage(R.drawable.spoiled_two))
                captureImageFab.isEnabled = false
                captureImageFab.isClickable = false
                resetImageFab.isEnabled = true
                resetImageFab.isClickable = true
                inputImageView.visibility = View.VISIBLE
                cameraFeed.visibility = View.INVISIBLE
            }
            R.id.imgSampleThree -> {
                setViewAndDetect(getSampleImage(R.drawable.spoiled_three))
                captureImageFab.isEnabled = false
                captureImageFab.isClickable = false
                resetImageFab.isEnabled = true
                resetImageFab.isClickable = true
                inputImageView.visibility = View.VISIBLE
                cameraFeed.visibility = View.INVISIBLE
            }
        }
    }

    /**
     * runObjectDetection(bitmap: Bitmap)
     *      TFLite Object Detection function
     */
    private fun runObjectDetection(bitmap: Bitmap) {
        // Step 1: Create TFLite's TensorImage object
        val image = TensorImage.fromBitmap(bitmap)

        // Step 2: Initialize the detector object
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.3f)
            .build()
        val detector = ObjectDetector.createFromFileAndOptions(
            this,
            "lettuce.tflite",
            options
        )

        // Step 3: Feed given image to the detector
        val results = detector.detect(image)

        // Step 4: Parse the detection result and show it
        val resultToDisplay = results.map {
            // Get the top-1 category and craft the display text
            val category = it.categories.first()
            val text = "${category.label}, ${category.score.times(100).toInt()}%"

            // Create a data object to display the detection result
            DetectionResult(it.boundingBox, text)
        }
        // Draw the detection result on the bitmap and show it.
        val imgWithResult = drawDetectionResult(bitmap, resultToDisplay)
        runOnUiThread {
            inputImageView.setImageBitmap(imgWithResult)
        }

        val hasLettuce = hasLettuceDetection(resultToDisplay)
        if (hasLettuce) {
            Log.i(TAG, "Lettuce (Iceberg or Romaine) detected!")
            val processedResults = getDetectionResultsWithArea(resultToDisplay)

            // Access the processed results (list of DetectionInfo objects)
            processedResults.forEach { info ->
                Log.i(TAG, "Class: ${info.className}, Area: ${info.area}, Percent: ${info.percent}, Scale: ${info.scale}")
            }

            val spoilageScales = listSpoilageScales(processedResults)
            Log.i(TAG, spoilageScales.toString())

            spoilageScales.forEach { info ->
                Log.i(TAG, "Class: ${info.scale}")
            }
        } else {
            Log.i(TAG, "No lettuce detected.")
        }



    }

    /**
     * debugPrint(visionObjects: List<Detection>)
     *      Print the detection result to logcat to examine
     */
    private fun debugPrint(results : List<Detection>) {
        for ((i, obj) in results.withIndex()) {
            val box = obj.boundingBox

            Log.d(TAG, "Detected object: ${i} ")
            Log.d(TAG, "  boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})")

            for ((j, category) in obj.categories.withIndex()) {
                Log.d(TAG, "    Label $j: ${category.label}")
                val confidence: Int = category.score.times(100).toInt()
                Log.d(TAG, "    Confidence: ${confidence}%")
            }
        }
    }

    /**
     * setViewAndDetect(bitmap: Bitmap)
     *      Set image to view and call object detection
     */
    private fun setViewAndDetect(bitmap: Bitmap) {

        // Start LoadingActivity
        val loadingIntent = Intent(this, LoadingScreen::class.java)
        startActivity(loadingIntent)

        // Display capture image
        inputImageView.setImageBitmap(bitmap)
        tvPlaceholder.visibility = View.INVISIBLE

        // Run ODT and display result
        // Note that we run this in the background thread to avoid blocking the app UI because
        // TFLite object detection is a synchronised process.
        lifecycleScope.launch(Dispatchers.Default) {
            runObjectDetection(bitmap)
        }

    }

    /**
     * getCapturedImage():
     *      Decodes and crops the captured image from camera.
     */
    private fun getCapturedImage(): Bitmap {
        // Get the dimensions of the View
        val targetW: Int = inputImageView.width
        val targetH: Int = inputImageView.height

        val bmOptions = BitmapFactory.Options().apply {
            // Get the dimensions of the bitmap
            inJustDecodeBounds = true

            BitmapFactory.decodeFile(currentPhotoPath, this)

            val photoW: Int = outWidth
            val photoH: Int = outHeight

            // Determine how much to scale down the image
            val scaleFactor: Int = max(1, min(photoW / targetW, photoH / targetH))

            // Decode the image file into a Bitmap sized to fill the View
            inJustDecodeBounds = false
            inSampleSize = scaleFactor
            inMutable = true
        }
        val exifInterface = ExifInterface(currentPhotoPath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                rotateImage(bitmap, 90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                rotateImage(bitmap, 180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotateImage(bitmap, 270f)
            }
            else -> {
                bitmap
            }
        }
    }

    /**
     * getSampleImage():
     *      Get image form drawable and convert to bitmap.
     */
    private fun getSampleImage(drawable: Int): Bitmap {
        return BitmapFactory.decodeResource(resources, drawable, BitmapFactory.Options().apply {
            inMutable = true
        })
    }

    /**
     * rotateImage():
     *     Decodes and crops the captured image from camera.
     */
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    /**
     * createImageFile():
     *     Generates a temporary image file for the Camera app to write to.
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    /**
     * dispatchTakePictureIntent():
     *     Start the Camera app to take a photo.
     */
    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Log.e(TAG, e.message.toString())
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.mycropspt.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    /**
     * drawDetectionResult(bitmap: Bitmap, detectionResults: List<DetectionResult>
     *      Draw a box around each objects and show the object's name.
     */
    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<DetectionResult>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResults.forEach {
            // draw bounding box
            pen.color = Color.RED
            pen.strokeWidth = 8F
            pen.style = Paint.Style.STROKE
            val box = it.boundingBox
            canvas.drawRect(box, pen)


            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            pen.style = Paint.Style.FILL_AND_STROKE
            pen.color = Color.YELLOW
            pen.strokeWidth = 2F

            pen.textSize = MAX_FONT_SIZE
            pen.getTextBounds(it.text, 0, it.text.length, tagSize)
            val fontSize: Float = pen.textSize * box.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.textSize) pen.textSize = fontSize

            var margin = (box.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvas.drawText(
                it.text, box.left + margin,
                box.top + tagSize.height().times(1F), pen
            )
        }
        return outputBitmap
    }

    private fun hasLettuceDetection(detectionResults: List<DetectionResult>): Boolean {
        detectionResults.forEach { result ->
            val className = result.text.split(",").first().trim()
            if (className == "IcebergLettuce" || className == "RomaineLettuce") {
                return true // Lettuce detected, exit early
            }
        }
        return false // No lettuce detected
    }

    private fun getDetectionResultsWithArea(
        detectionResults: List<DetectionResult>
    ): List<DetectionInfo> {
        val resultList = mutableListOf<DetectionInfo>()
        val results = detectionResults

        var lettuceArea = 1.toFloat()
        var SoftLeafArea = 0.toFloat()
        var SlimyLeafArea = 0.toFloat()
        var DiscolorationArea = 0.toFloat()

        var SoftLeafPer = 0
        var SlimyLeafPer = 0
        var DiscolorationPer = 0

        var scale = 0

        results.forEach { result ->
            val boundingBox = result.boundingBox
            val className = result.text.split(",").first().trim()
            val isLettuce = (className == "IcebergLettuce" || className == "RomaineLettuce")

            val width = boundingBox.width()
            val height = boundingBox.height()
            val area = width*height

            if (isLettuce) {
                lettuceArea = area
                Log.i(TAG, "Detected lettuce: $className, area: $lettuceArea")
            } else if (lettuceArea>1){
                when (className) {
                    "SlimyLeaf" -> SlimyLeafArea += area
                    "SoftLeaf" -> SoftLeafArea += area
                    "Discoloration" -> DiscolorationArea += area
                }
            }
        }

        SoftLeafPer = (SoftLeafArea/lettuceArea*100).toInt()
        scale = convertToZeroToFiveScale(SoftLeafPer)
        resultList.add(DetectionInfo("SoftLeaf", SoftLeafArea, SoftLeafPer, scale))

        DiscolorationPer = (DiscolorationArea/lettuceArea*100).toInt()
        scale = convertToZeroToFiveScale(DiscolorationPer)
        resultList.add(DetectionInfo("Discoloration", DiscolorationArea, DiscolorationPer, scale))

        SlimyLeafPer = (SlimyLeafArea/lettuceArea*100).toInt()
        scale = convertToZeroToFiveScale(SlimyLeafPer)
        resultList.add(DetectionInfo("SlimyLeaf", SlimyLeafArea, SlimyLeafPer, scale))


        return resultList.toList()
    }

    fun convertToZeroToFiveScale(percentage: Int): Int {
        require(percentage in 0..100) { "Percentage must be between 0 and 100" }

        if (percentage == 0) {
            return 0
        } else if (percentage in 1..19) {
            return 1
        } else if (percentage in 21..40) {
            return 2
        } else if (percentage in 41..60) {
            return 3
        } else if (percentage in 61..80) {
            return 4
        } else if (percentage in 81..100) {
            return 5
        } else {
            return -1
        }

    }

    private fun listSpoilageScales(processedResults: List<DetectionInfo>): List<SpoilageScales> {
        val resultList = mutableListOf<SpoilageScales>()

        processedResults.forEach { result ->
            resultList.add(SpoilageScales(result.scale))
        }

        return resultList.toList()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

}

/**
 * DetectionResult
 *      A class to store the visualization info of a detected object.
 */
data class DetectionResult(val boundingBox: RectF, val text: String)
/**
 * DetectionInfo
 *      A class to store the class names and area of the boxes
 */
data class DetectionInfo(val className: String, val area: Float, val percent: Int, val scale: Int)

/**
 * SpoilageScales
 *      A class to store the scales of the spoilage indicators
 */
data class SpoilageScales(val scale: Int)