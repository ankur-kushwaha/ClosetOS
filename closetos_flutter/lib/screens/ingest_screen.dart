import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/garment_attr_field.dart';
import '../widgets/stripe_background.dart';

enum _IngestPhase { import, review }

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

  _IngestPhase _phase = _IngestPhase.import;
  bool _detecting = false;
  bool _fetchingMetadata = false;
  List<DetectedBox> _boxes = [];
  List<IngestionItem> _reviewQueue = [];
  int _reviewIndex = 0;
  int _accepted = 0;
  int _rejected = 0;

  int get _foundCount => _boxes.length;

  IngestionItem? get _currentReview =>
      _reviewIndex < _reviewQueue.length ? _reviewQueue[_reviewIndex] : null;

  IngestionItem? get _nextReview => _reviewIndex + 1 < _reviewQueue.length
      ? _reviewQueue[_reviewIndex + 1]
      : null;

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

  Future<void> _processImageBytesList(
    List<Future<Uint8List?>> bytesFutures,
    List<String> names,
  ) async {
    setState(() {
      _detecting = true;
      _boxes = [];
      _fetchingMetadata = false;
      _phase = _IngestPhase.import;
      _reviewQueue = [];
      _reviewIndex = 0;
      _accepted = 0;
      _rejected = 0;
    });

    final repo = context.read<WardrobeRepository>();
    final allBoxes = <DetectedBox>[];

    for (int i = 0; i < bytesFutures.length; i++) {
      final bytes = await bytesFutures[i];
      if (bytes == null) continue;
      if (!mounted) return;
      
      final boxes = await repo.detectFromImage(bytes, names[i]);
      if (boxes != null) {
        allBoxes.addAll(boxes);
        setState(() => _boxes = List.of(allBoxes));
      }
    }

    if (!mounted) return;

    if (allBoxes.isEmpty) {
      setState(() {
        _detecting = false;
        _selectedGalleryItems.clear();
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            repo.lastError ?? 'No garments detected.',
            style: AppTypography.ui(color: AppColors.surface),
          ),
        ),
      );
      return;
    }

    setState(() {
      _detecting = false;
      _boxes = allBoxes;
      _selectedGalleryItems.clear();
    });
  }

  Future<void> _pickBulkPhotos() async {
    if (_detecting) return;
    final picked = await _picker.pickMultiImage(imageQuality: 85);
    if (picked.isEmpty) return;
    _processImageBytesList(
      picked.map((file) => file.readAsBytes()).toList(),
      picked.map((file) => file.name).toList(),
    );
  }

  Future<void> _pickFromCamera() async {
    if (_detecting) return;
    final picked = await _picker.pickImage(source: ImageSource.camera, imageQuality: 85);
    if (picked == null) return;
    _processImageBytesList([picked.readAsBytes()], [picked.name]);
  }

  Future<void> _processSelected() async {
    if (_detecting || _selectedGalleryItems.isEmpty) return;
    final selected = List<GalleryItem>.from(_selectedGalleryItems);
    _processImageBytesList(
      selected.map((item) => item.getBytes()).toList(),
      selected.map((item) => item.name).toList(),
    );
  }

  void _deleteBox(int index) {
    if (_fetchingMetadata) return;
    setState(() => _boxes.removeAt(index));
  }

  Future<void> _startReview() async {
    if (_boxes.isEmpty || _fetchingMetadata) return;

    final repo = context.read<WardrobeRepository>();
    final boxes = List.of(_boxes);

    setState(() => _fetchingMetadata = true);

    final metadata = await repo.fetchMetadataBulk(boxes);
    if (!mounted) return;

    final items = await Future.wait([
      for (var i = 0; i < boxes.length; i++)
        repo.ingestionItemFromBox(
          boxes[i],
          attributes: metadata[i],
        ),
    ]);

    if (!mounted) return;

    setState(() {
      _reviewQueue = items;
      _reviewIndex = 0;
      _phase = _IngestPhase.review;
      _boxes = [];
      _fetchingMetadata = false;
    });
  }

  void _acceptCurrent(ExtractedAttributes? editedAttrs) {
    final item = _currentReview;
    if (item == null) return;

    final attrs = editedAttrs ??
        item.attributes ??
        ExtractedAttributes.fromLabel(item.label ?? 'garment');

    setState(() {
      _accepted++;
      _reviewIndex++;
    });

    context.read<WardrobeRepository>().enqueueFinalize(item, attrs);
    _checkReviewComplete();
  }

  void _rejectCurrent() {
    final item = _currentReview;
    if (item == null) return;

    context.read<WardrobeRepository>().ingestionQueue
        .removeWhere((i) => i.id == item.id);
    setState(() {
      _rejected++;
      _reviewIndex++;
    });
    _checkReviewComplete();
  }

  void _checkReviewComplete() {
    if (_reviewIndex < _reviewQueue.length) return;

    if (!mounted) return;

    final parts = <String>[];
    if (_accepted > 0) parts.add('$_accepted saving in background');
    if (_rejected > 0) parts.add('$_rejected skipped');

    if (parts.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${parts.join(', ')}.',
            style: AppTypography.ui(color: AppColors.surface),
          ),
        ),
      );
    }

    final shouldGoToWardrobe = _accepted > 0;

    setState(() {
      _phase = _IngestPhase.import;
      _boxes = [];
      _reviewQueue = [];
      _reviewIndex = 0;
      _accepted = 0;
      _rejected = 0;
      _selectedGalleryItems.clear();
    });

    if (shouldGoToWardrobe) {
      widget.onReviewComplete?.call();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: AppColors.canvas,
      child: _phase == _IngestPhase.review
          ? _ReviewSweepView(
              current: _reviewIndex + 1,
              total: _reviewQueue.length,
              item: _currentReview!,
              nextItem: _nextReview,
              onReject: _rejectCurrent,
              onAccept: _acceptCurrent,
            )
          : _ImportView(
              detecting: _detecting,
              fetchingMetadata: _fetchingMetadata,
              foundCount: _foundCount,
              boxes: _boxes,
              onBulkPhotos: _pickBulkPhotos,
              onDeleteBox: _deleteBox,
              onContinue: _startReview,
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
              onOpenDrawer: widget.onOpenDrawer,
            ),
    );
  }
}

// ─── Import phase ────────────────────────────────────────────────────────────

class _ImportView extends StatelessWidget {
  const _ImportView({
    required this.detecting,
    required this.fetchingMetadata,
    required this.foundCount,
    required this.boxes,
    required this.onBulkPhotos,
    required this.onDeleteBox,
    required this.onContinue,
    required this.galleryItems,
    required this.selectedGalleryItems,
    required this.galleryLoading,
    required this.hasGalleryPermission,
    required this.onToggleSelect,
    required this.onScanSelected,
    required this.onPickFromCamera,
    this.onOpenDrawer,
  });

  final bool detecting;
  final bool fetchingMetadata;
  final int foundCount;
  final List<DetectedBox> boxes;
  final VoidCallback onBulkPhotos;
  final void Function(int index) onDeleteBox;
  final VoidCallback onContinue;

  final List<GalleryItem> galleryItems;
  final List<GalleryItem> selectedGalleryItems;
  final bool galleryLoading;
  final bool hasGalleryPermission;
  final void Function(GalleryItem item) onToggleSelect;
  final VoidCallback onScanSelected;
  final VoidCallback onPickFromCamera;
  final VoidCallback? onOpenDrawer;

  @override
  Widget build(BuildContext context) {
    final showGrid = detecting || boxes.isNotEmpty;
    final placeholderCount = detecting ? 6 : 0;

    if (showGrid) {
      return ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: IconButton(
              icon: const Icon(Icons.menu, size: 22, color: AppColors.ink900),
              padding: EdgeInsets.zero,
              onPressed: onOpenDrawer,
            ),
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
          const SizedBox(height: 20),
          _StatusLine(
            detecting: detecting,
            fetchingMetadata: fetchingMetadata,
            count: foundCount,
          ),
          const SizedBox(height: 16),
          _ItemGrid(
            boxes: boxes,
            placeholderCount: placeholderCount,
            onDeleteBox: fetchingMetadata ? null : onDeleteBox,
          ),
          if (boxes.isNotEmpty && !detecting) ...[
            const SizedBox(height: 20),
            FilledButton(
              onPressed: fetchingMetadata ? null : onContinue,
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.clay500,
                foregroundColor: AppColors.surface,
                minimumSize: const Size.fromHeight(48),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
              child: fetchingMetadata
                  ? Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AppColors.surface,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Text(
                          'Analyzing $foundCount item${foundCount == 1 ? '' : 's'}…',
                          style: AppTypography.ui(
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    )
                  : Text(
                      'Confirm & review $foundCount item${foundCount == 1 ? '' : 's'}',
                      style: AppTypography.ui(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
            ),
          ],
        ],
      );
    }

    return Stack(
      children: [
        CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
              sliver: SliverToBoxAdapter(
                child: Column(
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
                      hasGalleryPermission
                          ? 'Select photos from camera roll to scan'
                          : 'Select demo clothes or upload files to scan',
                      style: AppTypography.ui(
                        fontSize: 14,
                        color: AppColors.ink600,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (galleryLoading)
              const SliverFillRemaining(
                child: Center(
                  child: CircularProgressIndicator(),
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
                sliver: SliverGrid(
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 3,
                    mainAxisSpacing: 8,
                    crossAxisSpacing: 8,
                    childAspectRatio: 1,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      if (index == 0) {
                        return _ActionTile(
                          icon: Icons.camera_alt_outlined,
                          label: 'Camera',
                          onTap: onPickFromCamera,
                        );
                      }
                      if (index == 1) {
                        return _ActionTile(
                          icon: Icons.photo_outlined,
                          label: 'Files',
                          onTap: onBulkPhotos,
                        );
                      }

                      final item = galleryItems[index - 2];
                      final isSelected = selectedGalleryItems.contains(item);
                      final selectIndex = selectedGalleryItems.indexOf(item);

                      return _GalleryItemTile(
                        item: item,
                        isSelected: isSelected,
                        selectIndex: selectIndex,
                        onTap: () => onToggleSelect(item),
                      );
                    },
                    childCount: galleryItems.length + 2,
                  ),
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

class _StatusLine extends StatelessWidget {
  const _StatusLine({
    required this.detecting,
    required this.fetchingMetadata,
    required this.count,
  });

  final bool detecting;
  final bool fetchingMetadata;
  final int count;

  @override
  Widget build(BuildContext context) {
    final String text;
    if (detecting) {
      text = count > 0
          ? '$count items found, and counting…'
          : 'Scanning your photos…';
    } else if (fetchingMetadata) {
      text = 'Analyzing colors & details for $count item${count == 1 ? '' : 's'}…';
    } else {
      text = '$count items found — remove any mistakes, then confirm';
    }

    return Row(
      children: [
        Container(
          width: 7,
          height: 7,
          decoration: const BoxDecoration(
            color: AppColors.clay500,
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            text,
            style: AppTypography.ui(
              fontSize: 13,
              color: AppColors.ink600,
            ),
          ),
        ),
      ],
    );
  }
}

class _ItemGrid extends StatelessWidget {
  const _ItemGrid({
    required this.boxes,
    required this.placeholderCount,
    this.onDeleteBox,
  });

  final List<DetectedBox> boxes;
  final int placeholderCount;
  final void Function(int index)? onDeleteBox;

  @override
  Widget build(BuildContext context) {
    final total = boxes.length + placeholderCount;

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        mainAxisSpacing: 10,
        crossAxisSpacing: 10,
        childAspectRatio: 1,
      ),
      itemCount: total,
      itemBuilder: (_, i) {
        if (i < boxes.length) {
          return _GridThumbnail(
            box: boxes[i],
            onDelete: onDeleteBox != null ? () => onDeleteBox!(i) : null,
          );
        }
        return const _GridPlaceholder();
      },
    );
  }
}

class _GridThumbnail extends StatelessWidget {
  const _GridThumbnail({
    required this.box,
    this.onDelete,
  });

  final DetectedBox box;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        fit: StackFit.expand,
        children: [
          box.cropBase64.isNotEmpty
              ? Image.memory(
                  base64Decode(box.cropBase64),
                  fit: BoxFit.cover,
                  gaplessPlayback: true,
                )
              : const StripeBackground(
                  baseColor: AppColors.surface,
                  opacity: 0.35,
                ),
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
              color: AppColors.ink900.withValues(alpha: 0.65),
              child: Text(
                box.label,
                textAlign: TextAlign.center,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: AppTypography.ui(
                  fontSize: 10,
                  fontWeight: FontWeight.w500,
                  color: AppColors.surface,
                ),
              ),
            ),
          ),
          if (onDelete != null)
            Positioned(
              top: 4,
              right: 4,
              child: Material(
                color: AppColors.ink900.withValues(alpha: 0.55),
                shape: const CircleBorder(),
                child: InkWell(
                  onTap: onDelete,
                  customBorder: const CircleBorder(),
                  child: const Padding(
                    padding: EdgeInsets.all(4),
                    child: Icon(
                      Icons.close,
                      size: 14,
                      color: AppColors.surface,
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

class _GridPlaceholder extends StatelessWidget {
  const _GridPlaceholder();

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: const StripeBackground(
        baseColor: AppColors.surface,
        opacity: 0.35,
      ),
    );
  }
}

// ─── Review sweep phase ──────────────────────────────────────────────────────

class _ReviewSweepView extends StatefulWidget {
  const _ReviewSweepView({
    required this.current,
    required this.total,
    required this.item,
    required this.nextItem,
    required this.onReject,
    required this.onAccept,
  });

  final int current;
  final int total;
  final IngestionItem item;
  final IngestionItem? nextItem;
  final VoidCallback onReject;
  final void Function(ExtractedAttributes? attrs) onAccept;

  @override
  State<_ReviewSweepView> createState() => _ReviewSweepViewState();
}

class _ReviewSweepViewState extends State<_ReviewSweepView> {
  final _cardKey = GlobalKey<_ReviewCardState>();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  'Review your sweep',
                  style: AppTypography.display(
                    fontSize: 30,
                    color: AppColors.ink900,
                    fontWeight: FontWeight.w500,
                    height: 1.1,
                  ),
                ),
              ),
              Text(
                '${widget.current} / ${widget.total}',
                style: AppTypography.ui(
                  fontSize: 13,
                  color: AppColors.ink600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Expanded(
            child: Stack(
              alignment: Alignment.center,
              children: [
                if (widget.nextItem != null)
                  Positioned(
                    top: 12,
                    left: 16,
                    right: 16,
                    bottom: 0,
                    child: Transform.scale(
                      scale: 0.96,
                      child: _ReviewCard(
                        key: ValueKey(widget.nextItem!.id),
                        item: widget.nextItem!,
                        interactive: false,
                      ),
                    ),
                  ),
                Positioned.fill(
                  child: _ReviewCard(
                    key: _cardKey,
                    item: widget.item,
                    interactive: true,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _SweepActionButton(
                icon: Icons.close,
                filled: false,
                onPressed: widget.onReject,
              ),
              const SizedBox(width: 28),
              _SweepActionButton(
                icon: Icons.check,
                filled: true,
                onPressed: () {
                  widget.onAccept(_cardKey.currentState?.editedAttributes);
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ReviewCard extends StatefulWidget {
  const _ReviewCard({
    super.key,
    required this.item,
    this.interactive = true,
  });

  final IngestionItem item;
  final bool interactive;

  @override
  State<_ReviewCard> createState() => _ReviewCardState();
}

class _ReviewCardState extends State<_ReviewCard> {
  late TextEditingController _subcategoryCtrl;
  late TextEditingController _categoryCtrl;
  late TextEditingController _colorCtrl;
  late TextEditingController _materialCtrl;
  late TextEditingController _patternCtrl;
  late TextEditingController _fitCtrl;

  ExtractedAttributes get _baseAttrs =>
      widget.item.attributes ??
      ExtractedAttributes.fromLabel(widget.item.label ?? 'Garment');

  ExtractedAttributes? get editedAttributes => _baseAttrs.copyWith(
        subcategory: _subcategoryCtrl.text.trim(),
        category: _categoryCtrl.text.trim(),
        colorName: _colorCtrl.text.trim(),
        material: _materialCtrl.text.trim(),
        pattern: _patternCtrl.text.trim(),
        fit: _fitCtrl.text.trim(),
      );

  void _syncControllers(ExtractedAttributes attrs) {
    _subcategoryCtrl.text = attrs.subcategory.isNotEmpty
        ? attrs.subcategory
        : (widget.item.label ?? '');
    _categoryCtrl.text = attrs.category;
    _colorCtrl.text = attrs.colorName;
    _materialCtrl.text = attrs.material;
    _patternCtrl.text = attrs.pattern;
    _fitCtrl.text = attrs.fit;
  }

  @override
  void initState() {
    super.initState();
    final attrs = _baseAttrs;
    _subcategoryCtrl = TextEditingController();
    _categoryCtrl = TextEditingController();
    _colorCtrl = TextEditingController();
    _materialCtrl = TextEditingController();
    _patternCtrl = TextEditingController();
    _fitCtrl = TextEditingController();
    _syncControllers(attrs);
  }

  @override
  void didUpdateWidget(covariant _ReviewCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.item.id != widget.item.id) {
      _syncControllers(_baseAttrs);
    }
  }

  @override
  void dispose() {
    _subcategoryCtrl.dispose();
    _categoryCtrl.dispose();
    _colorCtrl.dispose();
    _materialCtrl.dispose();
    _patternCtrl.dispose();
    _fitCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final imageB64 = widget.item.cropBase64 ?? '';
    final label = widget.item.label ?? 'Garment';

    return Opacity(
      opacity: widget.interactive ? 1 : 0.55,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppColors.border),
          boxShadow: widget.interactive
              ? [
                  BoxShadow(
                    color: AppColors.ink900.withValues(alpha: 0.06),
                    blurRadius: 24,
                    offset: const Offset(0, 8),
                  ),
                ]
              : null,
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              flex: 10,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  if (imageB64.isNotEmpty)
                    Image.memory(
                      base64Decode(imageB64),
                      fit: BoxFit.contain,
                      gaplessPlayback: true,
                    )
                  else
                    const StripeBackground(
                      baseColor: AppColors.surface,
                      opacity: 0.35,
                    ),
                  Positioned(
                    left: 12,
                    top: 12,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 5,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.ink900.withValues(alpha: 0.7),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        label,
                        style: AppTypography.ui(
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                          color: AppColors.surface,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            if (widget.interactive)
              Flexible(
                flex: 9,
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      GarmentAttrField(
                        label: 'Name',
                        controller: _subcategoryCtrl,
                        hint: 'e.g. Crew neck tee',
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: GarmentAttrField(
                              label: 'Category',
                              controller: _categoryCtrl,
                              hint: 'Top',
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: GarmentAttrField(
                              label: 'Color',
                              controller: _colorCtrl,
                              hint: 'Navy',
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: GarmentAttrField(
                              label: 'Material',
                              controller: _materialCtrl,
                              hint: 'Cotton',
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: GarmentAttrField(
                              label: 'Pattern',
                              controller: _patternCtrl,
                              hint: 'Solid',
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      GarmentAttrField(
                        label: 'Fit',
                        controller: _fitCtrl,
                        hint: 'Regular',
                      ),
                    ],
                  ),
                ),
              )
            else
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
                child: Text(
                  _baseAttrs.subcategory.isNotEmpty
                      ? _baseAttrs.subcategory
                      : label,
                  style: AppTypography.ui(
                    fontSize: 17,
                    fontWeight: FontWeight.w600,
                    color: AppColors.ink900,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _SweepActionButton extends StatelessWidget {
  const _SweepActionButton({
    required this.icon,
    required this.filled,
    required this.onPressed,
  });

  final IconData icon;
  final bool filled;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: filled ? AppColors.clay500 : AppColors.surface,
      shape: const CircleBorder(),
      child: InkWell(
        onTap: onPressed,
        customBorder: const CircleBorder(),
        child: Container(
          width: 56,
          height: 56,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: filled ? null : Border.all(color: AppColors.border),
          ),
          child: Icon(
            icon,
            size: 24,
            color: filled ? AppColors.surface : AppColors.clay500,
          ),
        ),
      ),
    );
  }
}
