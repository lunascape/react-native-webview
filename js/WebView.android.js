/**
 * Copyright (c) 2015-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * @format
 * @flow
 */

'use strict';

import React from 'react';

import ReactNative from 'react-native';
import {
  ActivityIndicator,
  StyleSheet,
  UIManager,
  View,
  Image,
  requireNativeComponent,
} from 'react-native';

import invariant from 'fbjs/lib/invariant';
import keyMirror from 'fbjs/lib/keyMirror';

import WebViewShared from './WebViewShared';
import type {
  WebViewEvent,
  WebViewError,
  WebViewErrorEvent,
  WebViewMessageEvent,
  WebViewNavigation,
  WebViewNavigationEvent,
  WebViewSharedProps,
  WebViewSource,
  WebViewProgressEvent,
} from './WebViewTypes';

const resolveAssetSource = Image.resolveAssetSource;
var RCT_WEBVIEW_REF = 'webview';

const WebViewState = keyMirror({
  IDLE: null,
  LOADING: null,
  ERROR: null,
});

const defaultRenderLoading = () => (
  <View style={styles.loadingView}>
    <ActivityIndicator style={styles.loadingProgressBar} />
  </View>
);

type State = {|
  viewState: WebViewState,
  lastErrorEvent: ?WebViewError,
|};

/**
 * Renders a native WebView.
 */
class WebView extends React.Component<WebViewSharedProps, State> {
  static defaultProps = {
    overScrollMode: 'always',
    javaScriptEnabled: true,
    thirdPartyCookiesEnabled: true,
    scalesPageToFit: true,
    allowFileAccess: false,
    saveFormDataDisabled: false,
    originWhitelist: WebViewShared.defaultOriginWhitelist,
  };

  state = {
    viewState: this.props.startInLoadingState ? WebViewState.LOADING : WebViewState.IDLE,
    lastErrorEvent: null,
  };

  webViewRef = React.createRef();

  render() {
    let otherView = null;

    if (this.state.viewState === WebViewState.LOADING) {
      otherView = (this.props.renderLoading || defaultRenderLoading)();
    } else if (this.state.viewState === WebViewState.ERROR) {
      const errorEvent = this.state.lastErrorEvent;
      invariant(errorEvent != null, 'lastErrorEvent expected to be non-null');
      otherView =
        this.props.renderError &&
        this.props.renderError(
          errorEvent.domain,
          errorEvent.code,
          errorEvent.description,
        );
    } else if (this.state.viewState !== WebViewState.IDLE) {
      console.error(
        'RNCWebView invalid state encountered: ' + this.state.viewState,
      );
    }

    const webViewStyles = [styles.container, this.props.style];
    if (
      this.state.viewState === WebViewState.LOADING ||
      this.state.viewState === WebViewState.ERROR
    ) {
      // if we're in either LOADING or ERROR states, don't show the webView
      webViewStyles.push(styles.hidden);
    }

    let source: WebViewSource = this.props.source || {};
    if (!this.props.source && this.props.html) {
      source = { html: this.props.html };
    } else if (!this.props.source && this.props.url) {
      source = { uri: this.props.url };
    }

    if (source.method === 'POST' && source.headers) {
      console.warn(
        'WebView: `source.headers` is not supported when using POST.',
      );
    } else if (source.method === 'GET' && source.body) {
      console.warn('WebView: `source.body` is not supported when using GET.');
    }

    const nativeConfig = this.props.nativeConfig || {};

    const originWhitelist = (this.props.originWhitelist || []).map(
      WebViewShared.originWhitelistToRegex,
    );

    let NativeWebView = nativeConfig.component || RNCWebView;

    const webView = (
      <NativeWebView
        // ref={this.webViewRef}
        ref={RCT_WEBVIEW_REF}
        key="webViewKey"
        style={webViewStyles}
        source={resolveAssetSource(source)}
        scalesPageToFit={this.props.scalesPageToFit}
        allowFileAccess={this.props.allowFileAccess}
        injectedJavaScript={this.props.injectedJavaScript}
        onShouldCreateNewWindow={this.onShouldCreateNewWindow}
        userAgent={this.props.userAgent}
        customSchemes={this.props.customSchemes}
        customOverrideUrlFormat={this.props.customOverrideUrlFormat}
        javaScriptEnabled={this.props.javaScriptEnabled}
        thirdPartyCookiesEnabled={this.props.thirdPartyCookiesEnabled}
        domStorageEnabled={this.props.domStorageEnabled}
        messagingEnabled={typeof this.props.onMessage === 'function'}
        onMessage={this.onMessage}
        overScrollMode={this.props.overScrollMode}
        onLsMessage={this.onLsMessage}
        contentInset={this.props.contentInset}
        onShouldStartLoadWithRequest={this.props.onShouldStartLoadWithRequest}
        automaticallyAdjustContentInsets={
          this.props.automaticallyAdjustContentInsets
        }
        onContentSizeChange={this.props.onContentSizeChange}
        onLoadingStart={this.onLoadingStart}
        onLoadingFinish={this.onLoadingFinish}
        onLoadingError={this.onLoadingError}
        onLoadingProgress={this.onLoadingProgress}
        testID={this.props.testID}
        geolocationEnabled={this.props.geolocationEnabled}
        mediaPlaybackRequiresUserAction={
          this.props.mediaPlaybackRequiresUserAction
        }
        allowUniversalAccessFromFileURLs={
          this.props.allowUniversalAccessFromFileURLs
        }
        onCaptureScreen={this.onCaptureScreen}
        onLocationAskPermission={this.onLocationAskPermission}
        originWhitelist={originWhitelist}
        mixedContentMode={this.props.mixedContentMode}
        saveFormDataDisabled={this.props.saveFormDataDisabled}
        urlPrefixesForDefaultIntent={this.props.urlPrefixesForDefaultIntent}
        {...nativeConfig.props}
      />
    );

    // This part is to handle the RefreshControl or Pull to Refresh webview feature.
    const refreshControl = this.props.refreshControl;
    if (refreshControl) {
      return React.cloneElement(
        refreshControl,
        {},
        <View style={styles.container}>
          {webView}
          {otherView}
        </View>
      );
    }

    return (
      <View style={styles.container}>
        {webView}
        {otherView}
      </View>
    );
  }

  goForward = () => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.goForward,
      null,
    );
  };

  goBack = () => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.goBack,
      null,
    );
  };

  reload = () => {
    this.setState({
      viewState: WebViewState.LOADING,
    });
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.reload,
      null,
    );
  };

  stopLoading = () => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.stopLoading,
      null,
    );
  };

  postMessage = (data: string) => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.postMessage,
      [String(data)],
    );
  };

  /**
   * Injects a javascript string into the referenced WebView. Deliberately does not
   * return a response because using eval() to return a response breaks this method
   * on pages with a Content Security Policy that disallows eval(). If you need that
   * functionality, look into postMessage/onMessage.
   */
  injectJavaScript = (data: string) => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.injectJavaScript,
      [data],
    );
  };

  // Bookmarklet handling
  evaluateJavaScript = (js) => {
    this.injectJavaScript(js);
  };

  /**
   * Capture Screen the current page.
   */
  captureScreen = (data: string) => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.captureScreen,
      [String(data)]
    );
  };

  /**
   * Make a CallBack message when Ask for Geolocation access
   */
  setGeolocationPermission = (origin: string, allow: boolean) => {
    console.log(origin);
    console.log(allow);
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.setGeolocationPermission,
      [String(origin), Boolean(allow)]
    );
  };

  /**
   * We return an event with a bunch of fields including:
   *  url, title, loading, canGoBack, canGoForward
   */
  updateNavigationState = (event: WebViewNavigationEvent) => {
    if (this.props.onNavigationStateChange) {
      this.props.onNavigationStateChange(event.nativeEvent);
    }
  };

  onLocationAskPermission = (event: WebViewEvent) => {
    if (this.props.onLocationAskPermission) {
      this.props.onLocationAskPermission(event.nativeEvent);
    }
  }
  /**
   * Allows custom handling of window.open() by a JS handler. Return true
   * or false from this method to use default behavior.
   * @platform ios
  */
  onShouldCreateNewWindow = (event: any) => {
    console.log(event.nativeEvent);
    if (this.props.onShouldCreateNewWindow) {
      this.props.onShouldCreateNewWindow(event.nativeEvent);
    }
  };

  onCaptureScreen = (event: WebViewEvent) => {
    if (this.props.onCaptureScreen) {
      this.props.onCaptureScreen(event.nativeEvent);
    }
  };
  /**
   * Find keyword in the current page.
   */
  findInPage = (data: string, callback: any) => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.RNCWebView.Commands.findInPage,
      [String(data), callback]
    );
  };

  getWebViewHandle = () => {
    // return ReactNative.findNodeHandle(this.webViewRef.current);
    return ReactNative.findNodeHandle(this.refs[RCT_WEBVIEW_REF]);
  };

  onLoadingStart = (event: WebViewNavigationEvent) => {
    const onLoadStart = this.props.onLoadStart;
    onLoadStart && onLoadStart(event);
    this.updateNavigationState(event);
  };

  onLoadingError = (event: WebViewErrorEvent) => {
    event.persist(); // persist this event because we need to store it
    const { onError, onLoadEnd } = this.props;
    onError && onError(event);
    onLoadEnd && onLoadEnd(event);
    console.warn('Encountered an error loading page', event.nativeEvent);

    this.setState({
      lastErrorEvent: event.nativeEvent,
      viewState: WebViewState.ERROR,
    });
  };

  onLoadingFinish = (event: WebViewNavigationEvent) => {
    const { onLoad, onLoadEnd } = this.props;
    onLoad && onLoad(event);
    onLoadEnd && onLoadEnd(event);
    this.setState({
      viewState: WebViewState.IDLE,
    });
    this.updateNavigationState(event);
  };

  onMessage = (event: WebViewMessageEvent) => {
    const { onMessage } = this.props;
    onMessage && onMessage(event);
  };
  
  onLoadingProgress = (event: WebViewProgressEvent) => {
    const { onLoadProgress} = this.props;
    onLoadProgress && onLoadProgress(event);
  }

  onLsMessage = (event: WebViewMessageEvent) => {
    const { onLsMessage } = this.props;
    onLsMessage && onLsMessage(event);
  };

}

const RNCWebView = requireNativeComponent('RNCWebView');

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  hidden: {
    height: 0,
    flex: 0, // disable 'flex:1' when hiding a View
  },
  loadingView: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingProgressBar: {
    height: 20,
  },
});

module.exports = WebView;
