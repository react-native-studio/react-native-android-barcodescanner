package com.barcodescanner;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.Collections;
import java.util.List;

public class RCTZBarCameraPackage implements ReactPackage {

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactApplicationContext) {
        return Collections.<NativeModule>singletonList(new RCTZBarCameraModule(reactApplicationContext));
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactApplicationContext) {
        //noinspection ArraysAsListWithZeroOrOneArgument
        return Collections.<ViewManager>singletonList(new RCTCameraViewManager());
    }

}
