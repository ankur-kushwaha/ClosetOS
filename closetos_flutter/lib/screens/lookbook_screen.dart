import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';

class LookbookScreen extends StatelessWidget {
  const LookbookScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    final outfits = repo.generateRecommendations(72, occasion: 'Lookbook');

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const SectionHeader(
          title: 'Lookbook',
          subtitle: 'Curated combinations from your wardrobe.',
        ),
        if (outfits.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 48),
            child: Center(
              child: Text(
                'Add more garments to build looks.',
                style: TextStyle(color: AppColors.gray400),
              ),
            ),
          )
        else
          ...outfits.map((outfit) => _OutfitRow(
                outfit: outfit,
                garments: repo.garmentsForOutfit(outfit),
              )),
      ],
    );
  }
}

class _OutfitRow extends StatelessWidget {
  const _OutfitRow({required this.outfit, required this.garments});

  final Outfit outfit;
  final List<Garment> garments;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        border: Border.all(color: AppColors.gray800),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            height: 100,
            child: Row(
              children: garments
                  .map((g) => Expanded(
                        child: GarmentImage(path: g.displayImage),
                      ))
                  .toList(),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        outfit.name,
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        outfit.reason,
                        style: const TextStyle(
                          color: AppColors.gray400,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                ),
                Text(
                  '${(outfit.overallScore * 100).round()}%',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
