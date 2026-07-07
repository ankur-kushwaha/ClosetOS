import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';
import 'outfit_detail_screen.dart';

class TravelScreen extends StatefulWidget {
  const TravelScreen({super.key});

  @override
  State<TravelScreen> createState() => _TravelScreenState();
}

class _TravelScreenState extends State<TravelScreen> {
  final _destination = TextEditingController();
  int _days = 5;
  double _tempLow = 13;
  double _tempHigh = 24;
  String _weather = 'Clear';
  TravelCapsulePlan? _plan;

  @override
  void dispose() {
    _destination.dispose();
    super.dispose();
  }

  Future<void> _generate() async {
    if (_destination.text.trim().isEmpty) return;
    final repo = context.read<WardrobeRepository>();
    final plan = await repo.planTrip(
      destination: _destination.text.trim(),
      days: _days,
      tempLowF: _tempLow * 9 / 5 + 32,
      tempHighF: _tempHigh * 9 / 5 + 32,
      weather: _weather,
    );
    if (!mounted) return;
    setState(() => _plan = plan);
    if (plan == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(repo.lastError ?? 'Could not generate capsule.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    final garmentsById = {for (final g in repo.garments) g.id: g};

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const SectionHeader(
          title: 'Travel',
          subtitle: 'Pack a capsule wardrobe for your trip.',
        ),
        TextField(
          controller: _destination,
          decoration: const InputDecoration(labelText: 'Destination'),
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: Text('Days: $_days', style: const TextStyle(fontSize: 13)),
            ),
            IconButton(
              onPressed: () => setState(() => _days = (_days - 1).clamp(1, 21)),
              icon: const Icon(Icons.remove, size: 18),
            ),
            IconButton(
              onPressed: () => setState(() => _days = (_days + 1).clamp(1, 21)),
              icon: const Icon(Icons.add, size: 18),
            ),
          ],
        ),
        Text('Low temp: ${_tempLow.round()}°C'),
        Slider(
          value: _tempLow,
          min: -5,
          max: 35,
          onChanged: (v) => setState(() => _tempLow = v),
        ),
        Text('High temp: ${_tempHigh.round()}°C'),
        Slider(
          value: _tempHigh,
          min: -5,
          max: 45,
          onChanged: (v) => setState(() => _tempHigh = v),
        ),
        DropdownButtonFormField<String>(
          value: _weather,
          decoration: const InputDecoration(labelText: 'Weather'),
          items: ['Clear', 'Rain', 'Snow', 'Hot', 'Cold']
              .map((w) => DropdownMenuItem(value: w, child: Text(w)))
              .toList(),
          onChanged: (v) => setState(() => _weather = v ?? 'Clear'),
        ),
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: repo.isLoading ? null : _generate,
            child: repo.isLoading
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Generate Capsule'),
          ),
        ),
        if (_plan != null) ...[
          const SizedBox(height: 32),
          const MinimalDivider(),
          const SizedBox(height: 20),
          Text(
            'CAPSULE · ${_plan!.capsuleGarmentIds.length} items',
            style: const TextStyle(
              fontSize: 11,
              letterSpacing: 2,
              color: AppColors.gray400,
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 80,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _plan!.capsuleGarmentIds.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (_, i) {
                final g = garmentsById[_plan!.capsuleGarmentIds[i]];
                if (g == null) return const SizedBox.shrink();
                return SizedBox(
                  width: 64,
                  child: GarmentImage(path: g.displayImage),
                );
              },
            ),
          ),
          if (_plan!.packingNotes.isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(
              _plan!.packingNotes,
              style: const TextStyle(color: AppColors.gray400, fontSize: 13),
            ),
          ],
          const SizedBox(height: 24),
          ..._plan!.dailyOutfits.map((day) {
            final dayGarments = day.garmentIds
                .map((id) => garmentsById[id])
                .whereType<Garment>()
                .toList();
            final dayOutfit = Outfit(
              id: 'travel_day_${day.day}',
              garmentIds: day.garmentIds,
              name: 'Day ${day.day} Look',
              overallScore: 1.0,
              reason: day.reason,
              isAiGenerated: true,
              tags: ['Travel', _destination.text.trim()],
            );

            return GestureDetector(
              onTap: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => OutfitDetailScreen(outfit: dayOutfit),
                  ),
                );
              },
              child: Card(
                margin: const EdgeInsets.only(bottom: 20),
                clipBehavior: Clip.antiAlias,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    OutfitGarmentsPreview(garments: dayGarments, height: 110),
                    Padding(
                      padding: const EdgeInsets.all(14),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'DAY ${day.day}',
                            style: AppTypography.ui(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              letterSpacing: 1.5,
                              color: AppColors.clay500,
                            ),
                          ),
                          if (day.reason.isNotEmpty) ...[
                            const SizedBox(height: 4),
                            Text(
                              day.reason,
                              style: AppTypography.ui(
                                color: AppColors.ink600,
                                fontSize: 12,
                                height: 1.3,
                              ),
                            ),
                          ],
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
