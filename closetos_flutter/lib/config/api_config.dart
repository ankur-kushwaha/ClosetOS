import 'package:flutter/foundation.dart';

class ApiConfig {
  static const String productionUrl = 'https://closet.adboardtools.com';

  /// Local dev: Android emulator uses 10.0.2.2, web uses localhost.
  static String get baseUrl {
    if (kDebugMode) {
      return 'http://192.168.29.193:8000';
    }
    return productionUrl;
  }
}
