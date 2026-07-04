import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import 'common.dart';
import 'garment_attr_field.dart';

Future<void> showGarmentDetailSheet(BuildContext context, Garment garment) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    backgroundColor: AppColors.surface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
    ),
    builder: (ctx) => _GarmentDetailSheet(garment: garment),
  );
}

class _GarmentDetailSheet extends StatefulWidget {
  const _GarmentDetailSheet({required this.garment});

  final Garment garment;

  @override
  State<_GarmentDetailSheet> createState() => _GarmentDetailSheetState();
}

class _GarmentDetailSheetState extends State<_GarmentDetailSheet> {
  late final TextEditingController _subcategoryCtrl;
  late final TextEditingController _categoryCtrl;
  late final TextEditingController _colorCtrl;
  late final TextEditingController _materialCtrl;
  late final TextEditingController _patternCtrl;
  late final TextEditingController _fitCtrl;
  bool _saving = false;
  bool _deleting = false;

  @override
  void initState() {
    super.initState();
    final g = widget.garment;
    _subcategoryCtrl = TextEditingController(text: g.subcategory);
    _categoryCtrl = TextEditingController(text: g.category);
    _colorCtrl = TextEditingController(text: g.colorName);
    _materialCtrl = TextEditingController(text: g.material);
    _patternCtrl = TextEditingController(text: g.pattern);
    _fitCtrl = TextEditingController(text: g.fit);
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

  Garment _buildUpdatedGarment() {
    return widget.garment.copyWith(
      subcategory: _subcategoryCtrl.text.trim(),
      category: _categoryCtrl.text.trim(),
      colorName: _colorCtrl.text.trim(),
      material: _materialCtrl.text.trim(),
      pattern: _patternCtrl.text.trim(),
      fit: _fitCtrl.text.trim(),
    );
  }

  Future<void> _save() async {
    if (_saving || _deleting) return;
    setState(() => _saving = true);
    final repo = context.read<WardrobeRepository>();
    await repo.updateGarment(_buildUpdatedGarment());
    if (!mounted) return;
    setState(() => _saving = false);
    Navigator.pop(context);
    if (repo.lastError != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            repo.lastError!,
            style: AppTypography.ui(color: AppColors.surface),
          ),
        ),
      );
    }
  }

  Future<void> _confirmDelete() async {
    if (_saving || _deleting) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(
          'Remove from closet?',
          style: AppTypography.ui(
            fontSize: 17,
            fontWeight: FontWeight.w600,
            color: AppColors.ink900,
          ),
        ),
        content: Text(
          'This permanently deletes the garment, its images, and all saved references.',
          style: AppTypography.ui(fontSize: 14, color: AppColors.ink600),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(
              'Delete',
              style: AppTypography.ui(color: AppColors.clay500),
            ),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    setState(() => _deleting = true);
    final repo = context.read<WardrobeRepository>();
    await repo.deleteGarment(widget.garment.id);
    if (!mounted) return;
    Navigator.pop(context);
    if (repo.lastError != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            repo.lastError!,
            style: AppTypography.ui(color: AppColors.surface),
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottomInset),
      child: DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.88,
        minChildSize: 0.45,
        maxChildSize: 0.95,
        builder: (ctx, scrollController) {
          return SingleChildScrollView(
            controller: scrollController,
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.border,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 20),
                AspectRatio(
                  aspectRatio: 1,
                  child: Container(
                    decoration: BoxDecoration(
                      color: AppColors.greige,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppColors.border),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: GarmentImage(
                      path: widget.garment.displayImage,
                      fit: BoxFit.contain,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'Worn ${widget.garment.wearCount}x',
                  style: AppTypography.ui(fontSize: 13, color: AppColors.ink400),
                ),
                const SizedBox(height: 16),
                GarmentAttrField(
                  label: 'Name',
                  controller: _subcategoryCtrl,
                  hint: 'e.g. Oxford shirt',
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
                const SizedBox(height: 24),
                FilledButton(
                  onPressed: _saving || _deleting ? null : _save,
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.clay500,
                    foregroundColor: AppColors.surface,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: _saving
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AppColors.surface,
                          ),
                        )
                      : const Text('Save changes'),
                ),
                const SizedBox(height: 10),
                OutlinedButton(
                  onPressed: _saving || _deleting ? null : _confirmDelete,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.clay500,
                    side: const BorderSide(color: AppColors.border),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: _deleting
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Remove from closet'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
