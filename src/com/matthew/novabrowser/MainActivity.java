package com.matthew.novabrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

public class MainActivity extends Activity {

    static final int BG = 0xFF0E0F13;
    static final int SURFACE = 0xFF191B21;
    static final int SURFACE2 = 0xFF24262E;
    static final int TEXT = 0xFFE7E9F0;
    static final int SUBTLE = 0xFF9AA0AE;
    static final int ACCENT = 0xFF8AB4F8;
    static final int PRIVATE = 0xFFC79DF2;
    static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:115.0) Gecko/20100101 Firefox/115.0";

    static class Tab {
        WebView view;
        String url = "";
        String title = "New tab";
        boolean incognito = false;
        boolean desktop = false;
    }

    ArrayList<Tab> tabs = new ArrayList<Tab>();
    int current = -1;

    FrameLayout root;
    LinearLayout chrome;      // everything that hides in fullscreen video
    LinearLayout urlRow;
    EditText urlBox;
    ProgressBar progress;
    FrameLayout tabContainer;
    LinearLayout bottomBar;
    TextView tabsBtn;
    TextView deskBtn;

    FrameLayout switcher;     // tab switcher overlay
    ListView switchList;
    SwitchAdapter switchAdapter;

    FrameListOverlay listOverlay; // bookmarks / history reuse

    View fullscreenView;
    WebChromeClient.CustomViewCallback fullscreenCb;
    FrameLayout fullscreenWrap;

    ValueCallback<Uri[]> fileCb;
    boolean awaitingFileChooser = false;

    static class FrameListOverlay {
        FrameLayout host;
        TextView title;
        ListView list;
        TextView action;
        Runnable onAction;
        BaseAdapter adapter;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        DataStore.init(this);
        buildUi();
        applyBars();

        boolean openedByLink = false;
        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (data != null) {
            String u = data.toString();
            if (u.startsWith("http")) {
                newTab(u, false);
                openedByLink = true;
            }
        }
        if (!openedByLink) {
            JSONArray saved = DataStore.savedTabs();
            boolean restored = false;
            try {
                if (saved.length() > 0) {
                    for (int i = 0; i < saved.length(); i++) {
                        JSONObject o = saved.getJSONObject(i);
                        Tab t = newTab(o.optString("url", ""), false);
                        if (t != null) t.title = o.optString("title", "Tab");
                    }
                    restored = tabs.size() > 0;
                    if (restored) showTab(tabs.get(tabs.size() - 1));
                }
            } catch (Exception e) {
            }
            if (!restored) newTab(DataStore.homepage(), false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null) {
            String u = intent.getData().toString();
            if (u.startsWith("http")) {
                newTab(u, false);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            JSONArray a = new JSONArray();
            for (Tab t : tabs) {
                if (t == null || t.incognito) continue;
                JSONObject o = new JSONObject();
                o.put("url", t.url);
                o.put("title", t.title);
                a.put(o);
            }
            DataStore.saveTabs(a);
        } catch (Exception e) {
        }
    }

    @Override
    protected void onDestroy() {
        for (Tab t : tabs) {
            try {
                t.view.destroy();
            } catch (Exception e) {
            }
        }
        super.onDestroy();
    }

    // ---------- UI ----------

    TextView barButton(String glyph, View.OnClickListener c) {
        TextView tv = new TextView(this);
        tv.setText(glyph);
        tv.setTextColor(TEXT);
        tv.setTextSize(20);
        tv.setTypeface(Typeface.DEFAULT);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(48));
        tv.setLayoutParams(lp);
        tv.setOnClickListener(c);
        return tv;
    }

    void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        root.addView(chrome);

        // top bar
        urlRow = new LinearLayout(this);
        urlRow.setOrientation(LinearLayout.HORIZONTAL);
        urlRow.setGravity(Gravity.CENTER_VERTICAL);
        urlRow.setBackgroundColor(SURFACE);
        urlRow.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams urlRowLp = new LinearLayout.LayoutParams(-1, -2);
        urlRow.setLayoutParams(urlRowLp);
        chrome.addView(urlRow);

        urlBox = new EditText(this);
        urlBox.setSingleLine(true);
        urlBox.setHint("Search or type URL");
        urlBox.setHintTextColor(SUBTLE);
        urlBox.setTextColor(TEXT);
        urlBox.setTextSize(15);
        urlBox.setBackground(rounded(SURFACE2, dp(24)));
        urlBox.setPadding(dp(16), dp(8), dp(16), dp(8));
        urlBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBox.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams ubLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        urlBox.setLayoutParams(ubLp);
        urlBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    navigate(urlBox.getText().toString());
                    hideKeyboard();
                    return true;
                }
                return false;
            }
        });
        urlRow.addView(urlBox);

        TextView go = barButton("↻", new View.OnClickListener() {
            public void onClick(View v) {
                String typed = urlBox.getText().toString().trim();
                Tab t = cur();
                if (t == null) return;
                if (typed.length() == 0 || typed.equals(t.url)) {
                    t.view.reload();
                } else {
                    navigate(typed);
                }
                hideKeyboard();
            }
        });
        urlRow.addView(go);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE2));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, dp(3));
        progress.setLayoutParams(pLp);
        progress.setVisibility(View.GONE);
        chrome.addView(progress);

        // webview container
        tabContainer = new FrameLayout(this);
        tabContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        chrome.addView(tabContainer);

        // bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setBackgroundColor(SURFACE);
        LinearLayout.LayoutParams bbLp = new LinearLayout.LayoutParams(-1, dp(52));
        bottomBar.setLayoutParams(bbLp);
        chrome.addView(bottomBar);

        bottomBar.addView(barButton("←", new View.OnClickListener() {
            public void onClick(View v) {
                Tab t = cur();
                if (t != null && t.view.canGoBack()) t.view.goBack();
            }
        }));
        bottomBar.addView(barButton("→", new View.OnClickListener() {
            public void onClick(View v) {
                Tab t = cur();
                if (t != null && t.view.canGoForward()) t.view.goForward();
            }
        }));
        bottomBar.addView(barButton("⌂", new View.OnClickListener() {
            public void onClick(View v) {
                Tab t = cur();
                if (t != null) t.view.loadUrl(DataStore.homepage());
            }
        }));

        deskBtn = barButton("◐", new View.OnClickListener() {
            public void onClick(View v) {
                toggleDesktop();
            }
        });

        tabsBtn = barButton("▣", new View.OnClickListener() {
            public void onClick(View v) {
                openSwitcher();
            }
        });

        LinearLayout spacer = new LinearLayout(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        bottomBar.addView(spacer);
        bottomBar.addView(deskBtn);
        bottomBar.addView(tabsBtn);
        bottomBar.addView(barButton("⋮", new View.OnClickListener() {
            public void onClick(View v) {
                openMenu(v);
            }
        }));
    }

    GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    int dp(float v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    void applyBars() {
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        } catch (Exception e) {
        }
    }

    void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(urlBox.getWindowToken(), 0);
        } catch (Exception e) {
        }
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------- tabs ----------

    Tab cur() {
        if (current >= 0 && current < tabs.size()) return tabs.get(current);
        return null;
    }

    Tab newTab(String url, boolean incognito) {
        Tab t = new Tab();
        t.incognito = incognito;
        t.view = createWebView(t);
        tabs.add(t);
        current = tabs.size() - 1;
        showTab(t);
        if (url != null && url.length() > 0) {
            t.view.loadUrl(url);
        } else {
            t.view.loadUrl(DataStore.homepage());
        }
        updateTabsBtn();
        return t;
    }

    void closeTab(int i) {
        if (i < 0 || i >= tabs.size()) return;
        Tab t = tabs.get(i);
        try {
            t.view.stopLoading();
            t.view.destroy();
        } catch (Exception e) {
        }
        tabs.remove(i);
        if (tabs.size() == 0) {
            newTab(DataStore.homepage(), false);
            if (switcher.getVisibility() == View.VISIBLE) closeSwitcher();
            return;
        }
        if (current >= tabs.size()) current = tabs.size() - 1;
        if (current == i) {
            showTab(tabs.get(current));
        } else if (current > i) {
            current = current - 1;
        }
        updateTabsBtn();
        if (switcher.getVisibility() == View.VISIBLE) switchAdapter.notifyDataSetChanged();
    }

    void showTab(Tab t) {
        tabContainer.removeAllViews();
        tabContainer.addView(t.view, new FrameLayout.LayoutParams(-1, -1));
        current = tabs.indexOf(t);
        closeSwitcher();
        hideOverlays();
        updateUrlBar(t);
        CookieManager.getInstance().setAcceptCookie(!t.incognito);
        updatePrivateTint(t);
    }

    void updatePrivateTint(Tab t) {
        if (t.incognito) {
            urlBox.setBackground(rounded(0xFF2A2233, dp(24)));
            urlBox.setHintTextColor(PRIVATE);
            urlBox.setHint("Private tab - Search or type URL");
        } else {
            urlBox.setBackground(rounded(SURFACE2, dp(24)));
            urlBox.setHintTextColor(SUBTLE);
            urlBox.setHint("Search or type URL");
        }
    }

    void updateUrlBar(Tab t) {
        urlBox.setText(t.url == null ? "" : t.url);
    }

    void updateTabsBtn() {
        tabsBtn.setText("▣ " + tabs.size());
    }

    void toggleDesktop() {
        Tab t = cur();
        if (t == null) return;
        t.desktop = !t.desktop;
        WebSettings s = t.view.getSettings();
        if (t.desktop) {
            s.setUserAgentString(DESKTOP_UA);
        } else {
            s.setUserAgentString(null);
        }
        t.view.reload();
        deskBtn.setTextColor(t.desktop ? ACCENT : TEXT);
        toast(t.desktop ? "Desktop site on" : "Desktop site off");
    }

    // ---------- webview ----------

    WebView createWebView(final Tab t) {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSafeBrowsingEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setTextZoom(100);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, !t.incognito);

        w.setBackgroundColor(BG);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String u) {
                Uri uri = Uri.parse(u);
                String sch = uri.getScheme();
                if ("http".equals(sch) || "https".equals(sch)) return false;
                if ("file".equals(sch)) {
                    toast("Local files blocked");
                    return true;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    toast("No app for this link");
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String u, android.graphics.Bitmap fav) {
                t.url = u;
                if (t == cur()) urlBox.setText(u);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                t.url = u;
                String title = v.getTitle();
                if (title != null && title.length() > 0) t.title = title;
                if (!t.incognito) DataStore.addHistory(t.title, u);
                if (t == cur()) updateUrlBar(t);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    showErrorPage(v, String.valueOf(err.getDescription()));
                }
            }

            @Override
            public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) {
                h.cancel();
                toast("Blocked: insecure certificate");
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (t == cur()) {
                    progress.setProgress(p);
                    progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onReceivedTitle(WebView v, String title) {
                if (title != null && title.length() > 0) t.title = title;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullscreenView != null) {
                    cb.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCb = cb;
                chrome.setVisibility(View.GONE);
                fullscreenWrap = new FrameLayout(MainActivity.this);
                fullscreenWrap.setBackgroundColor(0xFF000000);
                fullscreenWrap.addView(view, new FrameLayout.LayoutParams(-1, -1));
                root.addView(fullscreenWrap, new FrameLayout.LayoutParams(-1, -1));
                try {
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                } catch (Exception e) {
                }
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }

            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            request.deny();
                        } catch (Exception e) {
                        }
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (awaitingFileChooser) {
                    return false;
                }
                fileCb = cb;
                awaitingFileChooser = true;
                try {
                    startActivityForResult(params.createIntent(), 1001);
                    return true;
                } catch (Exception e) {
                    awaitingFileChooser = false;
                    fileCb = null;
                    return false;
                }
            }
        });

        w.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setMimeType(mime);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) r.addRequestHeader("cookie", cookies);
                    r.addRequestHeader("User-Agent", ua);
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String name = URLUtil.guessFileName(url, cd, mime);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(r);
                    toast("Downloading " + name);
                } catch (Exception e) {
                    toast("Download failed");
                }
            }
        });

        w.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                WebView.HitTestResult hr = w.getHitTestResult();
                if (hr != null && hr.getExtra() != null) {
                    int type = hr.getType();
                    if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                            || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                        linkMenu(hr.getExtra());
                        return true;
                    }
                }
                return false;
            }
        });

        return w;
    }

    void hideFullscreen() {
        if (fullscreenWrap != null) {
            root.removeView(fullscreenWrap);
            fullscreenWrap = null;
        }
        chrome.setVisibility(View.VISIBLE);
        try {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } catch (Exception e) {
        }
        if (fullscreenCb != null) {
            try {
                fullscreenCb.onCustomViewHidden();
            } catch (Exception e) {
            }
            fullscreenCb = null;
        }
        fullscreenView = null;
    }

    void showErrorPage(WebView v, String msg) {
        String html = "<html><body style='background:#0E0F13;color:#E7E9F0;font-family:sans-serif;padding:24px'>"
                + "<h2 style='color:#8AB4F8'>Can't load this page</h2>"
                + "<p style='color:#9AA0AE'>" + msg + "</p>"
                + "<p style='color:#9AA0AE'>Check the address, or try again when you're back online.</p>"
                + "</body></html>";
        v.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    // ---------- navigation ----------

    void navigate(String input) {
        String u = input == null ? "" : input.trim();
        if (u.length() == 0) {
            Tab t = cur();
            if (t != null) t.view.reload();
            return;
        }
        Tab t = cur();
        if (t == null) return;
        if (u.startsWith("about:") || u.startsWith("javascript:") || u.startsWith("data:")) {
            t.view.loadUrl(u);
            return;
        }
        Uri uri = Uri.parse(u);
        String sch = uri.getScheme();
        if (sch != null && !sch.startsWith("http")) {
            if ("file".equals(sch)) {
                toast("Local files blocked");
                return;
            }
            t.view.loadUrl(u);
            return;
        }
        boolean looksUrl = u.contains(".") && !u.contains(" ");
        if (looksUrl) {
            if (sch == null || !sch.startsWith("http")) u = "https://" + u;
            t.view.loadUrl(u);
        } else {
            t.view.loadUrl(DataStore.searchUrl(u));
        }
    }

    // ---------- link menu ----------

    void linkMenu(final String link) {
        String[] items = new String[]{"Open in new tab", "Open in private tab", "Copy link", "Share link"};
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            newTab(link, false);
                        } else if (which == 1) {
                            newTab(link, true);
                        } else if (which == 2) {
                            copyToClipboard(link);
                            toast("Link copied");
                        } else {
                            shareText(link);
                        }
                    }
                }).show();
    }

    void copyToClipboard(String s) {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("url", s));
        } catch (Exception e) {
        }
    }

    void shareText(String s) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, s);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception e) {
        }
    }

    // ---------- switcher ----------

    void openSwitcher() {
        if (switcher == null) {
            switcher = new FrameLayout(this);
            switcher.setBackgroundColor(0xE60E0F13);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
            col.setLayoutParams(clp);

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(12), dp(10), dp(4), dp(10));
            TextView h = headLabel("Tabs");
            head.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(barButton("+", new View.OnClickListener() {
                public void onClick(View v) {
                    newTab(DataStore.homepage(), false);
                }
            }));
            head.addView(barButton("◑", new View.OnClickListener() {
                public void onClick(View v) {
                    newTab(DataStore.homepage(), true);
                }
            }));
            head.addView(barButton("✕", new View.OnClickListener() {
                public void onClick(View v) {
                    closeSwitcher();
                }
            }));
            col.addView(head, new LinearLayout.LayoutParams(-1, -2));

            switchList = new ListView(this);
            switchList.setDivider(null);
            switchAdapter = new SwitchAdapter();
            switchList.setAdapter(switchAdapter);
            switchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                    showTab(tabs.get(pos));
                    updateTabsBtn();
                }
            });
            col.addView(switchList, new LinearLayout.LayoutParams(-1, 0, 1f));

            switcher.addView(col, new FrameLayout.LayoutParams(-1, -1));
            root.addView(switcher, new FrameLayout.LayoutParams(-1, -1));
        }
        switchAdapter.notifyDataSetChanged();
        switcher.setVisibility(View.VISIBLE);
        hideOverlays();
        hideKeyboard();
    }

    void closeSwitcher() {
        if (switcher != null) switcher.setVisibility(View.GONE);
    }

    TextView headLabel(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextColor(TEXT);
        tv.setTextSize(18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    class SwitchAdapter extends BaseAdapter {
        public int getCount() {
            return tabs.size();
        }

        public Object getItem(int i) {
            return tabs.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(8), dp(8));
            row.setBackground(rounded(i == current ? 0xFF2B3348 : SURFACE, dp(12)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(dp(8), dp(4), dp(8), dp(4));
            row.setLayoutParams(lp);

            Tab t = tabs.get(i);
            LinearLayout textCol = new LinearLayout(MainActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, -2, 1f);
            textCol.setLayoutParams(tcLp);

            TextView title = new TextView(MainActivity.this);
            title.setText((t.incognito ? "Private: " : "") + (t.title == null ? "Tab" : t.title));
            title.setTextColor(t.incognito ? PRIVATE : TEXT);
            title.setTextSize(15);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            textCol.addView(title);

            TextView url = new TextView(MainActivity.this);
            url.setText(t.url == null ? "" : t.url);
            url.setTextColor(SUBTLE);
            url.setTextSize(12);
            url.setSingleLine(true);
            textCol.addView(url);

            row.addView(textCol);
            row.addView(barButton("✕", new View.OnClickListener() {
                public void onClick(View v) {
                    closeTab(i);
                }
            }));

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showTab(tabs.get(i));
                    updateTabsBtn();
                }
            });
            return row;
        }
    }

    // ---------- bookmarks / history overlays ----------

    void hideOverlays() {
        if (listOverlay != null && listOverlay.host != null) {
            listOverlay.host.setVisibility(View.GONE);
        }
    }

    FrameListOverlay makeOverlay(String title, String actionLabel) {
        if (listOverlay == null) {
            listOverlay = new FrameListOverlay();
            listOverlay.host = new FrameLayout(this);
            listOverlay.host.setBackgroundColor(0xF60E0F13);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(12), dp(10), dp(4), dp(10));
            listOverlay.title = headLabel(title);
            head.addView(listOverlay.title, new LinearLayout.LayoutParams(0, -2, 1f));
            listOverlay.action = new TextView(this);
            listOverlay.action.setTextColor(ACCENT);
            listOverlay.action.setTextSize(15);
            listOverlay.action.setTypeface(Typeface.DEFAULT_BOLD);
            listOverlay.action.setPadding(dp(12), dp(10), dp(12), dp(10));
            head.addView(listOverlay.action);
            head.addView(barButton("✕", new View.OnClickListener() {
                public void onClick(View v) {
                    hideOverlays();
                }
            }));
            col.addView(head, new LinearLayout.LayoutParams(-1, -2));

            listOverlay.list = new ListView(this);
            col.addView(listOverlay.list, new LinearLayout.LayoutParams(-1, 0, 1f));

            listOverlay.host.addView(col, new FrameLayout.LayoutParams(-1, -1));
            root.addView(listOverlay.host, new FrameLayout.LayoutParams(-1, -1));
        }
        listOverlay.title.setText(title);
        listOverlay.action.setText(actionLabel);
        listOverlay.host.setVisibility(View.VISIBLE);
        hideOverlays();
        listOverlay.host.setVisibility(View.VISIBLE);
        return listOverlay;
    }

    void openBookmarks() {
        final FrameListOverlay o = makeOverlay("Bookmarks", "");
        o.action.setVisibility(View.GONE);
        final JSONArray bm = DataStore.bookmarks();
        BaseAdapter ad = new BaseAdapter() {
            public int getCount() {
                return bm.length();
            }

            public Object getItem(int i) {
                return bm.optJSONObject(i);
            }

            public long getItemId(int i) {
                return i;
            }

            public View getView(int i, View c, ViewGroup p) {
                return listRow(bm.optJSONObject(i), o, "bookmark");
            }
        };
        o.list.setAdapter(ad);
        o.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                String u = bm.optJSONObject(pos).optString("url", "");
                hideOverlays();
                Tab t = cur();
                if (t != null) t.view.loadUrl(u);
            }
        });
        o.list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                final JSONObject item = bm.optJSONObject(pos);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Remove \"" + item.optString("title", "") + "\" from bookmarks?")
                        .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                DataStore.removeBookmark(item.optString("url", ""));
                                toast("Bookmark removed");
                                openBookmarks();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
        });
    }

    void openHistory() {
        final FrameListOverlay o = makeOverlay("History", "Clear all");
        o.action.setVisibility(View.VISIBLE);
        o.action.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Clear all history?")
                        .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                DataStore.clearHistory();
                                toast("History cleared");
                                openHistory();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        final JSONArray hs = DataStore.history();
        BaseAdapter ad = new BaseAdapter() {
            public int getCount() {
                return hs.length();
            }

            public Object getItem(int i) {
                return hs.optJSONObject(i);
            }

            public long getItemId(int i) {
                return i;
            }

            public View getView(int i, View c, ViewGroup p) {
                return listRow(hs.optJSONObject(i), o, "history");
            }
        };
        o.list.setAdapter(ad);
        o.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                String u = hs.optJSONObject(pos).optString("url", "");
                hideOverlays();
                Tab t = cur();
                if (t != null) t.view.loadUrl(u);
            }
        });
        o.list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                DataStore.removeHistory(hs.optJSONObject(pos).optString("url", ""));
                toast("Removed");
                openHistory();
                return true;
            }
        });
    }

    View listRow(JSONObject item, FrameListOverlay o, final String kind) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(10));
        TextView title = new TextView(this);
        title.setText(item.optString("title", ""));
        title.setTextColor(TEXT);
        title.setTextSize(15);
        title.setSingleLine(true);
        col.addView(title);
        TextView url = new TextView(this);
        url.setText(item.optString("url", ""));
        url.setTextColor(SUBTLE);
        url.setTextSize(12);
        url.setSingleLine(true);
        col.addView(url);
        return col;
    }

    // ---------- menu ----------

    void openMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        android.view.Menu m = pm.getMenu();
        Tab t = cur();

        m.add(0, 1, 0, "New tab");
        m.add(0, 2, 0, "New private tab");
        if (t != null && !t.incognito && t.url != null && t.url.startsWith("http")) {
            if (DataStore.isBookmarked(t.url)) {
                m.add(0, 3, 0, "Remove bookmark");
            } else {
                m.add(0, 3, 0, "Bookmark this page");
            }
        }
        m.add(0, 4, 0, "Bookmarks");
        m.add(0, 5, 0, "History");
        m.add(0, 6, 0, "Find in page");
        m.add(0, 7, 0, "Desktop site");
        m.getItem(m.size() - 1).setCheckable(true).setChecked(t != null && t.desktop);
        m.add(0, 8, 0, "Set homepage");
        android.view.Menu eng = m.addSubMenu(0, 9, 0, "Search engine");
        String[] engines = new String[]{"DuckDuckGo", "Google", "Bing", "Brave", "Startpage"};
        for (int i = 0; i < engines.length; i++) {
            eng.add(1, 100 + i, i, engines[i]).setCheckable(true).setChecked(DataStore.searchEngine().equals(engines[i]));
        }
        m.add(0, 10, 0, "Share page");
        m.add(0, 11, 0, "Clear browsing data");

        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                int id = item.getItemId();
                Tab t = cur();
                if (id == 1) {
                    newTab(DataStore.homepage(), false);
                } else if (id == 2) {
                    newTab(DataStore.homepage(), true);
                } else if (id == 3) {
                    if (t != null && DataStore.isBookmarked(t.url)) {
                        DataStore.removeBookmark(t.url);
                        toast("Bookmark removed");
                    } else if (t != null) {
                        DataStore.addBookmark(t.title, t.url);
                        toast("Bookmarked");
                    }
                } else if (id == 4) {
                    openBookmarks();
                } else if (id == 5) {
                    openHistory();
                } else if (id == 6) {
                    findInPage();
                } else if (id == 7) {
                    toggleDesktop();
                } else if (id == 8) {
                    if (t != null && t.url != null && t.url.startsWith("http")) {
                        DataStore.setHomepage(t.url);
                        toast("Homepage set");
                    } else {
                        toast("Open a page first");
                    }
                } else if (id == 10) {
                    if (t != null) shareText(t.url);
                } else if (id == 11) {
                    confirmClearData();
                } else if (id >= 100 && id < 105) {
                    DataStore.setSearchEngine(item.getTitle().toString());
                    toast("Search: " + item.getTitle());
                }
                return true;
            }
        });
        pm.show();
    }

    void confirmClearData() {
        new AlertDialog.Builder(this)
                .setMessage("Clear history, cookies and cache?")
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        DataStore.clearHistory();
                        CookieManager.getInstance().removeAllCookies(null);
                        for (Tab t : tabs) t.view.clearCache(true);
                        toast("Browsing data cleared");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    View wrapInput(EditText input) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(24), dp(8), dp(24), 0);
        wrap.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    void findInPage() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Find");
        new AlertDialog.Builder(this)
                .setTitle("Find in page")
                .setView(wrapInput(input))
                .setPositiveButton("Next", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Tab t = cur();
                        if (t != null) {
                            t.view.findAllAsync(input.getText().toString());
                            t.view.findNext(true);
                        }
                    }
                })
                .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Tab t = cur();
                        if (t != null) t.view.clearMatches();
                    }
                })
                .show();
    }

    // ---------- back / results ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fullscreenView != null) {
                hideFullscreen();
                return true;
            }
            if (listOverlay != null && listOverlay.host.getVisibility() == View.VISIBLE) {
                hideOverlays();
                return true;
            }
            if (switcher != null && switcher.getVisibility() == View.VISIBLE) {
                closeSwitcher();
                return true;
            }
            Tab t = cur();
            if (t != null && t.view.canGoBack()) {
                t.view.goBack();
                return true;
            }
            if (tabs.size() > 1) {
                closeTab(current);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1001 && fileCb != null) {
            awaitingFileChooser = false;
            ValueCallback<Uri[]> cb = fileCb;
            fileCb = null;
            cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
        }
    }
}
