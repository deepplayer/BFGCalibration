package com.bfgtools.calibration.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.onebitmonochrome.blacksbbox.BuildConfig;

import java.security.MessageDigest;

final class AppIntegrity {
    private static final String EXPECTED_PACKAGE = "com.bfgtools.calibration.imported";
    private static final String RELEASE_CERT_SHA256 =
            "51882e171f7e99c72f9a44dbf47788f3896110db1dec36d4bd6078ca6b7b08d0";

    private AppIntegrity() {
    }

    static boolean isTrusted(Context context) {
        if (context == null || !EXPECTED_PACKAGE.equals(context.getPackageName())) return false;
        // Development builds can run from Android Studio. In a release build this
        // compile-time constant is false; the release certificate check remains mandatory.
        if (BuildConfig.DEBUG) return true;
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo = packageManager.getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (packageInfo.signingInfo == null) return false;
                signatures = packageInfo.signingInfo.hasMultipleSigners()
                        ? packageInfo.signingInfo.getApkContentsSigners()
                        : packageInfo.signingInfo.getSigningCertificateHistory();
            } else {
                packageInfo = packageManager.getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = packageInfo.signatures;
            }
            if (signatures == null) return false;
            for (Signature signature : signatures) {
                String digest = sha256(signature.toByteArray());
                if (RELEASE_CERT_SHA256.equals(digest)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte b : digest) value.append(String.format("%02x", b & 0xff));
        return value.toString();
    }
}
