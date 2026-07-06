import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

import '../config/api_config.dart';
import '../models/models.dart';

class ApiService {
  ApiService({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;
  String? lastError;
  String? _authToken;

  void setAuthToken(String? token) => _authToken = token;

  bool get hasAuth => _authToken != null;

  Map<String, String> _headers({bool json = false}) {
    final headers = <String, String>{};
    if (json) headers['Content-Type'] = 'application/json';
    if (_authToken != null) {
      headers['Authorization'] = 'Bearer $_authToken';
    }
    return headers;
  }

  Uri _uri(String path) => Uri.parse('${ApiConfig.baseUrl}$path');

  Future<bool> healthCheck() async {
    try {
      final res = await _client.get(_uri('/')).timeout(const Duration(seconds: 5));
      return res.statusCode >= 200 && res.statusCode < 400;
    } catch (_) {
      return false;
    }
  }

  Future<AuthResult?> signup({
    required String name,
    required String email,
    required String password,
  }) async {
    lastError = null;
    try {
      final res = await _client
          .post(
            _uri('/auth/signup'),
            headers: _headers(json: true),
            body: jsonEncode({
              'name': name,
              'email': email,
              'password': password,
            }),
          )
          .timeout(const Duration(seconds: 15));

      if (res.statusCode == 409) {
        lastError = 'This email is already registered';
        return null;
      }
      if (res.statusCode != 200) {
        lastError = _parseError(res);
        return null;
      }

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      return AuthResult(
        token: root['token'] as String,
        user: AppUser.fromJson(root['user'] as Map<String, dynamic>),
      );
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<AuthResult?> login({
    required String email,
    required String password,
  }) async {
    lastError = null;
    try {
      final res = await _client
          .post(
            _uri('/auth/login'),
            headers: _headers(json: true),
            body: jsonEncode({
              'email': email,
              'password': password,
            }),
          )
          .timeout(const Duration(seconds: 15));

      if (res.statusCode == 401) {
        lastError = 'Invalid email or password';
        return null;
      }
      if (res.statusCode != 200) {
        lastError = _parseError(res);
        return null;
      }

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      return AuthResult(
        token: root['token'] as String,
        user: AppUser.fromJson(root['user'] as Map<String, dynamic>),
      );
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<AppUser?> fetchCurrentUser() async {
    lastError = null;
    try {
      final res = await _client
          .get(_uri('/auth/me'), headers: _headers())
          .timeout(const Duration(seconds: 10));

      if (res.statusCode != 200) {
        lastError = _parseError(res);
        return null;
      }

      return AppUser.fromJson(
        jsonDecode(res.body) as Map<String, dynamic>,
      );
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<AppUser?> updateProfile({
    required UserTaste taste,
    String? name,
    String? email,
    bool? onboardingCompleted,
  }) async {
    lastError = null;
    try {
      final body = <String, dynamic>{
        'taste': taste.toJson(),
        if (onboardingCompleted != null) 'onboarding_completed': onboardingCompleted,
        if (name != null) 'name': name,
        if (email != null) 'email': email,
      };

      final res = await _client
          .patch(
            _uri('/auth/onboarding'),
            headers: _headers(json: true),
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 15));

      if (res.statusCode != 200) {
        lastError = _parseError(res);
        return null;
      }

      return AppUser.fromJson(
        jsonDecode(res.body) as Map<String, dynamic>,
      );
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<AppUser?> updateOnboarding(UserTaste taste) async {
    return updateProfile(taste: taste, onboardingCompleted: true);
  }

  Future<String?> uploadSelfie(Uint8List imageBytes, String filename) async {
    lastError = null;
    try {
      final request = http.MultipartRequest('POST', _uri('/auth/selfie'));
      if (_authToken != null) {
        request.headers['Authorization'] = 'Bearer $_authToken';
      }
      request.files.add(http.MultipartFile.fromBytes(
        'file',
        imageBytes,
        filename: filename,
        contentType: MediaType('image', 'jpeg'),
      ));

      final streamed = await request.send().timeout(const Duration(seconds: 60));
      final res = await http.Response.fromStream(streamed);
      if (res.statusCode != 200) {
        lastError = 'Selfie upload failed (${res.statusCode})';
        return null;
      }

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      return root['selfie_url'] as String?;
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  String _parseError(http.Response res) {
    try {
      final root = jsonDecode(res.body) as Map<String, dynamic>;
      final detail = root['detail'];
      if (detail is String) return detail;
      if (detail is List && detail.isNotEmpty) {
        return detail.first['msg']?.toString() ?? 'Request failed';
      }
    } catch (_) {}
    return 'Request failed (${res.statusCode})';
  }

  Future<List<DetectedBox>?> detectGarments(Uint8List imageBytes, String filename) async {
    lastError = null;
    try {
      final request = http.MultipartRequest('POST', _uri('/yolo-world/detect'));
      request.files.add(http.MultipartFile.fromBytes(
        'file',
        imageBytes,
        filename: filename,
        contentType: MediaType('image', 'jpeg'),
      ));

      final streamed = await request.send().timeout(const Duration(seconds: 60));
      final res = await http.Response.fromStream(streamed);
      if (res.statusCode != 200) {
        lastError = 'Detection failed (${res.statusCode})';
        return null;
      }

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      final sourceImageId = root['source_image_id'] as String?;
      final bboxes = root['bboxes'] as List<dynamic>;
      final labels = root['labels'] as List<dynamic>;
      final scores = root['scores'] as List<dynamic>;
      final crops = root['crops_base64'] as List<dynamic>;

      final boxes = <DetectedBox>[];
      for (var i = 0; i < bboxes.length; i++) {
        boxes.add(DetectedBox(
          bbox: (bboxes[i] as List<dynamic>)
              .map((e) => (e as num).round())
              .toList(),
          label: labels.length > i ? labels[i].toString() : 'Garment ${i + 1}',
          score: scores.length > i ? (scores[i] as num).toDouble() : 0,
          cropBase64: crops.length > i ? crops[i].toString() : '',
          sourceImageId: sourceImageId,
        ));
      }
      return boxes;
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<Map<String, dynamic>?> normalizeGarment(
    String cropBase64,
    String label, {
    String? garmentId,
  }) async {
    lastError = null;
    try {
      final payload = <String, dynamic>{
        'crop_base64': cropBase64,
        'label': label,
        if (garmentId != null) 'garment_id': garmentId,
      };

      final res = await _client
          .post(
            _uri('/yolo-world/normalize'),
            headers: _headers(json: true),
            body: jsonEncode(payload),
          )
          .timeout(const Duration(minutes: 2));

      if (res.statusCode != 200) {
        lastError = 'Normalization failed (${res.statusCode})';
        return null;
      }
      return jsonDecode(res.body) as Map<String, dynamic>;
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<ExtractedAttributes?> extractMetadata(
    String cropBase64,
    String label,
  ) async {
    lastError = null;
    try {
      final res = await _client
          .post(
            _uri('/yolo-world/extract-metadata'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'crop_base64': cropBase64, 'label': label}),
          )
          .timeout(const Duration(minutes: 2));

      if (res.statusCode != 200) {
        lastError = 'Metadata extraction failed (${res.statusCode})';
        return null;
      }
      final root = jsonDecode(res.body) as Map<String, dynamic>;
      return ExtractedAttributes.fromJson(
        root['attributes'] as Map<String, dynamic>,
      );
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  /// Light bulk metadata (label heuristics + pixel color, no embedding).
  Future<Map<String, ExtractedAttributes>> extractMetadataBulk(
    List<({String id, String cropBase64, String label})> items,
  ) async {
    lastError = null;
    if (items.isEmpty) return {};

    try {
      final timeout = Duration(seconds: 15 + items.length * 2);
      final res = await _client
          .post(
            _uri('/yolo-world/extract-metadata/bulk'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'items': items
                  .map(
                    (i) => {
                      'id': i.id,
                      'crop_base64': i.cropBase64,
                      'label': i.label,
                    },
                  )
                  .toList(),
            }),
          )
          .timeout(timeout);

      if (res.statusCode != 200) {
        lastError = 'Bulk metadata failed (${res.statusCode})';
        return {};
      }

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      final rows = root['items'] as List<dynamic>;
      final out = <String, ExtractedAttributes>{};
      for (final row in rows) {
        final map = row as Map<String, dynamic>;
        final id = map['id'] as String;
        if (map['ok'] == true && map['attributes'] != null) {
          out[id] = ExtractedAttributes.fromJson(
            map['attributes'] as Map<String, dynamic>,
          );
        }
      }
      return out;
    } catch (e) {
      lastError = e.toString();
      return {};
    }
  }

  Future<Garment?> finalizeGarment({
    required String imageBase64,
    required String cropBase64,
    required String label,
    String? sourceImageId,
    ExtractedAttributes? precomputed,
  }) async {
    lastError = null;
    try {
      final payload = <String, dynamic>{
        'image_base64': imageBase64,
        'crop_base64': cropBase64,
        'label': label,
      };
      if (sourceImageId != null) payload['source_image_id'] = sourceImageId;
      if (precomputed != null) {
        payload['precomputed_attributes'] = precomputed.toJson();
      }

      final res = await _client
          .post(
            _uri('/yolo-world/finalize'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode(payload),
          )
          .timeout(const Duration(minutes: 5));

      if (res.statusCode != 200) {
        lastError = 'Finalize failed (${res.statusCode}): ${res.body}';
        return null;
      }

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      final attrs = ExtractedAttributes.fromJson(
        root['attributes'] as Map<String, dynamic>,
      );
      final normalizedBase64 = root['image_base64'] as String;
      final straightenedBase64 =
          root['straightened_image_base64'] as String? ?? normalizedBase64;

      return Garment(
        id: root['garment_id'] as String? ?? '',
        category: attrs.category,
        subcategory: attrs.subcategory,
        colorName: attrs.colorName,
        material: attrs.material,
        pattern: attrs.pattern,
        fit: attrs.fit,
        seasons: attrs.seasons,
        formalityScore: attrs.formalityScore,
        silhouette: attrs.silhouette,
        embedding: attrs.embedding,
        imagePath: b64Ref('crop', normalizedBase64),
        straightenedImagePath: b64Ref('straight', straightenedBase64),
        dateAdded: DateTime.now(),
      );
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<String?> renderTryOn({
    required String personBase64,
    required List<Garment> garments,
    String? outfitId,
  }) async {
    lastError = null;
    try {
      final garmentPayload = <Map<String, dynamic>>[];
      for (final g in garments) {
        final path = g.displayImage;
        String? imageBase64;
        if (path.startsWith('b64://')) {
          imageBase64 = extractB64Payload(path);
        } else if (path.startsWith('http://') || path.startsWith('https://')) {
          try {
            final imgRes = await _client.get(Uri.parse(path)).timeout(
              const Duration(seconds: 15),
            );
            if (imgRes.statusCode == 200) {
              imageBase64 = base64Encode(imgRes.bodyBytes);
            }
          } catch (_) {}
        }
        if (imageBase64 == null || imageBase64.isEmpty) continue;
        garmentPayload.add({
          'id': g.id,
          'category': g.category,
          'subcategory': g.subcategory,
          'colorName': g.colorName,
          'image_base64': imageBase64,
        });
      }
      if (garmentPayload.isEmpty) {
        lastError = 'No garment images available for try-on.';
        return null;
      }

      final payload = <String, dynamic>{
        'person_image_base64': personBase64,
        'garments': garmentPayload,
      };
      if (outfitId != null) payload['outfit_id'] = outfitId;

      final res = await _client
          .post(
            _uri('/try-on/render'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode(payload),
          )
          .timeout(const Duration(minutes: 3));

      if (res.statusCode != 200) {
        lastError = 'Try-on failed (${res.statusCode})';
        return null;
      }
      final root = jsonDecode(res.body) as Map<String, dynamic>;
      return root['image_base64'] as String?;
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<TravelCapsulePlan?> generateTravelCapsule({
    required String destination,
    required int tripDays,
    required double tempLowF,
    required double tempHighF,
    required String weatherCondition,
    required List<Garment> garments,
    List<String> preferredStyles = const [],
  }) async {
    lastError = null;
    try {
      final garmentPayload = garments
          .map((g) => {
                'id': g.id,
                'category': g.category,
                'subcategory': g.subcategory,
                'colorName': g.colorName,
                'material': g.material,
                'pattern': g.pattern,
                'fit': g.fit,
                'seasons': g.seasons,
                'formalityScore': g.formalityScore,
                'laundryStatus': g.laundryStatus.name,
                'wearCount': g.wearCount,
                'brand': g.brand,
              })
          .toList();

      final res = await _client
          .post(
            _uri('/travel/capsule'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'destination': destination,
              'trip_days': tripDays,
              'temp_low_f': tempLowF,
              'temp_high_f': tempHighF,
              'weather_condition': weatherCondition,
              'garments': garmentPayload,
              'preferred_styles': preferredStyles,
            }),
          )
          .timeout(const Duration(minutes: 2));

      if (res.statusCode != 200) {
        lastError = 'Travel capsule failed (${res.statusCode})';
        return null;
      }
      return TravelCapsulePlan.fromJson(
        jsonDecode(res.body) as Map<String, dynamic>,
      );
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<List<Garment>?> fetchWardrobe() async {
    lastError = null;
    if (!hasAuth) return null;
    try {
      final res = await _client
          .get(_uri('/wardrobe'), headers: _headers())
          .timeout(const Duration(seconds: 30));

      if (res.statusCode != 200) {
        lastError = _parseError(res);
        return null;
      }

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      final list = root['garments'] as List<dynamic>;
      return list
          .map((e) => Garment.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<Garment?> syncGarment(
    Garment garment, {
    String? imageBase64,
    String? straightenedBase64,
  }) async {
    lastError = null;
    if (!hasAuth) return null;
    try {
      final payload = <String, dynamic>{'garment': garment.toJson()};
      if (imageBase64 != null) payload['image_base64'] = imageBase64;
      if (straightenedBase64 != null) {
        payload['straightened_image_base64'] = straightenedBase64;
      }

      final res = await _client
          .post(
            _uri('/wardrobe/sync'),
            headers: _headers(json: true),
            body: jsonEncode(payload),
          )
          .timeout(const Duration(seconds: 60));

      if (res.statusCode != 200) {
        lastError = _parseError(res);
        return null;
      }

      final root = jsonDecode(res.body) as Map<String, dynamic>;
      return Garment.fromJson(root['garment'] as Map<String, dynamic>);
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  Future<bool> deleteWardrobeItem(String itemId) async {
    lastError = null;
    if (!hasAuth) return false;
    try {
      final res = await _client
          .delete(_uri('/wardrobe/$itemId'), headers: _headers())
          .timeout(const Duration(seconds: 15));

      if (res.statusCode != 200) {
        lastError = _parseError(res);
        return false;
      }
      return true;
    } catch (e) {
      lastError = e.toString();
      return false;
    }
  }

  Future<Uint8List?> downloadImage(String url) async {
    try {
      final res = await _client.get(Uri.parse(url)).timeout(const Duration(seconds: 30));
      if (res.statusCode == 200) {
        return res.bodyBytes;
      }
      lastError = 'Failed to download image (${res.statusCode})';
      return null;
    } catch (e) {
      lastError = e.toString();
      return null;
    }
  }

  void dispose() => _client.close();
}
