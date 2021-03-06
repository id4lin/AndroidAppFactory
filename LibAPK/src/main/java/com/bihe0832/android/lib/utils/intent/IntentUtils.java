package com.bihe0832.android.lib.utils.intent;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.bihe0832.android.lib.log.ZLog;


public class IntentUtils {

    private static final String TAG = "IntentUtils";

    public static boolean jumpToOtherApp(String url, Context context) {
        if (context == null) {
            return false;
        }
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            ZLog.d(TAG, "jumpToOtherApp url:" + url + ",intent:" + intent.toString());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            ZLog.e(TAG, "jumpToOtherApp failed:" + e.getMessage());
            return false;
        }
    }

    public static boolean openWebPage(String url, Context context) {
        if (context == null) {
            return false;
        }
        try {
            Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            ZLog.d(TAG, "openWebPage url:" + url + ",intent:" + intent.toString());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            ZLog.e(TAG, "openWebPage failed:" + e.getMessage());
            return false;
        }
    }


    public static boolean startIntent(Context ctx, Intent intent) {
        if (null == intent) {
            ZLog.d("startIntent intent == null");
            return false;
        }

        if (null == ctx) {
            ZLog.d("startIntent ctx == null");
            return false;
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void sendTextInfo(final Context context, final String title, final String content) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, content);
        sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        sendIntent.setType("text/plain");
        try {
            context.startActivity(Intent.createChooser(sendIntent, title));
        } catch (Exception e) {
            e.printStackTrace();
            try {
                context.startActivity(sendIntent);
            } catch (Exception ee) {
                ee.printStackTrace();
            }
        }
    }

}
