package com.example.pm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.makeright.com.example.pm.NaverMapManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.naver.maps.map.MapView
import com.naver.maps.map.util.FusedLocationSource

class MainActivity : AppCompatActivity() {
    private var serverUrl = "http://220.68.8.68:8000"    // 서버 환경 주소
//    var serverUrl = "http://10.0.2.2:8000"          // 로컬 환경 주소
    private val PERMISSION_REQUEST_CODE = 1000      // 권한 요청 코드
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,   // 현재 위치 권한
        Manifest.permission.ACCESS_COARSE_LOCATION, // 대략적인 위치 권한
        Manifest.permission.CAMERA                  // 카메라 권한
    )

    // 네이버 지도 관련 변수들
    private lateinit var naverMapManager: NaverMapManager                   // 네이버 지도 매니저
    private lateinit var locationSource: FusedLocationSource                // 위치 소스
    private lateinit var fusedLocationClient: FusedLocationProviderClient   // 위치 클라이언트

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 권한 요청
        requestPermissions()

        // 지도 뷰 가져오기
        val mapView: MapView = findViewById(R.id.map_view)
        // 위치 소스 및 클라이언트 초기화
        locationSource = FusedLocationSource(this, PERMISSION_REQUEST_CODE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // 네이버 지도 매니저 초기화
        naverMapManager = NaverMapManager(this, mapView, locationSource, fusedLocationClient, permissionsGranted(), serverUrl)
        // 지도 초기화를 보장하기 위해(initializeMap함수가 모두 수행되기를 기다리는) post 메서드 사용
        mapView.post {
            naverMapManager.initializeMap()
        }

        // 카메라 버튼 설정
        val cameraButton: Button = findViewById(R.id.camera)
        cameraButton.setOnClickListener {
            naverMapManager.checkCurrentLocationInForbiddenZone { isInForbiddenZone ->
                if (isInForbiddenZone) {
                    Toast.makeText(this@MainActivity, "금지구역 안에 있습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    showImagePopup(this)
                }
            }
        }

        // 현재 금지구역 안인지 확인하는 버튼
//        val calcButton: Button = findViewById(R.id.calc)
//        calcButton.setOnClickListener {
//            naverMapManager.checkCurrentLocationInForbiddenZone { isInForbiddenZone ->
//                if (isInForbiddenZone) {
//                    Toast.makeText(this@MainActivity, "금지구역 안에 있습니다.", Toast.LENGTH_SHORT).show()
//                } else {
//                    Toast.makeText(this@MainActivity, "금지구역 밖에 있습니다.", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
    }

    /**
     * 권한이 부여되었는지 확인하는 함수
     **/
    private fun permissionsGranted(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     *  권한 요청 함수
     * */
    private fun requestPermissions() {
        if (!permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * 권한 요청 결과 처리 함수
     **/
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 모든 권한 허용시
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "모든 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                // 필수 권한 거부시
                Toast.makeText(this, "필수 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showImagePopup(context: Context) {
        // 커스텀 레이아웃 인플레이트
        val dialogView = LayoutInflater.from(context).inflate(R.layout.image_noti, null)

        // 다이얼로그 생성 및 커스텀 뷰 설정
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                // 확인 버튼을 눌렀을 때 실행할 로직
                // CameraActivity를 시작
                val intent = Intent(this, CameraActivity::class.java)
                intent.putExtra("server_url", serverUrl)
                startActivity(intent)
            }
            .create()
        dialog.show()
    }


}
