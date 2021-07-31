package sushi.hardcore.aira.adapters

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager

//https://stackoverflow.com/questions/36724898/notifyitemchanged-make-the-recyclerview-scroll-and-jump-to-up

class FuckRecyclerView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
): LinearLayoutManager(context, attrs, defStyleAttr, defStyleRes) {
    override fun isAutoMeasureEnabled(): Boolean {
        return false
    }
}