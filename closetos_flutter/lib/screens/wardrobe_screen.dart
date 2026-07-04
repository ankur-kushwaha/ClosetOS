import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';

class WardrobeScreen extends StatefulWidget {
  const WardrobeScreen({super.key});

  @override
  State<WardrobeScreen> createState() => _WardrobeScreenState();
}

class _WardrobeScreenState extends State<WardrobeScreen> {
  String _category = 'All';
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    final items = repo.filterGarments(category: _category, query: _query);

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
          child: TextField(
            onChanged: (v) => setState(() => _query = v),
            decoration: const InputDecoration(
              hintText: 'Search wardrobe',
              prefixIcon: Icon(Icons.search, size: 20),
              isDense: true,
              contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 14),
            ),
          ),
        ),
        const SizedBox(height: 12),
        SizedBox(
          height: 36,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 20),
            itemCount: WardrobeRepository.categories.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (_, i) {
              final cat = WardrobeRepository.categories[i];
              final selected = cat == _category;
              return GestureDetector(
                onTap: () => setState(() => _category = cat),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: selected ? AppColors.white : Colors.transparent,
                    border: Border.all(
                      color: selected ? AppColors.white : AppColors.gray600,
                    ),
                  ),
                  child: Text(
                    cat,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: selected ? AppColors.black : AppColors.white,
                    ),
                  ),
                ),
              );
            },
          ),
        ),
        Expanded(
          child: items.isEmpty
              ? const Center(
                  child: Text(
                    'No items yet.\nDigitize your first garment.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.gray400),
                  ),
                )
              : GridView.builder(
                  padding: const EdgeInsets.all(20),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 0.72,
                  ),
                  itemCount: items.length,
                  itemBuilder: (_, i) {
                    final g = items[i];
                    return GarmentTile(
                      label: g.subcategory.isNotEmpty ? g.subcategory : g.category,
                      subtitle: '${g.colorName} · ${g.brand}',
                      imagePath: g.displayImage,
                      trailing: IconButton(
                        icon: Icon(
                          _laundryIcon(g.laundryStatus),
                          size: 16,
                          color: AppColors.gray400,
                        ),
                        onPressed: () => repo.toggleLaundry(g.id),
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                      ),
                      onTap: () => _showDetail(context, g.id),
                    );
                  },
                ),
        ),
      ],
    );
  }

  IconData _laundryIcon(LaundryStatus status) {
    return switch (status) {
      LaundryStatus.clean => Icons.check_circle_outline,
      LaundryStatus.dirty => Icons.water_drop_outlined,
      LaundryStatus.inLaundry => Icons.local_laundry_service_outlined,
    };
  }

  void _showDetail(BuildContext context, String id) {
    final repo = context.read<WardrobeRepository>();
    final g = repo.garments.firstWhere((e) => e.id == id);
    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.black,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(2)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AspectRatio(
              aspectRatio: 1,
              child: GarmentImage(path: g.displayImage),
            ),
            const SizedBox(height: 16),
            Text(
              g.subcategory.isNotEmpty ? g.subcategory : g.category,
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w300),
            ),
            Text(
              '${g.colorName} · ${g.material} · ${g.fit}',
              style: const TextStyle(color: AppColors.gray400, fontSize: 13),
            ),
            const SizedBox(height: 20),
            OutlinedButton(
              onPressed: () {
                repo.deleteGarment(g.id);
                Navigator.pop(ctx);
              },
              child: const Text('Remove from wardrobe'),
            ),
          ],
        ),
      ),
    );
  }
}
