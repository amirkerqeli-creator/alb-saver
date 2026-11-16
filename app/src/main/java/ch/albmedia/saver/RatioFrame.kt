package ch.albmedia.saver

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** FrameLayout mit festem Seitenverhältnis (Höhe = Breite × ratio) und runden Ecken. */
class RatioFrame @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    var ratio = 1.25f
        set(v) { field = v; requestLayout() }

    init { clipToOutline = true }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec((w * ratio).toInt(), MeasureSpec.EXACTLY))
    }
}
