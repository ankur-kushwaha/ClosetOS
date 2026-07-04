import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/stripe_background.dart';

enum _IngestPhase { import, review }

class IngestScreen extends StatefulWidget {
  const IngestScreen({super.key});

  @override
  State<IngestScreen> createState() => _IngestScreenState();
}

class _IngestScreenState extends State<IngestScreen> {
  final _picker = ImagePicker();

  _IngestPhase _phase = _IngestPhase.import;
  bool _detecting = false;
  bool _processing = false;
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

  Future<void> _pickBulkPhotos() async {
    if (_detecting || _processing) return;

    final picked = await _picker.pickMultiImage(imageQuality: 85);
    if (picked.isEmpty || !mounted) return;

    setState(() {
      _detecting = true;
      _boxes = [];
      _phase = _IngestPhase.import;
      _reviewQueue = [];
      _reviewIndex = 0;
      _accepted = 0;
      _rejected = 0;
    });

    final repo = context.read<WardrobeRepository>();
    final allBoxes = <DetectedBox>[];

    for (final file in picked) {
      final bytes = await file.readAsBytes();
      if (!mounted) return;
      final boxes = await repo.detectFromImage(bytes, file.name);
      if (boxes != null) {
        allBoxes.addAll(boxes);
        setState(() => _boxes = List.of(allBoxes));
      }
    }

    if (!mounted) return;

    if (allBoxes.isEmpty) {
      setState(() => _detecting = false);
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
      _processing = true;
    });

    await _prepareForReview(allBoxes);
  }

  Future<void> _prepareForReview(List<DetectedBox> boxes) async {
    final repo = context.read<WardrobeRepository>();
    final prepared = await repo.prepareIngestionBatch(boxes);

    if (!mounted) return;

    final reviewItems = prepared.where((i) => i.status == 'review').toList();
    final failed = prepared.where((i) => i.status == 'failed').length;

    setState(() {
      _processing = false;
      _reviewQueue = reviewItems;
      _reviewIndex = 0;
      _phase =
          reviewItems.isNotEmpty ? _IngestPhase.review : _IngestPhase.import;
    });

    if (reviewItems.isEmpty && failed > 0 && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '$failed items could not be processed.',
            style: AppTypography.ui(color: AppColors.surface),
          ),
        ),
      );
    }
  }

  Future<void> _acceptCurrent() async {
    final item = _currentReview;
    if (item == null || _processing) return;

    setState(() => _processing = true);
    final repo = context.read<WardrobeRepository>();
    final hasNormalized = (item.normalizedBase64 ?? '').isNotEmpty &&
        item.normalizedBase64 != item.cropBase64;

    final result = await repo.finalizeIngestion(
      item,
      useNormalized: hasNormalized,
    );

    if (!mounted) return;

    setState(() {
      _processing = false;
      if (result.status == 'done') _accepted++;
      _reviewIndex++;
    });

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
    if (_accepted > 0) parts.add('$_accepted added');
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

    setState(() {
      _phase = _IngestPhase.import;
      _boxes = [];
      _reviewQueue = [];
      _reviewIndex = 0;
      _accepted = 0;
      _rejected = 0;
    });
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
              processing: _processing,
              onReject: _rejectCurrent,
              onAccept: _acceptCurrent,
            )
          : _ImportView(
              detecting: _detecting,
              processing: _processing,
              foundCount: _foundCount,
              boxes: _boxes,
              onBulkPhotos: _pickBulkPhotos,
            ),
    );
  }
}

// ─── Import phase ────────────────────────────────────────────────────────────

class _ImportView extends StatelessWidget {
  const _ImportView({
    required this.detecting,
    required this.processing,
    required this.foundCount,
    required this.boxes,
    required this.onBulkPhotos,
  });

  final bool detecting;
  final bool processing;
  final int foundCount;
  final List<DetectedBox> boxes;
  final VoidCallback onBulkPhotos;

  @override
  Widget build(BuildContext context) {
    final showGrid = detecting || processing || boxes.isNotEmpty;
    final placeholderCount = detecting ? 6 : 0;

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      children: [
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
        _BulkPhotosCard(
          onTap: detecting || processing ? null : onBulkPhotos,
          busy: detecting || processing,
        ),
        if (showGrid) ...[
          const SizedBox(height: 20),
          _StatusLine(
            detecting: detecting,
            processing: processing,
            count: foundCount,
          ),
          const SizedBox(height: 16),
          _ItemGrid(
            boxes: boxes,
            placeholderCount: placeholderCount,
          ),
        ],
      ],
    );
  }
}

class _BulkPhotosCard extends StatelessWidget {
  const _BulkPhotosCard({required this.onTap, required this.busy});

  final VoidCallback? onTap;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          height: 112,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.border),
          ),
          child: busy
              ? const Center(
                  child: SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                )
              : Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(
                      Icons.photo_outlined,
                      size: 28,
                      color: AppColors.ink600,
                    ),
                    const SizedBox(height: 10),
                    Text(
                      'Bulk photos',
                      style: AppTypography.ui(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
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

class _StatusLine extends StatelessWidget {
  const _StatusLine({
    required this.detecting,
    required this.processing,
    required this.count,
  });

  final bool detecting;
  final bool processing;
  final int count;

  @override
  Widget build(BuildContext context) {
    final String text;
    if (detecting) {
      text = count > 0
          ? '$count items found, and counting…'
          : 'Scanning your photos…';
    } else if (processing) {
      text = '$count items found — normalizing…';
    } else {
      text = '$count items found';
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
  });

  final List<DetectedBox> boxes;
  final int placeholderCount;

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
          return _GridThumbnail(box: boxes[i]);
        }
        return const _GridPlaceholder();
      },
    );
  }
}

class _GridThumbnail extends StatelessWidget {
  const _GridThumbnail({required this.box});

  final DetectedBox box;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: box.cropBase64.isNotEmpty
          ? Image.memory(
              base64Decode(box.cropBase64),
              fit: BoxFit.cover,
              gaplessPlayback: true,
            )
          : const StripeBackground(
              baseColor: AppColors.surface,
              opacity: 0.35,
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

class _ReviewSweepView extends StatelessWidget {
  const _ReviewSweepView({
    required this.current,
    required this.total,
    required this.item,
    required this.nextItem,
    required this.processing,
    required this.onReject,
    required this.onAccept,
  });

  final int current;
  final int total;
  final IngestionItem item;
  final IngestionItem? nextItem;
  final bool processing;
  final VoidCallback onReject;
  final VoidCallback onAccept;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
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
                  '$current / $total',
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
                  if (nextItem != null)
                    Positioned(
                      top: 12,
                      left: 16,
                      right: 16,
                      bottom: 0,
                      child: Transform.scale(
                        scale: 0.96,
                        child: _ReviewCard(
                          item: nextItem!,
                          interactive: false,
                        ),
                      ),
                    ),
                  Positioned.fill(
                    child: _ReviewCard(
                      item: item,
                      interactive: !processing,
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
                  onPressed: processing ? null : onReject,
                ),
                const SizedBox(width: 28),
                _SweepActionButton(
                  icon: Icons.check,
                  filled: true,
                  onPressed: processing ? null : onAccept,
                  loading: processing,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ReviewCard extends StatelessWidget {
  const _ReviewCard({
    required this.item,
    this.interactive = true,
  });

  final IngestionItem item;
  final bool interactive;

  @override
  Widget build(BuildContext context) {
    final attrs = item.attributes;
    final imageB64 = item.normalizedBase64?.isNotEmpty == true
        ? item.normalizedBase64!
        : (item.cropBase64 ?? '');
    final name = _garmentName(item, attrs);
    final chips = _buildChips(attrs);

    return Opacity(
      opacity: interactive ? 1 : 0.55,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppColors.border),
          boxShadow: interactive
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
              flex: 11,
              child: imageB64.isNotEmpty
                  ? Image.memory(
                      base64Decode(imageB64),
                      fit: BoxFit.contain,
                      gaplessPlayback: true,
                    )
                  : const StripeBackground(
                      baseColor: AppColors.surface,
                      opacity: 0.35,
                    ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Expanded(
                        child: Text(
                          name,
                          style: AppTypography.ui(
                            fontSize: 17,
                            fontWeight: FontWeight.w600,
                            color: AppColors.ink900,
                          ),
                        ),
                      ),
                    ],
                  ),
                  if (chips.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: chips,
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _garmentName(IngestionItem item, ExtractedAttributes? attrs) {
    if (attrs != null && attrs.subcategory.isNotEmpty) {
      return attrs.subcategory;
    }
    if (attrs != null && attrs.category.isNotEmpty) {
      return attrs.category;
    }
    return item.label ?? 'Garment';
  }

  List<Widget> _buildChips(ExtractedAttributes? attrs) {
    if (attrs == null) return [];

    final tags = <(String label, bool highlight)>[
      (attrs.category, true),
      if (attrs.colorName.isNotEmpty) (attrs.colorName, false),
      if (attrs.material.isNotEmpty) (attrs.material, false),
      if (attrs.fit.isNotEmpty) ('${attrs.fit} fit', false),
    ];

    return tags.map((tag) => _MetaChip(label: tag.$1, selected: tag.$2)).toList();
  }
}

class _MetaChip extends StatelessWidget {
  const _MetaChip({required this.label, required this.selected});

  final String label;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: selected ? AppColors.clay100 : AppColors.greige,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: selected ? AppColors.clay500.withValues(alpha: 0.35) : Colors.transparent,
        ),
      ),
      child: Text(
        label,
        style: AppTypography.ui(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          color: selected ? AppColors.clay700 : AppColors.ink600,
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
    this.loading = false,
  });

  final IconData icon;
  final bool filled;
  final VoidCallback? onPressed;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: filled ? AppColors.clay500 : AppColors.surface,
      shape: const CircleBorder(),
      elevation: filled ? 0 : 0,
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
          child: loading
              ? const Padding(
                  padding: EdgeInsets.all(16),
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppColors.surface,
                  ),
                )
              : Icon(
                  icon,
                  size: 24,
                  color: filled ? AppColors.surface : AppColors.clay500,
                ),
        ),
      ),
    );
  }
}
