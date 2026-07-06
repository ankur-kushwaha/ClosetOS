import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import 'common.dart';

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
  bool _isEditing = false;
  bool _showNormalized = true;
  bool _saving = false;
  bool _deleting = false;
  bool _normalizing = false;

  late String _selectedMaterial;
  late String _selectedPattern;
  late String _selectedFit;

  static const _materialOptions = [
    'Wool',
    'Cotton',
    'Linen',
    'Silk',
    'Denim',
    'Polyester',
    'Leather',
    'Knit',
    'Nylon',
  ];

  static const _patternOptions = [
    'Solid',
    'Striped',
    'Checked',
    'Floral',
    'Plaid',
    'Graphic',
    'Patterned',
  ];

  static const _fitOptions = [
    'Regular',
    'Slim',
    'Relaxed',
    'Oversized',
    'Loose',
  ];

  @override
  void initState() {
    super.initState();
    final g = widget.garment;
    _selectedMaterial = g.material;
    _selectedPattern = g.pattern;
    _selectedFit = g.fit;
    _showNormalized = g.straightenedImagePath.isNotEmpty && g.straightenedImagePath != g.imagePath;
  }

  Garment get _latestGarment {
    final repo = context.read<WardrobeRepository>();
    return repo.garments.firstWhere(
      (item) => item.id == widget.garment.id,
      orElse: () => widget.garment,
    );
  }

  Garment _buildUpdatedGarment() {
    return _latestGarment.copyWith(
      material: _selectedMaterial.trim(),
      pattern: _selectedPattern.trim(),
      fit: _selectedFit.trim(),
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

  bool get _isNormalized {
    final g = _latestGarment;
    return g.straightenedImagePath.isNotEmpty && g.straightenedImagePath != g.imagePath;
  }

  Future<void> _normalize() async {
    if (_normalizing) return;
    setState(() => _normalizing = true);

    final repo = context.read<WardrobeRepository>();
    debugPrint("DEBUG _normalize start: id=${_latestGarment.id}, subcategory=${_latestGarment.subcategory}");
    final newPath = await repo.normalizeGarment(_latestGarment);
    debugPrint("DEBUG _normalize end: newPath=$newPath, error=${repo.lastError}");

    if (!mounted) return;
    setState(() => _normalizing = false);

    if (newPath == null && repo.lastError != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            repo.lastError!,
            style: AppTypography.ui(color: AppColors.surface),
          ),
        ),
      );
    } else {
      setState(() {
        _showNormalized = true;
      });
    }
  }

  Future<void> _discard() async {
    final repo = context.read<WardrobeRepository>();
    await repo.discardNormalized(_latestGarment);
    if (!mounted) return;
    setState(() {
      _showNormalized = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    final repo = context.watch<WardrobeRepository>();
    final g = repo.garments.firstWhere(
      (item) => item.id == widget.garment.id,
      orElse: () => widget.garment,
    );
    debugPrint("DEBUG build: id=${g.id}, imagePath=${g.imagePath}, straightenedImagePath=${g.straightenedImagePath}, isNormalized=${g.straightenedImagePath.isNotEmpty && g.straightenedImagePath != g.imagePath}");



    final dateStr = g.dateAdded != null
        ? 'Added ${DateFormat('MMM yyyy').format(g.dateAdded!)}'
        : 'Added Nov 2024';

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
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    CircleIconButton(
                      icon: _isEditing ? Icons.visibility_outlined : Icons.edit_outlined,
                      onTap: () => setState(() => _isEditing = !_isEditing),
                    ),
                    const SizedBox(width: 12),
                    CircleIconButton(
                      icon: Icons.close,
                      onTap: () => Navigator.pop(context),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                if (!_isEditing) ...[
                  Row(
                    mainAxisAlignment: MainAxisAlignment.start,
                    children: [
                      SegmentedToggle(
                        showNormalized: _showNormalized,
                        onChanged: (val) {
                          setState(() => _showNormalized = val);
                        },
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                ],
                AspectRatio(
                  aspectRatio: 1,
                  child: Container(
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppColors.border),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: Stack(
                      children: [
                        Positioned.fill(
                          child: Opacity(
                            opacity: (_showNormalized && !_isNormalized) ? 0.4 : 1.0,
                            child: GarmentImage(
                              path: (_isNormalized && _showNormalized)
                                  ? g.straightenedImagePath
                                  : g.imagePath,
                              fit: BoxFit.cover,
                            ),
                          ),
                        ),
                        if (_showNormalized && !_isNormalized && !_normalizing)
                          Center(
                            child: ElevatedButton.icon(
                              onPressed: _normalize,
                              icon: const Icon(Icons.auto_awesome, size: 16, color: AppColors.surface),
                              label: Text(
                                'Normalize',
                                style: AppTypography.ui(
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.surface,
                                ),
                              ),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppColors.clay500,
                                foregroundColor: AppColors.surface,
                                shape: const StadiumBorder(),
                                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                                elevation: 4,
                                shadowColor: Colors.black.withOpacity(0.15),
                              ),
                            ),
                          ),
                        if (_isNormalized && _showNormalized)
                          Positioned(
                            top: 12,
                            right: 12,
                            child: OutlinedButton(
                              onPressed: _discard,
                              style: OutlinedButton.styleFrom(
                                backgroundColor: Colors.white.withOpacity(0.9),
                                foregroundColor: AppColors.error,
                                side: BorderSide(color: AppColors.border.withOpacity(0.8)),
                                shape: const StadiumBorder(),
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                                elevation: 2,
                                shadowColor: Colors.black.withOpacity(0.1),
                              ),
                              child: Text(
                                'Discard',
                                style: AppTypography.ui(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.error,
                                ),
                              ),
                            ),
                          ),
                        if (_normalizing)
                          Container(
                            color: Colors.white.withOpacity(0.8),
                            child: Center(
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const CircularProgressIndicator(color: AppColors.clay500),
                                  const SizedBox(height: 16),
                                  Text(
                                    'Generating normalized output...',
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
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                if (!_isEditing) ...[
                  if (g.category.isNotEmpty) ...[
                    Text(
                      g.category.toUpperCase(),
                      style: AppTypography.ui(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: AppColors.ink600,
                        letterSpacing: 0.5,
                      ),
                    ),
                    const SizedBox(height: 4),
                  ],
                  Text(
                    g.subcategory.isNotEmpty
                        ? g.subcategory[0].toUpperCase() + g.subcategory.substring(1)
                        : 'Garment',
                    style: AppTypography.display(fontSize: 24, color: AppColors.ink900),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    dateStr,
                    style: AppTypography.ui(fontSize: 13, color: AppColors.ink400),
                  ),
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      if (g.subcategory.isNotEmpty) _buildAttributeChip(g.subcategory, highlight: true),
                      if (g.colorName.isNotEmpty) _buildAttributeChip(g.colorName),
                      if (g.material.isNotEmpty) _buildAttributeChip(g.material),
                      if (g.fit.isNotEmpty) _buildAttributeChip(g.fit),
                      if (g.category.isNotEmpty) _buildAttributeChip(g.category),
                      if (g.pattern.isNotEmpty) _buildAttributeChip(g.pattern),
                    ],
                  ),
                  const SizedBox(height: 24),
                  const Divider(color: AppColors.border, height: 1),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      const Icon(Icons.wb_sunny_outlined, size: 16, color: AppColors.ink400),
                      const SizedBox(width: 8),
                      Text(
                        g.seasons.isNotEmpty
                            ? 'Best in ${g.seasons.join(" · ")}'
                            : 'Best in Fall · Winter',
                        style: AppTypography.ui(fontSize: 14, color: AppColors.ink600),
                      ),
                    ],
                  ),
                ] else ...[
                  _buildPillSelector(
                    label: 'Material',
                    options: _materialOptions,
                    selectedValue: _selectedMaterial,
                    onSelected: (val) => setState(() => _selectedMaterial = val),
                  ),
                  const SizedBox(height: 16),
                  _buildPillSelector(
                    label: 'Pattern',
                    options: _patternOptions,
                    selectedValue: _selectedPattern,
                    onSelected: (val) => setState(() => _selectedPattern = val),
                  ),
                  const SizedBox(height: 16),
                  _buildPillSelector(
                    label: 'Fit',
                    options: _fitOptions,
                    selectedValue: _selectedFit,
                    onSelected: (val) => setState(() => _selectedFit = val),
                  ),
                  const SizedBox(height: 32),
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
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildAttributeChip(String label, {bool highlight = false}) {
    if (label.isEmpty) return const SizedBox.shrink();
    final formatted = label[0].toUpperCase() + label.substring(1);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: highlight ? AppColors.clay100 : AppColors.greige.withOpacity(0.4),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        formatted,
        style: AppTypography.ui(
          fontSize: 13,
          fontWeight: FontWeight.w500,
          color: highlight ? AppColors.clay700 : AppColors.ink900,
        ),
      ),
    );
  }

  Widget _buildPillSelector({
    required String label,
    required List<String> options,
    required String selectedValue,
    required ValueChanged<String> onSelected,
  }) {
    final choices = List<String>.from(options);
    if (selectedValue.isNotEmpty &&
        !choices.any((c) => c.toLowerCase() == selectedValue.toLowerCase())) {
      choices.insert(0, selectedValue);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label.toUpperCase(),
          style: AppTypography.label(),
        ),
        const SizedBox(height: 8),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: choices.map((opt) {
              final isSelected = opt.toLowerCase() == selectedValue.toLowerCase();
              return Padding(
                padding: const EdgeInsets.only(right: 8.0),
                child: ChoiceChip(
                  label: Text(
                    opt,
                    style: AppTypography.ui(
                      fontSize: 13,
                      color: isSelected ? AppColors.surface : AppColors.ink900,
                      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                    ),
                  ),
                  selected: isSelected,
                  onSelected: (selected) {
                    if (selected) {
                      onSelected(opt);
                    }
                  },
                  selectedColor: AppColors.clay500,
                  backgroundColor: AppColors.greige.withOpacity(0.3),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(20),
                    side: BorderSide(
                      color: isSelected ? AppColors.clay500 : AppColors.border,
                    ),
                  ),
                  showCheckmark: false,
                ),
              );
            }).toList(),
          ),
        ),
      ],
    );
  }
}

class CircleIconButton extends StatelessWidget {
  const CircleIconButton({
    super.key,
    required this.icon,
    required this.onTap,
    this.color = AppColors.ink900,
  });

  final IconData icon;
  final VoidCallback onTap;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 36,
      height: 36,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: AppColors.border),
        color: AppColors.surface,
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          customBorder: const CircleBorder(),
          child: Center(
            child: Icon(
              icon,
              size: 18,
              color: color,
            ),
          ),
        ),
      ),
    );
  }
}

class SegmentedToggle extends StatelessWidget {
  const SegmentedToggle({
    super.key,
    required this.showNormalized,
    required this.onChanged,
  });

  final bool showNormalized;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(2),
      decoration: BoxDecoration(
        color: AppColors.greige.withOpacity(0.3),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildItem('Original', !showNormalized, () => onChanged(false)),
          _buildItem('Normalized', showNormalized, () => onChanged(true)),
        ],
      ),
    );
  }

  Widget _buildItem(String text, bool active, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.surface : Colors.transparent,
          borderRadius: BorderRadius.circular(18),
          boxShadow: active
              ? [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.05),
                    blurRadius: 2,
                    offset: const Offset(0, 1),
                  )
                ]
              : null,
        ),
        child: Text(
          text,
          style: AppTypography.ui(
            fontSize: 13,
            fontWeight: active ? FontWeight.w600 : FontWeight.w400,
            color: active ? AppColors.ink900 : AppColors.ink600,
          ),
        ),
      ),
    );
  }
}
