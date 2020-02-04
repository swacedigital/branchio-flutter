package se.swacedigital.branchio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.app.Activity;

import androidx.annotation.NonNull;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import io.branch.referral.util.BranchEvent;
import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.branch.referral.Branch;
import io.branch.referral.BranchError;


/** BranchioPlugin */
public class BranchioPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler, PluginRegistry.NewIntentListener, ActivityAware {

  private static final String BranchFlutterInitialize = "init";
  private static final String BranchFlutterSetIdentity = "setIdentity";
  private static final String BranchFlutterLogEvent = "logEvent";
  private static final String BranchFlutterLatestReferringParams = "latestReferringParams";
  private static final String BranchFlutterUnsetIdentity = "unsetIdentity";

  private EventChannel eventChannel;
  private EventChannel.EventSink sink;
  private BroadcastReceiver broadcastReceiver;
  private Context applicationContext;
  private Activity activity;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    final MethodChannel channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "plugins.swace.se/branchio");
    final BranchioPlugin instance = new BranchioPlugin();
    channel.setMethodCallHandler(instance);
    instance.eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "plugins.swace.se/branchio-events");
    instance.eventChannel.setStreamHandler(instance);
    instance.setContext(flutterPluginBinding.getApplicationContext());
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "plugins.swace.se/branchio");
    final BranchioPlugin instance = new BranchioPlugin();
    channel.setMethodCallHandler(instance);
    instance.eventChannel = new EventChannel(registrar.messenger(), "plugins.swace.se/branchio-events");
    instance.eventChannel.setStreamHandler(instance);
    registrar.addNewIntentListener(instance);
    instance.setContext(registrar.activity().getApplicationContext());
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    Log.wtf("WTF:", "logging shit");
    if (call.method.equals(BranchFlutterInitialize)) {
      final boolean isTest = call.argument("is_test");
      final boolean debug = call.argument("debug");
      this.init(isTest, debug);
      result.success(null);
    } else if(call.method.equals(BranchFlutterUnsetIdentity)) {
      this.unsetIdentity();
    } else if(call.method.equals(BranchFlutterLatestReferringParams)) {
      result.success(this.latestReferringParams());
    } else if(call.method.equals(BranchFlutterLogEvent)) {
      final String name = call.argument("name");
      final Map<String, String> params = call.argument("params");
      this.logEvent(name, params);
      result.success(null);
    } else if(call.method.equals(BranchFlutterSetIdentity)) {
      final String userId = call.argument("user_id");
      this.setIdentity(userId);
      result.success(null);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    applicationContext = null;
    eventChannel.setStreamHandler(null);
    eventChannel = null;
  }

  private void setContext(Context context) {
    applicationContext = context;
  }

  private void init(boolean isTest, boolean debug) {
    Log.i("BRANCH_SDK", "Initializing branch with " + isTest + " and debug: " + debug);
    if(isTest) Branch.enableTestMode();
    if(debug) Branch.enableDebugMode();

    Branch.getAutoInstance(applicationContext).initSession(branchReferralInitListener);
  }

  private Map<String, Object> jsonObjectToHash(JSONObject json) {
    if (json == null) return null;
    final Map<String, Object> values = new HashMap();
    final Iterator<String> iterator = json.keys();
    while(iterator.hasNext()) {
      String key = iterator.next();
      try {
        values.put(key, json.get(key));
      }catch(Exception e) {
        Log.i("BRANCH SDK", "Unable to add entry to map: " + e);
      }
    }
    return values;
  }

  private Branch.BranchReferralInitListener branchReferralInitListener = new Branch.BranchReferralInitListener() {
                @Override
                public void onInitFinished(JSONObject referringParams, BranchError error) {
                  if (error == null) {
                    Log.i("BRANCH SDK", referringParams.toString());
                    if(this.sink != null) {
                      this.sink.success(jsonObjectToHash(referringParams));
                    }
                  } else {
                    Log.i("BRANCH SDK", error.getMessage());
                  }
                }
            };


  private void setIdentity(String userId) {
    Branch.getInstance().setIdentity(userId);
  }

  private void unsetIdentity() {
    Branch.getInstance().logout();
  }

  private void logEvent(String name, Map<String, String> params) {
    BranchEvent event = new BranchEvent(name);
    if(params != null) {
      for(String key: params.keySet()) {
        event.addCustomDataProperty(key, params.get(key));
      }
    }
    event.logEvent(applicationContext);
  }

  private Map<String, Object> latestReferringParams() {
    final Map<String, Object> values = new HashMap();
    final JSONObject json = Branch.getInstance().getLatestReferringParams();
    return jsonObjectToHash(json);
  }


  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    this.sink = events;
  }

  @Override
  public void onCancel(Object arguments) {
    applicationContext.unregisterReceiver(broadcastReceiver);
  }

  @Override
  public boolean onNewIntent(Intent intent) {
    Branch.getInstance().reInitSession(this.activity, branchReferralInitListener);
    sink.success(latestReferringParams());
    return false;
  }

  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    Log.wtf("WTF:", "logging shit");
    this.activity = binding.getActivity();
  }

  public void onDetachedFromActivity() {
    Log.wtf("WTF:", "logging shit");
    this.activity = null;
  }

  public void onDetachedFromActivityForConfigChanges() {
    Log.wtf("WTF:", "logging shit");
    this.activity = null;
  }

  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    Log.wtf("WTF:", "logging shit");
    this.activity = binding.getActivity();
  }
}
