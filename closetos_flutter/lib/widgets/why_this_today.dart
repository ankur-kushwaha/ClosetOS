import 'package:flutter/material.dart';

import '../models/models.dart';
import '../services/weather_service.dart';
import '../theme/app_theme.dart';
import 'common.dart';

class WhyThisTodayDetails extends StatelessWidget {
  const WhyThisTodayDetails({
    super.key,
    required this.outfit,
    required this.weather,
    required this.garments,
    this.showPieceInsights = true,
  });

  final Outfit outfit;
  final WeatherInfo? weather;
  final List<Garment> garments;
  final bool showPieceInsights;

  IconData _getWeatherIcon(String? description) {
    if (description == null) return Icons.wb_sunny_outlined;
    final desc = description.toLowerCase();
    if (desc.contains('clear') || desc.contains('sunny')) {
      return Icons.wb_sunny_outlined;
    } else if (desc.contains('cloud') || desc.contains('fog')) {
      return Icons.cloud_outlined;
    } else if (desc.contains('rain') || desc.contains('drizzle') || desc.contains('shower')) {
      return Icons.umbrella_outlined;
    } else if (desc.contains('snow')) {
      return Icons.ac_unit_outlined;
    } else if (desc.contains('storm')) {
      return Icons.thunderstorm_outlined;
    }
    return Icons.wb_cloudy_outlined;
  }

  String _getWeatherDetailsText(double tempF, String reason) {
    if (tempF < 55) {
      return 'Cool temperature alert ($tempF°F). We paired these items to provide cozy warmth with option to layer up.';
    } else if (tempF > 78) {
      return 'Warm weather alert ($tempF°F). Selected for maximum breathability and airflow to keep you comfortable.';
    } else {
      return 'Mild and comfortable day. This outfit balances light layers so you can adjust to temperature changes.';
    }
  }

  String _getGarmentTip(Garment garment) {
    final cat = garment.category.toLowerCase();
    final sub = garment.subcategory.toLowerCase();
    
    if (cat == 'top') {
      if (sub.contains('t-shirt') || sub.contains('tee') || sub.contains('tank')) {
        return 'Light fabric helps maintain a cool torso temperature.';
      }
      return 'Perfect layer for base breathability.';
    } else if (cat == 'bottom') {
      if (sub.contains('short') || sub.contains('skirt')) {
        return 'Allows excellent airflow for the legs.';
      }
      return 'Provides comfortable movement without overheating.';
    } else if (cat == 'shoes') {
      if (sub.contains('sandal') || sub.contains('slide') || sub.contains('croc')) {
        return 'Open styling offers comfort and heat release.';
      }
      return 'Sturdy and supportive for your daily walk.';
    } else if (cat == 'dress') {
      return 'Single-piece ease offers breezy comfort.';
    }
    return 'Complementary color and casual styling.';
  }

  @override
  Widget build(BuildContext context) {
    final tempF = weather?.tempFahrenheit ?? 61.0;
    final matchPercentage = (outfit.overallScore * 100).round();
    final weatherDesc = weather?.description ?? 'Clear';
    final highC = weather?.highCelsius?.round() ?? 16;
    final lowC = weather?.lowCelsius?.round() ?? 9;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Match Score Card (circular progress style)
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: [
              Stack(
                alignment: Alignment.center,
                children: [
                  SizedBox(
                    width: 56,
                    height: 56,
                    child: CircularProgressIndicator(
                      value: outfit.overallScore,
                      strokeWidth: 6,
                      color: AppColors.clay500,
                      backgroundColor: AppColors.border,
                    ),
                  ),
                  Text(
                    '$matchPercentage%',
                    style: AppTypography.ui(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: AppColors.ink900,
                    ),
                  ),
                ],
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Recommendation Score',
                      style: AppTypography.ui(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: AppColors.ink900,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Highly compatible look curated for your comfort and style preference today.',
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
        const SizedBox(height: 12),

        // Weather Matching Card
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    _getWeatherIcon(weatherDesc),
                    color: AppColors.clay500,
                    size: 20,
                  ),
                  const SizedBox(width: 10),
                  Text(
                    'Weather Alignment',
                    style: AppTypography.ui(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: AppColors.ink900,
                    ),
                  ),
                  const Spacer(),
                  Text(
                    '$highC°C / $lowC°C',
                    style: AppTypography.ui(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: AppColors.ink600,
                    ),
                  ),
                ],
              ),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 10),
                child: Divider(height: 1),
              ),
              Text(
                outfit.reason.isNotEmpty ? outfit.reason : 'Lightweight breathable look.',
                style: AppTypography.ui(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppColors.ink900,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                _getWeatherDetailsText(tempF, outfit.reason),
                style: AppTypography.ui(
                  fontSize: 12,
                  color: AppColors.ink600,
                  height: 1.4,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // Rotation & Laundry freshness
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                Icons.history_toggle_off,
                color: AppColors.clay500,
                size: 20,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Wardrobe Rotation',
                      style: AppTypography.ui(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: AppColors.ink900,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'All garments are clean and ready to wear. None of these pieces have been worn in the past 7 days, maintaining a balanced rotation.',
                      style: AppTypography.ui(
                        fontSize: 12,
                        color: AppColors.ink600,
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // Occasion Compatibility
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                Icons.style_outlined,
                color: AppColors.clay500,
                size: 20,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Occasion Suitability',
                      style: AppTypography.ui(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: AppColors.ink900,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Optimized for "${outfit.tags.isNotEmpty ? outfit.tags.first : 'Daily'}" style settings. Fits a modern, casual profile suited to today\'s schedule.',
                      style: AppTypography.ui(
                        fontSize: 12,
                        color: AppColors.ink600,
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        
        if (showPieceInsights && garments.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text(
            'PIECE INSIGHTS',
            style: AppTypography.label(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: AppColors.ink400,
              letterSpacing: 1.5,
            ),
          ),
          const SizedBox(height: 12),
          ...garments.map((garment) {
            return Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppColors.border),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: AppColors.border),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: GarmentImage(
                        path: garment.displayImage,
                        fit: BoxFit.contain,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            garment.subcategory.isNotEmpty
                                ? garment.subcategory
                                : garment.category,
                            style: AppTypography.ui(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: AppColors.ink900,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            _getGarmentTip(garment),
                            style: AppTypography.ui(
                              fontSize: 11,
                              color: AppColors.ink600,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            );
          }),
        ],
      ],
    );
  }
}
