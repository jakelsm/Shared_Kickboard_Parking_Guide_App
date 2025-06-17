package com.example.makeright

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View

class Loading_View @JvmOverloads constructor(
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

        rectPath.reset()
        rectPath.addRect(centralRect, Path.Direction.CW)

        canvas.save()

        // 블러 효과 적용
        paint.maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)

        // 흐림 효과를 적용한 반투명 오버레이 그리기
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        canvas.restore()
    }

}
