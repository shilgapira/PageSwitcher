package com.gilshapira.PageSwitcher;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Animation.AnimationListener;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.gilshapira.PageSwitcher.PageSwitcher.PreviewDelegate;
import com.gilshapira.PageSwitcher.ValueAnimator.IntAnimator;

/**
 * A {@code PagePreviewer} is used to show previews of pages.
 *
 * @author Gil Shapira
 */
public final class PagePreviewer extends ViewGroup implements PreviewDelegate {
    
    public static final String LOGTAG = "PagePreviewer";
    
    /** The alpha of the preview frame after it's faded in */
    private static final float DEFAULT_FRAME_ALPHA = 0.9f;
    
    /** The duration of the preview frame's fade in/out animations */
    private static final int FRAME_ANIMATION_DURATION = 300;
    
    /** The frame used as background for the container of the preview views */
    private FrameLayout mFrame;
    
    /** The marker used shown at a preview entry's offset */
    private ImageView mMarker;
    
    /** The axis to draw the various elements above, 0 is bottom of screen */
    private int mAxis;
    
    /** The current view being shown as preview */
    private View mCurrent;
    
    /** The current x coordinate to draw the marker at */
    private int mX;
    
    /** A cached layout params object used when adding previews to the frame */
    private FrameLayout.LayoutParams mLayoutParams;
    
    /** The animation used to hide the frame */
    private Animation mHideAnimation;
    
    /** The animation used to show the frame */
    private Animation mShowAnimation;
    
    /**
     * Creates a new {@code PagePreviewer} object.
     * @param context The Context the view is running in, through which it can
     * access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public PagePreviewer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Creates a new {@code PagePreviewer} object.
     * @param context The Context the view is running in, through which it can
     * access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     * will be applied (beyond what is included in the theme). This may either
     * be an attribute resource, whose value will be retrieved from the current
     * theme, or an explicit style resource.
     */
    public PagePreviewer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // parse XML attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagePreviewer);
        
        Drawable frame = a.getDrawable(R.styleable.PagePreviewer_frame);
        Drawable marker = a.getDrawable(R.styleable.PagePreviewer_marker);
        float alpha = a.getFloat(R.styleable.PagePreviewer_alpha, DEFAULT_FRAME_ALPHA);
        mAxis = (int) a.getDimension(R.styleable.PagePreviewer_axis, 0);
        
        a.recycle();
        
        // initialize state
        mFrame = new FrameLayout(getContext());
        mFrame.setBackgroundDrawable(frame);
        mMarker = new ImageView(getContext());
        mMarker.setBackgroundDrawable(marker);
        mLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        
        // we start with the entire ViewGroup set to Gone, and we'll change this when it's shown
        setVisibility(View.GONE);
        
        // add the subviews to the group, though they aren't shown yet
        addView(mFrame);
        addView(mMarker);
        
        // looks better than the default
        float interpolationFactor = 1.75f;

        mHideAnimation = new AlphaAnimation(alpha, 0);
        mHideAnimation.setAnimationListener(mHideAnimationListener);
        mHideAnimation.setInterpolator(new AccelerateInterpolator(interpolationFactor));
        mHideAnimation.setDuration(FRAME_ANIMATION_DURATION);
        mHideAnimation.setFillEnabled(true);
        mHideAnimation.setFillAfter(true);
        
        mShowAnimation = new AlphaAnimation(0, alpha);
        mShowAnimation.setInterpolator(new DecelerateInterpolator(interpolationFactor));
        mShowAnimation.setDuration(FRAME_ANIMATION_DURATION);
        mShowAnimation.setFillEnabled(true);
        mShowAnimation.setFillAfter(true);
    }
    
    /**
     * Hides the current preview, effectively causing the entire frame to fade out.
     */
    public void hide() {
        if (getVisibility() == View.VISIBLE) {
            hideFrame();
        }
    }

    /**
     * Shows a preview. This either causes the frame to fade in or replaces the
     * current preview if the frame is already being shown.
     */
    public void show(int entry, View preview, int x) {
        if (getVisibility() == View.GONE) {
            showFrame(entry, preview, x);
        } else {
            showPreview(entry, preview, x);
        }
    }

    /**
     * Shows a preview in the frame.
     */
    private void showPreview(int entry, View preview, int x) {
        if (mCurrent == preview) {
            return;
        }
        
        if (mCurrent != null) {
            mFrame.removeAllViews();
        }
        
        mCurrent = preview;
        if (mX != x) {
            mMarkerAnimator.start(mX, x);
        }
        
        mFrame.addView(preview, mLayoutParams);
    }
    
    /**
     * Shows the frame with a new preview in it.
     */
    private void showFrame(int entry, View preview, int x) {
        // we don't want the marker to be animated when the frame appears,
        // so set mX to the appropriate value here
        mX = x;
        
        showPreview(entry, preview, x);
        setVisibility(View.VISIBLE);
        startAnimation(mShowAnimation);
    }
    
    /**
     * Hides the frame.
     */
    private void hideFrame() {
        startAnimation(mHideAnimation);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // layout the frame
        int frameLeft = getPaddingLeft();
        int frameRight = r - l - getPaddingRight() - 1;
        int frameTop = b - t - mAxis - getPaddingBottom() - mFrame.getMeasuredHeight();
        int frameBottom = frameTop + mFrame.getMeasuredHeight() - 1;
        mFrame.layout(frameLeft, frameTop, frameRight, frameBottom);
        
        // layout the marker
        int markerLeft = mX - (mMarker.getMeasuredWidth() / 2);
        int markerRight = markerLeft + mMarker.getMeasuredWidth() - 1;
        int markerTop = b - t - mAxis - getPaddingBottom() - mMarker.getMeasuredHeight();
        int markerBottom = markerTop + mMarker.getMeasuredHeight() - 1;
        mMarker.layout(markerLeft, markerTop, markerRight, markerBottom);
    }
    
    /**
     * An animator that moves the preview marker from entry to entry.
     */
    private IntAnimator mMarkerAnimator = new IntAnimator(this) {
        
        @Override
        protected void onAnimationStep(Integer value) {
            mX = value;
            requestLayout();
        }
        
    };
    
    /**
     * Performs cleanup after the hide animation is finished.
     */
    private AnimationListener mHideAnimationListener = new AnimationListener() {
        
        public void onAnimationStart(Animation animation) {}
        public void onAnimationRepeat(Animation animation) {}
        
        public void onAnimationEnd(Animation animation) {
            mFrame.removeAllViews();
            mCurrent = null;
            setVisibility(View.GONE);
        }
        
    };

}
