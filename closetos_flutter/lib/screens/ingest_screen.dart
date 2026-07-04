import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/garment_attr_field.dart';
import '../widgets/stripe_background.dart';

enum _IngestPhase { import, review }

class IngestScreen extends StatefulWidget {
  const IngestScreen({super.key, this.onReviewComplete});

  final VoidCallback? onReviewComplete;

  @override
  State<IngestScreen> createState() => _IngestScreenState();
}

class _IngestScreenState extends State<IngestScreen> {
  final _picker = ImagePicker();

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

  Future<void> _pickBulkPhotos() async {
    if (_detecting) return;

    final picked = await _picker.pickMultiImage(imageQuality: 85);
    if (picked.isEmpty || !mounted) return;

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
    });
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
  });

  final bool detecting;
  final bool fetchingMetadata;
  final int foundCount;
  final List<DetectedBox> boxes;
  final VoidCallback onBulkPhotos;
  final void Function(int index) onDeleteBox;
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    final showGrid = detecting || boxes.isNotEmpty;
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
          onTap: detecting ? null : onBulkPhotos,
          busy: detecting,
        ),
        if (showGrid) ...[
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
        ],
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
