package com.example.makeright

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View

class camera_OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.parseColor("#80000000") // 반투명 검정색
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val rectPath = Path()
    private var centralRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // 중앙 사각형의 너비와 높이 정의 (이미지 크기의 60%로 설정)
        val centralRectWidth = 0.5f * width
        val centralRectHeight = 0.4f * height

        // 중앙 사각형의 좌표 계산
        val centralRectXMin = centerX - centralRectWidth / 2f
        val centralRectYMin = centerY - centralRectHeight / 2f
        val centralRectXMax = centerX + centralRectWidth / 2f
        val centralRectYMax = centerY + centralRectHeight / 2f

        centralRect.set(centralRectXMin, centralRectYMin, centralRectXMax, centralRectYMax)

        rectPath.reset()
        rectPath.addRect(centralRect, Path.Direction.CW)

        canvas.save()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(rectPath)
        } else {
            @Suppress("DEPRECATION")
            canvas.clipPath(rectPath, Region.Op.DIFFERENCE)
        }

        // 블러 효과 적용
        paint.maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)

        // 흐림 효과를 적용한 반투명 오버레이 그리기
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        canvas.restore()
    }
}