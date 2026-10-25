package com.ahmad.nutritionplanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_SAVE_BACKUP = 701;
    private static final int REQUEST_IMPORT_FILE = 702;
    private WebView webView;
    private ValueCallback<Uri[]> uploadCallback;
    private String pendingText = "";
    private String pendingFileName = "nutrition-backup.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(Uri.parse(url));
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try {
                    startActivityForResult(Intent.createChooser(intent, "اختيار نسخة احتياطية"), REQUEST_IMPORT_FILE);
                } catch (Exception ex) {
                    uploadCallback = null;
                    Toast.makeText(MainActivity.this, "تعذر فتح مدير الملفات", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void downloadText(String text, String suggestedName) {
            pendingText = text == null ? "" : text;
            pendingFileName = (suggestedName == null || suggestedName.trim().isEmpty())
                    ? "nutrition-backup.json" : suggestedName;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, pendingFileName);
                try {
                    startActivityForResult(intent, REQUEST_SAVE_BACKUP);
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "تعذر فتح نافذة حفظ النسخة", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void printPdf() {
            runOnUiThread(() -> {
                try {
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (printManager == null) throw new IllegalStateException("Printing unavailable");
                    String jobName = "خطة غذائية - مخطط أحمد";
                    printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), new PrintAttributes.Builder().build());
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "تعذر فتح خدمة الطباعة على هذا الجهاز", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_FILE) {
            if (uploadCallback != null) {
                Uri[] results = resultCode == RESULT_OK && data != null && data.getData() != null
                        ? new Uri[]{data.getData()} : null;
                uploadCallback.onReceiveValue(results);
                uploadCallback = null;
            }
            return;
        }
        if (requestCode == REQUEST_SAVE_BACKUP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("No output stream");
                output.write(pendingText.getBytes(StandardCharsets.UTF_8));
                output.flush();
                Toast.makeText(this, "تم حفظ النسخة الاحتياطية ✅", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Toast.makeText(this, "فشل حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
