package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.example.myapplication.ApiService

class MainActivity : AppCompatActivity() {

    private var myName: String = ""
    private fun showNameDialog() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("이름 입력")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("확인") { _, _ ->
                val name = editText.text.toString()
                WebSocketManager.connect(name)

                WebSocketManager.onUserListUpdate = { userList ->
                    runOnUiThread {
                        updateUserList(userList)
                    }
                }
            }.show()
    }
    private fun updateUserList(users: List<Pair<String, Double>>) {
        val container = findViewById<LinearLayout>(R.id.userListContainer)
        container.removeAllViews()

        for ((name, distance) in users) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val nameView = TextView(this).apply {
                text = "[$name]"
                setPadding(0, 0, 32, 0)
            }
            val distView = TextView(this).apply {
                text = "${distance}m"
            }
            row.addView(nameView)
            row.addView(distView)
            container.addView(row)
        }
    }

    private lateinit var timeInput: EditText
    private var setDurationInSec = 0
    private val autoStopHandler = Handler(Looper.getMainLooper())
    /*
    * 서버연동 설정
    * */

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var lastLocation: Location? = null
    private var totalDistance = 0f
    private var isTracking = false

    private var timerSeconds = 0
    private var isTimerRunning = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var toggleButton: Button
    private lateinit var distanceTextView: TextView
    private lateinit var timeTextView: TextView

    private val LOCATION_PERMISSION_REQUEST_CODE = 1000

    private lateinit var stopButton: Button
    private var finalDistance = 0f

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                timerSeconds++
                timeTextView.text = formatSecondsToTime(timerSeconds)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun formatSecondsToTime(seconds: Int): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hrs, mins, secs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApiService.sendEcho("앱에서 서버로 보낸다!")
        showNameDialog()

        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleButton)
        timeInput = findViewById(R.id.timeInput)
        stopButton = findViewById(R.id.stopButton)
        distanceTextView = findViewById(R.id.distanceTextView)
        timeTextView = findViewById(R.id.timeTextView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val currentLocation = locationResult.lastLocation ?: return

                if (isTracking) {
                    lastLocation?.let {
                        val distance = it.distanceTo(currentLocation)
                        if (distance in 0.1..100.0) {
                            totalDistance += distance
                            distanceTextView.text =
                                "총 이동 거리: ${"%.2f".format(totalDistance)} m"

//                            WebSocketManager.sendDistance(totalDistance / 1000.0)
                            WebSocketManager.sendDistance(totalDistance.toDouble())
                        }
                    }
                    lastLocation = currentLocation
                }
            }
        }

        toggleButton.setOnClickListener {
            isTracking = !isTracking
            if (isTracking) {
                // ⏱ 시간 입력 값 확인
                val secondsStr = timeInput.text.toString()
                setDurationInSec = secondsStr.toIntOrNull() ?: 0

                if (setDurationInSec <= 0) {
                    Toast.makeText(this, "시간(초)을 정확히 입력하세요", Toast.LENGTH_SHORT).show()
                    isTracking = false
                    return@setOnClickListener
                }

                toggleButton.text = "일시정지"
                stopButton.visibility = Button.VISIBLE
                startTimer()
                Toast.makeText(this, "측정 시작", Toast.LENGTH_SHORT).show()

                // ⏲ 입력된 시간 후 자동 종료 예약
                autoStopHandler.postDelayed({
                    if (isTracking) {
                        stopTrackingAndShowResult()
                    }
                }, setDurationInSec * 1000L)

            } else {
                toggleButton.text = "다시 시작"
                stopTimer()
                autoStopHandler.removeCallbacksAndMessages(null) // 자동 종료 취소
                Toast.makeText(this, "측정 일시정지", Toast.LENGTH_SHORT).show()
            }
        }


        // 정지 버튼
        stopButton.setOnClickListener {
            stopTrackingAndShowResult()
        }

        checkLocationPermissionAndStart()
    }

//    private fun stopTrackingAndShowResult() {
//        // 측정 중단
//        isTracking = false
//        stopTimer()
//        autoStopHandler.removeCallbacksAndMessages(null)
//        fusedLocationClient.removeLocationUpdates(locationCallback)
//        toggleButton.text = "시작"
//        stopButton.visibility = Button.GONE
//
//        finalDistance = totalDistance // 서버 전송용 등
//
//        val message = "총 이동 거리: ${"%.2f".format(finalDistance)} m\n총 시간: ${formatSecondsToTime(timerSeconds)}"
//
//        // 알림창 표시
//        val builder = android.app.AlertDialog.Builder(this)
//            .setTitle("운동 종료")
//            .setMessage(message)
//            .setPositiveButton("확인", null)
//        builder.show()
//    }
    private fun stopTrackingAndShowResult() {
        // 측정 중단
        isTracking = false
        stopTimer()
        autoStopHandler.removeCallbacksAndMessages(null)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        toggleButton.text = "시작"
        stopButton.visibility = Button.GONE

        finalDistance = totalDistance // 서버 전송용 등

        val rank = WebSocketManager.getMyCurrentRank() // 순위 가져오기

        val message = """
            총 이동 거리: ${"%.2f".format(finalDistance)} m
            총 시간: ${formatSecondsToTime(timerSeconds)}
            현재 순위: ${rank}위
        """.trimIndent()

        // 알림창 표시
        val builder = AlertDialog.Builder(this)
            .setTitle("운동 종료")
            .setMessage(message)
            .setPositiveButton("확인", null)
        builder.show()
    }


    private fun startTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true
            handler.post(timerRunnable)
        }
    }

    private fun stopTimer() {
        isTimerRunning = false
        handler.removeCallbacks(timerRunnable)
    }

    private fun checkLocationPermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 3000 // 1초마다 요청
            fastestInterval = 2000 // 최소 간격
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopTimer()
    }
}
