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
import io.flutter.plugin.common.BinaryMessenger;
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
    BranchioPlugin.setup(flutterPluginBinding.getApplicationContext(), flutterPluginBinding.getBinaryMessenger());
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
    BranchioPlugin instance = BranchioPlugin.setup(registrar.activity().getApplicationContext(), registrar.messenger());
    registrar.addNewIntentListener(instance);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
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

  private static BranchioPlugin setup(Context context, BinaryMessenger messenger) {
    final MethodChannel channel = new MethodChannel(messenger, "plugins.swace.se/branchio");
    final EventChannel eventChannel = new EventChannel(messenger, "plugins.swace.se/branchio-events");
    final BranchioPlugin instance = new BranchioPlugin();
    channel.setMethodCallHandler(instance);
    eventChannel.setStreamHandler(instance);
    instance.eventChannel = eventChannel;
    instance.setContext(context);
    return instance;
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
                    if(sink != null) {
                      sink.success(jsonObjectToHash(referringParams));
                    }
                  } else {
                    Log.i("BRANCH SDK", error.getMessage());
                  }
                }
            };


  private void setIdentity(String userId) {
    Branch.getInstance(applicationContext).setIdentity(userId);
  }

  private void unsetIdentity() {
    Branch.getInstance(applicationContext).logout();
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
    final JSONObject json = Branch.getInstance(applicationContext).getLatestReferringParams();
    return jsonObjectToHash(json);
  }


  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    sink = events;
  }

  @Override
  public void onCancel(Object arguments) {
    sink = null;
  }

  @Override
  public boolean onNewIntent(Intent intent) {
    if(activity != null && branchReferralInitListener != null) {
      final boolean reinitialized = Branch.getInstance(applicationContext).reInitSession(activity, branchReferralInitListener);
      if(reinitialized) {
        sink.success(latestReferringParams());
      }
    }
    return false;
  }

  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    binding.addOnNewIntentListener(this);
    activity = binding.getActivity();
  }

  public void onDetachedFromActivity() {
    activity = null;
  }

  public void onDetachedFromActivityForConfigChanges() {
    activity = null;
  }

  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    binding.addOnNewIntentListener(this);
    activity = binding.getActivity();
  }
}
