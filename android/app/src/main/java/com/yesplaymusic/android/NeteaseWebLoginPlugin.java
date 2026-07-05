package com.yesplaymusic.android;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NeteaseWebLogin")
public class NeteaseWebLoginPlugin extends Plugin {
    private static final String LOGIN_URL = "https://music.163.com/#/login";
    private static final long COOKIE_CHECK_INTERVAL_MS = 750L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Dialog loginDialog;
    private WebView loginWebView;
    private PluginCall pendingCall;
    private boolean completed;

    private final Runnable cookieCheck = new Runnable() {
        @Override
        public void run() {
            if (pendingCall == null || loginDialog == null || !loginDialog.isShowing()) {
                return;
            }

            String cookie = CookieManager.getInstance().getCookie("https://music.163.com");
            if (containsCookie(cookie, "MUSIC_U")) {
                completeLogin(cookie);
                return;
            }

            handler.postDelayed(this, COOKIE_CHECK_INTERVAL_MS);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @PluginMethod
    public void start(PluginCall call) {
        if (pendingCall != null) {
            call.reject("网易云登录窗口已经打开");
            return;
        }

        call.setKeepAlive(true);
        pendingCall = call;
        completed = false;

        getActivity().runOnUiThread(() -> {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);

            loginWebView = new WebView(getActivity());
            WebSettings settings = loginWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
            );
            CookieManager.getInstance().setAcceptThirdPartyCookies(loginWebView, true);

            loginWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    view.loadUrl(request.getUrl().toString());
                    return true;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    focusQrCode(view);
                    handler.postDelayed(() -> {
                        if (loginWebView != null) focusQrCode(loginWebView);
                    }, 900L);
                    handler.removeCallbacks(cookieCheck);
                    handler.post(cookieCheck);
                }
            });

            TextView hint = new TextView(getActivity());
            hint.setText("请使用网易云音乐 App 扫描网页中的二维码，并在网易云中确认登录");
            hint.setTextColor(Color.rgb(45, 45, 45));
            hint.setTextSize(15);
            hint.setGravity(Gravity.CENTER);
            int horizontalPadding = dp(18);
            hint.setPadding(horizontalPadding, dp(12), horizontalPadding, dp(12));

            LinearLayout content = new LinearLayout(getActivity());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setBackgroundColor(Color.WHITE);
            content.addView(
                hint,
                new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            );
            content.addView(
                loginWebView,
                new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            );
            ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                    WindowInsetsCompat.Type.displayCutout()
                );
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });

            loginDialog = new Dialog(
                getActivity(),
                android.R.style.Theme_DeviceDefault_Light_NoActionBar
            );
            loginDialog.setContentView(content);
            loginDialog.setOnDismissListener(dialog -> {
                handler.removeCallbacks(cookieCheck);
                destroyLoginWebView();
                if (!completed && pendingCall != null) {
                    PluginCall callToReject = pendingCall;
                    pendingCall = null;
                    callToReject.setKeepAlive(false);
                    callToReject.reject("已取消扫码登录");
                }
            });

            Window window = loginDialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                );
            }

            loginDialog.show();
            if (window != null) {
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                );
            }
            loginWebView.loadUrl(LOGIN_URL);
            handler.post(cookieCheck);
        });
    }

    private void completeLogin(String cookie) {
        if (pendingCall == null) return;

        completed = true;
        CookieManager.getInstance().flush();
        PluginCall call = pendingCall;
        pendingCall = null;
        call.setKeepAlive(false);

        JSObject result = new JSObject();
        result.put("cookie", cookie);
        call.resolve(result);

        if (loginDialog != null && loginDialog.isShowing()) {
            loginDialog.dismiss();
        }
    }

    private boolean containsCookie(String cookieString, String key) {
        if (cookieString == null || cookieString.isEmpty()) return false;
        for (String item : cookieString.split(";")) {
            String trimmed = item.trim();
            if (trimmed.startsWith(key + "=") && trimmed.length() > key.length() + 1) {
                return true;
            }
        }
        return false;
    }

    private void focusQrCode(WebView webView) {
        String script =
            "(function(){" +
            "var q=document.querySelector('#login-qrcode');" +
            "if(!q){" +
            "var b=document.querySelector('#m-topbar-user-unlogin [data-action=\"login\"]," +
            ".m-tophead [data-action=\"login\"]');" +
            "if(b)b.click();return;" +
            "}" +
            "if(q.parentElement!==document.body)document.body.appendChild(q);" +
            "Array.prototype.forEach.call(document.body.children,function(e){" +
            "if(e!==q)e.style.setProperty('display','none','important');" +
            "});" +
            "document.documentElement.style.setProperty('overflow','hidden','important');" +
            "document.body.style.setProperty('margin','0','important');" +
            "document.body.style.setProperty('overflow','hidden','important');" +
            "q.classList.remove('f-hide');" +
            "q.style.cssText='position:fixed!important;inset:0!important;" +
            "width:100vw!important;height:100vh!important;min-height:0!important;" +
            "display:flex!important;align-items:center!important;justify-content:center!important;" +
            "background:#fff!important;z-index:2147483647!important;';" +
            "var left=q.querySelector('.left');if(left)left.style.display='none';" +
            "var cnt=q.querySelector('.cnt');if(cnt)cnt.style.cssText=" +
            "'width:auto!important;height:auto!important;margin:0!important;';" +
            "var main=q.querySelector('.main');if(main)main.style.cssText=" +
            "'width:auto!important;height:auto!important;margin:0!important;display:block!important;';" +
            "var right=q.querySelector('.right');if(right)right.style.cssText=" +
            "'float:none!important;width:300px!important;margin:0!important;text-align:center!important;';" +
            "var qr=q.querySelector('.qr');if(qr)qr.style.cssText=" +
            "'width:240px!important;height:240px!important;margin:16px auto!important;';" +
            "Array.prototype.forEach.call(q.querySelectorAll('.qr img,.qr canvas'),function(e){" +
            "e.style.cssText='width:240px!important;height:240px!important;max-width:none!important;';" +
            "});" +
            "var title=q.querySelector('.title');if(title)title.style.cssText=" +
            "'font-size:24px!important;line-height:36px!important;font-weight:600!important;';" +
            "var txt=q.querySelector('.txt');if(txt)txt.style.cssText=" +
            "'font-size:15px!important;line-height:24px!important;';" +
            "})();";
        webView.evaluateJavascript(script, null);
    }

    private int dp(int value) {
        float density = getActivity().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void destroyLoginWebView() {
        if (loginWebView == null) return;
        loginWebView.stopLoading();
        loginWebView.loadUrl("about:blank");
        loginWebView.clearHistory();
        loginWebView.removeAllViews();
        loginWebView.destroy();
        loginWebView = null;
        loginDialog = null;
    }

    @Override
    protected void handleOnDestroy() {
        handler.removeCallbacks(cookieCheck);
        if (loginDialog != null && loginDialog.isShowing()) {
            completed = true;
            loginDialog.dismiss();
        }
        destroyLoginWebView();
        pendingCall = null;
        super.handleOnDestroy();
    }
}
