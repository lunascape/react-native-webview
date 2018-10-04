package com.reactnativecommunity.webview.events;

import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.RCTEventEmitter;

/**
 * Event emitted when there is an error in loading.
 */

public class TopMessageEvent {
  public static final String CAPTURE_SCREEN_EVENT_NAME = "captureScreen";
  public static final String ON_MESSAGE_EVENT_NAME = "topMessage";

  public static PBWebViewGenericEvent createMessageEvent(int viewId, WritableMap eventData) {
    return new PBWebViewGenericEvent(viewId, ON_MESSAGE_EVENT_NAME, eventData);
  }
  public static PBWebViewGenericEvent createCaptureScreenEvent(int viewId, WritableMap eventData) {
    return new PBWebViewGenericEvent(viewId, CAPTURE_SCREEN_EVENT_NAME, eventData);
  }

  static class PBWebViewGenericEvent extends Event<PBWebViewGenericEvent> {

    public static final String EVENT_NAME = "topMessage";
    private WritableMap mData;
    public String eventName;

    public PBWebViewGenericEvent(int viewId, String eventName, WritableMap data) {
      super(viewId);
      mData = data;
      this.eventName = eventName;
    }

    @Override
    public String getEventName() {
      return this.eventName;
    }

    @Override
    public boolean canCoalesce() {
      return false;
    }

    @Override
    public short getCoalescingKey() {
      // All events for a given view can be coalesced.
      return 0;
    }

    @Override
    public void dispatch(RCTEventEmitter rctEventEmitter) {
      // WritableMap data = Arguments.createMap();
      // data.putString("data", mData);
      rctEventEmitter.receiveEvent(getViewTag(), getEventName(), mData);
    }
  }
}