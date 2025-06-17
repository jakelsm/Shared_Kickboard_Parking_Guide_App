package com.example.makeright.com.example.pm

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.pm.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapView
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.PolygonOverlay
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import com.naver.maps.map.MapFragment
import com.naver.maps.map.util.FusedLocationSource

class NaverMapManager(
    private val context: Context, // 현재 컨텍스트를 나타내는 변수
    private val mapView: MapView, // 지도 뷰 객체
    private val locationSource: FusedLocationSource, // 위치 소스 객체
    private var fusedLocationClient: FusedLocationProviderClient, // 위치 제공자 클라이언트
    private var permissionsGranted: Boolean, // 위치 권한이 부여되었는지 여부
    private var serverUrl: String // 서버 URL
) : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var naverMap: NaverMap // NaverMap 객체
    private val forbiddenZones = mutableListOf<List<LatLng>>() // 금지구역 데이터를 저장할 변수

    /**
     * 네이버 지도를 초기화하는 함수
     */
    fun initializeMap() {
        Log.d("NaverMapManager", "Initializing map...")
        // 네이버 지도 SDK 클라이언트 설정
        NaverMapSdk.getInstance(context).client = NaverMapSdk.NaverCloudPlatformClient("kne5t2wstb")
        mapView.onCreate(null) // 지도 생성
        mapView.getMapAsync(this) // 지도 준비 콜백 설정

        // 지도 프래그먼트가 없으면 새로 생성하여 추가
        val mapFragment = (context as AppCompatActivity).supportFragmentManager.findFragmentById(R.id.map_view) as MapFragment?
            ?: MapFragment.newInstance().also {
                (context as AppCompatActivity).supportFragmentManager.beginTransaction().add(R.id.map_view, it).commit()
            }
        mapFragment.getMapAsync(this)
    }

    /**
     * 네이버 지도가 준비되었을 때 호출되는 함수
     */
    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap // 네이버 맵 객체를 초기화
        try {
            if (permissionsGranted) {
                // 네이버 지도에 위치 소스를 설정
                naverMap.locationSource = locationSource
                // 위치 추적 모드를 'Follow'로 설정하여 현재 위치를 따라가도록 설정
                naverMap.locationTrackingMode = LocationTrackingMode.Follow

                // 마지막 위치를 가져와 지도 카메라를 설정
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        naverMap.cameraPosition = CameraPosition(latLng, 16.0)
//                        Toast.makeText(context, "현재 위치: ${latLng.latitude}, ${latLng.longitude}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // 권한이 없는 경우 권한 요청
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA),
                    1000
                )
            }
            // 지도 클릭 이벤트 처리 (PointF와 LatLng 두 매개변수를 받음)
            naverMap.setOnMapClickListener { pointF, latLng ->
                val latitude = latLng.latitude
                val longitude = latLng.longitude
                Log.d("NaverMapManager", "${latLng.latitude}, ${latLng.longitude}")
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }

        // 서버에서 금지 구역 데이터를 가져옴
        fetchOverlayData()
    }

// 서버 사용 불가 환경에서 사용할 테스트 데이터
//        forbiddenZones.add(
//            listOf(
//                LatLng(37.0666102, 127.0783881),
//                LatLng(38.5676102, 126.9783881),
//                LatLng(37.5676102, 126.9793881),
//                LatLng(37.5666102, 126.9793881)
//            )
//        )
//        Handler(Looper.getMainLooper()).post {
//            addProhibitedAreaOverlays(forbiddenZones)
//        }

    /**
     * 서버에서 금지 구역 데이터를 가져오는 함수
     */
    private fun fetchOverlayData() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$serverUrl/forbidden_zones") // 서버에서 금지 구역 데이터 URL 설정
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OverlayDataFetch", "Failed to fetch data", e)
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseData ->
                        Log.d("OverlayDataFetch", "Response data: $responseData")
                        val jsonArray = JSONArray(responseData)

                        // 금지 구역 리스트 초기화 후 서버 응답 데이터를 추가
                        forbiddenZones.clear()

                        for (i in 0 until jsonArray.length()) {
                            val jsonPolygon = jsonArray.getJSONArray(i)
                            val points = mutableListOf<LatLng>()
                            for (j in 0 until jsonPolygon.length()) {
                                val point = jsonPolygon.getJSONArray(j)
                                val latitude = point.getDouble(0)
                                val longitude = point.getDouble(1)
                                points.add(LatLng(latitude, longitude))
                            }
                            forbiddenZones.add(points)
                        }

                        // 메인 스레드에서 금지 구역 오버레이 추가
                        Handler(Looper.getMainLooper()).post {
                            addProhibitedAreaOverlays(forbiddenZones)
                        }
                    }
                } else {
                    Log.e("OverlayDataFetch", "Response was not successful: ${response.code}")
                }
            }
        })
    }

    /**
     * 금지 구역을 지도에 오버레이로 추가하는 함수
     */
    fun addProhibitedAreaOverlays(prohibitedAreas: List<List<LatLng>>) {
        for (area in prohibitedAreas) {
            val polygon = PolygonOverlay()
            polygon.coords = area // 금지 구역의 좌표 설정
            polygon.color = Color.argb(100, 255, 0, 0) // 반투명 빨간색으로 표시
            polygon.map = naverMap // 네이버 지도에 오버레이 추가

            // 오버레이 추가 로그 기록
            Log.d("OverlayDataFetch", "Added overlay: $area")
        }
    }

    /**
     * 특정 점이 다각형 내부에 있는지 확인하는 함수 (위치 비교 알고리즘)
     */
    fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        var intersects = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            if ((polygon[i].longitude > point.longitude) != (polygon[j].longitude > point.longitude) &&
                point.latitude < (polygon[j].latitude - polygon[i].latitude) * (point.longitude - polygon[i].longitude) / (polygon[j].longitude - polygon[i].longitude) + polygon[i].latitude
            ) {
                intersects = !intersects
            }
            j = i
        }
        return intersects
    }

    /**
     * 현재 위치가 금지 구역 내부에 있는지 확인하고 결과를 콜백으로 반환하는 함수
     */
    fun checkCurrentLocationInForbiddenZone(callback: (Boolean) -> Unit) {
        try {
            if (permissionsGranted) {
                // 마지막 위치를 가져와 금지 구역 내부에 있는지 확인
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        Log.d("CurrentLocation", "(${latLng.latitude}, ${latLng.longitude})")

                        for (area in forbiddenZones) {
                            if (isPointInPolygon(latLng, area)) {
                                Log.d("Result", "True") // 금지 구역 내부에 있음
                                callback(true)
                                return@addOnSuccessListener
                            }
                        }
                        Log.d("Result", "False") // 금지 구역 외부
                        callback(false)
                    } else {
                        callback(false) // 위치 정보를 가져오지 못한 경우
                    }
                }
            } else {
                callback(false) // 권한이 없는 경우
            }
        } catch (e: SecurityException) {
            callback(false) // 예외 처리
        }
    }
}