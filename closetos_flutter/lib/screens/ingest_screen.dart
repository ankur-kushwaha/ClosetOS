import 'dart:convert';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';

class IngestScreen extends StatefulWidget {
  const IngestScreen({super.key, this.onReviewComplete, this.onOpenDrawer});

  final VoidCallback? onReviewComplete;
  final VoidCallback? onOpenDrawer;

  @override
  State<IngestScreen> createState() => _IngestScreenState();
}

class _IngestScreenState extends State<IngestScreen> {
  final _picker = ImagePicker();

  static const List<String> _mockImages = [
    'https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=400&q=80', // jeans
    'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=400&q=80', // t-shirt
    'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=400&q=80', // shirt
    'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=400&q=80', // jacket
    'https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=400&q=80', // handbag
    'https://images.unsplash.com/photo-1608231387042-66d1773070a5?w=400&q=80', // shoes
    'https://images.unsplash.com/photo-1578587018452-892bacefd3f2?w=400&q=80', // dress
    'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=400&q=80', // coat
    'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=400&q=80', // fashion dress
    'https://images.unsplash.com/photo-1551488831-00ddcb6c6bd3?w=400&q=80', // sweatshirt
    'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=400&q=80', // sweater
    'https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=400&q=80', // blouse
  ];

  List<GalleryItem> _galleryItems = [];
  final List<GalleryItem> _selectedGalleryItems = [];
  bool _galleryLoading = true;
  bool _hasGalleryPermission = false;

  bool _detecting = false;

  @override
  void initState() {
    super.initState();
    _loadGallery();
  }

  Future<void> _loadGallery() async {
    if (kIsWeb) {
      if (!mounted) return;
      setState(() {
        _galleryItems = _mockImages.map((url) => GalleryItem(url: url)).toList();
        _galleryLoading = false;
      });
      return;
    }

    try {
      final ps = await PhotoManager.requestPermissionExtend();
      if (!mounted) return;
      if (ps.isAuth) {
        final albums = await PhotoManager.getAssetPathList(type: RequestType.image);
        if (albums.isNotEmpty) {
          final recentAssets = await albums.first.getAssetListRange(start: 0, end: 80);
          if (!mounted) return;
          setState(() {
            _galleryItems = recentAssets.map((asset) => GalleryItem(entity: asset)).toList();
            _hasGalleryPermission = true;
            _galleryLoading = false;
          });
          return;
        }
      }
    } catch (e) {
      debugPrint('Error loading device gallery: $e');
    }

    if (!mounted) return;
    setState(() {
      _galleryItems = _mockImages.map((url) => GalleryItem(url: url)).toList();
      _hasGalleryPermission = false;
      _galleryLoading = false;
    });
  }

  Future<void> _scanPhotos(
    List<Future<Uint8List?>> bytesFutures,
    List<String> names,
  ) async {
    setState(() {
      _detecting = true;
    });

    final repo = context.read<WardrobeRepository>();
    ImportedImage? lastImported;

    for (int i = 0; i < bytesFutures.length; i++) {
      final bytes = await bytesFutures[i];
      if (bytes == null) continue;
      if (!mounted) return;

      final imported = await repo.importAndScanImage(bytes, names[i]);
      if (imported != null) {
        lastImported = imported;
      }
    }

    if (!mounted) return;
    setState(() {
      _detecting = false;
      _selectedGalleryItems.clear();
    });

    if (lastImported != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Scanned and added to Scanned Photos.',
            style: AppTypography.ui(color: AppColors.surface),
          ),
          backgroundColor: AppColors.clay500,
        ),
      );
      _showGarmentsSheet(context, lastImported);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            repo.lastError ?? 'Scan failed or no garments found.',
            style: AppTypography.ui(color: AppColors.surface),
          ),
        ),
      );
    }
  }

  Future<void> _pickBulkPhotos() async {
    if (_detecting) return;
    final picked = await _picker.pickMultiImage(imageQuality: 85);
    if (picked.isEmpty) return;
    _scanPhotos(
      picked.map((file) => file.readAsBytes()).toList(),
      picked.map((file) => file.name).toList(),
    );
  }

  Future<void> _pickFromCamera() async {
    if (_detecting) return;
    final picked = await _picker.pickImage(source: ImageSource.camera, imageQuality: 85);
    if (picked == null) return;
    _scanPhotos([picked.readAsBytes()], [picked.name]);
  }

  Future<void> _processSelected() async {
    if (_detecting || _selectedGalleryItems.isEmpty) return;
    final selected = List<GalleryItem>.from(_selectedGalleryItems);
    _scanPhotos(
      selected.map((item) => item.getBytes()).toList(),
      selected.map((item) => item.name).toList(),
    );
  }

  void _showGarmentsSheet(BuildContext context, ImportedImage image) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => _GarmentsSheet(image: image),
    );
  }

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    return ColoredBox(
      color: AppColors.canvas,
      child: Stack(
        children: [
          _ImportView(
            detecting: _detecting,
            importedImages: repo.importedImages,
            onBulkPhotos: _pickBulkPhotos,
            galleryItems: _galleryItems,
            selectedGalleryItems: _selectedGalleryItems,
            galleryLoading: _galleryLoading,
            hasGalleryPermission: _hasGalleryPermission,
            onToggleSelect: (item) {
              setState(() {
                if (_selectedGalleryItems.contains(item)) {
                  _selectedGalleryItems.remove(item);
                } else {
                  _selectedGalleryItems.add(item);
                }
              });
            },
            onScanSelected: _processSelected,
            onPickFromCamera: _pickFromCamera,
            onImageClick: (image) => _showGarmentsSheet(context, image),
            onDeleteImage: (image) => repo.deleteImportedImage(image),
            onOpenDrawer: widget.onOpenDrawer,
          ),
          if (_detecting)
            Positioned.fill(
              child: Container(
                color: Colors.black.withValues(alpha: 0.4),
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 5, sigmaY: 5),
                  child: Center(
                    child: Card(
                      color: AppColors.surface,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const CircularProgressIndicator(color: AppColors.clay500),
                            const SizedBox(height: 16),
                            Text(
                              'Scanning photo...',
                              style: AppTypography.ui(
                                fontSize: 16,
                                fontWeight: FontWeight.w600,
                                color: AppColors.ink900,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Detecting garments & details',
                              style: AppTypography.ui(
                                fontSize: 13,
                                color: AppColors.ink600,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

// ─── Import phase ────────────────────────────────────────────────────────────

class _ImportView extends StatelessWidget {
  const _ImportView({
    required this.detecting,
    required this.importedImages,
    required this.onBulkPhotos,
    required this.galleryItems,
    required this.selectedGalleryItems,
    required this.galleryLoading,
    required this.hasGalleryPermission,
    required this.onToggleSelect,
    required this.onScanSelected,
    required this.onPickFromCamera,
    required this.onImageClick,
    required this.onDeleteImage,
    this.onOpenDrawer,
  });

  final bool detecting;
  final List<ImportedImage> importedImages;
  final VoidCallback onBulkPhotos;
  final List<GalleryItem> galleryItems;
  final List<GalleryItem> selectedGalleryItems;
  final bool galleryLoading;
  final bool hasGalleryPermission;
  final void Function(GalleryItem item) onToggleSelect;
  final VoidCallback onScanSelected;
  final VoidCallback onPickFromCamera;
  final void Function(ImportedImage image) onImageClick;
  final void Function(ImportedImage image) onDeleteImage;
  final VoidCallback? onOpenDrawer;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 100),
              sliver: SliverList(
                delegate: SliverChildListDelegate([
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.menu, size: 22, color: AppColors.ink900),
                        padding: EdgeInsets.zero,
                        onPressed: onOpenDrawer,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Add your closet',
                        style: AppTypography.display(
                          fontSize: 30,
                          color: AppColors.ink900,
                          fontWeight: FontWeight.w500,
                          height: 1.1,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Select photos from camera roll to scan or scan new photos',
                        style: AppTypography.ui(
                          fontSize: 14,
                          color: AppColors.ink600,
                        ),
                      ),
                      const SizedBox(height: 20),
                      // Camera & Files side-by-side Actions Row
                      Row(
                        children: [
                          Expanded(
                            child: _ActionTile(
                              icon: Icons.camera_alt_outlined,
                              label: 'Camera',
                              onTap: onPickFromCamera,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: _ActionTile(
                              icon: Icons.photo_library_outlined,
                              label: 'Files',
                              onTap: onBulkPhotos,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 28),
                      // Imported Photos Section Header
                      Text(
                        'Imported Photos',
                        style: AppTypography.display(
                          fontSize: 20,
                          fontWeight: FontWeight.w600,
                          color: AppColors.ink900,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Click an image to view or add detected garments',
                        style: AppTypography.ui(
                          fontSize: 13,
                          color: AppColors.ink600,
                        ),
                      ),
                      const SizedBox(height: 12),
                      // Imported Photos list
                      _ImportedPhotosTray(
                        images: importedImages,
                        onImageClick: onImageClick,
                        onDeleteImage: onDeleteImage,
                      ),
                      const SizedBox(height: 28),
                      // Gallery Section Header
                      Text(
                        hasGalleryPermission ? 'Camera Roll' : 'Demo Clothes',
                        style: AppTypography.display(
                          fontSize: 20,
                          fontWeight: FontWeight.w600,
                          color: AppColors.ink900,
                        ),
                      ),
                      const SizedBox(height: 12),
                    ],
                  ),
                  if (galleryLoading)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 40),
                      child: Center(
                        child: CircularProgressIndicator(),
                      ),
                    )
                  else
                    GridView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 3,
                        mainAxisSpacing: 8,
                        crossAxisSpacing: 8,
                        childAspectRatio: 1,
                      ),
                      itemBuilder: (context, index) {
                        final item = galleryItems[index];
                        final isSelected = selectedGalleryItems.contains(item);
                        final selectIndex = selectedGalleryItems.indexOf(item);

                        return _GalleryItemTile(
                          item: item,
                          isSelected: isSelected,
                          selectIndex: selectIndex,
                          onTap: () => onToggleSelect(item),
                        );
                      },
                      itemCount: galleryItems.length,
                    ),
                ]),
              ),
            ),
          ],
        ),
        if (selectedGalleryItems.isNotEmpty)
          Positioned(
            left: 20,
            right: 20,
            bottom: 24,
            child: FilledButton(
              onPressed: onScanSelected,
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.clay500,
                foregroundColor: AppColors.surface,
                minimumSize: const Size.fromHeight(50),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                elevation: 4,
              ),
              child: Text(
                'Scan ${selectedGalleryItems.length} selected photo${selectedGalleryItems.length == 1 ? '' : 's'}',
                style: AppTypography.ui(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _ActionTile extends StatelessWidget {
  const _ActionTile({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          height: 80,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.border),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                icon,
                size: 26,
                color: AppColors.clay500,
              ),
              const SizedBox(height: 6),
              Text(
                label,
                style: AppTypography.ui(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: AppColors.ink600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GalleryItemTile extends StatelessWidget {
  const _GalleryItemTile({
    required this.item,
    required this.isSelected,
    required this.selectIndex,
    required this.onTap,
  });

  final GalleryItem item;
  final bool isSelected;
  final int selectIndex;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: Container(
          decoration: BoxDecoration(
            color: AppColors.greige.withValues(alpha: 0.5),
            border: Border.all(
              color: isSelected ? AppColors.clay500 : AppColors.border,
              width: isSelected ? 2 : 1,
            ),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Stack(
            fit: StackFit.expand,
            children: [
              item.isMock
                  ? Image.network(
                      item.url!,
                      fit: BoxFit.cover,
                      errorBuilder: (context, error, stackTrace) {
                        return const Center(
                          child: Icon(Icons.broken_image_outlined, color: AppColors.ink400),
                        );
                      },
                    )
                  : _AssetThumbnailWidget(entity: item.entity!),
              if (isSelected)
                Container(
                  color: Colors.black.withValues(alpha: 0.15),
                ),
              Positioned(
                top: 8,
                right: 8,
                child: Container(
                  width: 20,
                  height: 20,
                  decoration: BoxDecoration(
                    color: isSelected ? AppColors.clay500 : Colors.black.withValues(alpha: 0.3),
                    shape: BoxShape.circle,
                    border: Border.all(color: AppColors.surface, width: 1.5),
                  ),
                  child: isSelected
                      ? Center(
                          child: Text(
                            '${selectIndex + 1}',
                            style: AppTypography.ui(
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                              color: AppColors.surface,
                            ),
                          ),
                        )
                      : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AssetThumbnailWidget extends StatelessWidget {
  const _AssetThumbnailWidget({required this.entity});

  final AssetEntity entity;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Uint8List?>(
      future: entity.thumbnailData,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.done && snapshot.hasData) {
          return Image.memory(
            snapshot.data!,
            fit: BoxFit.cover,
            errorBuilder: (context, error, stackTrace) {
              return const Center(
                child: Icon(Icons.broken_image_outlined, color: AppColors.ink400),
              );
            },
          );
        }
        return const Center(
          child: SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        );
      },
    );
  }
}

class GalleryItem {
  final AssetEntity? entity;
  final String? url;

  GalleryItem({this.entity, this.url});

  bool get isMock => url != null;
  String get id => isMock ? url! : entity!.id;

  String get name {
    if (isMock) {
      return '${url!.split('/').last.split('?').first}.jpg';
    } else {
      return entity!.title ?? 'image.jpg';
    }
  }

  Future<Uint8List?> getBytes() async {
    if (isMock) {
      try {
        final res = await http.get(Uri.parse(url!));
        if (res.statusCode == 200) {
          return res.bodyBytes;
        }
      } catch (e) {
        debugPrint('Error fetching mock bytes: $e');
      }
      return null;
    } else {
      return await entity!.originBytes;
    }
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is GalleryItem &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;
}

class _ImportedPhotosTray extends StatelessWidget {
  const _ImportedPhotosTray({
    required this.images,
    required this.onImageClick,
    required this.onDeleteImage,
  });

  final List<ImportedImage> images;
  final void Function(ImportedImage image) onImageClick;
  final void Function(ImportedImage image) onDeleteImage;

  @override
  Widget build(BuildContext context) {
    if (images.isEmpty) {
      return Container(
        height: 100,
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.border),
        ),
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.photo_outlined, color: AppColors.ink400, size: 28),
              const SizedBox(height: 8),
              Text(
                'No scanned photos yet',
                style: AppTypography.ui(
                  fontSize: 13,
                  color: AppColors.ink600,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      );
    }

    return SizedBox(
      height: 120,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: images.length,
        separatorBuilder: (_, __) => const SizedBox(width: 12),
        itemBuilder: (context, idx) {
          final image = images[idx];
          final total = image.garments.length;
          final added = image.garments.where((g) => g.status == 'done').length;
          final allAdded = total > 0 && added == total;

          return GestureDetector(
            onTap: () => onImageClick(image),
            child: Stack(
              children: [
                Container(
                  width: 110,
                  height: 110,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: AppColors.border),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: GarmentImage(
                    path: image.imagePath,
                    fit: BoxFit.cover,
                  ),
                ),
                // Garments count badge
                Positioned(
                  left: 8,
                  bottom: 8,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                    decoration: BoxDecoration(
                      color: allAdded ? Colors.green : AppColors.ink900.withValues(alpha: 0.75),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      allAdded ? 'Added' : '$added/$total items',
                      style: AppTypography.ui(
                        fontSize: 9,
                        fontWeight: FontWeight.w700,
                        color: AppColors.surface,
                      ),
                    ),
                  ),
                ),
                // Delete button
                Positioned(
                  top: 4,
                  right: 4,
                  child: GestureDetector(
                    onTap: () => onDeleteImage(image),
                    child: Container(
                      padding: const EdgeInsets.all(4),
                      decoration: BoxDecoration(
                        color: AppColors.ink900.withValues(alpha: 0.6),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.close,
                        size: 12,
                        color: AppColors.surface,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _GarmentsSheet extends StatelessWidget {
  const _GarmentsSheet({required this.image});

  final ImportedImage image;

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    // Look up the image in repository to get the latest reactive updates
    final currentImage = repo.importedImages.firstWhere(
      (item) => item.id == image.id,
      orElse: () => image,
    );

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.canvas,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Center(
            child: Container(
              width: 38,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.border,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Detected Garments',
                      style: AppTypography.display(
                        fontSize: 22,
                        fontWeight: FontWeight.w600,
                        color: AppColors.ink900,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${currentImage.garments.length} garments detected in this photo',
                      style: AppTypography.ui(
                        fontSize: 13,
                        color: AppColors.ink600,
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                icon: const Icon(Icons.close_rounded, color: AppColors.ink600),
                onPressed: () => Navigator.pop(context),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Flexible(
            child: ListView.separated(
              shrinkWrap: true,
              itemCount: currentImage.garments.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, idx) {
                final garment = currentImage.garments[idx];
                return _GarmentRowItem(
                  image: currentImage,
                  garment: garment,
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _GarmentRowItem extends StatefulWidget {
  const _GarmentRowItem({
    required this.image,
    required this.garment,
  });

  final ImportedImage image;
  final IngestionItem garment;

  @override
  State<_GarmentRowItem> createState() => _GarmentRowItemState();
}

class _GarmentRowItemState extends State<_GarmentRowItem> {
  bool _localLoading = false;

  @override
  Widget build(BuildContext context) {
    final repo = context.read<WardrobeRepository>();
    final isAdded = widget.garment.status == 'done';
    final isProcessing = widget.garment.status == 'processing' || _localLoading;

    final name = widget.garment.attributes?.subcategory.isNotEmpty == true
        ? widget.garment.attributes!.subcategory
        : (widget.garment.label ?? 'Garment');
    final category = widget.garment.attributes?.category ?? 'Top';

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: SizedBox(
              width: 64,
              height: 64,
              child: widget.garment.cropBase64?.isNotEmpty == true
                  ? Image.memory(
                      base64Decode(widget.garment.cropBase64!),
                      fit: BoxFit.cover,
                    )
                  : Container(color: AppColors.border),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.ui(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: AppColors.ink900,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  category,
                  style: AppTypography.ui(
                    fontSize: 12,
                    color: AppColors.ink600,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          if (isAdded)
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.check_circle_rounded, color: Colors.green, size: 20),
                const SizedBox(width: 4),
                Text(
                  'Added',
                  style: AppTypography.ui(
                    fontSize: 14,
                    color: Colors.green,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            )
          else if (isProcessing)
            const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: AppColors.clay500,
              ),
            )
          else
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.clay500,
                foregroundColor: AppColors.surface,
                elevation: 0,
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
              onPressed: () async {
                setState(() => _localLoading = true);
                try {
                  await repo.addImportedGarmentToCloset(widget.image, widget.garment);
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Failed to add: $e')),
                    );
                  }
                } finally {
                  if (mounted) {
                    setState(() => _localLoading = false);
                  }
                }
              },
              child: Text(
                'Add',
                style: AppTypography.ui(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.surface,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
