import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';
import 'outfit_detail_screen.dart';

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
    return GestureDetector(
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => OutfitDetailScreen(outfit: outfit),
          ),
        );
      },
      child: Card(
        margin: const EdgeInsets.only(bottom: 20),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            OutfitGarmentsPreview(garments: garments),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          outfit.name,
                          style: AppTypography.ui(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: AppColors.ink900,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          outfit.reason,
                          style: AppTypography.ui(
                            color: AppColors.ink600,
                            fontSize: 12,
                            height: 1.3,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppColors.greige,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.auto_awesome,
                          size: 12,
                          color: AppColors.clay500,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${(outfit.overallScore * 100).round()}%',
                          style: AppTypography.ui(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: AppColors.clay500,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

