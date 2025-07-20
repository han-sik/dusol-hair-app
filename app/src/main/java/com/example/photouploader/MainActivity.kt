package com.example.photouploader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.photouploader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    
    private var currentPhotoPath: String? = null
    private var currentBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePhoto()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                loadAndDisplayImage(path)
            }
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "DUSOLHairPrefs"
        private const val KEY_PHONE_NAME = "phone_name"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_SECURITY_TOKEN = "security_token"
        private const val KEY_LAST_FILE_NUMBER = "last_file_number"
        
        private const val CAMERA_PERMISSION_REQUEST = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // OpenCV 초기화
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV 초기화 실패")
        }
        
        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // UI 초기화
        setupUI()
        
        // 저장된 설정 불러오기
        loadSettings()
    }
    
    private fun setupUI() {
        // 필터 옵션 설정
        val filterOptions = arrayOf(
            getString(R.string.no_filter),
            getString(R.string.brightness),
            getString(R.string.saturation),
            getString(R.string.contrast),
            getString(R.string.hue),
            getString(R.string.smooth_skin),
            getString(R.string.vintage_filter),
            getString(R.string.warm_filter),
            getString(R.string.cool_filter)
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filterOptions)
        binding.spinnerFilter.setAdapter(adapter)
        binding.spinnerFilter.setText(filterOptions[0], false)
        
        // 필터 선택 리스너
        binding.spinnerFilter.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> hideFilterSlider() // 필터 없음
                in 1..4 -> showFilterSlider() // 밝기, 채도, 대비, 색조
                else -> hideFilterSlider() // 기타 필터들
            }
            applyFilter()
        }
        
        // 필터 강도 슬라이더 리스너
        binding.seekBarFilter.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvFilterValue.text = "강도: ${progress}%"
                if (fromUser) {
                    applyFilter()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        // 버튼 클릭 리스너
        binding.btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndTakePhoto()
        }
        
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
        
        binding.btnUpload.setOnClickListener {
            uploadPhoto()
        }
    }
    
    private fun checkCameraPermissionAndTakePhoto() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                takePhoto()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun takePhoto() {
        val photoFile = createImageFile()
        photoFile?.let { file ->
            currentPhotoPath = file.absolutePath
            val photoURI = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            }
            cameraLauncher.launch(intent)
        }
    }
    
    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
    
    private fun loadAndDisplayImage(imagePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                val rotatedBitmap = rotateImageIfNeeded(bitmap, imagePath)
                
                withContext(Dispatchers.Main) {
                    originalBitmap = rotatedBitmap
                    currentBitmap = rotatedBitmap.copy(rotatedBitmap.config, true)
                    binding.imagePreview.setImageBitmap(currentBitmap)
                    binding.tvStatus.text = getString(R.string.photo_taken)
                    
                    // 필터 적용
                    applyFilter()
                }
            } catch (e: Exception) {
                Log.e(TAG, "이미지 로드 실패", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "이미지 로드 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun rotateImageIfNeeded(bitmap: Bitmap, imagePath: String): Bitmap {
        try {
            val exif = android.media.ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            
            return if (orientation != android.media.ExifInterface.ORIENTATION_NORMAL) {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "이미지 회전 처리 실패", e)
            return bitmap
        }
    }
    
    private fun showFilterSlider() {
        binding.layoutFilterSlider.visibility = View.VISIBLE
    }
    
    private fun hideFilterSlider() {
        binding.layoutFilterSlider.visibility = View.GONE
    }
    
    private fun applyFilter() {
        val original = originalBitmap ?: return
        val filterType = binding.spinnerFilter.text.toString()
        val intensity = binding.seekBarFilter.progress / 100f
        
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val filteredBitmap = when (filterType) {
                    getString(R.string.brightness) -> applyBrightnessFilter(original, intensity)
                    getString(R.string.saturation) -> applySaturationFilter(original, intensity)
                    getString(R.string.contrast) -> applyContrastFilter(original, intensity)
                    getString(R.string.hue) -> applyHueFilter(original, intensity)
                    getString(R.string.smooth_skin) -> applySmoothSkinFilter(original)
                    getString(R.string.vintage_filter) -> applyVintageFilter(original)
                    getString(R.string.warm_filter) -> applyWarmFilter(original)
                    getString(R.string.cool_filter) -> applyCoolFilter(original)
                    else -> original
                }
                
                withContext(Dispatchers.Main) {
                    currentBitmap = filteredBitmap
                    binding.imagePreview.setImageBitmap(filteredBitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "필터 적용 실패", e)
            }
        }
    }
    
    private fun applyBrightnessFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        val brightness = (intensity - 0.5f) * 100
        mat.convertTo(mat, -1, 1.0, brightness.toDouble())
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Utils.matToBitmap(mat, result)
        mat.release()
        
        return result
    }
    
    private fun applySaturationFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        val hsv = Mat()
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV)
        
        val saturation = intensity * 2.0
        for (i in 0 until hsv.rows()) {
            for (j in 0 until hsv.cols()) {
                val pixel = hsv.get(i, j)
                pixel[1] = (pixel[1] * saturation).coerceIn(0.0, 255.0)
                hsv.put(i, j, pixel)
            }
        }
        
        Imgproc.cvtColor(hsv, mat, Imgproc.COLOR_HSV2BGR)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Utils.matToBitmap(mat, result)
        mat.release()
        hsv.release()
        
        return result
    }
    
    private fun applyContrastFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        val contrast = 1.0 + intensity
        mat.convertTo(mat, -1, contrast, 0.0)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Utils.matToBitmap(mat, result)
        mat.release()
        
        return result
    }
    
    private fun applyHueFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        val hsv = Mat()
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV)
        
        val hueShift = (intensity - 0.5f) * 180
        for (i in 0 until hsv.rows()) {
            for (j in 0 until hsv.cols()) {
                val pixel = hsv.get(i, j)
                pixel[0] = (pixel[0] + hueShift) % 180
                hsv.put(i, j, pixel)
            }
        }
        
        Imgproc.cvtColor(hsv, mat, Imgproc.COLOR_HSV2BGR)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Utils.matToBitmap(mat, result)
        mat.release()
        hsv.release()
        
        return result
    }
    
    private fun applySmoothSkinFilter(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // 가우시안 블러 적용
        val blurred = Mat()
        Imgproc.GaussianBlur(mat, blurred, Size(15.0, 15.0), 0.0)
        
        // 원본과 블러된 이미지 블렌딩
        val result = Mat()
        Core.addWeighted(mat, 0.7, blurred, 0.3, 0.0, result)
        
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Utils.matToBitmap(result, resultBitmap)
        
        mat.release()
        blurred.release()
        result.release()
        
        return resultBitmap
    }
    
    private fun applyVintageFilter(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // 세피아 톤 적용
        val sepia = Mat()
        val kernel = Mat(3, 3, CvType.CV_32F)
        kernel.put(0, 0, 0.393, 0.769, 0.189, 0.349, 0.686, 0.168, 0.272, 0.534, 0.131)
        Imgproc.transform(mat, sepia, kernel)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Utils.matToBitmap(sepia, result)
        
        mat.release()
        sepia.release()
        kernel.release()
        
        return result
    }
    
    private fun applyWarmFilter(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // 따뜻한 톤 적용 (빨간색 채널 강화)
        val channels = ArrayList<Mat>()
        Core.split(mat, channels)
        
        val redChannel = channels[2]
        Core.add(redChannel, Scalar(20.0), redChannel)
        
        val result = Mat()
        Core.merge(channels, result)
        
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Utils.matToBitmap(result, resultBitmap)
        
        mat.release()
        result.release()
        channels.forEach { it.release() }
        
        return resultBitmap
    }
    
    private fun applyCoolFilter(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // 시원한 톤 적용 (파란색 채널 강화)
        val channels = ArrayList<Mat>()
        Core.split(mat, channels)
        
        val blueChannel = channels[0]
        Core.add(blueChannel, Scalar(20.0), blueChannel)
        
        val result = Mat()
        Core.merge(channels, result)
        
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Utils.matToBitmap(result, resultBitmap)
        
        mat.release()
        result.release()
        channels.forEach { it.release() }
        
        return resultBitmap
    }
    
    private fun saveSettings() {
        val phoneName = binding.etPhoneName.text.toString()
        val serverIP = binding.etServerIP.text.toString()
        val serverPort = binding.etServerPort.text.toString()
        val securityToken = binding.etSecurityToken.text.toString()
        
        if (phoneName.isBlank() || serverIP.isBlank() || serverPort.isBlank() || securityToken.isBlank()) {
            Toast.makeText(this, getString(R.string.please_enter_settings), Toast.LENGTH_SHORT).show()
            return
        }
        
        // 포트 번호 검증
        val port = serverPort.toIntOrNull()
        if (port == null || port < 1024 || port > 65535) {
            Toast.makeText(this, getString(R.string.invalid_port), Toast.LENGTH_SHORT).show()
            return
        }
        
        // 토큰 검증
        if (securityToken.length != 6 || !securityToken.all { it.isDigit() }) {
            Toast.makeText(this, getString(R.string.invalid_token), Toast.LENGTH_SHORT).show()
            return
        }
        
        sharedPreferences.edit().apply {
            putString(KEY_PHONE_NAME, phoneName)
            putString(KEY_SERVER_IP, serverIP)
            putString(KEY_SERVER_PORT, serverPort)
            putString(KEY_SECURITY_TOKEN, securityToken)
            apply()
        }
        
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }
    
    private fun loadSettings() {
        binding.etPhoneName.setText(sharedPreferences.getString(KEY_PHONE_NAME, ""))
        binding.etServerIP.setText(sharedPreferences.getString(KEY_SERVER_IP, ""))
        binding.etServerPort.setText(sharedPreferences.getString(KEY_SERVER_PORT, getString(R.string.default_port)))
        binding.etSecurityToken.setText(sharedPreferences.getString(KEY_SECURITY_TOKEN, getString(R.string.default_token)))
    }
    
    private fun uploadPhoto() {
        val currentBitmap = currentBitmap
        if (currentBitmap == null) {
            Toast.makeText(this, getString(R.string.please_take_photo), Toast.LENGTH_SHORT).show()
            return
        }
        
        val phoneName = binding.etPhoneName.text.toString()
        val serverIP = binding.etServerIP.text.toString()
        val serverPort = binding.etServerPort.text.toString()
        val securityToken = binding.etSecurityToken.text.toString()
        
        if (phoneName.isBlank() || serverIP.isBlank() || serverPort.isBlank() || securityToken.isBlank()) {
            Toast.makeText(this, getString(R.string.please_enter_settings), Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 파일명 생성
                val fileName = generateFileName(phoneName)
                
                // 이미지를 파일로 저장
                val imageFile = saveBitmapToFile(currentBitmap, fileName)
                
                // 서버로 업로드
                val success = uploadToServer(imageFile, serverIP, serverPort, securityToken)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        binding.tvStatus.text = getString(R.string.upload_success)
                        Toast.makeText(this@MainActivity, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                    } else {
                        binding.tvStatus.text = getString(R.string.upload_failed)
                        Toast.makeText(this@MainActivity, getString(R.string.upload_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "업로드 실패", e)
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.upload_failed)
                    Toast.makeText(this@MainActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun generateFileName(phoneName: String): String {
        val lastNumber = sharedPreferences.getInt(KEY_LAST_FILE_NUMBER, 0)
        val newNumber = lastNumber + 1
        
        sharedPreferences.edit().putInt(KEY_LAST_FILE_NUMBER, newNumber).apply()
        
        return "${phoneName}-${String.format("%03d", newNumber)}.jpg"
    }
    
    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String): File {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
        return file
    }
    
    private suspend fun uploadToServer(imageFile: File, serverIP: String, serverPort: String, securityToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        imageFile.name,
                        imageFile.asRequestBody("image/jpeg".toMediaType())
                    )
                    .build()
                
                val url = "http://$serverIP:$serverPort/upload?token=$securityToken"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                response.close()
                
                response.isSuccessful
            } catch (e: IOException) {
                Log.e(TAG, "업로드 실패", e)
                false
            }
        }
    }
} 