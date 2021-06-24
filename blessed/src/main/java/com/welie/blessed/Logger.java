package com.welie.blessed;

import android.util.Log;
import timber.log.Timber;

class Logger {

    static boolean enabled = true;

    /**
     * Send a verbose log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void v(String tag, String msg) {
        triggerLogger(Log.VERBOSE, tag, msg);
    }

    /** Log an verbose message with optional format args. */
    public static void v(String tag, String msg, Object... args) {
        triggerLogger(Log.VERBOSE, tag, msg, args);
    }

    /**
     * Send a debug log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void d(String tag, String msg) {
        triggerLogger(Log.DEBUG, tag, msg);
    }

    /** Log an debug message with optional format args. */
    public static void d(String tag, String msg, Object... args) {
        triggerLogger(Log.DEBUG, tag, msg, args);
    }

    /**
     * Send an info log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void i(String tag, String msg) {
        triggerLogger(Log.INFO, tag, msg);
    }

    /** Log an info message with optional format args. */
    public static void i(String tag, String msg, Object... args) {
        triggerLogger(Log.INFO, tag, msg, args);
    }

    /**
     * Send a warn log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void w(String tag, String msg) {
        triggerLogger(Log.WARN, tag, msg);
    }

    /** Log an warn message with optional format args. */
    public static void w(String tag, String msg, Object... args) {
        triggerLogger(Log.WARN, tag, msg, args);
    }

    /**
     * Send an error log message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void e(String tag, String msg) {
        triggerLogger(Log.ERROR, tag, msg);
    }

    /** Log an error message with optional format args. */
    public static void e(String tag, String msg, Object... args) {
        triggerLogger(Log.ERROR, tag, msg, args);
    }

    /**
     * What a Terrible Failure: Report a condition that should never happen.
     * The error will always be logged at level severe with the call stack.
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     */
    public static void wtf(String tag, String msg) {
        triggerLogger(Log.ASSERT, tag, msg);
    }

    /** Log an wtf message with optional format args. */
    public static void wtf(String tag, String msg, Object... args) {
        triggerLogger(Log.ASSERT, tag, msg, args);
    }

    private static void triggerLogger(int priority, String tag, String msg, Object... args) {
        if (enabled) {
            triggerLogger(priority, tag, String.format(msg, args));
        }
    }

    private static void triggerLogger(int priority, String tag, String msg) {
        if (enabled) {
            Timber.tag(tag).log(priority, msg);
        }
    }
}
