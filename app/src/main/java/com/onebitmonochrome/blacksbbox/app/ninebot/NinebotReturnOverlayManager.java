package com.onebitmonochrome.blacksbbox.app.ninebot;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import top.niunaijun.blackbox.BlackBoxCore;

/** Adds an in-app return bubble to every activity of the embedded Ninebot guest. */
public final class NinebotReturnOverlayManager {
    public static final String EXTRA_RETURN_FROM_NINEBOT =
            "com.onebitmonochrome.blacksbbox.extra.RETURN_FROM_NINEBOT";

    private static final String TOOL_ACTIVITY =
            "com.bfgtools.calibration.core.MainActivity";
    private static final String CONTAINER_TAG = "ninebot_return_overlay_container";
    private static final String BUBBLE_TAG = "ninebot_return_to_detector";
    private static boolean initialized;

    private NinebotReturnOverlayManager() {}

    public static synchronized void init(Application application) {
        if (application == null || initialized) return;
        initialized = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity activity) {
                attachToActivity(activity);
            }

            @Override public void onActivityResumed(Activity activity) {
                attachToActivity(activity);
            }

            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
        Toast.makeText(application,
                "请登录并同步车辆，完成后点击“返回检测”", Toast.LENGTH_LONG).show();
    }

    public static void attachToActivity(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) decor;
        root.post(() -> attachNow(activity, root));
        root.postDelayed(() -> attachNow(activity, root), 350);
    }

    private static void attachNow(Activity activity, ViewGroup decor) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        View existing = decor.findViewWithTag(CONTAINER_TAG);
        if (existing != null) {
            existing.setVisibility(View.VISIBLE);
            existing.bringToFront();
            existing.setTranslationZ(dp(activity, 30));
            return;
        }

        FrameLayout root = new FrameLayout(activity);
        root.setTag(CONTAINER_TAG);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setClickable(false);
        root.setFocusable(false);

        TextView bubble = new TextView(activity);
        bubble.setTag(BUBBLE_TAG);
        bubble.setText("登录完成\n返回检测");
        bubble.setContentDescription("登录完成，返回车辆检测工具");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(14);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(activity, 15), dp(activity, 9), dp(activity, 15), dp(activity, 9));
        bubble.setElevation(dp(activity, 10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xEE1769E0);
        background.setCornerRadius(dp(activity, 24));
        background.setStroke(dp(activity, 1), 0x99FFFFFF);
        bubble.setBackground(background);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        params.setMarginEnd(dp(activity, 12));
        bubble.setLayoutParams(params);
        installDragAndReturn(bubble, root, activity);
        root.addView(bubble);
        activity.addContentView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.bringToFront();
        root.setTranslationZ(dp(activity, 30));
    }

    private static void installDragAndReturn(TextView bubble, FrameLayout root, Activity activity) {
        WeakReference<Activity> activityRef = new WeakReference<>(activity);
        final float[] downRaw = new float[2];
        final float[] downPosition = new float[2];
        final boolean[] moved = {false};
        bubble.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw[0] = event.getRawX();
                    downRaw[1] = event.getRawY();
                    downPosition[0] = view.getX();
                    downPosition[1] = view.getY();
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRaw[0];
                    float dy = event.getRawY() - downRaw[1];
                    if (Math.abs(dx) > dp(view.getContext(), 6)
                            || Math.abs(dy) > dp(view.getContext(), 6)) moved[0] = true;
                    float maxX = Math.max(0, root.getWidth() - view.getWidth());
                    float maxY = Math.max(0, root.getHeight() - view.getHeight());
                    view.setX(Math.max(0, Math.min(maxX, downPosition[0] + dx)));
                    view.setY(Math.max(0, Math.min(maxY, downPosition[1] + dy)));
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) returnToTool(activityRef.get());
                    return true;
                default:
                    return true;
            }
        });
    }

    private static void returnToTool(Activity guestActivity) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(BlackBoxCore.getHostPkg(), TOOL_ACTIVITY));
            intent.putExtra(EXTRA_RETURN_FROM_NINEBOT, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            BlackBoxCore.getContext().startActivity(intent);
        } catch (Throwable error) {
            if (guestActivity != null) {
                Toast.makeText(guestActivity,
                        "返回检测工具失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
