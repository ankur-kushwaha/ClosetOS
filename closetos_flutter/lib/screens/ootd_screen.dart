import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../services/weather_service.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';

class OotdScreen extends StatefulWidget {
  const OotdScreen({super.key});

  @override
  State<OotdScreen> createState() => _OotdScreenState();
}

class _OotdScreenState extends State<OotdScreen> {
  WeatherInfo? _weather;
  int _selectedIndex = 0;
  String? _tryOnPath;
  bool _tryOnLoading = false;

  @override
  void initState() {
    super.initState();
    _loadWeather();
  }

  Future<void> _loadWeather() async {
    final w = await WeatherService.fetch();
    if (mounted) setState(() => _weather = w);
  }

  Future<void> _loadTryOn(Outfit outfit) async {
    setState(() {
      _tryOnLoading = true;
      _tryOnPath = null;
    });
    final path = await context.read<WardrobeRepository>().renderTryOn(outfit);
    if (mounted) {
      setState(() {
        _tryOnPath = path;
        _tryOnLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    final tempF = _weather?.tempFahrenheit ?? 73;
    final outfits = repo.generateRecommendations(tempF);
    final outfit = outfits.isNotEmpty ? outfits[_selectedIndex] : null;
    final garments = outfit != null ? repo.garmentsForOutfit(outfit) : <Garment>[];

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    DateFormat('EEEE').format(DateTime.now()).toUpperCase(),
                    style: const TextStyle(
                      fontSize: 11,
                      letterSpacing: 2,
                      color: AppColors.gray400,
                    ),
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    'Outfit of the Day',
                    style: TextStyle(fontSize: 26, fontWeight: FontWeight.w300),
                  ),
                ],
              ),
            ),
            if (_weather != null)
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  border: Border.all(color: AppColors.gray800),
                ),
                child: Text(
                  '${_weather!.tempCelsius.round()}°C\n${_weather!.description}',
                  textAlign: TextAlign.right,
                  style: const TextStyle(fontSize: 11, height: 1.4),
                ),
              ),
          ],
        ),
        const SizedBox(height: 24),
        const MinimalDivider(),
        const SizedBox(height: 24),
        if (outfit == null)
          const Center(
            child: Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Text(
                'Digitize garments to get daily recommendations.',
                style: TextStyle(color: AppColors.gray400),
                textAlign: TextAlign.center,
              ),
            ),
          )
        else ...[
          AspectRatio(
            aspectRatio: 3 / 4,
            child: Container(
              decoration: BoxDecoration(
                border: Border.all(color: AppColors.gray800),
                color: AppColors.gray800,
              ),
              child: _tryOnLoading
                  ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
                  : _tryOnPath != null
                      ? GarmentImage(path: _tryOnPath!, fit: BoxFit.contain)
                      : Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Wrap(
                                spacing: 8,
                                children: garments
                                    .map((g) => SizedBox(
                                          width: 72,
                                          height: 96,
                                          child: GarmentImage(
                                            path: g.displayImage,
                                          ),
                                        ))
                                    .toList(),
                              ),
                              const SizedBox(height: 16),
                              OutlinedButton(
                                onPressed: () => _loadTryOn(outfit),
                                child: const Text('Virtual Try-On'),
                              ),
                            ],
                          ),
                        ),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            outfit.reason,
            style: const TextStyle(color: AppColors.gray400, fontSize: 13),
          ),
          const SizedBox(height: 20),
          SizedBox(
            height: 36,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: outfits.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (_, i) {
                final selected = i == _selectedIndex;
                return GestureDetector(
                  onTap: () {
                    setState(() {
                      _selectedIndex = i;
                      _tryOnPath = null;
                    });
                  },
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
                      '${i + 1}',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: selected ? AppColors.black : AppColors.white,
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ],
    );
  }
}
