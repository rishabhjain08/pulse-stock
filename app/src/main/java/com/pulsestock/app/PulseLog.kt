package com.pulsestock.app

import android.util.Log

/**
 * Debug-only logging. Controlled by BuildConfig.VERBOSE_LOGGING:
 *   debug builds  → true  (logs appear in logcat)
 *   release builds → false (zero log output, zero overhead)
 *
 * To remove all logging before a production release: grep the codebase for "PulseLog".
 * All call-sites are in-development instrumentation and can be deleted together.
 */
object PulseLog {
    private const val ROOT_TAG = "PulseStock"

    fun d(tag: String, msg: String) {
        if (BuildConfig.VERBOSE_LOGGING) Log.d("$ROOT_TAG/$tag", msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.VERBOSE_LOGGING) Log.w("$ROOT_TAG/$tag", msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.VERBOSE_LOGGING) Log.e("$ROOT_TAG/$tag", msg, t)
    }
}
