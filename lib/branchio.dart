import 'dart:async';

import 'package:flutter/services.dart';

class Branchio {
  static final MethodChannel _channel = MethodChannel('plugins.swace.se/branchio');
  static final EventChannel _eventChannel =
      EventChannel('plugins.swace.se/branchio-events');

  static Stream<Map<String, dynamic>> _referringParamsStream;

  static Stream<Map<String, dynamic>> get onReferringParamsChanged {
    if (_referringParamsStream == null) {
      _referringParamsStream = _eventChannel
          .receiveBroadcastStream()
          .map((data) => Map<String, dynamic>.from(data));
    }
    return _referringParamsStream;
  }

  static Future<void> init({bool isTest = false, bool debug = false}) async {
    await _channel.invokeMethod('init', {'is_test': isTest, 'debug': debug});
  }

  static Future<void> setIdentity(String userId) async {
    await _channel.invokeMethod("setIdentity", {'user_id': userId});
  }

  static Future<void> logStandardEvent(String name) async {
    await _channel.invokeMethod('logStandardEvent', {'name': name});
  }

  static Future<void> logEvent(String name, {Map<String, String> params}) async {
    await _channel.invokeMethod('logEvent', {'name': name, 'params': params});
  }

  static Future<Map<String, dynamic>> latestReferringParams() async {
    return _channel.invokeMapMethod('latestReferringParams');
  }

  static Future<void> unsetIdentity() async {
    return _channel.invokeMethod('unsetIdentity');
  }
}
