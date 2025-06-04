package com.google.mediapipe.examples.facelandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: FaceLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Clear previous drawings if results exist but have no face landmarks
        if (results?.faceLandmarks().isNullOrEmpty()) {
            clear()
            return
        }

        results?.let { faceLandmarkerResult ->

            // Calculate scaled image dimensions
            val scaledImageWidth = imageWidth * scaleFactor
            val scaledImageHeight = imageHeight * scaleFactor

            // Calculate offsets to center the image on the canvas
            val offsetX = (width - scaledImageWidth) / 2f
            val offsetY = (height - scaledImageHeight) / 2f

            // Iterate through each detected face
            faceLandmarkerResult.faceLandmarks().forEach { faceLandmarks ->
                // Draw all landmarks for the current face
//                drawFaceLandmarks(canvas, faceLandmarks, offsetX, offsetY)

                // Draw all connectors for the current face
//                drawFaceConnectors(canvas, faceLandmarks, offsetX, offsetY)
                drawLipstickOverlay(canvas, faceLandmarks, offsetX, offsetY)
            }
        }
    }

    private fun buildSmoothPath(
        landmarks: List<NormalizedLandmark>,
        indices: List<Int>,
        offsetX: Float,
        offsetY: Float
    ): Path {
        val path = Path()
        val points = indices.mapNotNull { idx ->
            landmarks.getOrNull(idx)?.let {
                PointF(
                    it.x() * imageWidth * scaleFactor + offsetX,
                    it.y() * imageHeight * scaleFactor + offsetY
                )
            }
        }

        if (points.size < 2) return path

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size - 1) {
            val midPointX = (points[i].x + points[i + 1].x) / 2
            val midPointY = (points[i].y + points[i + 1].y) / 2
            path.quadTo(points[i].x, points[i].y, midPointX, midPointY)
        }

        // Connect back to the start
        path.lineTo(points.last().x, points.last().y)
        path.lineTo(points[0].x, points[0].y)
        path.close()

        return path
    }

    private fun drawLipstickOverlay(
        canvas: Canvas,
        faceLandmarks: List<NormalizedLandmark>,
        offsetX: Float,
        offsetY: Float
    ) {
        val outerLip = listOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146)
        val innerLip = listOf(78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191)

        val outerPath = buildSmoothPath(faceLandmarks, outerLip, offsetX, offsetY)
        val innerPath = buildSmoothPath(faceLandmarks, innerLip, offsetX, offsetY)

        val fullPath = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addPath(outerPath)
            addPath(innerPath)
        }

        val lipPoints = outerLip.mapNotNull { faceLandmarks.getOrNull(it) }
        val centerX = lipPoints.map { it.x() }.average() * imageWidth * scaleFactor + offsetX
        val centerY = lipPoints.map { it.y() }.average() * imageHeight * scaleFactor + offsetY
        val radius = (lipPoints.maxOf { it.x() } - lipPoints.minOf { it.x() }) * imageWidth * scaleFactor / 1.5f

        val lipstickShader = RadialGradient(
            centerX.toFloat(), centerY.toFloat(), radius.toFloat(),
            intArrayOf(Color.parseColor("#A60000"), Color.parseColor("#3A0000")), // Rich red
            floatArrayOf(0.2f, 1f),
            Shader.TileMode.CLAMP
        )
        val matteShader = RadialGradient(
            centerX.toFloat(), centerY.toFloat(), radius.toFloat(),
            intArrayOf(Color.parseColor("#A60000"), Color.parseColor("#3A0000")),
            floatArrayOf(0.3f, 1f),
            Shader.TileMode.CLAMP
        )
        // Simulated velvet gradient
        val velvetShader = RadialGradient(
            centerX.toFloat(), centerY.toFloat(), radius.toFloat(),
            intArrayOf(Color.parseColor("#B71C1C"), Color.parseColor("#4A0000")),
            floatArrayOf(0.2f, 1f),
            Shader.TileMode.CLAMP
        )


        val lipstickPaint = Paint().apply {
            shader = /*lipstickShader*/ velvetShader /*matteShader*/
//            shader = lipstickShader
//            color = context.getColor(R.color.cherry_red_dense)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val erasePaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            isAntiAlias = true
        }


        val layerId = canvas.saveLayer(null, null)
        canvas.drawPath(fullPath, lipstickPaint)
        canvas.drawPath(innerPath, erasePaint)
        canvas.restoreToCount(layerId)
    }


    /**
     * Draws all landmarks for a single face on the canvas.
     */
    private fun drawFaceLandmarks(
        canvas: Canvas,
        faceLandmarks: List<NormalizedLandmark>,
        offsetX: Float,
        offsetY: Float
    ) {
        faceLandmarks.forEach { landmark ->
            val x = landmark.x() * imageWidth * scaleFactor + offsetX
            val y = landmark.y() * imageHeight * scaleFactor + offsetY
            canvas.drawPoint(x, y, pointPaint)
        }
    }

    /**
     * Draws all the connectors between landmarks for a single face on the canvas.
     */
    private fun drawFaceConnectors(
        canvas: Canvas,
        faceLandmarks: List<NormalizedLandmark>,
        offsetX: Float,
        offsetY: Float
    ) {
        FaceLandmarker.FACE_LANDMARKS_CONNECTORS.filterNotNull().forEach { connector ->
            val startLandmark = faceLandmarks.getOrNull(connector.start())
            val endLandmark = faceLandmarks.getOrNull(connector.end())

            if (startLandmark != null && endLandmark != null) {
                val startX = startLandmark.x() * imageWidth * scaleFactor + offsetX
                val startY = startLandmark.y() * imageHeight * scaleFactor + offsetY
                val endX = endLandmark.x() * imageWidth * scaleFactor + offsetX
                val endY = endLandmark.y() * imageHeight * scaleFactor + offsetY

                canvas.drawLine(startX, startY, endX, endY, linePaint)
            }
        }
    }

    fun setResults(
        faceLandmarkerResults: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = faceLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
        private const val TAG = "Face Landmarker Overlay"
    }
}