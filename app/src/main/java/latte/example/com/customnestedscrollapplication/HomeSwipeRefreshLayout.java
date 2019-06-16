package latte.example.com.customnestedscrollapplication;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * Created by tommykim on 2016. 2. 22..
 */
public class HomeSwipeRefreshLayout extends SwipeRefreshLayout {

    public HomeSwipeRefreshLayout(Context context) {
        super(context);
    }

    public HomeSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            parent.requestDisallowInterceptTouchEvent(b);
        }
    }
}
