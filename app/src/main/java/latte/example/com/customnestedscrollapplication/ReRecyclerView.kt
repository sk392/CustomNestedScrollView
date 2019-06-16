package latte.example.com.customnestedscrollapplication

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

class ReRecyclerView : RecyclerView{
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0)


    override fun onTouchEvent(e: MotionEvent?): Boolean {
        Log.d("RecyclerView","onTouch ${e?.action} / ${e?.x} / ${e?.y}")
        return super.onTouchEvent(e)

    }
}