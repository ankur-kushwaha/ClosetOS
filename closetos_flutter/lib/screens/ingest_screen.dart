import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';
import '../widgets/normalization_review_sheet.dart';

class IngestScreen extends StatefulWidget {
  const IngestScreen({super.key});

  @override
  State<IngestScreen> createState() => _IngestScreenState();
}

class _IngestScreenState extends State<IngestScreen> {
  final _picker = ImagePicker();
  bool _detecting = false;
  bool _processing = false;
  List<DetectedBox>? _boxes;
  final _processingItems = <String, IngestionItem>{};

  Future<void> _pickImages({required bool multiple}) async {
    if (_detecting || _processing) return;

    final List<XFile> files;
    if (multiple) {
      final picked = await _picker.pickMultiImage(imageQuality: 85);
      if (picked.isEmpty || !mounted) return;
      files = picked;
    } else {
      final file = await _picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 85,
      );
      if (file == null || !mounted) return;
      files = [file];
    }

    setState(() {
      _detecting = true;
      _boxes = null;
    });

    final repo = context.read<WardrobeRepository>();
    final allBoxes = <DetectedBox>[];

    for (final file in files) {
      final bytes = await file.readAsBytes();
      if (!mounted) return;
      final boxes = await repo.detectFromImage(bytes, file.name);
      if (boxes != null) allBoxes.addAll(boxes);
    }

    if (!mounted) return;
    setState(() {
      _detecting = false;
      _boxes = allBoxes.isEmpty ? null : allBoxes;
    });

    if (allBoxes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(repo.lastError ?? 'No garments detected.'),
        ),
      );
    }
  }

  Future<void> _capturePhoto() async {
    if (_detecting || _processing) return;

    final file = await _picker.pickImage(
      source: ImageSource.camera,
      imageQuality: 85,
    );
    if (file == null || !mounted) return;

    setState(() {
      _detecting = true;
      _boxes = null;
    });

    final bytes = await file.readAsBytes();
    if (!mounted) return;
    final boxes = await context
        .read<WardrobeRepository>()
        .detectFromImage(bytes, file.name);

    if (!mounted) return;
    setState(() {
      _detecting = false;
      _boxes = boxes;
    });

    if (boxes == null || boxes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            context.read<WardrobeRepository>().lastError ??
                'No garments detected.',
          ),
        ),
      );
    }
  }

  void _toggleAll(bool selected) {
    final boxes = _boxes;
    if (boxes == null) return;
    setState(() {
      for (final box in boxes) {
        box.isSelected = selected;
      }
    });
  }

  Future<void> _processSelected() async {
    final selected = _boxes?.where((b) => b.isSelected).toList() ?? [];
    if (selected.isEmpty || _processing) return;

    setState(() {
      _processing = true;
      _boxes = null;
      _processingItems.clear();
    });

    final repo = context.read<WardrobeRepository>();
    final prepared = await repo.prepareIngestionBatch(
      selected,
      onUpdate: (item) {
        if (mounted) setState(() => _processingItems[item.id] = item);
      },
    );

    if (!mounted) return;

    setState(() => _processing = false);

    final reviewItems = prepared.where((i) => i.status == 'review').toList();
    var succeeded = 0;
    var failed = prepared.where((i) => i.status == 'failed').length;
    var skipped = 0;

    for (final item in reviewItems) {
      if (!mounted) break;

      final useNormalized = await showNormalizationReview(context, item);
      if (useNormalized == null) {
        skipped++;
        repo.ingestionQueue.removeWhere((i) => i.id == item.id);
        setState(() => _processingItems.remove(item.id));
        continue;
      }

      setState(() => _processing = true);
      final result = await repo.finalizeIngestion(
        item,
        useNormalized: useNormalized,
        onUpdate: (updated) {
          if (mounted) setState(() => _processingItems[updated.id] = updated);
        },
      );
      setState(() {
        _processing = false;
        _processingItems.remove(item.id);
      });

      if (result.status == 'done') {
        succeeded++;
      } else {
        failed++;
      }
    }

    if (!mounted) return;

    setState(() => _processingItems.clear());

    if (succeeded > 0 || failed > 0 || skipped > 0) {
      final parts = <String>[];
      if (succeeded > 0) parts.add('$succeeded added');
      if (failed > 0) parts.add('$failed failed');
      if (skipped > 0) parts.add('$skipped skipped');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(parts.join(', ') + '.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final queue = context.watch<WardrobeRepository>().ingestionQueue;
    final activeItems = {..._processingItems, ...{for (final i in queue) i.id: i}};
    final selectedCount = _boxes?.where((b) => b.isSelected).length ?? 0;
    final totalCount = _boxes?.length ?? 0;

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const SectionHeader(
          title: 'Digitize',
          subtitle: 'Photograph clothing to add to your wardrobe.',
        ),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _detecting || _processing
                    ? null
                    : () => _pickImages(multiple: false),
                icon: const Icon(Icons.photo_outlined, size: 18),
                label: const Text('Photo'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _detecting || _processing
                    ? null
                    : () => _pickImages(multiple: true),
                icon: const Icon(Icons.photo_library_outlined, size: 18),
                label: const Text('Bulk'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _detecting || _processing ? null : _capturePhoto,
                icon: const Icon(Icons.camera_alt_outlined, size: 18),
                label: const Text('Camera'),
              ),
            ),
          ],
        ),
        if (_detecting) ...[
          const SizedBox(height: 32),
          const Center(child: CircularProgressIndicator(strokeWidth: 2)),
          const SizedBox(height: 12),
          const Center(
            child: Text(
              'Detecting garments…',
              style: TextStyle(color: AppColors.gray400, fontSize: 13),
            ),
          ),
        ],
        if (_boxes != null && _boxes!.isNotEmpty) ...[
          const SizedBox(height: 28),
          Row(
            children: [
              const Text(
                'SELECT GARMENTS',
                style: TextStyle(
                  fontSize: 11,
                  letterSpacing: 2,
                  color: AppColors.gray400,
                ),
              ),
              const Spacer(),
              TextButton(
                onPressed: () => _toggleAll(true),
                child: const Text('All', style: TextStyle(fontSize: 12)),
              ),
              TextButton(
                onPressed: () => _toggleAll(false),
                child: const Text('None', style: TextStyle(fontSize: 12)),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ..._boxes!.asMap().entries.map((entry) {
            final box = entry.value;
            final key = '${box.label}_${entry.key}';
            return CheckboxListTile(
              key: ValueKey(key),
              value: box.isSelected,
              onChanged: (v) => setState(() => box.isSelected = v ?? false),
              secondary: box.cropBase64.isNotEmpty
                  ? SizedBox(
                      width: 48,
                      height: 48,
                      child: Image.memory(
                        base64Decode(box.cropBase64),
                        fit: BoxFit.cover,
                        gaplessPlayback: true,
                      ),
                    )
                  : null,
              title: Text(box.label),
              subtitle: Text(
                '${(box.score * 100).round()}% confidence',
                style: const TextStyle(color: AppColors.gray400, fontSize: 12),
              ),
              controlAffinity: ListTileControlAffinity.leading,
              contentPadding: EdgeInsets.zero,
            );
          }),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: selectedCount > 0 && !_processing ? _processSelected : null,
              child: Text(
                selectedCount == totalCount
                    ? 'Process All ($selectedCount)'
                    : 'Process Selected ($selectedCount)',
              ),
            ),
          ),
        ],
        if (_processing || activeItems.isNotEmpty) ...[
          const SizedBox(height: 32),
          const MinimalDivider(),
          const SizedBox(height: 20),
          Text(
            _processing ? 'PROCESSING' : 'QUEUE',
            style: const TextStyle(
              fontSize: 11,
              letterSpacing: 2,
              color: AppColors.gray400,
            ),
          ),
          const SizedBox(height: 12),
          ...activeItems.values.map((item) {
            return Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          item.label ?? 'Garment',
                          style: const TextStyle(fontSize: 13),
                        ),
                      ),
                      Text(
                        item.status == 'failed'
                            ? 'Failed'
                            : item.status == 'review'
                                ? 'Review'
                                : item.stepLabel,
                        style: TextStyle(
                          color: item.status == 'failed'
                              ? AppColors.white
                              : AppColors.gray400,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  LinearProgressIndicator(
                    value: item.status == 'failed' ? null : item.progress,
                    minHeight: 2,
                    backgroundColor: AppColors.gray800,
                  ),
                ],
              ),
            );
          }),
        ],
      ],
    );
  }
}
