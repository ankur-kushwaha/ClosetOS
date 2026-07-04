import 'dart:convert';

import 'package:flutter/material.dart';

import '../models/models.dart';
import '../theme/app_theme.dart';

/// Returns `true` to use normalized image, `false` for original crop, `null` if skipped.
Future<bool?> showNormalizationReview(
  BuildContext context,
  IngestionItem item,
) {
  return showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    backgroundColor: AppColors.black,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(2)),
    ),
    builder: (ctx) => _NormalizationReviewSheet(item: item),
  );
}

class _NormalizationReviewSheet extends StatefulWidget {
  const _NormalizationReviewSheet({required this.item});

  final IngestionItem item;

  @override
  State<_NormalizationReviewSheet> createState() =>
      _NormalizationReviewSheetState();
}

class _NormalizationReviewSheetState extends State<_NormalizationReviewSheet> {
  bool _showNormalized = true;

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final crop = item.cropBase64 ?? '';
    final normalized = item.normalizedBase64 ?? crop;
    final hasNormalized = normalized != crop && normalized.isNotEmpty;
    final attrs = item.attributes;
    final displayB64 = _showNormalized && hasNormalized ? normalized : crop;

    return Padding(
      padding: EdgeInsets.only(
        left: 24,
        right: 24,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  item.label ?? 'Garment',
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w300,
                  ),
                ),
              ),
              IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close, size: 20),
              ),
            ],
          ),
          const SizedBox(height: 4),
          const Text(
            'Review before adding to wardrobe.',
            style: TextStyle(color: AppColors.gray400, fontSize: 13),
          ),
          if (hasNormalized) ...[
            const SizedBox(height: 16),
            Row(
              children: [
                _toggleChip('Original', !_showNormalized, () {
                  setState(() => _showNormalized = false);
                }),
                const SizedBox(width: 8),
                _toggleChip('Normalized', _showNormalized, () {
                  setState(() => _showNormalized = true);
                }),
              ],
            ),
          ],
          const SizedBox(height: 16),
          AspectRatio(
            aspectRatio: 1,
            child: Container(
              decoration: BoxDecoration(
                border: Border.all(color: AppColors.gray800),
                color: AppColors.gray800,
              ),
              child: displayB64.isNotEmpty
                  ? Image.memory(
                      base64Decode(displayB64),
                      fit: BoxFit.contain,
                      gaplessPlayback: true,
                    )
                  : const Center(
                      child: Icon(Icons.image_not_supported_outlined,
                          color: AppColors.gray600),
                    ),
            ),
          ),
          if (attrs != null) ...[
            const SizedBox(height: 16),
            _metaRow('Category', '${attrs.category} · ${attrs.subcategory}'),
            _metaRow('Color', attrs.colorName),
            _metaRow('Material', '${attrs.material} · ${attrs.pattern}'),
            _metaRow('Fit', attrs.fit),
          ],
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: crop.isEmpty
                      ? null
                      : () => Navigator.pop(context, false),
                  child: const Text('Use Original'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton(
                  onPressed: () => Navigator.pop(
                    context,
                    hasNormalized ? true : false,
                  ),
                  child: Text(hasNormalized ? 'Accept' : 'Add to Wardrobe'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _toggleChip(String label, bool selected, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: selected ? AppColors.white : Colors.transparent,
          border: Border.all(
            color: selected ? AppColors.white : AppColors.gray600,
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w600,
            color: selected ? AppColors.black : AppColors.white,
          ),
        ),
      ),
    );
  }

  Widget _metaRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 72,
            child: Text(
              label.toUpperCase(),
              style: const TextStyle(
                fontSize: 10,
                letterSpacing: 1,
                color: AppColors.gray400,
              ),
            ),
          ),
          Expanded(
            child: Text(value, style: const TextStyle(fontSize: 13)),
          ),
        ],
      ),
    );
  }
}
