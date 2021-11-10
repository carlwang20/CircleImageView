package com.carlwang.circleimageview

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import kotlin.math.min
import kotlin.math.pow


@SuppressLint("AppCompatCustomView")
class CircleImageView : ImageView {

    private var SCALE_TYPE = ScaleType.CENTER_CROP

    private var BITMAP_CONFIG = Bitmap.Config.ARGB_8888
    private var COLORDRAWABLE_DIMENSION = 2

    private var DEFAULT_BORDER_WIDTH = 0
    private var DEFAULT_BORDER_COLOR: Int = Color.BLACK
    private var DEFAULT_CIRCLE_BACKGROUND_COLOR: Int = Color.TRANSPARENT
    private var DEFAULT_IMAGE_ALPHA = 255
    private var DEFAULT_BORDER_OVERLAY = false

    private var mDrawableRect = RectF()
    private var mBorderRect = RectF()

    private var mShaderMatrix: Matrix = Matrix()
    private var mBitmapPaint: Paint = Paint()
    private var mBorderPaint: Paint = Paint()
    private var mCircleBackgroundPaint: Paint = Paint()

    private var mBorderColor = DEFAULT_BORDER_COLOR
    private var mBorderWidth = DEFAULT_BORDER_WIDTH
    private var mCircleBackgroundColor = DEFAULT_CIRCLE_BACKGROUND_COLOR
    private var mImageAlpha = DEFAULT_IMAGE_ALPHA

    private var mBitmap: Bitmap? = null
    private var mBitmapCanvas: Canvas? = null

    private var mDrawableRadius = 0f
    private var mBorderRadius = 0f

    private var mColorFilter: ColorFilter? = null

    private var mInitialized = false
    private var mRebuildShader = false
    private var mDrawableDirty = false

    private var mBorderOverlay = false
    private var mDisableCircularTransformation = false

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        val array: TypedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.CircleImageView,
            defStyleAttr,
            0
        )

        mBorderWidth = array.getDimensionPixelSize(
            R.styleable.CircleImageView_cw_border_width,
            DEFAULT_BORDER_WIDTH
        )
        mBorderColor =
            array.getColor(R.styleable.CircleImageView_cw_border_color, DEFAULT_BORDER_COLOR)
        mBorderOverlay =
            array.getBoolean(R.styleable.CircleImageView_cw_border_overlay, DEFAULT_BORDER_OVERLAY)
        mCircleBackgroundColor = array.getColor(
            R.styleable.CircleImageView_cw_circle_background_color,
            DEFAULT_CIRCLE_BACKGROUND_COLOR
        )

        array.recycle()

        initView()
    }


    private fun initView() {
        mInitialized = true
        super.setScaleType(SCALE_TYPE)
        mBitmapPaint.isAntiAlias = true
        mBitmapPaint.isDither = true
        mBitmapPaint.isFilterBitmap = true
        mBitmapPaint.alpha = mImageAlpha
        mBitmapPaint.colorFilter = mColorFilter
        mBorderPaint.style = Paint.Style.STROKE
        mBorderPaint.isAntiAlias = true
        mBorderPaint.color = mBorderColor
        mBorderPaint.strokeWidth = mBorderWidth.toFloat()
        mCircleBackgroundPaint.style = Paint.Style.FILL
        mCircleBackgroundPaint.isAntiAlias = true
        mCircleBackgroundPaint.color = mCircleBackgroundColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = OutlineProvider()
        }
    }

    @SuppressLint("CanvasSize")
    override fun onDraw(canvas: Canvas) {
        if (mDisableCircularTransformation) {
            super.onDraw(canvas)
            return
        }

        if (mCircleBackgroundColor != Color.TRANSPARENT) {
            canvas.drawCircle(
                mDrawableRect.centerX(),
                mDrawableRect.centerY(),
                mDrawableRadius,
                mCircleBackgroundPaint
            )
        }

        mBitmap?.let { mBitmap ->
            if (mDrawableDirty) {
                mBitmapCanvas?.let { mBitmapCanvas ->
                    mDrawableDirty = false
                    val drawable: Drawable = drawable
                    drawable.setBounds(0, 0, mBitmapCanvas.width, mBitmapCanvas.height)
                    drawable.draw(mBitmapCanvas)
                }
            }
            if (mRebuildShader) {
                mRebuildShader = false
                val bitmapShader =
                    BitmapShader(mBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                bitmapShader.setLocalMatrix(mShaderMatrix)
                mBitmapPaint.shader = bitmapShader
            }
            canvas.drawCircle(
                mDrawableRect.centerX(),
                mDrawableRect.centerY(),
                mDrawableRadius,
                mBitmapPaint
            )
        }

        if (mBorderWidth > 0) {
            canvas.drawCircle(
                mBorderRect.centerX(),
                mBorderRect.centerY(),
                mBorderRadius,
                mBorderPaint
            )
        }
    }

    override fun invalidateDrawable(dr: Drawable) {
        mDrawableDirty = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateDimensions()
        invalidate()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom)
        updateDimensions()
        invalidate()
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        super.setPaddingRelative(start, top, end, bottom)
        updateDimensions()
        invalidate()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        initializeBitmap()
        invalidate()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        initializeBitmap()
        invalidate()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        initializeBitmap()
        invalidate()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        initializeBitmap()
        invalidate()
    }

    override fun setImageAlpha(alpha: Int) {
        val a: Int = alpha and 0xFF
        if (a == mImageAlpha) {
            return
        }
        mImageAlpha = a

        if (mInitialized) {
            mBitmapPaint.alpha = alpha
            invalidate()
        }
    }

    override fun getImageAlpha(): Int {
        return mImageAlpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        if (cf == mColorFilter) return
        mColorFilter = cf
        if (mInitialized) {
            mBitmapPaint.colorFilter = cf
            invalidate()
        }
    }

    override fun getColorFilter(): ColorFilter? {
        return mColorFilter
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mDisableCircularTransformation) return super.onTouchEvent(event)
        return inTouchableArea(event.x, event.y) && super.onTouchEvent(event)
    }

    private fun inTouchableArea(x: Float, y: Float): Boolean {
        if (mBorderRect.isEmpty) return true
        return (x - mBorderRect.centerX()).toDouble()
            .pow(2.0) + (y - mBorderRect.centerY()).toDouble()
            .pow(2.0) <= mBorderRadius.toDouble().pow(2.0)
    }

    private fun updateDimensions() {
        mBorderRect.set(calculateBounds())
        mBorderRadius = min(
            (mBorderRect.height() - mBorderWidth) / 2F,
            (mBorderRect.width() - mBorderWidth) / 2F
        )
        mDrawableRect.set(mBorderRect)
        if (!mBorderOverlay && mBorderWidth > 0) {
            mDrawableRect.inset(mBorderWidth - 1F, mBorderWidth - 1F)
        }
        mDrawableRadius = min(mDrawableRect.height() / 2F, mDrawableRect.width() / 2F)

        updateShaderMatrix()
    }

    private fun calculateBounds(): RectF {
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        val sideLength = min(availableWidth, availableHeight)

        val left = paddingLeft + (availableWidth - sideLength) / 2F
        val top = paddingTop + (availableHeight - sideLength) / 2F
        return RectF(left, top, left + sideLength, top + sideLength)
    }

    private fun updateShaderMatrix() {
        mBitmap?.let { mBitmap ->

            val scale: Float
            var dx = 0F
            var dy = 0F

            mShaderMatrix.set(null)

            val bitmapWidth = mBitmap.width
            val bitmapHeight = mBitmap.height

            if (bitmapWidth * mDrawableRect.height() > mDrawableRect.width() * bitmapHeight) {
                scale = mDrawableRect.height() / bitmapHeight.toFloat()
                dx = (mDrawableRect.width() - bitmapWidth * scale) * 0.5F
            } else {
                scale = mDrawableRect.width() / bitmapWidth.toFloat()
                dy = (mDrawableRect.height() - bitmapHeight * scale) * 0.5f
            }
            mShaderMatrix.setScale(scale, scale)
            mShaderMatrix.postTranslate(
                (dx + 0.5F).toInt() + mDrawableRect.left,
                (dy + 0.5F).toInt() + mDrawableRect.top
            )
            mRebuildShader = true
        }
    }

    private fun initializeBitmap() {
        mBitmap = getBitmapFromDrawable(drawable)
        mBitmapCanvas = if (mBitmap != null && mBitmap!!.isMutable) {
            Canvas(mBitmap!!)
        } else {
            null
        }

        if (!mInitialized) return

        if (mBitmap != null) {
            updateShaderMatrix()
        } else {
            mBitmapPaint.shader = null
        }
    }

    private fun getBitmapFromDrawable(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        try {
            val bitmap: Bitmap = if (drawable is ColorDrawable) {
                Bitmap.createBitmap(
                    COLORDRAWABLE_DIMENSION,
                    COLORDRAWABLE_DIMENSION,
                    BITMAP_CONFIG
                )
            } else {
                Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    BITMAP_CONFIG
                )
            }
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun getBorderColor(): Int {
        return mBorderColor
    }

    fun setBorderColor(@ColorInt borderColor: Int) {
        if (borderColor == mBorderColor) return
        mBorderColor = borderColor
        mBorderPaint.color = borderColor
        invalidate()
    }

    fun getCircleBackgroundColor(): Int {
        return mCircleBackgroundColor
    }

    fun setCircleBackgroundColor(@ColorInt circleBackgroundColor: Int) {
        if (circleBackgroundColor == mCircleBackgroundColor) return
        mCircleBackgroundColor = circleBackgroundColor
        mCircleBackgroundPaint.color = circleBackgroundColor
        invalidate()
    }

    fun getBorderWidth(): Int {
        return mBorderWidth
    }

    fun setBorderWidth(borderWidth: Int) {
        if (borderWidth == mBorderWidth) return
        mBorderWidth = borderWidth
        mBorderPaint.strokeWidth = borderWidth.toFloat()
        updateDimensions()
        invalidate()
    }

    fun isBorderOverlay(): Boolean {
        return mBorderOverlay
    }

    fun setBorderOverlay(borderOverlay: Boolean) {
        if (borderOverlay == mBorderOverlay) return
        mBorderOverlay = borderOverlay
        updateDimensions()
        invalidate()
    }

    fun isDisableCircularTransformation(): Boolean {
        return mDisableCircularTransformation
    }

    fun setDisableCircularTransformation(disableCircularTransformation: Boolean) {
        if (disableCircularTransformation == mDisableCircularTransformation) return
        mDisableCircularTransformation = disableCircularTransformation
        if (disableCircularTransformation) {
            mBitmap = null
            mBitmapCanvas = null
            mBitmapPaint.shader = null
        } else {
            initializeBitmap()
        }
        invalidate()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private inner class OutlineProvider : ViewOutlineProvider() {
        override fun getOutline(view: View?, outline: Outline?) {
            if (mDisableCircularTransformation) {
                BACKGROUND.getOutline(view, outline)
            } else {
                val bounds = Rect()
                mBorderRect.roundOut(bounds)
                outline?.setRoundRect(bounds, bounds.width() / 2.0F)
            }
        }
    }
}