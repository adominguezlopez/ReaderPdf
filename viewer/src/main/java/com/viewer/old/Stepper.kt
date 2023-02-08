package com.viewer.old

import android.annotation.SuppressLint
import android.view.View

class Stepper(protected val mPoster: View, protected val mTask: Runnable) {
    protected var mPending = false
    @SuppressLint("NewApi")
    fun prod() {
        if (!mPending) {
            mPending = true
            mPoster.postOnAnimation {
                mPending = false
                mTask.run()
            }
        }
    }
}