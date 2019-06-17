package latte.example.com.customnestedscrollapplication

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.Interpolator
import android.webkit.WebView
import android.widget.OverScroller
import androidx.core.view.NestedScrollingChild2
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat


open class BaseWebView : WebView, NestedScrollingChild2 {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var internalTouch = false
    var onScrollChangedCallback: ((left: Int, top: Int, oldLeft: Int, oldTop: Int) -> Unit)? = null
    var isBlockedScrollChangeWhenTouchDown = false

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isBlockedScrollChangeWhenTouchDown = false
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                internalTouch = false
            }
        }
        scrollIdleChecker.onTouchEventForScrollCheck(event)
        return onTouchEventForNestedScrolling(event)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!isBlockedScrollChangeWhenTouchDown) {
            onScrollChangedCallback?.invoke(l, t, oldl, oldt)
        }
        scrollIdleChecker.onScrollChangedForScrollCheck(l, t, oldl, oldt)
    }


    /**
     * Nested Scrolling Support
     */

    private val childHelper: NestedScrollingChildHelper by lazy(LazyThreadSafetyMode.NONE) { NestedScrollingChildHelper(this) }

    private val velocityTracker: VelocityTracker by lazy(LazyThreadSafetyMode.NONE) { VelocityTracker.obtain() }
    private var velocityTrackerOffsetY: Float = 0f

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity: Int = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity: Int = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private val scrollConsumed = IntArray(2)
    private val scrollOffset = IntArray(2)
    private val nestedScrollOffsets = FloatArray(2)

    private var lastTouch = PointF()

    private enum class TouchStatus {
        CHECKING,
        VERTICAL_DRAGGING,
        HORIZONTAL_DRAGGING
    }

    private var touchStatus = TouchStatus.CHECKING

    private var consumed = false
    private var hasBeenNestedScrolled = false
    private var isNestedScrolled = false
    private var dyUnconsumed = 0

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    override fun hasNestedScrollingParent(): Boolean {
        return childHelper.hasNestedScrollingParent()
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return childHelper.hasNestedScrollingParent(type)
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return childHelper.startNestedScroll(axes)
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return childHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll() {
        childHelper.stopNestedScroll()
    }

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int): Boolean {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    private fun onTouchEventForNestedScrolling(event: MotionEvent) : Boolean {
        var eventAddedToVelocityTracker = false

        /** touch event */
        val tEvent = MotionEvent.obtain(event)
        /** velocity tracker event */
        val vtEvent = MotionEvent.obtain(event)
        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            nestedScrollOffsets[0] = 0f
            nestedScrollOffsets[1] = 0f
            velocityTrackerOffsetY = 0f
            nestedScrollingFlinger.stopFling()
        }
        tEvent.offsetLocation(nestedScrollOffsets[0], nestedScrollOffsets[1])
        vtEvent.offsetLocation(0f, velocityTrackerOffsetY)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.x = event.x
                lastTouch.y = event.y
                isNestedScrollingEnabled = true
                touchStatus = TouchStatus.CHECKING
            }
            MotionEvent.ACTION_MOVE -> {
                val x = tEvent.x
                val y = tEvent.y
                val dx = lastTouch.x - x
                var dy = lastTouch.y - y

                when (touchStatus) {
                    TouchStatus.CHECKING -> {
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            val degree = Math.abs(Math.toDegrees(Math.atan2(-dy.toDouble(), -dx.toDouble())))
                            touchStatus = if (degree in 45.0 .. 135.0) {
                                TouchStatus.VERTICAL_DRAGGING
                            } else {
                                TouchStatus.HORIZONTAL_DRAGGING
                            }
                        }

                        tEvent.offsetLocation(dx, dy)
                        if (touchStatus != TouchStatus.CHECKING) {
                            nestedScrollOffsets[0] = dx
                            nestedScrollOffsets[1] = dy
                            if (touchStatus == TouchStatus.VERTICAL_DRAGGING) {
                                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                    }
                    TouchStatus.HORIZONTAL_DRAGGING -> {
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            val degree = Math.abs(Math.toDegrees(Math.atan2(-dy.toDouble(), -dx.toDouble())))
                            lastTouch.x = x
                            lastTouch.y = y
                            if (degree in 45.0 .. 135.0) {
                                touchStatus = TouchStatus.VERTICAL_DRAGGING
                                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                    }
                    TouchStatus.VERTICAL_DRAGGING -> {
                        isNestedScrolled = false
                        dyUnconsumed = dy.toInt()
                        lastTouch.x = x
                        lastTouch.y = y
                        if (dispatchNestedPreScroll(0, dyUnconsumed, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                            dyUnconsumed -= scrollConsumed[1]
                            dy -= dyUnconsumed
                            isNestedScrolled = true
                        }

                        if (dyUnconsumed + scrollY > 0) {
                            dyUnconsumed = 0
                        }

                        if (dispatchNestedScroll(0, 0, 0, dyUnconsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                            if (scrollOffset[0] != 0 || scrollOffset[1] != 0) {

                                dy -= (dy.toInt() + scrollOffset[1])
                                isNestedScrolled = true
                            }
                        }

                        if (isNestedScrolled) {
                            vtEvent.offsetLocation(0f, -dy)
                            velocityTrackerOffsetY -= dy
                            lastTouch.y += dy
                            super.onTouchEvent(
                                MotionEvent.obtain(tEvent.eventTime, tEvent.eventTime,
                                    MotionEvent.ACTION_CANCEL, tEvent.x, tEvent.y, tEvent.metaState))
                            consumed = true
                            hasBeenNestedScrolled = true
                        } else {
                            if (consumed) {
                                super.onTouchEvent(
                                    MotionEvent.obtain(tEvent.eventTime, tEvent.eventTime,
                                        MotionEvent.ACTION_DOWN, tEvent.x, tEvent.y, tEvent.metaState))
                            }
                            consumed = false
                        }

                    }
                }

            }
            MotionEvent.ACTION_UP -> {
                if (hasBeenNestedScrolled) {
                    super.onTouchEvent(
                        MotionEvent.obtain(tEvent.eventTime, tEvent.eventTime,
                            MotionEvent.ACTION_CANCEL, tEvent.x, tEvent.y, tEvent.metaState))
                }
                velocityTracker.addMovement(vtEvent)
                eventAddedToVelocityTracker = true
                velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                nestedScrollingFlinger.startFling(-velocityTracker.yVelocity, hasBeenNestedScrolled)
                endTouch()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (tEvent.pointerCount > 1 && tEvent.actionIndex == 0) {
                    lastTouch.x = tEvent.getX(1)
                    lastTouch.y = tEvent.getY(1)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                endTouch()
            }
        }

        if (!eventAddedToVelocityTracker) {
            velocityTracker.addMovement(vtEvent)
        }

        return consumed || super.onTouchEvent(tEvent)
    }

    private fun endTouch() {
        velocityTracker.clear()
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
        touchStatus = TouchStatus.CHECKING
        consumed = false
        hasBeenNestedScrolled = false
    }

    private val nestedScrollingFlinger = object : Runnable {

        private val scrollConsumed = IntArray(2)
        private val scrollOffset = IntArray(2)
        private var lastFlingY: Int = 0
        private val scroller: OverScroller = OverScroller(context, Interpolator { input ->
            var t = input
            t -= 1.0f
            t * t * t * t * t + 1.0f
        })
        private var hasBeenNestedScrolled: Boolean = false

        fun startFling(velocityY: Float, hasBeenNestedScrolled: Boolean) {
            if (Math.abs(velocityY) > minFlingVelocity && (hasBeenNestedScrolled || velocityY < 0)) {
                this.hasBeenNestedScrolled = hasBeenNestedScrolled

                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH)

                lastFlingY = 0
                scroller.fling(0, 0, 0, velocityY.toInt(),
                    Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE)
                postOnAnimation()
            }
        }

        override fun run() {
            if (hasBeenNestedScrolled) {
                flingAfterNestedScroll()
            } else {
                flingAfterWebViewScroll()
            }
        }

        private fun flingAfterNestedScroll() {
            val scroller = this.scroller
            if (scroller.isOverScrolled) {
                scroller.abortAnimation()
            }
            if (scroller.computeScrollOffset()) {
                val scrollConsumed = this.scrollConsumed
                val scrollOffset = this.scrollOffset
                val y = scroller.currY
                var dy = y - lastFlingY
                lastFlingY = y

                if (dispatchNestedPreScroll(0, dy, scrollConsumed, null, ViewCompat.TYPE_NON_TOUCH)) {
                    dy -= scrollConsumed[1]
                }

                if (dispatchNestedScroll(0, 0, 0, dy, scrollOffset, ViewCompat.TYPE_NON_TOUCH)) {
                    dy += scrollOffset[1]
                }

                val scrollToY = scrollY + dy
                if (dy < 0 && scrollToY < 0) {
                    scrollTo(0, 0)
                    scroller.abortAnimation()
                } else if (dy > 0 && scrollToY > computeVerticalScrollRange()) {
                    scrollTo(0, computeVerticalScrollRange())
                    scroller.abortAnimation()
                } else {
                    scrollBy(0, dy)
                }

                if (scroller.isFinished) {
                    endFling()
                } else {
                    postOnAnimation()
                }
            } else {
                endFling()
            }
        }

        private fun flingAfterWebViewScroll() {
            val scroller = this.scroller
            if (scroller.isOverScrolled) {
                scroller.abortAnimation()
            }
            if (scroller.computeScrollOffset()) {
                val scrollConsumed = this.scrollConsumed
                val scrollOffset = this.scrollOffset
                val y = scroller.currY
                val dy = y - lastFlingY
                lastFlingY = y

                var scrollToY = scrollY + dy
                if (dy < 0 && scrollToY < 0) {
                    if (dispatchNestedPreScroll(0, scrollToY, scrollConsumed, null, ViewCompat.TYPE_NON_TOUCH)) {
                        scrollToY -= scrollConsumed[1]
                    }

                    if (dispatchNestedScroll(0, 0, 0, scrollToY, scrollOffset, ViewCompat.TYPE_NON_TOUCH)) {
                        scrollToY += scrollOffset[1]
                    }
                }

                if (scrollToY == dy) {
                    scroller.abortAnimation()
                }

                if (scroller.isFinished) {
                    endFling()
                } else {
                    postOnAnimation()
                }
            } else {
                endFling()
            }
        }

        fun stopFling() {
            scroller.abortAnimation()
            endFling()
        }

        fun isFinished() = scroller.isFinished

        private fun endFling() {
            removeCallbacks(this)
            stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
        }

        private fun postOnAnimation() {
            removeCallbacks(this)
            ViewCompat.postOnAnimation(this@BaseWebView, this)
        }

    }

    /**
     *  Scroll Idle Check Support
     */

    var onScrollIdleCallback: (() -> Unit)? = null

    private val scrollIdleChecker = object : Runnable {

        private var isInTouch: Boolean = false
        private var lastScrollX: Int = 0
        private var lastScrollY: Int = 0
        private var isScrollingBeingChecked: Boolean = false

        override fun run() {
            if (!isScrollingBeingChecked) return
            if (nestedScrollingFlinger.isFinished() && lastScrollX == scrollX && lastScrollY == scrollY) {
                isScrollingBeingChecked = false
                dispatchScrollIdle()
            } else {
                startScrollCheck()
            }
        }

        fun onScrollChangedForScrollCheck(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
            if (isInTouch || isScrollingBeingChecked) return
            if (left != oldLeft || top != oldTop) {
                isScrollingBeingChecked = true
                startScrollCheck()
            }
        }

        fun onTouchEventForScrollCheck(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isInTouch = true
                    isScrollingBeingChecked  = false
                    removeCallbacks(this)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isInTouch = false
                    isScrollingBeingChecked = true
                    startScrollCheck()
                }
            }
        }

        private fun startScrollCheck() {
            lastScrollX = scrollX
            lastScrollY = scrollY
            postDelayed(this, 100L)
        }

        private fun dispatchScrollIdle() {
            onScrollIdleCallback?.invoke()
        }
    }

}