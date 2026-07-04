import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../models/models.dart';
import 'api_service.dart';
import 'storage_service.dart';
import 'weather_service.dart';

class WardrobeRepository extends ChangeNotifier {
  WardrobeRepository({
    required StorageService storage,
    required ApiService api,
  })  : _storage = storage,
        _api = api;

  final StorageService _storage;
  final ApiService _api;
  final _uuid = const Uuid();

  List<Garment> garments = [];
  List<Outfit> outfits = [];
  UserTaste taste = UserTaste();
  final List<IngestionItem> ingestionQueue = [];
  Map<String, String> tryOnCache = {};
  String? digitalTwinPath;

  bool isLoading = false;
  String? lastError;

  static const categories = ['All', 'Top', 'Bottom', 'Outerwear', 'Shoes', 'Accessory'];

  Future<void> init() async {
    garments = _storage.loadGarments();
    outfits = _storage.loadOutfits();
    taste = _storage.loadTaste();
    tryOnCache = _storage.loadTryOnCache();
    digitalTwinPath = _storage.digitalTwinPath;
    notifyListeners();
  }

  int get totalItems => garments.length;

  Map<String, int> get categoryCounts {
    final counts = <String, int>{};
    for (final g in garments) {
      counts[g.category] = (counts[g.category] ?? 0) + 1;
    }
    return counts;
  }

  Future<void> completeOnboarding(UserTaste newTaste, Uint8List? selfieBytes) async {
    taste = newTaste;
    if (selfieBytes != null) {
      digitalTwinPath = await _storage.saveImageBytes(selfieBytes, 'selfie');
      await _storage.setDigitalTwinPath(digitalTwinPath!);
    }
    await _storage.saveTaste(taste);
    await _storage.setOnboardingComplete();
    notifyListeners();
  }

  Future<void> addGarment(Garment garment) async {
    final id = garment.id.isEmpty ? _uuid.v4() : garment.id;
    garments = [
      ...garments,
      Garment(
        id: id,
        category: garment.category,
        subcategory: garment.subcategory,
        colorName: garment.colorName,
        material: garment.material,
        pattern: garment.pattern,
        fit: garment.fit,
        seasons: garment.seasons,
        formalityScore: garment.formalityScore,
        silhouette: garment.silhouette,
        price: garment.price,
        brand: garment.brand,
        imagePath: garment.imagePath,
        straightenedImagePath: garment.straightenedImagePath,
        embedding: garment.embedding,
        dateAdded: garment.dateAdded ?? DateTime.now(),
      ),
    ];
    await _storage.saveGarments(garments);
    notifyListeners();
  }

  Future<void> deleteGarment(String id) async {
    garments = garments.where((g) => g.id != id).toList();
    await _storage.saveGarments(garments);
    notifyListeners();
  }

  Future<void> toggleLaundry(String id) async {
    final idx = garments.indexWhere((g) => g.id == id);
    if (idx < 0) return;
    final g = garments[idx];
    final next = switch (g.laundryStatus) {
      LaundryStatus.clean => LaundryStatus.dirty,
      LaundryStatus.dirty => LaundryStatus.inLaundry,
      LaundryStatus.inLaundry => LaundryStatus.clean,
    };
    garments[idx] = g.copyWith(laundryStatus: next);
    await _storage.saveGarments(garments);
    notifyListeners();
  }

  List<Garment> filterGarments({String category = 'All', String query = ''}) {
    return garments.where((g) {
      final catOk = category == 'All' ||
          g.category.toLowerCase() == category.toLowerCase();
      final q = query.toLowerCase();
      final searchOk = q.isEmpty ||
          g.subcategory.toLowerCase().contains(q) ||
          g.colorName.toLowerCase().contains(q) ||
          g.brand.toLowerCase().contains(q);
      return catOk && searchOk;
    }).toList();
  }

  static const _lookNames = [
    'Cozy layered',
    'Polished edit',
    'Weekend ease',
    'Sharp lines',
    'Easy layers',
  ];

  static const _reasonsCool = [
    'Layered for a crisp morning — peel back by afternoon.',
    'Warm enough now, with room to add a layer tonight.',
    'Smart-casual for your day — cooler by evening.',
  ];

  static const _reasonsMild = [
    'Smart-casual for your 2pm review — cooler by evening.',
    'Balanced layers for a day that shifts temperature.',
    'Easy polish without overthinking it.',
  ];

  static const _reasonsWarm = [
    'Light and breathable — no layers needed today.',
    'A clean, airy look for a warm afternoon.',
    'Relaxed structure for heat and humidity.',
  ];

  List<Outfit> generateRecommendations(double tempF, {String occasion = 'Daily'}) {
    final clean = garments
        .where((g) => g.laundryStatus == LaundryStatus.clean)
        .toList();
    if (clean.length < 2) return [];

    final tops = clean.where((g) => g.category == 'Top').toList();
    final bottoms = clean.where((g) => g.category == 'Bottom').toList();
    final shoes = clean.where((g) => g.category == 'Shoes').toList();
    if (tops.isEmpty || bottoms.isEmpty) return [];

    final results = <Outfit>[];
    final random = Random(tempF.toInt() + occasion.hashCode);
    final reasons = _reasonsForTemp(tempF);
    final count = min(5, tops.length * bottoms.length);

    for (var i = 0; i < count; i++) {
      final top = tops[random.nextInt(tops.length)];
      final bottom = bottoms[random.nextInt(bottoms.length)];
      final ids = [top.id, bottom.id];
      if (shoes.isNotEmpty && random.nextBool()) {
        ids.add(shoes[random.nextInt(shoes.length)].id);
      }
      final score = 0.6 + random.nextDouble() * 0.35;
      results.add(Outfit(
        id: _uuid.v4(),
        garmentIds: ids,
        name: _lookNames[i % _lookNames.length],
        overallScore: score,
        reason: reasons[i % reasons.length],
        isAiGenerated: true,
        tags: [occasion],
      ));
    }
    results.sort((a, b) => b.overallScore.compareTo(a.overallScore));
    return results;
  }

  List<String> _reasonsForTemp(double tempF) {
    if (tempF < 55) return _reasonsCool;
    if (tempF > 78) return _reasonsWarm;
    return _reasonsMild;
  }

  List<Garment> garmentsForOutfit(Outfit outfit) {
    return outfit.garmentIds
        .map((id) => garments.cast<Garment?>().firstWhere(
              (g) => g?.id == id,
              orElse: () => null,
            ))
        .whereType<Garment>()
        .toList();
  }

  Future<List<DetectedBox>?> detectFromImage(Uint8List bytes, String name) async {
    final result = await _api.detectGarments(bytes, name);
    if (result == null) {
      lastError = _api.lastError;
      notifyListeners();
    }
    return result;
  }

  Future<IngestionItem> prepareIngestionReview(
    DetectedBox box, {
    void Function(IngestionItem)? onUpdate,
  }) async {
    final item = IngestionItem(
      id: _uuid.v4(),
      status: 'processing',
      stepLabel: 'Normalizing',
      progress: 0.2,
      label: box.label,
      cropBase64: box.cropBase64,
      sourceImageId: box.sourceImageId,
    );
    ingestionQueue.add(item);
    onUpdate?.call(item);
    notifyListeners();

    try {
      final results = await Future.wait([
        _api.normalizeGarment(box.cropBase64, box.label),
        _api.extractMetadata(box.cropBase64, box.label),
      ]);
      final normalized = results[0] as String?;
      final metadata = results[1] as ExtractedAttributes?;

      item.normalizedBase64 = normalized;
      item.attributes = metadata;
      item.status = 'review';
      item.stepLabel = 'Awaiting review';
      item.progress = 0.85;
      onUpdate?.call(item);
      notifyListeners();
    } catch (e) {
      item.status = 'failed';
      item.error = e.toString();
      item.stepLabel = 'Failed';
      onUpdate?.call(item);
      lastError = e.toString();
      ingestionQueue.removeWhere((i) => i.id == item.id);
      notifyListeners();
    }
    return item;
  }

  Future<List<IngestionItem>> prepareIngestionBatch(
    List<DetectedBox> boxes, {
    void Function(IngestionItem)? onUpdate,
  }) {
    return Future.wait(
      boxes.map((box) => prepareIngestionReview(box, onUpdate: onUpdate)),
    );
  }

  Future<IngestionItem> finalizeIngestion(
    IngestionItem item, {
    required bool useNormalized,
    void Function(IngestionItem)? onUpdate,
  }) async {
    final cropBase64 = item.cropBase64;
    if (cropBase64 == null || cropBase64.isEmpty) {
      item.status = 'failed';
      item.stepLabel = 'Failed';
      item.error = 'Missing crop image.';
      onUpdate?.call(item);
      ingestionQueue.removeWhere((i) => i.id == item.id);
      notifyListeners();
      return item;
    }

    final imageBase64 = useNormalized
        ? (item.normalizedBase64 ?? cropBase64)
        : cropBase64;

    item.status = 'processing';
    item.stepLabel = 'Saving';
    item.progress = 0.9;
    onUpdate?.call(item);
    notifyListeners();

    try {
      var metadata = item.attributes;
      if (metadata == null) {
        metadata = await _api.extractMetadata(cropBase64, item.label ?? 'garment');
        item.attributes = metadata;
      }

      final result = await _api.finalizeGarment(
        imageBase64: imageBase64,
        cropBase64: cropBase64,
        label: item.label ?? 'garment',
        sourceImageId: item.sourceImageId,
        precomputed: metadata,
      );

      if (result == null) {
        throw Exception(_api.lastError ?? 'Finalize failed');
      }

      var garment = result;
      if (garment.imagePath.startsWith('b64://')) {
        final cropB64 = extractB64Payload(garment.imagePath)!;
        final straightB64 = extractB64Payload(garment.straightenedImagePath)!;
        garment = garment.copyWith(
          imagePath: await _storage.saveImageFromBase64(cropB64, 'crop'),
          straightenedImagePath:
              await _storage.saveImageFromBase64(straightB64, 'straight'),
        );
      }

      if (garment.id.isEmpty) {
        garment = Garment(
          id: _uuid.v4(),
          category: garment.category,
          subcategory: garment.subcategory,
          colorName: garment.colorName,
          material: garment.material,
          pattern: garment.pattern,
          fit: garment.fit,
          seasons: garment.seasons,
          formalityScore: garment.formalityScore,
          silhouette: garment.silhouette,
          imagePath: garment.imagePath,
          straightenedImagePath: garment.straightenedImagePath,
          embedding: garment.embedding,
          dateAdded: DateTime.now(),
        );
      }

      await addGarment(garment);
      item.status = 'done';
      item.stepLabel = 'Complete';
      item.progress = 1;
      onUpdate?.call(item);
    } catch (e) {
      item.status = 'failed';
      item.error = e.toString();
      item.stepLabel = 'Failed';
      onUpdate?.call(item);
      lastError = e.toString();
    } finally {
      ingestionQueue.removeWhere((i) => i.id == item.id);
      notifyListeners();
    }
    return item;
  }

  Future<String?> renderTryOn(Outfit outfit) async {
    final cached = tryOnCache[outfit.id];
    if (cached != null) return cached;

    final selfiePath = digitalTwinPath;
    if (selfiePath == null) {
      lastError = 'Add a digital twin selfie in onboarding.';
      return null;
    }

    final personB64 = await _storage.readFileAsBase64(selfiePath);
    if (personB64 == null) {
      lastError = 'Could not read selfie image.';
      return null;
    }

    isLoading = true;
    notifyListeners();

    final outfitGarments = garmentsForOutfit(outfit);
    final resultB64 = await _api.renderTryOn(
      personBase64: personB64,
      garments: outfitGarments,
      outfitId: outfit.id,
    );

    isLoading = false;
    if (resultB64 == null) {
      lastError = _api.lastError;
      notifyListeners();
      return null;
    }

    final path = await _storage.saveImageFromBase64(resultB64, 'tryon');
    tryOnCache[outfit.id] = path;
    await _storage.saveTryOnCache(tryOnCache);
    notifyListeners();
    return path;
  }

  Future<TravelCapsulePlan?> planTrip({
    required String destination,
    required int days,
    required double tempLowF,
    required double tempHighF,
    required String weather,
  }) async {
    isLoading = true;
    notifyListeners();

    final plan = await _api.generateTravelCapsule(
      destination: destination,
      tripDays: days,
      tempLowF: tempLowF,
      tempHighF: tempHighF,
      weatherCondition: weather,
      garments: garments,
      preferredStyles: taste.preferredStyles,
    );

    isLoading = false;
    if (plan == null) lastError = _api.lastError;
    notifyListeners();
    return plan;
  }

  Future<WeatherInfo> fetchWeather() => WeatherService.fetch();
}
