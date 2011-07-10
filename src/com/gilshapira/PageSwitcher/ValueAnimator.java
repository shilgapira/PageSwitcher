package com.gilshapira.PageSwitcher;

import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * {@code ViewAnimator} is a utility class to perform simple value animations via a
 * {@code View} object's message queue. The animations set values directly so repeated
 * calls to {@code start()} can be used to set new destination values without waiting
 * for previous animations to finish. 
 * 
 * @param <T> the type of value to animate.
 *
 * @author Gil Shapira
 */
public abstract class ValueAnimator<T> {

    /** The duration in ms of every animation step */
    private static final int STEP_DURATION = 20;
    
    /** The default interpolator used to calculate values */
    private static final Interpolator DEFAULT_INTERPOLATOR = new DecelerateInterpolator();
    
    /** The default animation is ~150ms */
    private static final int DEFAULT_STEPS = 7;
    
    /** The view we're animating a value on */
    private View mView;
    
    /** The interpolator used to calculate values */
    private Interpolator mInterpolator;
    
    /** The total number of animation steps we'll perform */
    private int mSteps;
    
    /** The initial value the animation started at */
    private T mStart;
    
    /** The destination value the animation should reach at its end */
    private T mDest;
    
    /** The current step in the animation */
    private int mCurrentStep;
    
    /**
     * Creates a new {@code ViewAnimator}.
     * @param view the {@code View} we're animating a value on.
     */
    public ValueAnimator(View view) {
        mView = view;
        mSteps = DEFAULT_STEPS;
        mInterpolator = DEFAULT_INTERPOLATOR;
    }
    
    /**
     * Sets the duration of the animation. Shouldn't be called while an
     * animation is running.
     * @param duration the duration of the animation in ms.
     */
    public void setDuration(int duration) {
        mSteps = (int) Math.ceil(duration / STEP_DURATION);
    }
    
    /**
     * Starts animating the value.
     * @param start the initial value the animation starts at.
     * @param dest the destination value the animation should reach at its end.
     */
    public void start(T start, T dest) {
        mStart = start;
        mDest = dest;
        mCurrentStep = 0;

        schedule(false);
    }
    
    /**
     * Schedules the next step in the animation.
     */
    private void schedule(boolean delayed) {
        Handler handler = mView.getHandler();
        if (handler != null) {
            if (delayed) {
                handler.postDelayed(mStepper, STEP_DURATION);
            } else {
                handler.post(mStepper);
            }
        }
    }
    
    /**
     * Computes a new value at a certain point in the animation.
     * @param start the initial value the animation starts at.
     * @param dest the destination value the animation should reach at its end.
     * @param position a value in the range [0,1] that denotes the current position in the animation.
     * @return the new value that corresponds to the specified position.
     */
    protected abstract T computeValue(T start, T dest, float position);
    
    /**
     * An event that's invoked when a new value should be set.
     * @param value the new value.
     */
    protected abstract void onAnimationStep(T value);
    
    /**
     * An event that's invoked when the animation is done.
     */
    protected void onAnimationDone() {
    }
    
    /**
     * The runnable that's scheduled on the {@code View} object's message queue.
     */
    private Runnable mStepper = new Runnable() {
        
        @Override
        public void run() {
            float position = mInterpolator.getInterpolation(mCurrentStep / (float) mSteps);
            T newValue = computeValue(mStart, mDest, position);
            onAnimationStep(newValue);
            
            if (mCurrentStep < mSteps) {
                mCurrentStep++;
                schedule(true);
            } else {
                onAnimationDone();
            }
        }
        
    };
    
    /**
     * A {code ViewAnimator} for animating int values.
     */
    public static abstract class IntAnimator extends ValueAnimator<Integer> {

        public IntAnimator(View view) {
            super(view);
        }

        @Override
        public Integer computeValue(Integer start, Integer dest, float fraction) {
            return (int) (start + (dest - start) * fraction);
        }

    }
    
    /**
     * A {@code ViewAnimator} for animating float values.
     */
    public static abstract class FloatAnimator extends ValueAnimator<Float> {

        public FloatAnimator(View view) {
            super(view);
        }

        @Override
        public Float computeValue(Float start, Float dest, float fraction) {
            return start + (dest - start) * fraction;
        }
        
    }
    
}
