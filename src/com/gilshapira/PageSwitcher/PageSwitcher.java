package com.gilshapira.PageSwitcher;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.GestureDetector.SimpleOnGestureListener;

import com.gilshapira.PageSwitcher.ValueAnimator.IntAnimator;

/**
 * A {@code PageSwitcher} can be used together with an horizontally scrolling 
 * view to provide simple navigation between pages in that view.    
 *
 * @author Gil Shapira
 */
public class PageSwitcher extends View {
    
    public static final String LOGTAG = "PageSwitcher";
    
    /** The duration to wait before showing the first preview for an entry the user is hovering over */
    public static final int FIRST_PREVIEW_DELAY = 400;
    
    /** The duration to wait before showing subsequent previews after the first one has been shown */
    public static final int OTHER_PREVIEW_DELAY = 75;
    
    /** A value that signifies no entry */
    public static final int NONE = -1;
    
    /** The marker drawable drawn by default for each entry */
    private Drawable mDefaultMarker;
    
    /** An object that represents the draggable handle */
    private Handle mHandle;
    
    /** An object that represents the rail the handle moves on */
    private Rail mRail;
    
    /** The entries in this {@code PageSwitcher} */
    private List<Entry> mEntries;
    
    /** The current selected entry */
    private int mCurrentEntry;
    
    /** An object that tracks taps and long presses */
    private GestureDetector mTapDetector;
    
    /** Whether the handle is being dragged */
    private boolean mDragging;
    
    /** Whether the current drag was paused due to the touch going out of bounds */
    private boolean mDragWasPaused;
    
    /** The x coordinate of the last drag event we received */
    private int mDragLastX;
    
    /** The y coordinate of the last drag event we received */
    private int mDragLastY;
    
    /** The entry the user is currently hovering over */
    private int mHoveredEntry;
    
    /** When the user started to hover over this entry */
    private long mHoverStarted;
    
    /** Whether we've already shown the preview for the current hovered entry */
    private boolean mHoverHandled;
    
    /** Whether we've currently in the middle of a long hover */
    private boolean mLongHover;
    
    /** The id of a {@code PreviewDelegate} that was specified in the XML */
    private int mPendingPreviewDelegate;
    
    /** The object that's used to show previews of entries */
    private PreviewDelegate mPreviewDelegate;
    
    /** An object that's notified when the user interacts with the PageSwitcher */
    private PageSwitcherListener mListener;
    
    /**
     * Creates a new {@code PageSwitcher} object.
     * @param context The Context the view is running in, through which it can
     * access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public PageSwitcher(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Creates a new {@code PageSwitcher} object.
     * @param context The Context the view is running in, through which it can
     * access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     * will be applied (beyond what is included in the theme). This may either
     * be an attribute resource, whose value will be retrieved from the current
     * theme, or an explicit style resource.
     */
    public PageSwitcher(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // parse XML attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PageSwitcher);
        
        mDefaultMarker = a.getDrawable(R.styleable.PageSwitcher_marker);
        Drawable handleDrawable = a.getDrawable(R.styleable.PageSwitcher_handle);
        Drawable railDrawable = a.getDrawable(R.styleable.PageSwitcher_rail);
        int axis = (int) a.getDimension(R.styleable.PageSwitcher_axis, Rail.DEFAULT_AXIS);
        mPendingPreviewDelegate = a.getResourceId(R.styleable.PageSwitcher_previewer, NO_ID);
        
        a.recycle();
        
        // initialize state
        mEntries = new ArrayList<Entry>();
        mCurrentEntry = 0;
        mHoveredEntry = NONE;
        mRail = new Rail(railDrawable, axis);
        mHandle = new Handle(handleDrawable);
        mHandle.moveTo(mCurrentEntry);
        mTapDetector = new GestureDetector(getContext(), mGestureListener);
        setListener(null);
        
        // we start with an empty PreviewDelegate for now. if the XML specifies a
        // delegate to use by resource ID we'll match them on the first onToucnEvent
        // invocation, after the all views were created
        setPreviewDelegate(null);

        // has to be clickable or we won't handle any touch events
        setClickable(true);
    }
    
    /**
     * Looks for the {@code PreviewDelegate} specified in the XML by traversing
     * the view tree, starting from the root view.
     */
    private void initPreviewDelegate() {
        if (mPendingPreviewDelegate != NO_ID) {
            View view = getRootView().findViewById(mPendingPreviewDelegate);
            if (view instanceof PreviewDelegate) {
                setPreviewDelegate((PreviewDelegate) view);
            } else {
                throw new IllegalArgumentException("Invalid preview_delegate attribute");
            }

            mPendingPreviewDelegate = NO_ID;
        }
    }
    
    /**
     * Sets a new {@code PreviewDelegate}.
     * @param previewDelegate the {@code PreviewDelegate} to use.
     */
    public void setPreviewDelegate(PreviewDelegate previewDelegate) {
        if (previewDelegate != null) {
            mPreviewDelegate = previewDelegate;
        } else {
            mPreviewDelegate = new PreviewDelegate() {
                public void show(int entry, View preview, int offset) {}
                public void hide() {}
            };
        }
    }
    
    /**
     * Sets a listener to be notified when the user interacts with the {@code PageSwitcher}.
     * @param listener the listener.
     */
    public void setListener(PageSwitcherListener listener) {
        if (listener != null) {
            mListener = listener;
        } else {
            mListener = new PageSwitcherListener() {
                public void onPageSelected(int page) {}
                public void onPageHovered(int page) {}
            };
        }
    }
    
    /**
     * Adds an entry to the {@code PageSwitcher}.
     * @param entry the entry to add.
     */
    public void addEntry(Entry entry) {
        addEntry(mEntries.size(), entry);
    }
    
    /**
     * Adds an entry to the {@code PageSwitcher}.
     * @param index the index to add the new entry at.
     * @param entry the entry to add.
     */
    public void addEntry(int index, Entry entry) {
        mEntries.add(index, entry);
        refresh();
    }
    
    /**
     * Removes an entry from the {@code PageSwitcher}.
     * @param index the index of the entry to remove.
     */
    public void removeEntry(int index) {
        mEntries.remove(index);
        refresh();
    }
    
    /**
     * Sets a new list of entries.
     * @param entries the new list of entries.
     */
    public void setEntries(List<Entry> entries) {
        mEntries = new ArrayList<Entry>(entries);
        refresh();
    }
    
    /**
     * Refreshes the state of the {@code PageSwitcher}.
     */
    public void refresh() {
        mRail.reposition();
        mHandle.moveTo(mCurrentEntry);
        postInvalidate();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mRail.reposition();
        mHandle.moveTo(mCurrentEntry);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mRail.draw(canvas);
        mHandle.draw(canvas);
    }
    
    public boolean onTouchEvent(MotionEvent event) {
        initPreviewDelegate();
        
        if (mTapDetector.onTouchEvent(event)) {
            return super.onTouchEvent(event);
        }
        
        int x = (int) event.getX();
        int y = (int) event.getY();
        
        if (isEnabled() && isClickable()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mHandle.contains(x, y) && !mHandle.isAnimating()) {
                        mDragging = true;
                        mDragWasPaused = false;
                        mDragLastX = x;
                        mDragLastY = y;
                        invalidate();
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    
                    // set the new entry and notify the listener if it's changed
                    int newEntry = mHandle.findNearestEntry();
                    if (newEntry != mCurrentEntry) {
                        mCurrentEntry = newEntry;
                        mListener.onPageSelected(mCurrentEntry);
                    }
                    mHandle.moveTo(mCurrentEntry, true);
                    
                    // reset hovered state and hide any visible previews
                    mHoveredEntry = NONE;
                    mLongHover = false;
                    mPreviewDelegate.hide();
                    
                    invalidate();
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mDragging && !mEntries.isEmpty()) {
                        // the touch needs to happen inside the rail or the handle,
                        // otherwise we "pause" the drag until it comes back
                        if (mRail.contains(x, y) || mHandle.contains(x, y)) {
                            int deltaX = x - mDragLastX;
                            int deltaY = y - mDragLastY;
                            
                            // only bother doing anything if the touch actually moved
                            if (deltaX != 0 || deltaY != 0) {
                                mDragLastX = x;
                                mDragLastY = y;
                                if (!mDragWasPaused) {
                                    mHandle.moveBy(deltaX, deltaY);
                                } else {
                                    mHandle.moveTo(x, y);
                                }
                                invalidate();
                            }
                        } else {
                            mDragWasPaused = true;
                        }
                        
                        // handle showing and hiding of preview views
                        int nearestEntry = mHandle.findNearestEntry();
                        if (nearestEntry == mHoveredEntry) {
                            // nothing to do if we've shown the preview for this entry already
                            if (!mHoverHandled) {
                                // check if enough time has passed hovering on this very entry
                                if (System.currentTimeMillis() - mHoverStarted > FIRST_PREVIEW_DELAY) {
                                    mLongHover = true;
                                }
                                
                                // if we're already showing previews, then we wait considerably less
                                // before showing previews for subsequent entries
                                if (mLongHover && (System.currentTimeMillis() - mHoverStarted > OTHER_PREVIEW_DELAY)) {
                                    mHoverHandled = true;
                                    mPreviewDelegate.show(mHoveredEntry, mEntries.get(mHoveredEntry).getPreview(), mRail.getMarkerX(mHoveredEntry));
                                    mListener.onPageHovered(mHoveredEntry);
                                }
                            }
                        } else {
                            // user hovering over a different entry than before,
                            // so reset the state and hide the current preview
                            mHoveredEntry = nearestEntry;
                            mHoverStarted = System.currentTimeMillis();
                            mHoverHandled = false;
                        }
                    }
                    break;
            }
            return true;
        }

        return super.onTouchEvent(event);
    }
    
    /**
     * A gesture detector used to track long presses and taps.
     */
    private SimpleOnGestureListener mGestureListener = new SimpleOnGestureListener() {

        @Override
        public void onLongPress(MotionEvent e) {
            int x = (int) e.getX();
            int y = (int) e.getY();
            
            // check that the long press was actually on the rail
            if (!mDragging && mRail.contains(x, y)) {
                // move the handle to the entry the long press was on
                mCurrentEntry = mRail.findNearestEntry(x);
                mHandle.moveTo(mCurrentEntry, true);
                
                // set the state to as if the user started dragging
                mDragging = true;
                mDragWasPaused = true;
                mDragLastX = x;
                mDragLastY = y;
                
                // it's a long press so show a preview asap
                mLongHover = true;
                
                invalidate();
            }
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            int x = (int) e.getX();
            int y = (int) e.getY();
            
            // check that the tap was actually on the rail
            if (!mDragging && mRail.contains(x, y)) {
                // move the handle to the entry the long press was on
                mCurrentEntry = mRail.findNearestEntry(x);
                mHandle.moveTo(mCurrentEntry, true);
                invalidate();
                return true;
            }
            
            return false;
        }
        
    };
    
    //
    // PageSwitcherListener
    //
    
    /**
     * An object that's notified when the user interacts with the {@code PageSwitcher}.
     */
    public interface PageSwitcherListener {
        
        /**
         * An event that's called when a page is selected.
         * @param page the page.
         */
        void onPageSelected(int page);
        
        /**
         * An event that's called when a page is hovered over.
         */
        void onPageHovered(int page);
        
    }
    
    //
    // Rail
    //
    
    /**
     * An object that represents the rail the handle moves on
     */
    private class Rail {
        
        /** Default axis */
        public static final int DEFAULT_AXIS = -1;
        
        /** The default height to use in case the rail Drawable doesn't specify it */
        private static final int DEFAULT_HEIGHT = 20;
        
        /** The axis to draw the markers and handle on */
        private int mAxis;
        
        /** Whether the axis was defined or we should use the default one */
        private boolean mPredefinedAxis;
        
        /** The drawable to draw for the rail */
        private Drawable mDrawable;
        
        /** The amount of space between entries on the rail */
        private float mSpace;
        
        /** Any padding defined in the drawable */
        private Rect mPadding;

        /** The top of the rail drawable */
        private int mTop;
        
        /** The leftmost side of the rail */
        private int mLeft;
        
        /** The width of the rail */
        private int mWidth;
        
        /** The height of the rail drawable */
        private int mHeight;

        /**
         * Creates a new {@code Rail} object.
         * @param drawable the {@code Drawable} to draw for the rail.
         * @param axis the axis to draw the markers and handle on, -1 to use the default.
         */
        public Rail(Drawable drawable, int axis) {
            mDrawable = drawable;
            mPadding = new Rect();
            mAxis = axis;
            mPredefinedAxis = (mAxis != DEFAULT_AXIS);
            reposition();
        }
        
        /**
         * Recalculates all the values. This should be called if the bounds of the view change.
         */
        public void reposition() {
            mDrawable.getPadding(mPadding);
            
            // make sure we have a proper axis valus
            if (!mPredefinedAxis) {
                mAxis = getHeight() / 2;
            }

            // Rail and Handle only support horizontal orientation for now,
            // so the y coordinate values are simple
            mHeight = mDrawable.getIntrinsicHeight();
            if (mHeight == -1) mHeight = DEFAULT_HEIGHT;
            mTop = (getHeight() - mHeight) / 2;
            
            // to make calculations simpler we handle the no pages edge case as if there's one page
            int numEntries = Math.max(1, mEntries.size());
            
            // if there's N entries then the view is divided into N+1 spaces, one 
            // on the left, one on the right, and one between each two entries
            mSpace = getWidth() / (float) (1 + numEntries);
            mWidth = (int) (mSpace * (numEntries - 1));
            mLeft = (int) mSpace;
            
            // in the edge cases of having no pages or one page we pad the rail a bit to give it some size
            if (numEntries <= 1) {
                // the rail is set to be as wide as the margins around it
                int minRailWidth = getWidth() / 3;
                mPadding.left += minRailWidth / 2;
                mPadding.right += minRailWidth / 2;
            }
        }
        
        /**
         * Whether a point is contained in the rail.
         * @param x the x coordinate of the point.
         * @param y the y coordinate of the point.
         * @return true if the point is contained.
         */
        public boolean contains(int x, int y) {
            // how much leeway we allow for the point to be considered on the rail. since
            // the control is currently limited to horizontal orientation we get good results
            // by setting the y leeway to the same height as the view
            int xSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            int ySlop = getHeight();
            
            return (x > mLeft - xSlop) && (x < mLeft + mWidth + xSlop) && (y >= 0 - ySlop) && (y < getHeight() + ySlop);
        }
        
        /**
         * @return the axis of the rail.
         */
        public int getAxis() {
            return mAxis;
        }
        
        /**
         * @return the leftmost x coordinate of the rail, not including padding.
         */
        public int getLeftEdge() {
            return mLeft;
        }
        
        /**
         * @return the rightmost x coordinate of the rail, not including padding.
         */
        public int getRightEdge() {
            return mLeft + mWidth;
        }
        
        /**
         * Finds the entry closest to the specified x coordinate, or the entry we're
         * currently hovering over if we're not far enough away from it.
         * @param x the x coordinate.
         * @return the nearest entry.
         */
        public int findNearestEntry(int x) {
            // we want to find the entries near that x point, so first we shift the
            // x coordinate to offset for the empty space on the left of the rail
            x -= mLeft;
            
            int entry = Math.round(x / mSpace);
            
            if ((entry < 0) || (entry >= mEntries.size())) {
                return NONE;
            }
            
            return entry;
        }
        
        /**
         * Calculates the x coordinate of the marker for the specified entry. 
         * @param entry the entry.
         * @return the x coordinate of the entry.
         */
        public int getMarkerX(int entry) {
            int entryX = mLeft + (int) (entry * mSpace);
            
            return entryX;
        }
        
        /**
         * Draws the rail and the markers.
         */
        public void draw(Canvas canvas) {
            // we give extra room on the sides for the padding
            mDrawable.setBounds(mLeft - mPadding.left, mTop, mLeft + mWidth + mPadding.right, mTop + mHeight);
            mDrawable.draw(canvas);
            
            // draw each entry
            for (int i = 0; i < mEntries.size(); ++i) {
                drawMarker(canvas, i);
            }
        }
        
        /**
         * Draws the marker for a specific entry.
         */
        private void drawMarker(Canvas canvas, int entry) {
            // calculate the center of the marker
            int x = getMarkerX(entry);
            int y = mAxis;
            
            // get the appropriate drawable
            Drawable d = mEntries.get(entry).getMarker();
            if (d == null) d = mDefaultMarker;
            
            // set the drawable state 
            if (entry == mCurrentEntry) {
                d.setState(SELECTED_STATE_SET);
            } else {
                d.setState(EMPTY_STATE_SET);
            }
            
            // position the drawable where we want to draw it
            setDrawableBoundsCentered(d, x, y);
            
            d.draw(canvas);
        }
        
    }
    
    //
    // Handle
    //
    
    /**
     * An object that represents the draggable handle. 
     */
    private class Handle {

        /** The radius around the center of the handle in which we intercept drag events */
        private static final int TOUCH_AREA = 35;
        
        /** The x coordinate of the handle */
        private int mX;
        
        /** The y coordinate of the handle */
        private int mY;
        
        /** The handle drawable shown  */
        private Drawable mDrawable;
        
        /** Whether the handle is being animated */
        private boolean mAnimating;
        
        /**
         * Creates a new {@code Handle} object.
         * @param drawable the drawable to use for the handle.
         */
        public Handle(Drawable drawable) {
            mDrawable = drawable;
            mAnimating = false;
        }
        
        /**
         * @return whether the handle is being animated.
         */
        public boolean isAnimating() {
            return mAnimating;
        }
        
        /**
         * Whether a point is contained in the handle's touch area.
         * @param x the x coordinate of the point.
         * @param y the y coordinate of the point.
         * @return if the point is contained.
         */
        public boolean contains(int x, int y) {
            int distance = distance(x, y, mX, mY);
            float touchArea = TOUCH_AREA * getResources().getDisplayMetrics().density;
            return (distance <= touchArea);
        }
        
        /**
         * Moves the handle by the specified amount of pixels.
         */
        public void moveBy(int deltaX, int deltaY) {
            mX += deltaX;
            mY += deltaY;
            keepOnRail();
        }
        
        /**
         * Moves the handle to the specified x and y coordinates.
         */
        public void moveTo(int x, int y) {
            mX = x;
            mY = y;
            keepOnRail();
        }
        
        /**
         * Moves the handle to the specified entry, without animation.
         */
        public void moveTo(int entry) {
            moveTo(entry, false);
        }
        
        /**
         * Moves the handle to the specified entry, optionally animating the move.
         */
        public void moveTo(int entry, boolean animated) {
            final int dest = mRail.getMarkerX(entry);
            
            if (animated) {
                mAnimator.start(mX, dest);
            } else {
                moveTo(dest, mY);
            }
        }
        
        /**
         * Makes sure the handle is on the rail.
         */
        private void keepOnRail() {
            mY = mRail.getAxis();
            
            if (mX < mRail.getLeftEdge()) mX = mRail.getLeftEdge();
            if (mX > mRail.getRightEdge()) mX = mRail.getRightEdge();
        }
        
        /**
         * @return the entry that's nearest to the handle.
         */
        public int findNearestEntry() {
            int nearestEntry = mRail.findNearestEntry(mX);
            
            // once we're hovering over an entry, we need to travel a little more away
            // from it before some other entry is considered closer
            if (mHoveredEntry != NONE) {
                int distanceToNearest = mX - mRail.getMarkerX(nearestEntry);
                int distanceToHovered = mX - mRail.getMarkerX(mHoveredEntry);

                if (Math.abs(distanceToHovered) < 3 * Math.abs(distanceToNearest)) {
                    return mHoveredEntry;
                }
            }
            
            return nearestEntry;
        }
        
        /**
         * Draws the handle.
         */
        public void draw(Canvas canvas) {
            // don't draw the handle if there's no pages at all
            if (mEntries.isEmpty()) {
                return;
            }
            
            setDrawableBoundsCentered(mDrawable, mX, mY);
            
            // update drawable states depending on whether we're dragging the handle
            updateDrawableState();
            
            mDrawable.draw(canvas);
        }
        
        /**
         * Updates the drawable state so it change when touched.
         */
        private void updateDrawableState() {
            if (mDragging) {
                mDrawable.setState(PRESSED_ENABLED_STATE_SET);
            } else if (isEnabled()) {
                mDrawable.setState(ENABLED_STATE_SET);
            } else {
                mDrawable.setState(EMPTY_STATE_SET);
            }
        }
        
        /**
         * An animator that moves the handle.
         */
        private IntAnimator mAnimator = new IntAnimator(PageSwitcher.this) {
            @Override
            protected void onAnimationStep(Integer value) {
                moveTo(value, mY);
                invalidate();
            }
        };

    }
    
    //
    // PreviewDelegate
    //
    
    /**
     * An object that's reponsible to showing previews for entries. 
     */
    public interface PreviewDelegate {
        
        /**
         * Shows an entry's preview.
         * @param entry the index of the entry.
         * @param preview the preview to show.
         * @param x the x coordinate of the entry.
         */
        void show(int entry, View preview, int x);
        
        /**
         * Hides any visible previews.
         */
        void hide();
        
    }
    
    //
    // Entries
    //

    /**
     * A {@code PageSwitcher} entry. 
     */
    public interface Entry {
        
        /**
         * @return the marker drawable drawn for this entry, or {@code null} for the default.
         */
        Drawable getMarker();
        
        /**
         * @return the preview shown for this entry, or {@code null} for none.
         */
        View getPreview();
        
    }

    /**
     * A simple {@code Entry} implementation that always returns the same marker and preview.
     */
    public static class SimpleEntry implements Entry {
        
        /** The marker drawable drawn for this entry */
        private Drawable mMarker;
        
        /** The preview shown for this entry */
        private View mPreview;
        
        /**
         * Creates a new {@code SimpleEntry} object.
         */
        public SimpleEntry() {
            this(null, null);
        }

        /**
         * Creates a new {@code SimpleEntry} object.
         * @param preview The preview shown for this entry, or {@code null} for none.
         */
        public SimpleEntry(View preview) {
            this(null, preview);
        }

        /**
         * Creates a new {@code SimpleEntry} object.
         * @param marker The marker drawable drawn for this entry, or {@code null} for the default.
         * @param preview The preview shown for this entry, or {@code null} for none.
         */
        public SimpleEntry(Drawable marker, View preview) {
            mMarker = marker;
            mPreview = preview;
        }

        public Drawable getMarker() {
            return mMarker;
        }

        public View getPreview() {
            return mPreview;
        }
        
    }
    
    //
    // Helpers
    //
    
    /** The default width/height to use in case drawables don't specify them */
    private static final int DEFAULT_DRAWABLE_DIMENSION = 44;
    
    /**
     * Sets a {@code Drawable}'s bounds so it's centered around the specified coordinates.
     * @param d the {@code Drawable}.
     * @param x the x coordinate.
     * @param y the y coordinate.
     */
    private static void setDrawableBoundsCentered(Drawable d, int x, int y) {
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w == -1) w = DEFAULT_DRAWABLE_DIMENSION;
        if (h == -1) h = DEFAULT_DRAWABLE_DIMENSION;
        
        int left = x - (w / 2);
        int top = y - (h / 2);
        int right = x + (int) Math.ceil(w / 2.0f) - 1;
        int bottom = y + (int) Math.ceil(h / 2.0f) - 1;
        
        d.setBounds(left, top, right, bottom);
    }
    
    /**
     * @return distance between two points.
     */
    private static int distance(int x1, int y1, int x2, int y2) {
        return (int) Math.sqrt(((x1 - x2)*(x1 - x2)) + ((y1 - y2)*(y1 - y2)));
    }
    
}
