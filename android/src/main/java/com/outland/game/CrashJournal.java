package com.outland.game;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Best-effort crash journal. Private staging is authoritative; public export is opportunistic. */
public final class CrashJournal {
    private static final String TAG = "OUTLAND-DIAG";
    private static final String DIR = "outlandlogs";
    private static final Object LOCK = new Object();
    private static volatile Context app;
    private static volatile Thread.UncaughtExceptionHandler previous;
    private static volatile String session;

    private CrashJournal() {}

    public static void install(Context context) {
        app = context.getApplicationContext();
        session = stamp();
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            record("UNCAUGHT thread=" + thread.getName(), error);
            Thread.UncaughtExceptionHandler delegate = previous;
            if (delegate != null) delegate.uncaughtException(thread, error);
        });
        record("SESSION_START sdk=" + Build.VERSION.SDK_INT + " version=" + versionName(app), null);
    }

    public static void record(String message, Throwable error) {
        Context context = app;
        if (context == null) return;
        synchronized (LOCK) {
            StringBuilder text = new StringBuilder("\n[ ").append(new Date()).append(" ] ").append(message).append('\n');
            if (error != null) {
                StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw));
                text.append(sw);
            }
            text.append('\n');
            String name = "session-" + (session == null ? stamp() : session) + ".log";
            File privateDir = new File(context.getFilesDir(), DIR);
            File staged = new File(privateDir, name);
            try {
                if (!privateDir.exists() && !privateDir.mkdirs()) throw new IOException("Cannot create private log directory");
                try (FileOutputStream out = new FileOutputStream(staged, true)) {
                    out.write(text.toString().getBytes("UTF-8"));
                    out.getFD().sync();
                }
            } catch (Throwable writeError) {
                Log.e(TAG, "Private journal write failed", writeError);
                return;
            }
            flushOne(context, staged);
        }
    }

    /** Copies staged logs into /sdcard/outlandlogs when legacy storage permission permits it. */
    public static void flushPending(Context context) {
        Context c = context.getApplicationContext();
        synchronized (LOCK) {
            File dir = new File(c.getFilesDir(), DIR);
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) if (file.isFile()) flushOne(c, file);
        }
    }

    private static void flushOne(Context context, File staged) {
        if (Build.VERSION.SDK_INT >= 23 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return;
        File publicDir = new File("/sdcard/outlandlogs");
        File target = new File(publicDir, staged.getName());
        try {
            if (!publicDir.exists() && !publicDir.mkdirs()) throw new IOException("mkdir /sdcard/outlandlogs failed");
            try (InputStream in = new FileInputStream(staged); OutputStream out = new FileOutputStream(target, false)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.flush();
            }
            Log.i(TAG, "Journal exported: " + target.getAbsolutePath());
        } catch (Throwable failure) {
            // Keep private staged file; never let diagnostics interrupt gameplay.
            Log.w(TAG, "Public log export unavailable; retained private copy at " + staged, failure);
        }
    }

    private static String versionName(Context context) {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (Throwable ignored) { return "unknown"; }
    }
    private static String stamp() { return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date()); }
}
