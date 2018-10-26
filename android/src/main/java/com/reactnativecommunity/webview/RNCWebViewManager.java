package com.reactnativecommunity.webview;

import android.annotation.TargetApi;
import android.content.Context;
import com.facebook.react.uimanager.UIManagerModule;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Picture;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.ViewGroup.LayoutParams;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.common.build.ReactBuildConfig;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.ContentSizeChangeEvent;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.reactnativecommunity.webview.events.TopLoadingErrorEvent;
import com.reactnativecommunity.webview.events.TopLoadingFinishEvent;
import com.reactnativecommunity.webview.events.TopLoadingStartEvent;
import com.reactnativecommunity.webview.events.TopMessageEvent;
import android.annotation.TargetApi;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView.HitTestResult;
import android.os.Message;
import android.widget.RelativeLayout;
import android.view.ViewGroup;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.webkit.HttpAuthHandler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.regex.Matcher;

import java.io.File;
import java.io.FileOutputStream;
import android.graphics.Canvas;
import android.os.Environment;
import android.widget.Toast;
import android.content.DialogInterface;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import android.webkit.URLUtil;
import android.webkit.DownloadListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.net.URISyntaxException;


/**
 * Manages instances of {@link WebView}
 *
 * Can accept following commands:
 *  - GO_BACK
 *  - GO_FORWARD
 *  - RELOAD
 *
 * {@link WebView} instances could emit following direct events:
 *  - topLoadingFinish
 *  - topLoadingStart
 *  - topLoadingError
 *
 * Each event will carry the following properties:
 *  - target - view's react tag
 *  - url - url set for the webview
 *  - loading - whether webview is in a loading state
 *  - title - title of the current page
 *  - canGoBack - boolean, whether there is anything on a history stack to go back
 *  - canGoForward - boolean, whether it is possible to request GO_FORWARD command
 */
@ReactModule(name = RNCWebViewManager.REACT_CLASS)
public class RNCWebViewManager extends SimpleViewManager<WebView> {

  protected static final String REACT_CLASS = "RNCWebView";

  protected static final String HTML_ENCODING = "UTF-8";
  protected static final String HTML_MIME_TYPE = "text/html";
  protected static final String BRIDGE_NAME = "__REACT_WEB_VIEW_BRIDGE";

  protected static final String HTTP_METHOD_POST = "POST";

  public static final int COMMAND_GO_BACK = 1;
  public static final int COMMAND_GO_FORWARD = 2;
  public static final int COMMAND_RELOAD = 3;
  public static final int COMMAND_STOP_LOADING = 4;
  public static final int COMMAND_POST_MESSAGE = 5;
  public static final int COMMAND_INJECT_JAVASCRIPT = 6;
  public static final int SET_GEOLOCATION_PERMISSION = 8;
  public static final int CAPTURE_SCREEN = 7;
  public static final int COMMAND_SEARCH_IN_PAGE = 9;
  public static final String DOWNLOAD_DIRECTORY = Environment.getExternalStorageDirectory() + "/Android/data/jp.co.lunascape.android.ilunascape/downloads/";

  // Use `webView.loadUrl("about:blank")` to reliably reset the view
  // state and release page resources (including any running JavaScript).
  protected static final String BLANK_URL = "about:blank";

  protected WebViewConfig mWebViewConfig;
  protected @Nullable WebView.PictureListener mPictureListener;

  private RNCWebViewPackage mPackage;
  
  public void setPackage(RNCWebViewPackage aPackage){
    this.mPackage = aPackage;
  }

  public RNCWebViewPackage getPackage(){
    return this.mPackage;
  }

  protected static class RNCWebViewClient extends WebViewClient {

    protected boolean mLastLoadFailed = false;
    protected @Nullable ReadableArray mUrlPrefixesForDefaultIntent;
    protected @Nullable List<Pattern> mOriginWhitelist;

    @Override
    public void onPageFinished(WebView webView, String url) {
      super.onPageFinished(webView, url);

      if (!mLastLoadFailed) {
        RNCWebView reactWebView = (RNCWebView) webView;
        reactWebView.callInjectedJavaScript();
        reactWebView.linkBridge();
        emitFinishEvent(webView, url);
      }
    }

    @Override
    public void onPageStarted(WebView webView, String url, Bitmap favicon) {
      super.onPageStarted(webView, url, favicon);
      mLastLoadFailed = false;

      dispatchEvent(
          webView,
          new TopLoadingStartEvent(
              webView.getId(),
              createWebViewEvent(webView, url)));
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      if (url.equals(BLANK_URL)) return false;

      // url blacklisting
      if (mUrlPrefixesForDefaultIntent != null && mUrlPrefixesForDefaultIntent.size() > 0) {
        ArrayList<Object> urlPrefixesForDefaultIntent =
            mUrlPrefixesForDefaultIntent.toArrayList();
        for (Object urlPrefix : urlPrefixesForDefaultIntent) {
          if (url.startsWith((String) urlPrefix)) {
            launchIntent(view.getContext(), url);
            return true;
          }
        }
      }

      if (url.startsWith("intent://")) {
        try {
            Context context = view.getContext();
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            if (intent != null) {
                view.stopLoading();
                PackageManager packageManager = context.getPackageManager();
                ResolveInfo info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (info != null) {
                    context.startActivity(intent);
                } else {
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    view.loadUrl(fallbackUrl);
                }
                return true;
            }
        } catch (URISyntaxException e) {
          e.printStackTrace();
        }
      }

      // if (mOriginWhitelist != null && shouldHandleURL(mOriginWhitelist, url)) {
      //   return false;
      // }
      // launchIntent(view.getContext(), url);
      // return true;

      // Handle CustomScheme and CustomOverrideUrlFormat functionalities
      Uri uri = Uri.parse(url);
      RNCWebView webView = (RNCWebView) view;
      if (uri == null) {
        return false;
      }
      String urlScheme = uri.getScheme();
      if (urlScheme.equalsIgnoreCase("http") || urlScheme.equalsIgnoreCase("https")
          || urlScheme.equalsIgnoreCase("file")) {
        String customOverrideUrlFormat = webView.getCustomOverrideUrlFormat();
        if (customOverrideUrlFormat == null || customOverrideUrlFormat.length() == 0
            || Pattern.compile(customOverrideUrlFormat) == null) {
          return false;
        }
        Pattern pattern = Pattern.compile(customOverrideUrlFormat);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
          Intent intent = new Intent(Intent.ACTION_VIEW);
          intent.setData(Uri.parse(url));
          webView.getContext().startActivity(intent);
          webView.shouldStartLoadWithRequest(url);
          webView.onOpenExternalApp(url);
          return true;
        }
        return false;
      } else {
        ArrayList<Object> customSchemes = webView.getCustomSchemes();
        try {
          // Checking supported scheme only
          if (customSchemes != null && customSchemes.contains(urlScheme)) {
            webView.shouldStartLoadWithRequest(url);
            webView.onOpenExternalApp(url);
            return true;
          } else if (urlScheme.equalsIgnoreCase("intent")) {
            // Get payload and scheme the intent wants to open
            Pattern pattern = Pattern.compile("^intent://(\\S*)#Intent;.*scheme=([a-zA-Z]+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
              String payload = matcher.group(1);
              String scheme = matcher.group(2);
              // Checking supported scheme only
              if (customSchemes != null && customSchemes.contains(scheme)) {
                String convertedUrl = scheme + "://" + payload;
                webView.shouldStartLoadWithRequest(convertedUrl);
                webView.onOpenExternalApp(url);
                return true;
              }
            }
          }
          Intent intent = new Intent(Intent.ACTION_VIEW, uri);
          intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          view.getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
          FLog.w(ReactConstants.TAG, "activity not found to handle uri scheme for: " + url, e);
        }
        return true;
      }
    }

    private void launchIntent(Context context, String url) {
      try {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        context.startActivity(intent);
      } catch (ActivityNotFoundException e) {
        FLog.w(ReactConstants.TAG, "activity not found to handle uri scheme for: " + url, e);
      }
    }

    private boolean shouldHandleURL(List<Pattern> originWhitelist, String url) {
      Uri uri = Uri.parse(url);
      String scheme = uri.getScheme() != null ? uri.getScheme() : "";
      String authority = uri.getAuthority() != null ? uri.getAuthority() : "";
      String urlToCheck = scheme + "://" + authority;
      for (Pattern pattern : originWhitelist) {
        if (pattern.matcher(urlToCheck).matches()) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void onReceivedError(
        WebView webView,
        int errorCode,
        String description,
        String failingUrl) {
      super.onReceivedError(webView, errorCode, description, failingUrl);
      mLastLoadFailed = true;

      // In case of an error JS side expect to get a finish event first, and then get an error event
      // Android WebView does it in the opposite way, so we need to simulate that behavior
      emitFinishEvent(webView, failingUrl);

      WritableMap eventData = createWebViewEvent(webView, failingUrl);
      eventData.putDouble("code", errorCode);
      eventData.putString("description", description);

      dispatchEvent(
          webView,
          new TopLoadingErrorEvent(webView.getId(), eventData));
    }

    protected void emitFinishEvent(WebView webView, String url) {
      dispatchEvent(
          webView,
          new TopLoadingFinishEvent(
              webView.getId(),
              createWebViewEvent(webView, url)));
    }

    protected WritableMap createWebViewEvent(WebView webView, String url) {
      WritableMap event = Arguments.createMap();
      event.putDouble("target", webView.getId());
      // Don't use webView.getUrl() here, the URL isn't updated to the new value yet in callbacks
      // like onPageFinished
      event.putString("url", url);
      event.putBoolean("loading", !mLastLoadFailed && webView.getProgress() != 100);
      event.putString("title", webView.getTitle());
      event.putBoolean("canGoBack", webView.canGoBack());
      event.putBoolean("canGoForward", webView.canGoForward());
      event.putDouble("progress", webView.getProgress());
      return event;
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, final HttpAuthHandler handler, String host, String realm) {
      AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
      LayoutInflater inflater = LayoutInflater.from(view.getContext());
      builder.setView(inflater.inflate(R.layout.authenticate, null));

      final AlertDialog alertDialog = builder.create();
      alertDialog.getWindow().setLayout(600, 400);
      alertDialog.show();
      TextView titleTv = (TextView) alertDialog.findViewById(R.id.tv_login);
      titleTv.setText(view.getResources().getString(R.string.login_title).replace("%s", host));
      Button btnLogin = (Button) alertDialog.findViewById(R.id.btn_login);
      Button btnCancel = (Button) alertDialog.findViewById(R.id.btn_cancel);
      final EditText userField = (EditText) alertDialog.findViewById(R.id.edt_username);
      final EditText passField = (EditText) alertDialog.findViewById(R.id.edt_password);
      btnCancel.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          alertDialog.dismiss();
          handler.cancel();
        }
      });
      btnLogin.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          alertDialog.dismiss();
          handler.proceed(userField.getText().toString(), passField.getText().toString());
        }
      });
    }

    public void setUrlPrefixesForDefaultIntent(ReadableArray specialUrls) {
      mUrlPrefixesForDefaultIntent = specialUrls;
    }

    public void setOriginWhitelist(List<Pattern> originWhitelist) {
      mOriginWhitelist = originWhitelist;
    }
  }

  /**
   * Subclass of {@link WebView} that implements {@link LifecycleEventListener} interface in order
   * to call {@link WebView#destroy} on activity destroy event and also to clear the client
   */
  protected static class RNCWebView extends WebView implements LifecycleEventListener {
    protected @Nullable String injectedJS;
    protected boolean messagingEnabled = false;
    protected @Nullable RNCWebViewClient mRNCWebViewClient;
    private ArrayList<Object> customSchemes = new ArrayList<>();
    private String customOverrideUrlFormat = "";
    private GeolocationPermissions.Callback _callback;

    protected class RNCWebViewBridge {
      RNCWebView mContext;

      RNCWebViewBridge(RNCWebView c) {
        mContext = c;
      }

      @JavascriptInterface
      public void postMessage(String message) {
        mContext.onMessage(message);
      }
    }

    /**
     * WebView must be created with an context of the current activity
     *
     * Activity Context is required for creation of dialogs internally by WebView
     * Reactive Native needed for access to ReactNative internal system functionality
     *
     */
    public RNCWebView(ThemedReactContext reactContext) {
      super(reactContext);
    }

    @Override
    public void onHostResume() {
      // do nothing
    }

    @Override
    public void onHostPause() {
      // do nothing
    }

    @Override
    public void onHostDestroy() {
      cleanupCallbacksAndDestroy();
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
      super.setWebViewClient(client);
      mRNCWebViewClient = (RNCWebViewClient)client;
    }

    public @Nullable RNCWebViewClient getRNCWebViewClient() {
      return mRNCWebViewClient;
    }

    public void setInjectedJavaScript(@Nullable String js) {
      injectedJS = js;
    }

    protected RNCWebViewBridge createRNCWebViewBridge(RNCWebView webView) {
      return new RNCWebViewBridge(webView);
    }

    public void setMessagingEnabled(boolean enabled) {
      if (messagingEnabled == enabled) {
        return;
      }

      messagingEnabled = enabled;
      if (enabled) {
        addJavascriptInterface(createRNCWebViewBridge(this), BRIDGE_NAME);
        linkBridge();
      } else {
        removeJavascriptInterface(BRIDGE_NAME);
      }
    }

    protected void evaluateJavascriptWithFallback(String script) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        evaluateJavascript(script, null);
        return;
      }

      try {
        loadUrl("javascript:" + URLEncoder.encode(script, "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        // UTF-8 should always be supported
        throw new RuntimeException(e);
      }
    }

    public void callInjectedJavaScript() {
      if (getSettings().getJavaScriptEnabled() &&
          injectedJS != null &&
          !TextUtils.isEmpty(injectedJS)) {
        evaluateJavascriptWithFallback("(function() {\n" + injectedJS + ";\n})();");
      }
    }

    public void linkBridge() {
      if (messagingEnabled) {
        if (ReactBuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          // See isNative in lodash
          String testPostMessageNative = "String(window.postMessage) === String(Object.hasOwnProperty).replace('hasOwnProperty', 'postMessage')";
          evaluateJavascript(testPostMessageNative, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
              if (value.equals("true")) {
                FLog.w(ReactConstants.TAG, "Setting onMessage on a WebView overrides existing values of window.postMessage, but a previous value was defined");
              }
            }
          });
        }

        evaluateJavascriptWithFallback("(" +
          "window.originalPostMessage = window.postMessage," +
          "window.postMessage = function(data) {" +
            BRIDGE_NAME + ".postMessage(String(data));" +
          "}" +
        ")");
      }
    }

    public void onMessage(String message) {
      WritableMap data = Arguments.createMap();
      data.putString("data", message);
      // dispatchEvent(this, TopMessageEvent.createMessageEvent(this.getId(), data));
    }

    public void setCustomSchemes(ArrayList<Object> schemes) {
      this.customSchemes = schemes;
    }

    public ArrayList<Object> getCustomSchemes() {
      return this.customSchemes;
    }

    public void setCustomOverrideUrlFormat(String format) {
      this.customOverrideUrlFormat = format;
    }

    public String getCustomOverrideUrlFormat() {
      return this.customOverrideUrlFormat;
    }

    public void shouldStartLoadWithRequest(String url) {
      // Sending event to JS side
      WritableMap event = Arguments.createMap();
      event.putDouble("target", this.getId());
      event.putString("url", url);
      event.putBoolean("loading", false);
      event.putDouble("progress", this.getProgress());
      event.putString("title", this.getTitle());
      event.putBoolean("canGoBack", this.canGoBack());
      event.putBoolean("canGoForward", this.canGoForward());
      dispatchEvent(this, TopMessageEvent.createStartRequestEvent(this.getId(), event));
    }

    public void onOpenExternalApp(String url) {
      WritableMap data = Arguments.createMap();
      data.putString("type", "onOpenExternalApp");
      data.putString("url", url);
      dispatchEvent(this, TopMessageEvent.createMessageEvent(this.getId(), data));
    }

    protected void cleanupCallbacksAndDestroy() {
      setWebViewClient(null);
      destroy();
    }

    public void captureScreen(String message) {
      final String fileName = System.currentTimeMillis() + ".jpg";

      File d = new File(DOWNLOAD_DIRECTORY);
      d.mkdirs();
      final String localFilePath = DOWNLOAD_DIRECTORY + fileName;
      boolean success = false;
      try {
        Picture picture = this.capturePicture();
        int width = message.equals("CAPTURE_SCREEN") ? this.getWidth() : picture.getWidth();
        int height = message.equals("CAPTURE_SCREEN") ? this.getHeight() : picture.getHeight();
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        picture.draw(c);

        FileOutputStream fos = new FileOutputStream(localFilePath);
        if (fos != null) {
          b.compress(Bitmap.CompressFormat.JPEG, 80, fos);
          fos.close();
        }
        success = true;
      } catch (Throwable t) {
        System.out.println(t);
      } finally {
        WritableMap event = Arguments.createMap();
        event.putDouble("target", this.getId());
        event.putBoolean("result", success);
        if (success) {
          event.putString("data", localFilePath);
        }
        dispatchEvent(this, TopMessageEvent.createCaptureScreenEvent(this.getId(), event));
      }
    }

    public void setGeolocationPermissionCallback(GeolocationPermissions.Callback callback) {
      this._callback = callback;
    }

    public void setGeolocationPermission(String origin, boolean allow) {
      if (this._callback != null) {
        this._callback.invoke(origin, allow, false);
        this.setGeolocationPermissionCallback(null);
      }
    }

    public void searchInPage(String keyword) {
      String[] words = keyword.split(" |　");
      String[] highlightColors = {
        "yellow", "cyan", "magenta", "greenyellow", "tomato", "lightskyblue"
      };
      try {
        InputStream fileInputStream;
        fileInputStream = this.getContext().getAssets().open("SearchWebView.js");
        byte[] readBytes = new byte[fileInputStream.available()];
        fileInputStream.read(readBytes);
        String jsString = new String(readBytes);

        StringBuilder sb = new StringBuilder();
        sb.append("MyApp_RemoveAllHighlights();");
        for (int i = 0; i < words.length; i++) {
          String color = i < highlightColors.length ? highlightColors[i] : highlightColors[highlightColors.length - 1];
          sb.append("MyApp_HighlightAllOccurencesOfString('" + words[i] + "','" + color + "');");
        }
        sb.append("alert('" + this.getContext().getString(R.string.dialog_found) + ": ' + MyApp_SearchResultCount);");
        sb.append("MyApp_ScrollToHighlightTop();");
        this.loadUrl("javascript:" + jsString + sb.toString());
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public RNCWebViewManager() {
    mWebViewConfig = new WebViewConfig() {
      public void configWebView(WebView webView) {
      }
    };
  }

  public RNCWebViewManager(WebViewConfig webViewConfig) {
    mWebViewConfig = webViewConfig;
  }

  @Override
  public String getName() {
    return REACT_CLASS;
  }

  protected RNCWebView createRNCWebViewInstance(ThemedReactContext reactContext) {
    return new RNCWebView(reactContext);
  }

  @Override
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  protected WebView createViewInstance(final ThemedReactContext reactContext) {
    final RNCWebView webView = createRNCWebViewInstance(reactContext);
    final RNCWebViewModule module = this.mPackage.getModule();
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onConsoleMessage(ConsoleMessage message) {
        if (ReactBuildConfig.DEBUG) {
          return super.onConsoleMessage(message);
        }
        // Ignore console logs in non debug builds.
        return true;
      }

      @Override
      public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
        callback.invoke(origin, true, false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
          final boolean remember = false;
          AlertDialog.Builder builder = new AlertDialog.Builder(webView.getContext());
          builder.setTitle(webView.getContext().getResources().getString(R.string.locations));
          builder.setMessage(webView.getContext().getResources().getString(R.string.locations_ask_permission))
                  .setCancelable(true).setPositiveButton(webView.getContext().getResources().getString(R.string.allow), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              // origin, allow, remember
              callback.invoke(origin, true, remember);
            }
          }).setNegativeButton(webView.getContext().getResources().getString(R.string.dont_allow), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              // origin, allow, remember
              callback.invoke(origin, false, remember);
            }
          });
          AlertDialog alert = builder.create();
          alert.show();
        } else {
          webView.setGeolocationPermissionCallback(callback);
          WritableMap event = Arguments.createMap();
          event.putDouble("target", webView.getId());
          event.putString("origin", origin);
          dispatchEvent(webView, TopMessageEvent.createLocationAskPermissionEvent(webView.getId(), event));
        }
      }
      protected void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
        module.openFileChooserView(uploadMsg, acceptType);
      }

      protected void openFileChooser(ValueCallback<Uri> uploadMsg) {
        module.openFileChooserView(uploadMsg, null);
      }

      protected void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
        module.openFileChooserView(uploadMsg, acceptType);
      }

      @TargetApi(Build.VERSION_CODES.LOLLIPOP)
      @Override
      public boolean onShowFileChooser (WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        return module.openFileChooserViewL(filePathCallback, fileChooserParams);
      }
      @Override
      public boolean onCreateWindow(final WebView webView, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        final WebView newView = new WebView(reactContext);
        newView.setWebViewClient(new WebViewClient() {
          @Override
          public void onPageStarted(WebView view, String url, Bitmap favicon) {
            WritableMap event = Arguments.createMap();
            event.putDouble("target", webView.getId());
            event.putString("url", url);
            event.putBoolean("loading", false);
            event.putDouble("progress", webView.getProgress());
            event.putString("title", webView.getTitle());
            event.putBoolean("canGoBack", webView.canGoBack());
            event.putBoolean("canGoForward", webView.canGoForward());
            dispatchEvent(webView, TopMessageEvent.createNewWindowEvent(webView.getId(), event));
            try {
              webView.removeView(newView);
              newView.destroy();
            } catch (Exception e) {
              // Exception if occurs here only means that newView was removed.
              // No need to do anything in this case
            }
          }
        });
        // Create dynamically a new view
        newView.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        webView.addView(newView);

        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newView);
        resultMsg.sendToTarget();
        return true;
      }

    });
    reactContext.addLifecycleEventListener(webView);
    mWebViewConfig.configWebView(webView);
    WebSettings settings = webView.getSettings();
    settings.setBuiltInZoomControls(true);
    settings.setDisplayZoomControls(false);
    settings.setDomStorageEnabled(true);
    settings.setSupportMultipleWindows(true);

    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      settings.setAllowFileAccessFromFileURLs(false);
      setAllowUniversalAccessFromFileURLs(webView, false);
    }
    setMixedContentMode(webView, "never");

    // Fixes broken full-screen modals/galleries due to body height being 0.
    webView.setLayoutParams(
            new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

    setGeolocationEnabled(webView, false);
    if (ReactBuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      WebView.setWebContentsDebuggingEnabled(true);
    }

    webView.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View view) {
        final RNCWebView webView = (RNCWebView) view;
        HitTestResult result = webView.getHitTestResult();
        final String extra = result.getExtra();
        final int type = result.getType();
        if (type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE || type == HitTestResult.SRC_ANCHOR_TYPE || type == HitTestResult.IMAGE_TYPE || type == HitTestResult.UNKNOWN_TYPE) {
          Handler handler = new Handler(webView.getHandler().getLooper()) {
            @Override
            public void handleMessage(Message msg) {
              String url = (String) msg.getData().get("url");
              String image_url = extra;
              if (url == null && image_url == null) {
                super.handleMessage(msg);
              } else {
                if (type == HitTestResult.SRC_ANCHOR_TYPE) {
                  image_url = "";
                }
                // when any downloaded image file is showing in webview - https://github.com/lunascape/react-native-wkwebview/pull/45
                if (type == HitTestResult.IMAGE_TYPE && url == null) {
                  url = image_url;
                }
                WritableMap data = Arguments.createMap();
                data.putString("type", "contextmenu");
                data.putString("url", url);
                data.putString("image_url", image_url);
                dispatchEvent(webView, TopMessageEvent.createMessageEvent(webView.getId(), data));
              }
            }
          };
          Message msg = handler.obtainMessage();
          webView.requestFocusNodeHref(msg);
        }
        return false; // return true to disable copy/paste action bar
      }
    });
    webView.setDownloadListener(new DownloadListener() {
      // @Override
      public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
        final String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
        WritableMap data = Arguments.createMap();
        data.putString("type", "downloadAction");
        data.putString("url", url);
        data.putString("contentDisposition", contentDisposition);
        data.putString("filename", filename);
        data.putString("mimetype", mimetype);
        dispatchEvent(webView, TopMessageEvent.createMessageEvent(webView.getId(), data));
      }
    });

    return webView;
  }

  @ReactProp(name = "javaScriptEnabled")
  public void setJavaScriptEnabled(WebView view, boolean enabled) {
    view.getSettings().setJavaScriptEnabled(enabled);
  }

  @ReactProp(name = "thirdPartyCookiesEnabled")
  public void setThirdPartyCookiesEnabled(WebView view, boolean enabled) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      CookieManager.getInstance().setAcceptThirdPartyCookies(view, enabled);
    }
  }

  @ReactProp(name = "scalesPageToFit")
  public void setScalesPageToFit(WebView view, boolean enabled) {
    view.getSettings().setUseWideViewPort(!enabled);
  }

  @ReactProp(name = "domStorageEnabled")
  public void setDomStorageEnabled(WebView view, boolean enabled) {
    view.getSettings().setDomStorageEnabled(enabled);
  }

  @ReactProp(name = "userAgent")
  public void setUserAgent(WebView view, @Nullable String userAgent) {
    if (userAgent != null) {
      // TODO(8496850): Fix incorrect behavior when property is unset (uA == null)
      view.getSettings().setUserAgentString(userAgent);
    }
  }

  @ReactProp(name = "mediaPlaybackRequiresUserAction")
  public void setMediaPlaybackRequiresUserAction(WebView view, boolean requires) {
    view.getSettings().setMediaPlaybackRequiresUserGesture(requires);
  }

  @ReactProp(name = "allowUniversalAccessFromFileURLs")
  public void setAllowUniversalAccessFromFileURLs(WebView view, boolean allow) {
    view.getSettings().setAllowUniversalAccessFromFileURLs(allow);
  }

  @ReactProp(name = "saveFormDataDisabled")
  public void setSaveFormDataDisabled(WebView view, boolean disable) {
    view.getSettings().setSaveFormData(!disable);
  }

  @ReactProp(name = "injectedJavaScript")
  public void setInjectedJavaScript(WebView view, @Nullable String injectedJavaScript) {
    ((RNCWebView) view).setInjectedJavaScript(injectedJavaScript);
  }

  @ReactProp(name = "messagingEnabled")
  public void setMessagingEnabled(WebView view, boolean enabled) {
    ((RNCWebView) view).setMessagingEnabled(enabled);
  }

  @ReactProp(name = "source")
  public void setSource(WebView view, @Nullable ReadableMap source) {
    if (source != null) {
      if (source.hasKey("html")) {
        String html = source.getString("html");
        if (source.hasKey("baseUrl")) {
          view.loadDataWithBaseURL(
              source.getString("baseUrl"), html, HTML_MIME_TYPE, HTML_ENCODING, null);
        } else {
          view.loadData(html, HTML_MIME_TYPE, HTML_ENCODING);
        }
        return;
      }
      if (source.hasKey("uri")) {
        String url = source.getString("uri");
        String previousUrl = view.getUrl();
        if (previousUrl != null && previousUrl.equals(url)) {
          return;
        }
        if (source.hasKey("method")) {
          String method = source.getString("method");
          if (method.equals(HTTP_METHOD_POST)) {
            byte[] postData = null;
            if (source.hasKey("body")) {
              String body = source.getString("body");
              try {
                postData = body.getBytes("UTF-8");
              } catch (UnsupportedEncodingException e) {
                postData = body.getBytes();
              }
            }
            if (postData == null) {
              postData = new byte[0];
            }
            view.postUrl(url, postData);
            return;
          }
        }
        HashMap<String, String> headerMap = new HashMap<>();
        if (source.hasKey("headers")) {
          ReadableMap headers = source.getMap("headers");
          ReadableMapKeySetIterator iter = headers.keySetIterator();
          while (iter.hasNextKey()) {
            String key = iter.nextKey();
            if ("user-agent".equals(key.toLowerCase(Locale.ENGLISH))) {
              if (view.getSettings() != null) {
                view.getSettings().setUserAgentString(headers.getString(key));
              }
            } else {
              headerMap.put(key, headers.getString(key));
            }
          }
        }
        view.loadUrl(url, headerMap);
        return;
      }
    }
    view.loadUrl(BLANK_URL);
  }

  @ReactProp(name = "onContentSizeChange")
  public void setOnContentSizeChange(WebView view, boolean sendContentSizeChangeEvents) {
    if (sendContentSizeChangeEvents) {
      view.setPictureListener(getPictureListener());
    } else {
      view.setPictureListener(null);
    }
  }

  @ReactProp(name = "mixedContentMode")
  public void setMixedContentMode(WebView view, @Nullable String mixedContentMode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (mixedContentMode == null || "never".equals(mixedContentMode)) {
        view.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
      } else if ("always".equals(mixedContentMode)) {
        view.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
      } else if ("compatibility".equals(mixedContentMode)) {
        view.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
      }
    }
  }

  @ReactProp(name = "urlPrefixesForDefaultIntent")
  public void setUrlPrefixesForDefaultIntent(
      WebView view,
      @Nullable ReadableArray urlPrefixesForDefaultIntent) {
    RNCWebViewClient client = ((RNCWebView) view).getRNCWebViewClient();
    if (client != null && urlPrefixesForDefaultIntent != null) {
      client.setUrlPrefixesForDefaultIntent(urlPrefixesForDefaultIntent);
    }
  }

  @ReactProp(name = "allowFileAccess")
  public void setAllowFileAccess(
    WebView view,
    @Nullable Boolean allowFileAccess) {
    view.getSettings().setAllowFileAccess(allowFileAccess != null && allowFileAccess);
  }

  @ReactProp(name = "geolocationEnabled")
  public void setGeolocationEnabled(
    WebView view,
    @Nullable Boolean isGeolocationEnabled) {
    view.getSettings().setGeolocationEnabled(isGeolocationEnabled != null && isGeolocationEnabled);
  }

  @ReactProp(name = "originWhitelist")
  public void setOriginWhitelist(
    WebView view,
    @Nullable ReadableArray originWhitelist) {
    RNCWebViewClient client = ((RNCWebView) view).getRNCWebViewClient();
    if (client != null && originWhitelist != null) {
      List<Pattern> whiteList = new LinkedList<>();
      for (int i = 0 ; i < originWhitelist.size() ; i++) {
        whiteList.add(Pattern.compile(originWhitelist.getString(i)));
      }
      client.setOriginWhitelist(whiteList);
    }
  }

  @ReactProp(name = "customSchemes")
  public void setCustomSchemes(WebView view, ReadableArray schemes) {
    ((RNCWebView)view).setCustomSchemes(schemes.toArrayList());
  }

  @ReactProp(name = "customOverrideUrlFormat")
  public void setCustomOverrideUrlFormat(WebView view, String customOverrideUrlFormat) {
    ((RNCWebView)view).setCustomOverrideUrlFormat(customOverrideUrlFormat);
  }

  @Override
  protected void addEventEmitters(ThemedReactContext reactContext, WebView view) {
    // Do not register default touch emitter and let WebView implementation handle touches
    view.setWebViewClient(new RNCWebViewClient());
  }

  @Override
  public @Nullable Map<String, Integer> getCommandsMap() {
    Map<String, Integer> map = MapBuilder.of(
        "goBack", COMMAND_GO_BACK,
        "goForward", COMMAND_GO_FORWARD,
        "reload", COMMAND_RELOAD,
        "stopLoading", COMMAND_STOP_LOADING,
        "postMessage", COMMAND_POST_MESSAGE,
        "injectJavaScript", COMMAND_INJECT_JAVASCRIPT,
        "captureScreen", CAPTURE_SCREEN
    );
    map.put("setGeolocationPermission", SET_GEOLOCATION_PERMISSION);
    map.put("findInPage", COMMAND_SEARCH_IN_PAGE);
    return map;
  }

  @Override
  public void receiveCommand(WebView root, int commandId, @Nullable ReadableArray args) {
    switch (commandId) {
      case COMMAND_GO_BACK:
        root.goBack();
        break;
      case COMMAND_GO_FORWARD:
        root.goForward();
        break;
      case COMMAND_RELOAD:
        root.reload();
        break;
      case COMMAND_STOP_LOADING:
        root.stopLoading();
        break;
      case COMMAND_POST_MESSAGE:
        try {
          RNCWebView reactWebView = (RNCWebView) root;
          JSONObject eventInitDict = new JSONObject();
          eventInitDict.put("data", args.getString(0));
          reactWebView.evaluateJavascriptWithFallback("(function () {" +
            "var event;" +
            "var data = " + eventInitDict.toString() + ";" +
            "try {" +
              "event = new MessageEvent('message', data);" +
            "} catch (e) {" +
              "event = document.createEvent('MessageEvent');" +
              "event.initMessageEvent('message', true, true, data.data, data.origin, data.lastEventId, data.source);" +
            "}" +
            "document.dispatchEvent(event);" +
          "})();");
        } catch (JSONException e) {
          throw new RuntimeException(e);
        }
        break;
      case COMMAND_INJECT_JAVASCRIPT:
        RNCWebView reactWebView = (RNCWebView) root;
        reactWebView.evaluateJavascriptWithFallback(args.getString(0));
        break;
      case CAPTURE_SCREEN:
        ((RNCWebView) root).captureScreen(args.getString(0));
        break;
      case SET_GEOLOCATION_PERMISSION:
        if (args.size() == 2) {
          ((RNCWebView) root).setGeolocationPermission(args.getString(0), args.getBoolean(1));
        }
        break;
      case COMMAND_SEARCH_IN_PAGE:
        ((RNCWebView) root).searchInPage(args.getString(0));
        break;

    }
  }

  @Override
  public void onDropViewInstance(WebView webView) {
    super.onDropViewInstance(webView);
    ((ThemedReactContext) webView.getContext()).removeLifecycleEventListener((RNCWebView) webView);
    ((RNCWebView) webView).cleanupCallbacksAndDestroy();
  }

  protected WebView.PictureListener getPictureListener() {
    if (mPictureListener == null) {
      mPictureListener = new WebView.PictureListener() {
        @Override
        public void onNewPicture(WebView webView, Picture picture) {
          dispatchEvent(
            webView,
            new ContentSizeChangeEvent(
              webView.getId(),
              webView.getWidth(),
              webView.getContentHeight()));
        }
      };
    }
    return mPictureListener;
  }

  protected static void dispatchEvent(WebView webView, Event event) {
    ReactContext reactContext = (ReactContext) webView.getContext();
    EventDispatcher eventDispatcher =
      reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
    eventDispatcher.dispatchEvent(event);
  }

  @Override
  public @Nullable Map getExportedCustomDirectEventTypeConstants() {
      return MapBuilder.of(
        TopMessageEvent.CAPTURE_SCREEN_EVENT_NAME, MapBuilder.of("registrationName", "onCaptureScreen"),
        TopMessageEvent.CREATE_WINDOW_EVENT_NAME, MapBuilder.of("registrationName", "onShouldCreateNewWindow"),
        TopMessageEvent.ASK_LOCATION_PERMISSION_EVENT_NAME, MapBuilder.of("registrationName", "onLocationAskPermission"),
        TopMessageEvent.SHOULD_START_REQUEST_EVENT_NAME, MapBuilder.of("registrationName", "onShouldStartLoadWithRequest"),
        TopMessageEvent.ON_MESSAGE_EVENT_NAME, MapBuilder.of("registrationName", "onLsMessage")
      );
  }
}
