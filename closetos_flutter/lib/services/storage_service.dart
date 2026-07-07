import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/models.dart';

/// Local persistence via Hive (metadata) + file system (images on mobile).
class StorageService {
  static const _wardrobeBox = 'wardrobe';
  static const _settingsBox = 'settings';

  Box? _wardrobe;
  Box? _settings;
  Directory? _imageDir;

  Future<void> init() async {
    await Hive.initFlutter();
    _wardrobe = await Hive.openBox(_wardrobeBox);
    _settings = await Hive.openBox(_settingsBox);

    if (!kIsWeb) {
      final appDir = await getApplicationDocumentsDirectory();
      _imageDir = Directory('${appDir.path}/closet_images');
      if (!await _imageDir!.exists()) {
        await _imageDir!.create(recursive: true);
      }
    }
  }

  bool get hasCompletedOnboarding =>
      _settings?.get('has_completed_onboarding', defaultValue: false) == true;

  Future<void> setOnboardingComplete() async {
    await _settings?.put('has_completed_onboarding', true);
  }

  String? get authToken => _settings?.get('auth_token') as String?;

  Future<void> setAuthToken(String? token) async {
    if (token == null) {
      await _settings?.delete('auth_token');
    } else {
      await _settings?.put('auth_token', token);
    }
  }

  String? get userId => _settings?.get('user_id') as String?;

  Future<void> setUserId(String? id) async {
    if (id == null) {
      await _settings?.delete('user_id');
    } else {
      await _settings?.put('user_id', id);
    }
  }

  String? get userName => _settings?.get('user_name') as String?;

  Future<void> setUserName(String? name) async {
    if (name == null) {
      await _settings?.delete('user_name');
    } else {
      await _settings?.put('user_name', name);
    }
  }

  String? get userEmail => _settings?.get('user_email') as String?;

  Future<void> setUserEmail(String? email) async {
    if (email == null) {
      await _settings?.delete('user_email');
    } else {
      await _settings?.put('user_email', email);
    }
  }

  Future<void> clearAuth() async {
    await setAuthToken(null);
    await setUserId(null);
    await setUserName(null);
    await setUserEmail(null);
  }

  String? get digitalTwinPath => _settings?.get('digital_twin_path') as String?;

  Future<void> setDigitalTwinPath(String path) async {
    await _settings?.put('digital_twin_path', path);
  }

  List<Garment> loadGarments() {
    final raw = _wardrobe?.get('garments') as String?;
    return decodeJsonList(raw, Garment.fromJson);
  }

  Future<void> saveGarments(List<Garment> garments) async {
    await _wardrobe?.put(
      'garments',
      encodeJsonList(garments.map((g) => g.toJson()).toList()),
    );
  }

  List<Outfit> loadOutfits() {
    final raw = _wardrobe?.get('outfits') as String?;
    return decodeJsonList(raw, Outfit.fromJson);
  }

  Future<void> saveOutfits(List<Outfit> outfits) async {
    await _wardrobe?.put(
      'outfits',
      encodeJsonList(outfits.map((o) => o.toJson()).toList()),
    );
  }

  UserTaste loadTaste() {
    final raw = _wardrobe?.get('taste') as String?;
    if (raw == null) return UserTaste();
    return UserTaste.fromJson(jsonDecode(raw) as Map<String, dynamic>);
  }

  Future<void> saveTaste(UserTaste taste) async {
    await _wardrobe?.put('taste', jsonEncode(taste.toJson()));
  }

  Map<String, String> loadTryOnCache() {
    final raw = _wardrobe?.get('tryon_cache') as String?;
    if (raw == null) return {};
    final map = jsonDecode(raw) as Map<String, dynamic>;
    return map.map((k, v) => MapEntry(k, v.toString()));
  }

  Future<void> saveTryOnCache(Map<String, String> cache) async {
    await _wardrobe?.put('tryon_cache', jsonEncode(cache));
  }

  List<ImportedImage> loadImportedImages() {
    final raw = _wardrobe?.get('imported_images') as String?;
    return decodeJsonList(raw, ImportedImage.fromJson);
  }

  Future<void> saveImportedImages(List<ImportedImage> list) async {
    await _wardrobe?.put(
      'imported_images',
      encodeJsonList(list.map((i) => i.toJson()).toList()),
    );
  }

  /// Saves base64 image. On mobile writes to disk; on web stores inline b64 ref.
  Future<String> saveImageFromBase64(String base64, String prefix) async {
    if (kIsWeb) {
      return b64Ref(prefix, base64);
    }
    final bytes = base64Decode(base64);
    final file = File(
      '${_imageDir!.path}/${prefix}_${DateTime.now().millisecondsSinceEpoch}.png',
    );
    await file.writeAsBytes(bytes);
    return file.path;
  }

  Future<String> saveImageBytes(Uint8List bytes, String prefix) async {
    if (kIsWeb) {
      return b64Ref(prefix, base64Encode(bytes));
    }
    final file = File(
      '${_imageDir!.path}/${prefix}_${DateTime.now().millisecondsSinceEpoch}.jpg',
    );
    await file.writeAsBytes(bytes);
    return file.path;
  }

  Future<String?> readFileAsBase64(String path) async {
    final inline = extractB64Payload(path);
    if (inline != null) return inline;
    if (kIsWeb) return null;
    final file = File(path);
    if (!await file.exists()) return null;
    return base64Encode(await file.readAsBytes());
  }

  Future<void> deleteImageAt(String path) async {
    if (kIsWeb || path.isEmpty || path.startsWith('http') || path.startsWith('b64://')) {
      return;
    }
    try {
      final file = File(path);
      if (await file.exists()) {
        await file.delete();
      }
    } catch (_) {}
  }

  static Future<SharedPreferences> prefs() => SharedPreferences.getInstance();
}
