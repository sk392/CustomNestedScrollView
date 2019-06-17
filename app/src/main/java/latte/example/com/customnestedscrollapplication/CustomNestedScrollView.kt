package latte.example.com.customnestedscrollapplication

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.core.view.*
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import java.util.*

class CustomNestedScrollView : FrameLayout, NestedScrollingParent2, NestedScrollingChild2 {


    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs, 0) {

        mChildHelper.isNestedScrollingEnabled = true
    }

    companion object {
        private const val INVALID_POINTER = -1
    }

    private val mChildHelper = NestedScrollingChildHelper(this)
    private val mParentHelper = NestedScrollingParentHelper(this)

    private var firstChildType = NestedScrollViewChildType.Scrolling

    private var mActivePointerId = INVALID_POINTER
    private var configuration = ViewConfiguration.get(context)
    private val velocityTracker: VelocityTracker by lazy(LazyThreadSafetyMode.NONE) { VelocityTracker.obtain() }

    private val mScrollOffset = IntArray(2)
    private val mScrollConsumed = IntArray(2)
    private var nestedScrollOffset: Float = 0f
    private var childNestedScrollY: Int = 0
    private var mNestedYOffset: Int = 0
    private var mIsBeingDragged = false
    private var mTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var mMinimumVelocity: Float = configuration.scaledMinimumFlingVelocity.toFloat()
    private var mMaximumVelocity: Float = configuration.scaledMaximumFlingVelocity.toFloat()
    private var mLastMotionY: Int = 0
    private var hasBeenNestedScrolled: Boolean = false
    private var isNestedScrolled = false
    private var velocityTrackerOffsetY :Float = 0f

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

        var childTop = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.GONE) {
                val childWidth = child.measuredWidth
                val childHeight = if (i == childCount - 1 && child.top == 0) {
                    child.measuredHeight - findPinViewHeight()
                } else {
                    child.measuredHeight
                }
                child.layout(0, childTop, childWidth, childTop + childHeight)
                childTop += childHeight
            }
        }
    }

    //최초 onLayout에서 pin된 높이만큼 계산을 못해서 직접 가져와서 계산
    private fun findPinViewHeight(): Int {
        val viewQueue = ArrayDeque<ViewGroup>()

        if (parent is ViewGroup) viewQueue.push(parent as ViewGroup)

        while (viewQueue.isNotEmpty()) {
            val viewGroup = viewQueue.poll()
            if (viewGroup.childCount > 0) {
                for (i in 0 until childCount) {
                    val childView = viewGroup.getChildAt(i)
                    if (childView is CollapsingToolbarLayout
                        && childView.layoutParams is AppBarLayout.LayoutParams
                        && (childView.layoutParams as AppBarLayout.LayoutParams).scrollFlags and AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0
                    ) {
                        return childView.minimumHeight
                    }
//                    if(childView is Toolbar
//                        && childView.layoutParams is CollapsingToolbarLayout.LayoutParams
//                        && (childView.layoutParams as CollapsingToolbarLayout.LayoutParams).collapseMode == CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN){
//                        return childView.height
//                    }
                    if (childView is ViewGroup) viewQueue.push(childView)
                }
            }
        }

        return 0
        //((childView.parent as CollapsingToolbarLayout).layoutParams as AppBarLayout.LayoutParams).scrollFlags and AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED != 0
        //(((((parent as CoordinatorLayout).getChildAt(0) as ViewGroup).getChildAt(0) as CollapsingToolbarLayout).toolbar as Toolbar).layoutParams as CollapsingToolbarLayout.LayoutParams).collapseMode == CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
        //(((parent as CoordinatorLayout).getChildAt(0) as ViewGroup).getChildAt(0) as CollapsingToolbarLayout).toolbar.height
    }

    //NestedScrollingChild2

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return mChildHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type
        )
    }


    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return mChildHelper.startNestedScroll(axes, type)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }


    override fun stopNestedScroll(type: Int) {
        mChildHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return mChildHelper.hasNestedScrollingParent(type)
    }

    // NestedScrollingParent2

    override fun startNestedScroll(axes: Int): Boolean {
        return mChildHelper.startNestedScroll(axes)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        //touch UP했을 경우에  stopNestedScroll이 호출되는데, Type NonTouch의 형태로 스크롤이 들어오면
        if (!hasNestedScrollingParent(type)) {
            startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type)
        }
        //위로 올릴 때 + 내릴 때 -
        //consumed[0] =x [1] =y

        when (firstChildType) {
            NestedScrollViewChildType.Pin -> {

            }
            NestedScrollViewChildType.Scrolling -> {
                dispatchNestedPreScroll(dx, dy, consumed, null, type)
                if (childCount >= 2) {
                    val oldScrollY = scrollY

                    val adjustScrollY = dy - consumed[1]
                    val maxScrollY = getFirstChildHeight()
                    if (adjustScrollY > 0) {
                        if (adjustScrollY + scrollY < maxScrollY) {
                            scrollBy(0, adjustScrollY)
                        } else {
                            scrollBy(0, maxScrollY - scrollY)
                        }
                    } else {

                    }
                    consumed[1] += scrollY - oldScrollY
                }
            }
        }

    }

    override fun onStopNestedScroll(target: View, type: Int) {
        mParentHelper.onStopNestedScroll(target, type)
        stopNestedScroll(type)
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        mParentHelper.onNestedScrollAccepted(child, target, axes, type)
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        //올라갈 떄 + 내려갈 때 -

        if (!hasNestedScrollingParent(type)) {
            startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type)
        }
        when (firstChildType) {
            NestedScrollViewChildType.Pin -> {

            }
            NestedScrollViewChildType.Scrolling -> {
                val oldScrollY = scrollY
                if (childCount >= 2) {
                    val maxScrollY = getChildAt(0).height
                    if (dyUnconsumed > 0) {
                        //올라갈 때
                        //scrollY < top
                        if (scrollY + dyUnconsumed < maxScrollY) {
                            scrollBy(0, dyUnconsumed)
                        } else {
                            scrollBy(0, maxScrollY - scrollY)
                        }
                    } else {
                        //내려갈 때
                        //scrollY > 0
                        if (scrollY + dyUnconsumed > 0) {
                            scrollBy(0, dyUnconsumed)
                        } else {
                            scrollBy(0, -scrollY)
                        }
                    }
                }

                val myConsumed = scrollY - oldScrollY
                val myUnconsumed = dyUnconsumed - myConsumed

                dispatchNestedScroll(
                    0, myConsumed, 0, myUnconsumed, null,
                    type
                )
            }
        }
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        ev ?: return super.onTouchEvent(ev)
        // velocity Tracker Event
        val vtEvent = MotionEvent.obtain(ev)

        val actionMasked = ev.actionMasked

        var eventAddedToVelocityTracker = false
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            nestedScrollOffset = 0f
            velocityTrackerOffsetY = 0f
        }
        vtEvent.offsetLocation(0f, velocityTrackerOffsetY)

        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {

                mLastMotionY = ev.y.toInt()
                mActivePointerId = ev.getPointerId(0)
                nestedScrollingFlinger.stopFling()
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mActivePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    return false
                }

                val pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex == -1) {
                    return false
                }

                val y = ev.getY(pointerIndex).toInt()

                var dy = mLastMotionY - y
                if (Math.abs(dy) > mTouchSlop && nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL == 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    mIsBeingDragged = true
                    velocityTracker.addMovement(ev)
                }
                if (dispatchNestedPreScroll(0, dy, mScrollConsumed, mScrollOffset, ViewCompat.TYPE_TOUCH)) {
                    dy -= mScrollConsumed[1]
                    hasBeenNestedScrolled = true
                    vtEvent.offsetLocation(0f, mScrollOffset[1].toFloat())
                    velocityTrackerOffsetY += mScrollOffset[1].toFloat()
                    mNestedYOffset += mScrollOffset[1]
                }


                if (mIsBeingDragged) {
                    //네스티드 프리스크롤한 후에 시작되는 y값을 계산.
                    mLastMotionY = y - mScrollOffset[1]
                    val oldY = scrollY
                    overScroll(dy)

                    val scrollDy = scrollY - oldY
                    val unconsumedY = dy - scrollDy
                    if (childCount > 1) {
                        if ((unconsumedY > 0 && scrollY >= getChildAt(0).height)
                            || (unconsumedY < 0 && scrollY > 0)
                        ) {
                            isNestedScrolled = true
                            childNestedScrollY += unconsumedY
                            getChildAt(1).scrollBy(0, unconsumedY)
                        } else {
                            //내려갈 때
                            if (dispatchNestedScroll(
                                    0, scrollDy, 0, unconsumedY,
                                    mScrollOffset, ViewCompat.TYPE_TOUCH
                                )
                            ) {
                                hasBeenNestedScrolled = true
                                mLastMotionY -= mScrollOffset[1]
                                vtEvent.offsetLocation(0f, mScrollOffset[1].toFloat())
                                velocityTrackerOffsetY += mScrollOffset[1].toFloat()
                                mNestedYOffset += mScrollOffset[1]
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {

                velocityTracker.addMovement(vtEvent)
                eventAddedToVelocityTracker = true
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity)
                nestedScrollingFlinger.startFling(-velocityTracker.yVelocity, hasBeenNestedScrolled)
                mActivePointerId = INVALID_POINTER
                endDrag()
            }
            MotionEvent.ACTION_CANCEL -> {
                mActivePointerId = INVALID_POINTER
                endDrag()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                mLastMotionY = ev.getY(index).toInt()
                mActivePointerId = ev.getPointerId(index)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
                mLastMotionY = ev.getY(ev.findPointerIndex(mActivePointerId)).toInt()
            }
        }
        if (!eventAddedToVelocityTracker) {
            velocityTracker.addMovement(vtEvent)
        }
        vtEvent.recycle()
        return true
    }

    private fun overScroll(dy: Int) {
        val scrollRangeY = getFirstChildHeight()

        if (childCount > 1 && getChildAt(1).scrollY > 0 && dy < 0) {
            return
        }
        var newScrollY = scrollY + dy

        if (newScrollY > scrollRangeY) {
            newScrollY = scrollRangeY
        } else if (newScrollY < 0) {
            newScrollY = 0
        }

        scrollTo(0, newScrollY)
    }

    private fun getFirstChildHeight(): Int {
        var childSize = 0
        if (childCount > 0) {
            val child = getChildAt(0)
            val lp = child.layoutParams as FrameLayout.LayoutParams
            childSize = child.height + lp.topMargin + lp.bottomMargin
        }
        return childSize
    }

    fun setFirstChildType(type: NestedScrollViewChildType) {
        firstChildType = type
    }

    private fun endDrag() {
        mIsBeingDragged = false
        velocityTracker.clear()
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mLastMotionY = ev.getY(newPointerIndex).toInt()
            mActivePointerId = ev.getPointerId(newPointerIndex)
        }
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

        fun startFling(velocityY: Float, hasBeenNestedScrolled: Boolean) {
            if (Math.abs(velocityY) > mMinimumVelocity && (hasBeenNestedScrolled || velocityY < 0)) {

                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH)

                lastFlingY = 0
                scroller.fling(
                    0, 0, 0, velocityY.toInt(),
                    Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE
                )
                postOnAnimation()
            }
        }

        override fun run() {
            flingAfterNestedScroll()
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

                val oldScrollY = scrollY
                overScroll(dy)

                val dyConsumed = scrollY - oldScrollY
                var dyUnconsumed = dy - dyConsumed
                Log.d("FlingFling", "scrollY $scrollY dyUnconsumed $dyUnconsumed")
                if (childCount > 1 && (dyUnconsumed > 0 && scrollY >= getFirstChildHeight())
                    || (dyUnconsumed < 0 && scrollY > 0)
                ) {
                    getChildAt(1).scrollBy(0, dyUnconsumed)
                    dyUnconsumed = 0
                }

                if (dispatchNestedScroll(0, dyConsumed, 0, dyUnconsumed, scrollOffset, ViewCompat.TYPE_NON_TOUCH)) {
                    dy += scrollOffset[1]
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
            ViewCompat.postOnAnimation(this@CustomNestedScrollView, this)
        }

    }

}

enum class NestedScrollViewChildType {
    Pin,
    Scrolling
}