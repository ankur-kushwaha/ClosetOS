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

  Uri _uri(String path) => Uri.parse('${ApiConfig.baseUrl}$path');

  Future<bool> healthCheck() async {
    try {
      final res = await _client.get(_uri('/')).timeout(const Duration(seconds: 5));
      return res.statusCode >= 200 && res.statusCode < 400;
    } catch (_) {
      return false;
    }
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

  Future<String?> normalizeGarment(String cropBase64, String label) async {
    lastError = null;
    try {
      final res = await _client
          .post(
            _uri('/yolo-world/gpt-normalize'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'crop_base64': cropBase64, 'label': label}),
          )
          .timeout(const Duration(minutes: 2));

      if (res.statusCode != 200) {
        lastError = 'Normalization failed (${res.statusCode})';
        return null;
      }
      final root = jsonDecode(res.body) as Map<String, dynamic>;
      return root['image_base64'] as String?;
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
          .timeout(const Duration(minutes: 2));

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

  void dispose() => _client.close();
}
