package app.habittracker;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

/**
 * Habit Tracker — a single-Activity WebView host for the bundled SPA.
 */
public class MainActivity extends android.app.Activity {

    private static final String ASSET_DOMAIN = "appassets.androidplatform.net";
    private static final String START_URL =
            "https://" + ASSET_DOMAIN + "/assets/public/index.html";

    /** Matches --bg in the stylesheet, light and dark. */
    private static final int BG_LIGHT = Color.parseColor("#FBF7EF");
    private static final int BG_DARK = Color.parseColor("#1E2019");

    /**
     * Wraps the page's global setTheme() so native bars follow the web theme,
     * then reports the current theme once so the bars are right on first paint.
     */
    private static final String THEME_SHIM =
            "(function(){"
            + "  if(!window.AndroidHost) return;"
            + "  var orig = window.setTheme;"
            + "  if (typeof orig === 'function' && !orig.__nativeWrapped) {"
            + "    window.setTheme = function(t){ orig(t);"
            + "      try { AndroidHost.onTheme(t); } catch(e){} };"
            + "    window.setTheme.__nativeWrapped = true;"
            + "  }"
            + "  try {"
            + "    AndroidHost.onTheme("
            + "      document.documentElement.getAttribute('data-theme') || 'light');"
            + "  } catch(e){}"
            + "})();";

    /**
     * Skips Welcome only if the user is currently on the welcome screen.
     */
    private static final String BOOT_SCRIPT =
            "(function(){"
            + "  try {"
            + "    if (localStorage.getItem('habitTrackerSeenWelcome') === '1') {"
            + "      if (typeof show === 'function' && (typeof current === 'undefined' || current === 'welcome')) {"
            + "        show('home', false);"
            + "      }"
            + "    } else {"
            + "      localStorage.setItem('habitTrackerSeenWelcome', '1');"
            + "    }"
            + "  } catch(e){}"
            + "})();";

    /**
     * Back button: close an open overlay first, then walk the SPA's own history
     * stack, and only leave the app when there is nowhere left to go.
     */
    private static final String BACK_SCRIPT =
            "(function(){"
            + "  try {"
            + "    var o = document.querySelectorAll('.overlay');"
            + "    for (var i = 0; i < o.length; i++) {"
            + "      if (!o[i].hidden) { o[i].hidden = true; return 'handled'; }"
            + "    }"
            + "    if (typeof goBack === 'function' && typeof current !== 'undefined'"
            + "        && current && current !== 'home' && current !== 'welcome') {"
            + "      goBack(); return 'handled';"
            + "    }"
            + "  } catch(e){}"
            + "  return 'exit';"
            + "})();";

    private FrameLayout root;
    private WebView web;
    private boolean darkTheme = false;
    private boolean initialLoadDone = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        goEdgeToEdge();

        root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(BG_LIGHT);
        padForSystemBars(root);

        web = new WebView(this);
        web.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(BG_LIGHT);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage: habits + theme
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);           // nothing is loaded over file://
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(ASSET_DOMAIN)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClientCompat() {
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    @NonNull WebView view, @NonNull WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    @NonNull WebView view, @NonNull WebResourceRequest request) {
                Uri url = request.getUrl();
                if (ASSET_DOMAIN.equals(url.getHost())) {
                    return false; // in-app navigation
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(@NonNull WebView view, @NonNull String url) {
                if (!initialLoadDone) {
                    initialLoadDone = true;
                    view.evaluateJavascript(BOOT_SCRIPT, null);
                }
                view.evaluateJavascript(THEME_SHIM, null);
            }
        });

        web.addJavascriptInterface(new Host(), "AndroidHost");

        root.addView(web);
        setContentView(root);

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
    }

    /** Lets the app draw behind the system bars on every supported API level. */
    private void goEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    /** Insets the content so nothing sits under the clock or the nav bar. */
    private void padForSystemBars(final View target) {
        target.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
    }

    private void applyTheme(boolean dark) {
        darkTheme = dark;
        int bg = dark ? BG_DARK : BG_LIGHT;
        if (root != null) root.setBackgroundColor(bg);
        if (web != null) web.setBackgroundColor(bg);
        setLightBarIcons(!dark);
    }

    /** true = dark glyphs for a light background. */
    private void setLightBarIcons(boolean lightBackground) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(lightBackground ? mask : 0, mask);
            }
        } else {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (lightBackground) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    /** Bridge exposed to the page as window.AndroidHost. */
    private final class Host {
        @JavascriptInterface
        public void onTheme(final String theme) {
            final boolean dark = "dark".equals(theme);
            runOnUiThread(() -> applyTheme(dark));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript(BACK_SCRIPT, value -> {
            if (value == null || !value.contains("handled")) {
                finish();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.evaluateJavascript(THEME_SHIM, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AndroidHost");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
