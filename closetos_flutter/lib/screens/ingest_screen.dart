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
  const IngestScreen({super.key, this.onReviewComplete});

  final VoidCallback? onReviewComplete;

  @override
  State<IngestScreen> createState() => _IngestScreenState();
}

class _IngestScreenState extends State<IngestScreen> {
  final _picker = ImagePicker();

  _IngestPhase _phase = _IngestPhase.import;
  bool _detecting = false;
  bool _processing = false;
  List<DetectedBox> _boxes = [];
  List<IngestionItem?> _prepItems = [];
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
      _prepItems = [];
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
      _prepItems = List<IngestionItem?>.filled(allBoxes.length, null);
    });

    await _prepareForReview(allBoxes);
  }

  Future<void> _prepareForReview(List<DetectedBox> boxes) async {
    final repo = context.read<WardrobeRepository>();

    final prepared = await Future.wait(
      List.generate(boxes.length, (i) {
        final box = boxes[i];
        return repo.prepareIngestionReview(
          box,
          onUpdate: (item) {
            if (!mounted) return;
            setState(() => _prepItems[i] = item);
          },
        ).then((item) {
          if (mounted) setState(() => _prepItems[i] = item);
          return item;
        });
      }),
    );

    if (!mounted) return;

    final reviewItems = prepared.where((i) => i.status == 'review').toList();
    final failed = prepared.where((i) => i.status == 'failed').length;

    setState(() {
      _processing = false;
      _prepItems = [];
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

  void _acceptCurrent(ExtractedAttributes? editedAttrs) {
    final item = _currentReview;
    if (item == null) return;

    if (editedAttrs != null) {
      item.attributes = editedAttrs;
    }

    final hasNormalized = (item.normalizedBase64 ?? '').isNotEmpty &&
        item.normalizedBase64 != item.cropBase64;

    setState(() {
      _accepted++;
      _reviewIndex++;
    });

    final repo = context.read<WardrobeRepository>();
    repo.finalizeIngestion(item, useNormalized: hasNormalized).then((result) {
      if (!mounted) return;
      if (result.status == 'failed') {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              result.error ?? 'Could not save garment.',
              style: AppTypography.ui(color: AppColors.surface),
            ),
          ),
        );
      }
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

    final shouldGoToWardrobe = _accepted > 0;

    setState(() {
      _phase = _IngestPhase.import;
      _boxes = [];
      _prepItems = [];
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
              processing: _processing,
              foundCount: _foundCount,
              boxes: _boxes,
              prepItems: _prepItems,
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
    required this.prepItems,
    required this.onBulkPhotos,
  });

  final bool detecting;
  final bool processing;
  final int foundCount;
  final List<DetectedBox> boxes;
  final List<IngestionItem?> prepItems;
  final VoidCallback onBulkPhotos;

  @override
  Widget build(BuildContext context) {
    final showGrid = detecting || processing || boxes.isNotEmpty;
    final placeholderCount = detecting ? 6 : 0;
    final readyCount = prepItems.where((i) => i?.status == 'review').length;

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
            readyCount: readyCount,
          ),
          const SizedBox(height: 16),
          _ItemGrid(
            boxes: boxes,
            prepItems: prepItems,
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
    required this.readyCount,
  });

  final bool detecting;
  final bool processing;
  final int count;
  final int readyCount;

  @override
  Widget build(BuildContext context) {
    final String text;
    if (detecting) {
      text = count > 0
          ? '$count items found, and counting…'
          : 'Scanning your photos…';
    } else if (processing) {
      text = readyCount > 0
          ? '$readyCount of $count ready — fetching metadata…'
          : '$count items found — fetching metadata…';
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
    required this.prepItems,
    required this.placeholderCount,
  });

  final List<DetectedBox> boxes;
  final List<IngestionItem?> prepItems;
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
          final prep = i < prepItems.length ? prepItems[i] : null;
          final processing = prep == null || prep.status == 'processing';
          return _GridThumbnail(
            box: boxes[i],
            processing: processing,
            stepLabel: prep?.stepLabel,
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
    this.processing = false,
    this.stepLabel,
  });

  final DetectedBox box;
  final bool processing;
  final String? stepLabel;

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
          if (processing)
            ColoredBox(
              color: AppColors.ink900.withValues(alpha: 0.5),
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: AppColors.surface,
                      ),
                    ),
                    if (stepLabel != null && stepLabel!.isNotEmpty) ...[
                      const SizedBox(height: 6),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 6),
                        child: Text(
                          stepLabel!,
                          textAlign: TextAlign.center,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: AppTypography.ui(
                            fontSize: 9,
                            color: AppColors.surface,
                          ),
                        ),
                      ),
                    ],
                  ],
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

  ExtractedAttributes? get editedAttributes {
    final base = widget.item.attributes;
    if (base == null) return null;
    return base.copyWith(
      subcategory: _subcategoryCtrl.text.trim(),
      category: _categoryCtrl.text.trim(),
      colorName: _colorCtrl.text.trim(),
      material: _materialCtrl.text.trim(),
      pattern: _patternCtrl.text.trim(),
      fit: _fitCtrl.text.trim(),
    );
  }

  @override
  void initState() {
    super.initState();
    final attrs = widget.item.attributes;
    _subcategoryCtrl = TextEditingController(
      text: attrs?.subcategory.isNotEmpty == true
          ? attrs!.subcategory
          : (widget.item.label ?? ''),
    );
    _categoryCtrl = TextEditingController(text: attrs?.category ?? '');
    _colorCtrl = TextEditingController(text: attrs?.colorName ?? '');
    _materialCtrl = TextEditingController(text: attrs?.material ?? '');
    _patternCtrl = TextEditingController(text: attrs?.pattern ?? '');
    _fitCtrl = TextEditingController(text: attrs?.fit ?? '');
  }

  @override
  void didUpdateWidget(covariant _ReviewCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.item.id != widget.item.id) {
      final attrs = widget.item.attributes;
      _subcategoryCtrl.text = attrs?.subcategory.isNotEmpty == true
          ? attrs!.subcategory
          : (widget.item.label ?? '');
      _categoryCtrl.text = attrs?.category ?? '';
      _colorCtrl.text = attrs?.colorName ?? '';
      _materialCtrl.text = attrs?.material ?? '';
      _patternCtrl.text = attrs?.pattern ?? '';
      _fitCtrl.text = attrs?.fit ?? '';
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
    final imageB64 = widget.item.normalizedBase64?.isNotEmpty == true
        ? widget.item.normalizedBase64!
        : (widget.item.cropBase64 ?? '');

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
            if (widget.interactive)
              Flexible(
                flex: 9,
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _AttrField(
                        label: 'Name',
                        controller: _subcategoryCtrl,
                        hint: 'e.g. Crew neck tee',
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: _AttrField(
                              label: 'Category',
                              controller: _categoryCtrl,
                              hint: 'Top',
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: _AttrField(
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
                            child: _AttrField(
                              label: 'Material',
                              controller: _materialCtrl,
                              hint: 'Cotton',
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: _AttrField(
                              label: 'Pattern',
                              controller: _patternCtrl,
                              hint: 'Solid',
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      _AttrField(
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
                  widget.item.attributes?.subcategory.isNotEmpty == true
                      ? widget.item.attributes!.subcategory
                      : (widget.item.label ?? 'Garment'),
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

class _AttrField extends StatelessWidget {
  const _AttrField({
    required this.label,
    required this.controller,
    required this.hint,
  });

  final String label;
  final TextEditingController controller;
  final String hint;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: AppTypography.ui(
            fontSize: 11,
            fontWeight: FontWeight.w500,
            color: AppColors.ink400,
          ),
        ),
        const SizedBox(height: 4),
        TextField(
          controller: controller,
          style: AppTypography.ui(fontSize: 13, color: AppColors.ink900),
          decoration: InputDecoration(
            hintText: hint,
            hintStyle: AppTypography.ui(fontSize: 13, color: AppColors.ink400),
            isDense: true,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 10,
            ),
            filled: true,
            fillColor: AppColors.greige,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide.none,
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide.none,
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(
                color: AppColors.clay500.withValues(alpha: 0.5),
              ),
            ),
          ),
        ),
      ],
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
