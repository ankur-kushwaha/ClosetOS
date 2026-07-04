import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';
import '../widgets/stripe_background.dart';

String _garmentDisplayName(Garment g) {
  if (g.subcategory.isNotEmpty) return g.subcategory;
  return g.category;
}

class _WardrobeFilter {
  const _WardrobeFilter(
    this.label, {
    this.category,
    this.knitOnly = false,
    this.sortByColor = false,
  });

  final String label;
  final String? category;
  final bool knitOnly;
  final bool sortByColor;
}

class WardrobeScreen extends StatefulWidget {
  const WardrobeScreen({super.key, this.onAddGarment});

  final VoidCallback? onAddGarment;

  @override
  State<WardrobeScreen> createState() => _WardrobeScreenState();
}

class _WardrobeScreenState extends State<WardrobeScreen> {
  static const _filters = [
    _WardrobeFilter('All'),
    _WardrobeFilter('Outerwear', category: 'Outerwear'),
    _WardrobeFilter('Knitwear', category: 'Top', knitOnly: true),
    _WardrobeFilter('Color', sortByColor: true),
  ];

  int _filterIndex = 0;
  String _query = '';
  bool _searchOpen = false;
  final _searchFocus = FocusNode();
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchFocus.dispose();
    _searchController.dispose();
    super.dispose();
  }

  void _toggleSearch() {
    setState(() => _searchOpen = !_searchOpen);
    if (_searchOpen) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _searchFocus.requestFocus();
      });
    } else {
      _searchController.clear();
      _query = '';
      _searchFocus.unfocus();
    }
  }

  List<Garment> _filterItems(List<Garment> garments) {
    final filter = _filters[_filterIndex];
    var items = garments.where((g) {
      if (_query.isEmpty) return true;
      final q = _query.toLowerCase();
      return g.subcategory.toLowerCase().contains(q) ||
          g.category.toLowerCase().contains(q) ||
          g.colorName.toLowerCase().contains(q) ||
          g.brand.toLowerCase().contains(q) ||
          g.material.toLowerCase().contains(q);
    }).toList();

    if (filter.category != null) {
      items = items.where((g) => g.category == filter.category).toList();
      if (filter.knitOnly) {
        final knit = items
            .where(
              (g) =>
                  g.subcategory.toLowerCase().contains('knit') ||
                  g.material.toLowerCase().contains('knit') ||
                  g.subcategory.toLowerCase().contains('tank') ||
                  g.subcategory.toLowerCase().contains('sweater'),
            )
            .toList();
        if (knit.isNotEmpty) items = knit;
      }
    }

    if (filter.sortByColor) {
      items.sort((a, b) => a.colorName.compareTo(b.colorName));
    }

    return items;
  }

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    final items = _filterItems(repo.garments);

    return ColoredBox(
      color: AppColors.canvas,
      child: Stack(
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Expanded(
                      child: Text(
                        'Your closet',
                        style: AppTypography.display(
                          fontSize: 30,
                          color: AppColors.ink900,
                          fontWeight: FontWeight.w500,
                          height: 1.1,
                        ),
                      ),
                    ),
                    _SearchCircleButton(
                      active: _searchOpen,
                      onPressed: _toggleSearch,
                    ),
                  ],
                ),
              ),
              if (_searchOpen) ...[
                const SizedBox(height: 12),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: TextField(
                    controller: _searchController,
                    focusNode: _searchFocus,
                    onChanged: (v) => setState(() => _query = v),
                    style: AppTypography.ui(fontSize: 14),
                    decoration: InputDecoration(
                      hintText: 'Search your closet',
                      hintStyle: AppTypography.ui(
                        fontSize: 14,
                        color: AppColors.ink400,
                      ),
                      prefixIcon: const Icon(
                        Icons.search,
                        size: 20,
                        color: AppColors.ink600,
                      ),
                      suffixIcon: _query.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.close, size: 18),
                              color: AppColors.ink400,
                              onPressed: () {
                                _searchController.clear();
                                setState(() => _query = '');
                              },
                            )
                          : null,
                      filled: true,
                      fillColor: AppColors.surface,
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 12,
                      ),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(24),
                        borderSide: const BorderSide(color: AppColors.border),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(24),
                        borderSide: const BorderSide(color: AppColors.border),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(24),
                        borderSide: const BorderSide(
                          color: AppColors.clay500,
                          width: 1.5,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 16),
              SizedBox(
                height: 36,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  itemCount: _filters.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (_, i) {
                    final filter = _filters[i];
                    final selected = i == _filterIndex;
                    return _FilterChip(
                      label: filter.label,
                      selected: selected,
                      onTap: () => setState(() => _filterIndex = i),
                    );
                  },
                ),
              ),
              const SizedBox(height: 16),
              Expanded(
                child: items.isEmpty
                    ? _EmptyCloset(hasGarments: repo.garments.isNotEmpty)
                    : GridView.builder(
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 88),
                        gridDelegate:
                            const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          crossAxisSpacing: 12,
                          mainAxisSpacing: 12,
                          childAspectRatio: 0.72,
                        ),
                        itemCount: items.length,
                        itemBuilder: (_, i) => _GarmentCard(
                          garment: items[i],
                          onTap: () => _showDetail(context, items[i]),
                        ),
                      ),
              ),
            ],
          ),
          Positioned(
            right: 20,
            bottom: 16,
            child: _AddGarmentFab(onPressed: widget.onAddGarment),
          ),
        ],
      ),
    );
  }

  void _showDetail(BuildContext context, Garment g) {
    final repo = context.read<WardrobeRepository>();
    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 12, 24, 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
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
                  path: g.displayImage,
                  fit: BoxFit.contain,
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              _garmentDisplayName(g),
              style: AppTypography.display(
                fontSize: 22,
                color: AppColors.ink900,
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '${g.colorName} · ${g.material} · ${g.fit}',
              style: AppTypography.ui(fontSize: 13, color: AppColors.ink600),
            ),
            const SizedBox(height: 6),
            Text(
              'Worn ${g.wearCount}x',
              style: AppTypography.ui(fontSize: 13, color: AppColors.ink400),
            ),
            const SizedBox(height: 20),
            OutlinedButton(
              onPressed: () {
                repo.deleteGarment(g.id);
                Navigator.pop(ctx);
              },
              child: const Text('Remove from closet'),
            ),
          ],
        ),
      ),
    );
  }
}

class _SearchCircleButton extends StatelessWidget {
  const _SearchCircleButton({
    required this.active,
    required this.onPressed,
  });

  final bool active;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      shape: const CircleBorder(),
      child: InkWell(
        onTap: onPressed,
        customBorder: const CircleBorder(),
        child: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(
              color: active ? AppColors.clay500 : AppColors.border,
              width: active ? 1.5 : 1,
            ),
          ),
          child: Icon(
            active ? Icons.close : Icons.search,
            size: 20,
            color: active ? AppColors.clay500 : AppColors.ink600,
          ),
        ),
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? AppColors.clay100 : AppColors.greige,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: selected
                  ? AppColors.clay500.withValues(alpha: 0.3)
                  : Colors.transparent,
            ),
          ),
          child: Text(
            label,
            style: AppTypography.ui(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: selected ? AppColors.clay700 : AppColors.ink600,
            ),
          ),
        ),
      ),
    );
  }
}

class _GarmentCard extends StatelessWidget {
  const _GarmentCard({
    required this.garment,
    required this.onTap,
  });

  final Garment garment;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final name = _garmentDisplayName(garment);

    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Ink(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                flex: 7,
                child: ClipRRect(
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(15),
                  ),
                  child: garment.displayImage.isNotEmpty
                      ? GarmentImage(
                          path: garment.displayImage,
                          fit: BoxFit.contain,
                        )
                      : const StripeBackground(
                          baseColor: AppColors.surface,
                          opacity: 0.35,
                        ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: AppTypography.ui(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: AppColors.ink900,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      'Worn ${garment.wearCount}x',
                      style: AppTypography.ui(
                        fontSize: 12,
                        color: AppColors.ink600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AddGarmentFab extends StatelessWidget {
  const _AddGarmentFab({this.onPressed});

  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 4,
      shadowColor: AppColors.ink900.withValues(alpha: 0.12),
      color: AppColors.clay500,
      shape: const CircleBorder(),
      child: InkWell(
        onTap: onPressed,
        customBorder: const CircleBorder(),
        child: const SizedBox(
          width: 56,
          height: 56,
          child: Icon(Icons.add, color: AppColors.surface, size: 26),
        ),
      ),
    );
  }
}

class _EmptyCloset extends StatelessWidget {
  const _EmptyCloset({required this.hasGarments});

  final bool hasGarments;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Text(
          hasGarments
              ? 'Nothing matches this filter.'
              : 'Your closet is empty.\nDigitize your first piece.',
          textAlign: TextAlign.center,
          style: AppTypography.ui(
            fontSize: 14,
            color: AppColors.ink600,
            height: 1.5,
          ),
        ),
      ),
    );
  }
}
