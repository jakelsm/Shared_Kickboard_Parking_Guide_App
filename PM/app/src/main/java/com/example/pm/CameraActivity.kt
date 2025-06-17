package com.example.pm

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.makeright.Loading_View
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var serverUrl: String          // 서버 URL
    private lateinit var previewView: PreviewView   // 카메라 화면을 보여줄 뷰
    private lateinit var imageCapture: ImageCapture // 카메라 캡쳐를 위한 클래스
    private lateinit var progressBar: ProgressBar   // 로딩 바
    private lateinit var captureButton: Button      // 촬영 버튼
    private lateinit var loadingView: Loading_View
    private var cameraProvider: ProcessCameraProvider? = null // 카메라 프로바이더
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor() // 카메라 작업을 위한 스레드 풀

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera)  // CameraActivity의 레이아웃 설정

        // MainActivity에서 전달된 serverUrl을 가져옴
        serverUrl = intent.getStringExtra("server_url") ?: ""
            
        // 로딩창
        progressBar = findViewById(R.id.progressBar)

        // 로딩 뷰
        loadingView = findViewById(R.id.loadingView)

        // 카메라 화면을 보여줄 뷰 초기화
        previewView = findViewById(R.id.previewView)
        startCamera() // 카메라 초기화

        // 카메라 버튼 설정
        captureButton = findViewById(R.id.captureButton)
        captureButton.setOnClickListener {
            showLoading()
            takePhoto() // 버튼 클릭 시 사진 촬영
        }
    }

    private fun showLoading() {
        loadingView.visibility = View.VISIBLE
        captureButton.isEnabled = false  // 버튼을 비활성화
        progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingView.visibility = View.GONE
        captureButton.isEnabled = true   // 버튼을 활성화
        progressBar.visibility = View.GONE
    }

    /**
     * 카메라 초기화 함수
     */
    private fun startCamera() {
        // 카메라 프로바이더 인스턴스를 가져옴
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // 리스너를 추가하여 카메라 프로바이더가 준비되었을 때 호출됨
        cameraProviderFuture.addListener({
            // 카메라 프로바이더 가져오기
            cameraProvider = cameraProviderFuture.get()

            // 프리뷰 설정 빌드
            val preview = Preview.Builder().build().also {
                // 프리뷰 뷰의 SurfaceProvider를 설정
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 이미지 캡처 설정 빌드
            imageCapture = ImageCapture.Builder().build() // ImageCapture 설정

            // 기본 후면 카메라 선택
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 바인드된 모든 use case를 언바인드
                cameraProvider?.unbindAll()
                // 프리뷰와 이미지 캡처를 라이프사이클에 바인드
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                // 바인딩 실패 시 로그 출력
                Log.e("CameraXApp", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this)) // 메인 스레드에서 실행되도록 메인 실행자를 전달
    }

    /**
     * 카메라 촬영 로직
     */
    private fun takePhoto() {
        // 이미지 캡처 설정
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this), // 메인 스레드에서 실행되도록 설정
            object : ImageCapture.OnImageCapturedCallback() {
                // 이미지 캡처 성공 시 호출
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    // 비트맵 이미지를 서버로 전송
                    sendImageToServer(bitmap) // sendImageToServer 호출

                    // ImageProxy 객체를 해제
                    image.close()

                    // 결과를 설정하여 호출한 액티비티로 반환
                    val resultIntent = Intent().apply {
                        putExtra("photo_data", bytes) // 비트맵 데이터를 결과 Intent에 추가
                    }
                    setResult(Activity.RESULT_OK, resultIntent) // 결과를 설정
                }

                // 이미지 캡처 실패 시 호출
                override fun onError(exc: ImageCaptureException) {
                    // 로딩창 제거
                    hideLoading()
                    Log.e("CameraXApp", "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    /**
     * 비트맵 이미지를 서버로 전송하는 함수
     */
    private fun sendImageToServer(bitmap: Bitmap){
        // 비트맵 이미지를 바이트 배열로 변환
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        // MultipartBody를 사용하여 바이트 배열을 추가
        val client = OkHttpClient()
        val mediaType = "image/jpeg".toMediaTypeOrNull()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.jpg", byteArray.toRequestBody(mediaType))
            .build()

        // 요청 생성
        val request = Request.Builder()
            .url("$serverUrl/upload/")
            .post(requestBody)
            .build()

        // 비동기 요청 실행
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Upload Error", e.message ?: "Error")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // 서버 응답 바디를 문자열로 변환
                    val responseBody = response.body?.string()

                    // Gson을 사용하여 문자열을 ServerResponse 객체로 파싱
                    val gson = Gson()
                    val serverResponse = gson.fromJson(responseBody, ServerResponse::class.java)

                    // 파싱된 결과를 로그로 출력
                    Log.d("Upload Success", "Server response: result = ${serverResponse.result}")
                    // 메인 스레드에서 UI 업데이트
                    Handler(Looper.getMainLooper()).post {
                        /**
                         *# 금지 클래스랑 만난 경우 4가지 result 값이 1: crosswalk', 2:'braille_block', 3: 'bike_road', 4: 'car_road'
                         # 킥보드가 누워 있는 경우 result 값이 5
                         # 킥보드가 이미지 지정한 위치 내에 없는 경우 result 값이 6, 있긴 한데 킥보드의 pose 값이 검출이 되지 않는 경우 즉 잘 안보이는 경우 7
                         # 업로드 성공 시 성공 상태 코드 200과 함께 응답 반환
                         */
                        if (serverResponse.result == 1) {
                            showPopup("다른 곳에 주차해주세요", "킥보드가 횡단보도 위에 있어요!", false)
                        }
                        else if (serverResponse.result == 2) {
                            showPopup("다른 곳에 주차해주세요", "킥보드가 점자블록 위에 있어요!", false)
                        }
                        else if (serverResponse.result == 3) {
                            showPopup("다른 곳에 주차해주세요", "킥보드가 자전거 도로 위에 있어요!", false)
                        }
                        else if (serverResponse.result == 4) {
                            showPopup("다른 곳에 주차해주세요", "킥보드가 도로 위에 있어요!", false)
                        }
                        else if (serverResponse.result == 5) {
                            showPopup("킥보드를 세워주세요", "킥보드가 누워있어요!", false)
                        }
                        else if (serverResponse.result == 6) {
                            showPopup("사진을 다시 찍어주세요", "킥보드가 사진 속에 없어요!", false)
                        }
                        else if (serverResponse.result == 7) {
                            showPopup("사진을 다시 찍어주세요", "킥보드가 제대로 찍히지 않았어요!", false)
                        }
                        else if(serverResponse.result == 200) {
                            showPopup("반납 완료", "감사합니다.", true)
                        }

                        else {
                            Toast.makeText(this@CameraActivity, "서버에서 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }

//                    runOnUiThread {
//                        Toast.makeText(this@CameraActivity, "이미지가 성공적으로 업로드되었습니다.", Toast.LENGTH_SHORT).show()
//                    }
                } else {
                    Log.e("Upload Error", "Failed to upload image: ${response.code}, ${response.message}")
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, "이미지 업로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    /**
     * 팝업을 표시하는 함수 정의
     */
    fun showPopup(title: String, message: String, isFinish: Boolean) {
        // AlertDialog.Builder를 사용하여 팝업 생성
        val builder = AlertDialog.Builder(this@CameraActivity)
        builder.setTitle(title)   // 팝업의 제목
        builder.setMessage(message)  // 팝업의 메시지
        builder.setPositiveButton("확인") { dialog, _ ->
            dialog.dismiss()  // "확인" 버튼을 클릭하면 팝업 닫기

            if (isFinish) {
                finish() // CameraActivity를 종료하고 MainActivity로 돌아감
            }
        }

        // 팝업을 화면에 표시
        val dialog = builder.create()
        dialog.show()
        // 로딩창 제거
        hideLoading()
    }

    /**
     * 액티비티 종료 시 카메라 세션 해제
     */
    override fun onDestroy() {
        super.onDestroy()
        // 카메라 프로바이더 해제
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown() // 스레드 풀 종료
    }

    data class ServerResponse(
        val result: Int
    )
}


