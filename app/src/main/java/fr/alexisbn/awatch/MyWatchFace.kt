package fr.alexisbn.awatch

import android.content.*
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.SystemProviders
import android.support.wearable.complications.rendering.ComplicationDrawable
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val HOUR_STROKE_WIDTH = 6f
private const val MINUTE_STROKE_WIDTH = 5f
private const val SECOND_TICK_STROKE_WIDTH = 4f

private const val CENTER_GAP_AND_CIRCLE_RADIUS = 8f

private const val SHADOW_RADIUS = 0f

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn"t
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        private val TAG = "CanvasWatchFaceService"

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F
        private var mWidth: Float = 0F
        private var mHeight: Float = 0F

        private var mSecondHandLength: Float = 0F
        private var sMinuteHandLength: Float = 0F
        private var sHourHandLength: Float = 0F

        private var currentTimeString: String = "00:00"
        private var currentDateString: String = "1 janv."

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private var mWatchHandColor: Int = 0
        private var mWatchHandHighlightColor: Int = 0
        private var mWatchHandShadowColor: Int = 0

        private var accentColor: Int = Color.parseColor("#00DE7A")

        private lateinit var mHourPaint: Paint
        private lateinit var mMinutePaint: Paint
        private lateinit var mSecondPaint: Paint
        private lateinit var mTickPaint: Paint
        private lateinit var mCirclePaint: Paint

        private lateinit var mDigitalTimePaint: Paint
        private lateinit var mDatePaint: Paint

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mGrayBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        // Complications
        private lateinit var complicationDrawableTopSlot: ComplicationDrawable
        private lateinit var complicationDrawableLeftSlot: ComplicationDrawable
        private lateinit var complicationDrawableBottomSlot: ComplicationDrawable

        private var prideRing = false
        private var hideTicksInAmbient = false

        private lateinit var sharedPref: SharedPreferences

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            mCalendar = Calendar.getInstance()

            currentTimeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
            currentDateString = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Calendar.getInstance().time)

            sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE) ?: return
            prideRing = sharedPref.getBoolean("prideRing", false)
            accentColor = Color.parseColor(sharedPref.getString("selectedAccentColor", "#00DE7A"))
            hideTicksInAmbient = sharedPref.getBoolean("hideTicksInAmbient", false)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@MyWatchFace).setAcceptsTapEvents(true).build())

            setActiveComplications(10, 20, 30)

            // Setting default complications
            setDefaultSystemComplicationProvider(
                10,
                SystemProviders.WATCH_BATTERY,
                ComplicationData.TYPE_RANGED_VALUE
            )
            setDefaultSystemComplicationProvider(
                20,
                SystemProviders.STEP_COUNT,
                ComplicationData.TYPE_SHORT_TEXT
            )
            setDefaultSystemComplicationProvider(
                30,
                SystemProviders.UNREAD_NOTIFICATION_COUNT,
                ComplicationData.TYPE_SHORT_TEXT
            )

            // Create complication drawables
            complicationDrawableTopSlot = ComplicationDrawable(this@MyWatchFace)
            complicationDrawableTopSlot.setTextColorActive(accentColor)
            complicationDrawableTopSlot.setTextTypefaceActive(Typeface.create(ResourcesCompat.getFont(applicationContext, R.font.montserrat_medium), Typeface.NORMAL))
            complicationDrawableTopSlot.setRangedValuePrimaryColorActive(accentColor)

            complicationDrawableLeftSlot = ComplicationDrawable(this@MyWatchFace)
            complicationDrawableLeftSlot.setTextColorActive(accentColor)
            complicationDrawableTopSlot.setTextTypefaceActive(Typeface.create(ResourcesCompat.getFont(applicationContext, R.font.montserrat_medium), Typeface.NORMAL))
            complicationDrawableTopSlot.setRangedValuePrimaryColorActive(accentColor)

            complicationDrawableBottomSlot = ComplicationDrawable(this@MyWatchFace)
            complicationDrawableBottomSlot.setTextColorActive(accentColor)
            complicationDrawableTopSlot.setTextTypefaceActive(Typeface.create(ResourcesCompat.getFont(applicationContext, R.font.montserrat_medium), Typeface.NORMAL))
            complicationDrawableTopSlot.setRangedValuePrimaryColorActive(accentColor)

            initializeBackground()
            initializeWatchFace()
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.watchface_service_bg)

            /* Extracts colors from background image to improve watchface style. */
            /*Palette.from(mBackgroundBitmap).generate {
                it?.let {
                    mWatchHandHighlightColor = it.getVibrantColor(Color.RED)
                    mWatchHandColor = it.getLightVibrantColor(Color.WHITE)
                    mWatchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
                    updateWatchHandStyle()
                }
            }*/
        }

        private fun initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.LTGRAY
            mWatchHandHighlightColor = accentColor
            mWatchHandShadowColor = Color.BLACK

            mHourPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = HOUR_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            mMinutePaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = MINUTE_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            mSecondPaint = Paint().apply {
                color = mWatchHandHighlightColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            mTickPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            mCirclePaint = Paint().apply {
                color = mWatchHandHighlightColor
                strokeWidth = HOUR_STROKE_WIDTH
                isAntiAlias = true
                style = Paint.Style.STROKE
            }

            mDigitalTimePaint = Paint().apply {
                color = mWatchHandHighlightColor
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
                textSize = 22f * resources.displayMetrics.scaledDensity
                typeface = Typeface.create(ResourcesCompat.getFont(applicationContext, R.font.ubuntu_bold), Typeface.NORMAL)
            }

            mDatePaint = Paint().apply {
                color = mWatchHandColor
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
                textSize = 12.5f * resources.displayMetrics.scaledDensity
                typeface = Typeface.create(ResourcesCompat.getFont(applicationContext, R.font.ubuntu), Typeface.NORMAL)
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            currentTimeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
            currentDateString = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Calendar.getInstance().time)
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        override fun onComplicationDataUpdate(watchFaceComplicationId: Int, data: ComplicationData?) {
            Log.d(TAG, "onComplicationDataUpdate: id=$watchFaceComplicationId, $data")

            (when (watchFaceComplicationId) {
                10 -> complicationDrawableTopSlot
                20 -> complicationDrawableLeftSlot
                else -> complicationDrawableBottomSlot
            }).setComplicationData(data)

            invalidate()
        }

        private fun updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.color = Color.WHITE
                mMinutePaint.color = Color.WHITE
                mSecondPaint.color = Color.WHITE
                mTickPaint.color = Color.WHITE
                mCirclePaint.color = Color.WHITE
                mDigitalTimePaint.color = Color.WHITE
                mDatePaint.color = Color.WHITE
            } else {
                mHourPaint.color = mWatchHandColor
                mMinutePaint.color = mWatchHandColor
                mSecondPaint.color = mWatchHandHighlightColor
                mTickPaint.color = mWatchHandColor
                mCirclePaint.color = mWatchHandHighlightColor
                mDigitalTimePaint.color = mWatchHandHighlightColor
                mDatePaint.color = mWatchHandColor
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mHourPaint.alpha = if (inMuteMode) 100 else 255
                mMinutePaint.alpha = if (inMuteMode) 100 else 255
                mSecondPaint.alpha = if (inMuteMode) 80 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (mCenterX * 0.875).toFloat()
            sMinuteHandLength = (mCenterX * 0.75).toFloat()
            sHourHandLength = (mCenterX * 0.5).toFloat()

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            /*val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (mBackgroundBitmap.width * scale).toInt(),
                    (mBackgroundBitmap.height * scale).toInt(), true)

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don"t want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren"t
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }*/

            mWidth = width.toFloat()
            mHeight = height.toFloat()
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.width,
                    mBackgroundBitmap.height,
                    Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(@TapType tapType: Int, x: Int, y: Int, eventTime: Long) {
            Log.d(TAG, "onTapCommand")
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // The user has completed the tap gesture.
                    prideRing = sharedPref.getBoolean("prideRing", false)
                    hideTicksInAmbient = sharedPref.getBoolean("hideTicksInAmbient", false)
                    accentColor = Color.parseColor(sharedPref.getString("selectedAccentColor", "#00DE7A"))
                    initializeWatchFace()
                    complicationDrawableTopSlot.setTextColorActive(accentColor)
                    complicationDrawableTopSlot.setRangedValuePrimaryColorActive(accentColor)
                    complicationDrawableLeftSlot.setTextColorActive(accentColor)
                    complicationDrawableLeftSlot.setRangedValuePrimaryColorActive(accentColor)
                    complicationDrawableBottomSlot.setTextColorActive(accentColor)
                    complicationDrawableBottomSlot.setRangedValuePrimaryColorActive(accentColor)
                }
            }
            invalidate()
        }



        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)
            if (!isInAmbientMode) {
                drawComplications(canvas, bounds)
            }
            drawWatchFace(canvas)
        }

        private fun drawBackground(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
        }

        private fun drawComplications(canvas: Canvas, bounds: Rect) {
            //Log.d(TAG, "l=${bounds.left}, r=${bounds.right}, t=${bounds.top}, b=${bounds.bottom}")
            val w = bounds.right - bounds.left
            val h = bounds.bottom - bounds.top

            complicationDrawableTopSlot.setBounds((bounds.left + (3f/8)*w).toInt(), (bounds.top + (1f/8)*h).toInt(), (bounds.left + (5f/8)*w).toInt(), (bounds.top + (3f/8)*h).toInt())
            complicationDrawableLeftSlot.setBounds((bounds.left + (1f/8)*w).toInt(), (bounds.top + (3f/8)*h).toInt(), (bounds.left + (3f/8)*w).toInt(), (bounds.top + (5f/8)*h).toInt())
            complicationDrawableBottomSlot.setBounds((bounds.left + (3f/8)*w).toInt(), (bounds.top + (5f/8)*h).toInt(), (bounds.left + (5f/8)*w).toInt(), (bounds.top + (7f/8)*h).toInt())

            complicationDrawableTopSlot.draw(canvas, System.currentTimeMillis())
            complicationDrawableLeftSlot.draw(canvas, System.currentTimeMillis())
            complicationDrawableBottomSlot.draw(canvas, System.currentTimeMillis())
        }

        private fun drawWatchFace(canvas: Canvas) {
            canvas.drawText(currentTimeString, mWidth - (20f * resources.displayMetrics.density), mCenterY - (2f * resources.displayMetrics.density), mDigitalTimePaint)
            canvas.drawText(currentDateString.toUpperCase(Locale.getDefault()), mWidth - (20f * resources.displayMetrics.density), mCenterY + (12f * resources.displayMetrics.density), mDatePaint)
            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            val innerTickRadius = mCenterX - 10
            val outerTickRadius = mCenterX - 5
            if ((!prideRing && !mAmbient) || (mAmbient && !hideTicksInAmbient)) {
                for (tickIndex in 0..11) {
                    val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
                    val innerX = sin(tickRot.toDouble()).toFloat() * innerTickRadius
                    val innerY = (-cos(tickRot.toDouble())).toFloat() * innerTickRadius
                    val outerX = sin(tickRot.toDouble()).toFloat() * outerTickRadius
                    val outerY = (-cos(tickRot.toDouble())).toFloat() * outerTickRadius
                    canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickPaint)
                }
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            val seconds =
                    mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f
            val secondsRotation = seconds * 6f

            val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f

            val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            canvas.rotate(hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourPaint)

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinutePaint)

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY)
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint)
            }
            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCirclePaint)

            /* Restore the canvas' original orientation. */
            canvas.restore()

            if (!mAmbient && prideRing) {
                val drawable = ContextCompat.getDrawable(this@MyWatchFace, R.drawable.ic_ring3)!!
                val offset = (mWidth*(3f/60)).toInt()
                drawable.setBounds(-offset, -offset, mWidth.toInt()+offset, mHeight.toInt()+offset)
                drawable.draw(canvas)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            currentTimeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
            currentDateString = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Calendar.getInstance().time)
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}