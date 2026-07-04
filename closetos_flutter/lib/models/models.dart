import 'dart:convert';

enum LaundryStatus { clean, dirty, inLaundry }

class Garment {
  Garment({
    required this.id,
    required this.category,
    required this.subcategory,
    required this.colorName,
    this.material = '',
    this.pattern = '',
    this.fit = '',
    this.seasons = const [],
    this.formalityScore = 0.5,
    this.silhouette = '',
    this.price = 0,
    this.brand = 'Unknown',
    this.imagePath = '',
    this.straightenedImagePath = '',
    this.embedding = const [],
    this.wearCount = 0,
    this.laundryStatus = LaundryStatus.clean,
    this.dateAdded,
  });

  final String id;
  final String category;
  final String subcategory;
  final String colorName;
  final String material;
  final String pattern;
  final String fit;
  final List<String> seasons;
  final double formalityScore;
  final String silhouette;
  final double price;
  final String brand;
  final String imagePath;
  final String straightenedImagePath;
  final List<double> embedding;
  final int wearCount;
  final LaundryStatus laundryStatus;
  final DateTime? dateAdded;

  String get displayImage => straightenedImagePath.isNotEmpty
      ? straightenedImagePath
      : imagePath;

  Garment copyWith({
    String? category,
    String? subcategory,
    String? colorName,
    String? material,
    String? pattern,
    String? fit,
    List<String>? seasons,
    double? formalityScore,
    String? silhouette,
    double? price,
    String? brand,
    String? imagePath,
    String? straightenedImagePath,
    List<double>? embedding,
    int? wearCount,
    LaundryStatus? laundryStatus,
  }) {
    return Garment(
      id: id,
      category: category ?? this.category,
      subcategory: subcategory ?? this.subcategory,
      colorName: colorName ?? this.colorName,
      material: material ?? this.material,
      pattern: pattern ?? this.pattern,
      fit: fit ?? this.fit,
      seasons: seasons ?? this.seasons,
      formalityScore: formalityScore ?? this.formalityScore,
      silhouette: silhouette ?? this.silhouette,
      price: price ?? this.price,
      brand: brand ?? this.brand,
      imagePath: imagePath ?? this.imagePath,
      straightenedImagePath:
          straightenedImagePath ?? this.straightenedImagePath,
      embedding: embedding ?? this.embedding,
      wearCount: wearCount ?? this.wearCount,
      laundryStatus: laundryStatus ?? this.laundryStatus,
      dateAdded: dateAdded,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'category': category,
        'subcategory': subcategory,
        'colorName': colorName,
        'material': material,
        'pattern': pattern,
        'fit': fit,
        'seasons': seasons,
        'formalityScore': formalityScore,
        'silhouette': silhouette,
        'price': price,
        'brand': brand,
        'imagePath': imagePath,
        'straightenedImagePath': straightenedImagePath,
        'embedding': embedding,
        'wearCount': wearCount,
        'laundryStatus': laundryStatus.name,
        'dateAdded': dateAdded?.toIso8601String(),
      };

  factory Garment.fromJson(Map<String, dynamic> json) => Garment(
        id: json['id'] as String,
        category: json['category'] as String? ?? 'Top',
        subcategory: json['subcategory'] as String? ?? '',
        colorName: json['colorName'] as String? ?? '',
        material: json['material'] as String? ?? '',
        pattern: json['pattern'] as String? ?? '',
        fit: json['fit'] as String? ?? '',
        seasons: (json['seasons'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        formalityScore: (json['formalityScore'] as num?)?.toDouble() ?? 0.5,
        silhouette: json['silhouette'] as String? ?? '',
        price: (json['price'] as num?)?.toDouble() ?? 0,
        brand: json['brand'] as String? ?? 'Unknown',
        imagePath: json['imagePath'] as String? ?? '',
        straightenedImagePath: json['straightenedImagePath'] as String? ?? '',
        embedding: (json['embedding'] as List<dynamic>?)
                ?.map((e) => (e as num).toDouble())
                .toList() ??
            const [],
        wearCount: json['wearCount'] as int? ?? 0,
        laundryStatus: LaundryStatus.values.firstWhere(
          (s) => s.name == json['laundryStatus'],
          orElse: () => LaundryStatus.clean,
        ),
        dateAdded: json['dateAdded'] != null
            ? DateTime.tryParse(json['dateAdded'] as String)
            : null,
      );
}

class Outfit {
  Outfit({
    required this.id,
    required this.garmentIds,
    this.name = 'Outfit',
    this.overallScore = 0,
    this.reason = '',
    this.isFavorite = false,
    this.isSaved = false,
    this.isAiGenerated = false,
    this.tags = const [],
  });

  final String id;
  final List<String> garmentIds;
  final String name;
  final double overallScore;
  final String reason;
  final bool isFavorite;
  final bool isSaved;
  final bool isAiGenerated;
  final List<String> tags;

  Map<String, dynamic> toJson() => {
        'id': id,
        'garmentIds': garmentIds,
        'name': name,
        'overallScore': overallScore,
        'reason': reason,
        'isFavorite': isFavorite,
        'isSaved': isSaved,
        'isAiGenerated': isAiGenerated,
        'tags': tags,
      };

  factory Outfit.fromJson(Map<String, dynamic> json) => Outfit(
        id: json['id'] as String,
        garmentIds: (json['garmentIds'] as List<dynamic>)
            .map((e) => e.toString())
            .toList(),
        name: json['name'] as String? ?? 'Outfit',
        overallScore: (json['overallScore'] as num?)?.toDouble() ?? 0,
        reason: json['reason'] as String? ?? '',
        isFavorite: json['isFavorite'] as bool? ?? false,
        isSaved: json['isSaved'] as bool? ?? false,
        isAiGenerated: json['isAiGenerated'] as bool? ?? false,
        tags: (json['tags'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
      );
}

class AppUser {
  AppUser({
    required this.userId,
    required this.email,
    required this.name,
    this.taste,
    this.onboardingCompleted = false,
  });

  final String userId;
  final String email;
  final String name;
  final UserTaste? taste;
  final bool onboardingCompleted;

  factory AppUser.fromJson(Map<String, dynamic> json) {
    final tasteJson = json['taste'] as Map<String, dynamic>?;
    return AppUser(
      userId: json['user_id'] as String? ?? '',
      email: json['email'] as String? ?? '',
      name: json['name'] as String? ?? '',
      taste: tasteJson != null ? UserTaste.fromJson(tasteJson) : null,
      onboardingCompleted: json['onboarding_completed'] as bool? ?? false,
    );
  }
}

class AuthResult {
  AuthResult({required this.token, required this.user});

  final String token;
  final AppUser user;
}

class UserTaste {
  UserTaste({
    this.preferredStyles = const [],
    this.colorsAvoided = const [],
    this.preferredFits = const [],
    this.occasions = const [],
  });

  final List<String> preferredStyles;
  final List<String> colorsAvoided;
  final List<String> preferredFits;
  final List<String> occasions;

  Map<String, dynamic> toJson() => {
        'preferredStyles': preferredStyles,
        'colorsAvoided': colorsAvoided,
        'preferredFits': preferredFits,
        'occasions': occasions,
      };

  factory UserTaste.fromJson(Map<String, dynamic> json) => UserTaste(
        preferredStyles: (json['preferredStyles'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        colorsAvoided: (json['colorsAvoided'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        preferredFits: (json['preferredFits'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        occasions: (json['occasions'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
      );
}

class DetectedBox {
  DetectedBox({
    required this.bbox,
    required this.label,
    required this.score,
    required this.cropBase64,
    this.sourceImageId,
    this.isSelected = true,
  });

  final List<int> bbox;
  final String label;
  final double score;
  final String cropBase64;
  final String? sourceImageId;
  bool isSelected;

  factory DetectedBox.fromJson(Map<String, dynamic> json) => DetectedBox(
        bbox: (json['bbox'] as List<dynamic>)
            .map((e) => (e as num).round())
            .toList(),
        label: json['label'] as String? ?? 'Garment',
        score: (json['score'] as num?)?.toDouble() ?? 0,
        cropBase64: json['crop_base64'] as String? ?? '',
        sourceImageId: json['source_image_id'] as String?,
      );
}

class ExtractedAttributes {
  ExtractedAttributes({
    required this.category,
    required this.subcategory,
    required this.colorName,
    required this.material,
    required this.pattern,
    required this.fit,
    required this.seasons,
    required this.formalityScore,
    required this.silhouette,
    required this.embedding,
    this.labColor = const [50.0, 0.0, 0.0],
    this.florenceCaption = '',
  });

  final String category;
  final String subcategory;
  final String colorName;
  final String material;
  final String pattern;
  final String fit;
  final List<String> seasons;
  final double formalityScore;
  final String silhouette;
  final List<double> embedding;
  final List<double> labColor;
  final String florenceCaption;

  static String categoryFromLabel(String label) {
    final text = label.toLowerCase();
    if (RegExp(r'sneaker|loafer|boot|shoe|sandal|heel|footwear').hasMatch(text)) {
      return 'Shoes';
    }
    if (RegExp(r'jacket|coat|blazer|overcoat|parka|cardigan').hasMatch(text)) {
      return 'Outerwear';
    }
    if (RegExp(r'pants|jeans|trousers|shorts|skirt|leggings').hasMatch(text)) {
      return 'Bottom';
    }
    return 'Top';
  }

  factory ExtractedAttributes.fromLabel(String label) {
    final category = categoryFromLabel(label);
    return ExtractedAttributes(
      category: category,
      subcategory: label,
      colorName: '',
      material: '',
      pattern: '',
      fit: '',
      seasons: category == 'Outerwear'
          ? const ['Autumn', 'Winter', 'Spring']
          : const ['Spring', 'Summer', 'Autumn'],
      formalityScore: 0.5,
      silhouette: label.split(' ').last,
      embedding: const [],
      florenceCaption: label,
    );
  }

  factory ExtractedAttributes.fromJson(Map<String, dynamic> json) =>
      ExtractedAttributes(
        category: json['category'] as String? ?? 'Top',
        subcategory: json['subcategory'] as String? ?? '',
        colorName: json['colorName'] as String? ?? '',
        material: json['material'] as String? ?? '',
        pattern: json['pattern'] as String? ?? '',
        fit: json['fit'] as String? ?? '',
        seasons: (json['seasons'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        formalityScore: (json['formalityScore'] as num?)?.toDouble() ?? 0.5,
        silhouette: json['silhouette'] as String? ?? '',
        embedding: (json['embedding'] as List<dynamic>?)
                ?.map((e) => (e as num).toDouble())
                .toList() ??
            const [],
        labColor: (json['labColor'] as List<dynamic>?)
                ?.map((e) => (e as num).toDouble())
                .toList() ??
            const [50.0, 0.0, 0.0],
        florenceCaption: json['florenceCaption'] as String? ?? '',
      );

  Map<String, dynamic> toJson() => {
        'category': category,
        'subcategory': subcategory,
        'colorName': colorName,
        'labColor': labColor,
        'material': material,
        'pattern': pattern,
        'fit': fit,
        'seasons': seasons,
        'formalityScore': formalityScore,
        'silhouette': silhouette,
        'embedding': embedding,
        'florenceCaption': florenceCaption,
      };

  ExtractedAttributes copyWith({
    String? category,
    String? subcategory,
    String? colorName,
    String? material,
    String? pattern,
    String? fit,
    List<String>? seasons,
    double? formalityScore,
    String? silhouette,
    List<double>? embedding,
    List<double>? labColor,
    String? florenceCaption,
  }) {
    return ExtractedAttributes(
      category: category ?? this.category,
      subcategory: subcategory ?? this.subcategory,
      colorName: colorName ?? this.colorName,
      material: material ?? this.material,
      pattern: pattern ?? this.pattern,
      fit: fit ?? this.fit,
      seasons: seasons ?? this.seasons,
      formalityScore: formalityScore ?? this.formalityScore,
      silhouette: silhouette ?? this.silhouette,
      embedding: embedding ?? this.embedding,
      labColor: labColor ?? this.labColor,
      florenceCaption: florenceCaption ?? this.florenceCaption,
    );
  }
}

class TravelDayOutfit {
  TravelDayOutfit({
    required this.day,
    required this.garmentIds,
    required this.reason,
  });

  final int day;
  final List<String> garmentIds;
  final String reason;

  factory TravelDayOutfit.fromJson(Map<String, dynamic> json) =>
      TravelDayOutfit(
        day: json['day'] as int,
        garmentIds: (json['garment_ids'] as List<dynamic>)
            .map((e) => e.toString())
            .toList(),
        reason: json['reason'] as String? ?? '',
      );
}

class TravelCapsulePlan {
  TravelCapsulePlan({
    required this.capsuleGarmentIds,
    required this.dailyOutfits,
    this.packingNotes = '',
    this.provider = 'backend',
  });

  final List<String> capsuleGarmentIds;
  final List<TravelDayOutfit> dailyOutfits;
  final String packingNotes;
  final String provider;

  factory TravelCapsulePlan.fromJson(Map<String, dynamic> json) =>
      TravelCapsulePlan(
        capsuleGarmentIds: (json['capsule_garment_ids'] as List<dynamic>)
            .map((e) => e.toString())
            .toList(),
        dailyOutfits: (json['daily_outfits'] as List<dynamic>)
            .map((e) => TravelDayOutfit.fromJson(e as Map<String, dynamic>))
            .toList(),
        packingNotes: json['packing_notes'] as String? ?? '',
        provider: json['provider'] as String? ?? 'backend',
      );
}

class IngestionItem {
  IngestionItem({
    required this.id,
    required this.status,
    required this.stepLabel,
    this.progress = 0,
    this.label,
    this.cropBase64,
    this.normalizedBase64,
    this.attributes,
    this.sourceImageId,
    this.error,
  });

  final String id;
  String status;
  String stepLabel;
  double progress;
  String? label;
  String? cropBase64;
  String? normalizedBase64;
  ExtractedAttributes? attributes;
  String? sourceImageId;
  String? error;

  IngestionItem copyWith({
    String? status,
    String? stepLabel,
    double? progress,
    String? label,
    String? cropBase64,
    String? normalizedBase64,
    ExtractedAttributes? attributes,
    String? sourceImageId,
    String? error,
  }) {
    return IngestionItem(
      id: id,
      status: status ?? this.status,
      stepLabel: stepLabel ?? this.stepLabel,
      progress: progress ?? this.progress,
      label: label ?? this.label,
      cropBase64: cropBase64 ?? this.cropBase64,
      normalizedBase64: normalizedBase64 ?? this.normalizedBase64,
      attributes: attributes ?? this.attributes,
      sourceImageId: sourceImageId ?? this.sourceImageId,
      error: error ?? this.error,
    );
  }
}

String encodeJsonList(List<Map<String, dynamic>> items) => jsonEncode(items);

List<T> decodeJsonList<T>(
  String? raw,
  T Function(Map<String, dynamic>) fromJson,
) {
  if (raw == null || raw.isEmpty) return [];
  final list = jsonDecode(raw) as List<dynamic>;
  return list.map((e) => fromJson(e as Map<String, dynamic>)).toList();
}

String? extractB64Payload(String path) {
  if (!path.startsWith('b64://')) return null;
  final rest = path.substring('b64://'.length);
  final slash = rest.indexOf('/');
  if (slash < 0) return rest;
  return rest.substring(slash + 1);
}

String b64Ref(String prefix, String base64) => 'b64://$prefix/$base64';
