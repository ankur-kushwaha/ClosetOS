import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../services/weather_service.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';
import '../widgets/stripe_background.dart';

class OotdScreen extends StatefulWidget {
  const OotdScreen({super.key});

  @override
  State<OotdScreen> createState() => _OotdScreenState();
}

class _OotdScreenState extends State<OotdScreen>
    with SingleTickerProviderStateMixin {
  WeatherInfo? _weather;
  int _selectedIndex = 0;
  int? _previousIndex;
  String? _tryOnPath;
  bool _tryOnLoading = false;
  late final AnimationController _revealController;
  late final Animation<double> _revealFade;

  @override
  void initState() {
    super.initState();
    _revealController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 700),
    );
    _revealFade = CurvedAnimation(
      parent: _revealController,
      curve: Curves.easeOut,
    );
    _loadWeather();
    _revealController.forward();
  }

  @override
  void dispose() {
    _revealController.dispose();
    super.dispose();
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
      if (path != null) {
        _revealController
          ..reset()
          ..forward();
      }
    }
  }

  void _selectOutfit(int index) {
    if (index == _selectedIndex) return;
    setState(() {
      _previousIndex = _selectedIndex;
      _selectedIndex = index;
      _tryOnPath = null;
    });
    _revealController
      ..reset()
      ..forward();
  }

  void _undoSelection() {
    if (_previousIndex == null) return;
    _selectOutfit(_previousIndex!);
    setState(() => _previousIndex = null);
  }

  void _shuffleOutfit(List<Outfit> outfits) {
    if (outfits.length < 2) return;
    var next = _selectedIndex;
    while (next == _selectedIndex) {
      next = (next + 1 + outfits.length) % outfits.length;
    }
    _selectOutfit(next);
  }

  void _wearToday(Outfit outfit) {
    HapticFeedback.lightImpact();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Marked "${outfit.name}" as today\'s look',
          style: AppTypography.ui(color: AppColors.surface, fontSize: 14),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    final tempF = _weather?.tempFahrenheit ?? 61;
    final outfits = repo.generateRecommendations(tempF);
    final outfit = outfits.isNotEmpty ? outfits[_selectedIndex] : null;
    final garments =
        outfit != null ? repo.garmentsForOutfit(outfit) : <Garment>[];
    final alternatives = outfits
        .asMap()
        .entries
        .where((e) => e.key != _selectedIndex)
        .map((e) => e)
        .toList();

    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: Stack(
        fit: StackFit.expand,
        children: [
          const StripeBackground(),
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  AppColors.canvas.withValues(alpha: 0.05),
                  AppColors.canvas.withValues(alpha: 0.15),
                  const Color(0xFFD4B89E).withValues(alpha: 0.55),
                  const Color(0xFFB89578).withValues(alpha: 0.88),
                ],
                stops: const [0.0, 0.42, 0.72, 1.0],
              ),
            ),
          ),
          SafeArea(
            bottom: false,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _TopBar(weather: _weather),
                Expanded(
                  child: outfit == null
                      ? const _EmptyState()
                      : FadeTransition(
                          opacity: _revealFade,
                          child: _HeroRender(
                            garments: garments,
                            tryOnPath: _tryOnPath,
                            tryOnLoading: _tryOnLoading,
                            onRequestTryOn: () => _loadTryOn(outfit),
                          ),
                        ),
                ),
                if (outfit != null)
                  _BottomPanel(
                    outfit: outfit,
                    alternatives: alternatives,
                    repo: repo,
                    onWearToday: () => _wearToday(outfit),
                    onUndo: _undoSelection,
                    onShuffle: () => _shuffleOutfit(outfits),
                    onSelectAlternative: _selectOutfit,
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({this.weather});

  final WeatherInfo? weather;

  @override
  Widget build(BuildContext context) {
    final day = DateFormat('EEE').format(DateTime.now());
    final high = weather?.highFahrenheit.round() ?? 61;
    final low = weather?.lowFahrenheit.round() ?? 48;

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 7,
                  height: 7,
                  decoration: const BoxDecoration(
                    color: AppColors.clay500,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  '$day · $high° → $low°',
                  style: AppTypography.ui(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                    color: AppColors.ink900,
                  ),
                ),
              ],
            ),
          ),
          const Spacer(),
          _CircleIconButton(
            icon: Icons.share_outlined,
            onPressed: () {},
          ),
        ],
      ),
    );
  }
}

class _HeroRender extends StatelessWidget {
  const _HeroRender({
    required this.garments,
    required this.tryOnPath,
    required this.tryOnLoading,
    required this.onRequestTryOn,
  });

  final List<Garment> garments;
  final String? tryOnPath;
  final bool tryOnLoading;
  final VoidCallback onRequestTryOn;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Stack(
        alignment: Alignment.center,
        children: [
          if (tryOnLoading)
            const Center(
              child: SizedBox(
                width: 28,
                height: 28,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            )
          else if (tryOnPath != null)
            Positioned.fill(
              child: GarmentImage(path: tryOnPath!, fit: BoxFit.contain),
            )
          else if (garments.isNotEmpty)
            Positioned.fill(
              child: GestureDetector(
                onTap: onRequestTryOn,
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    for (var i = 0; i < garments.length; i++)
                      Positioned(
                        top: 40 + i * 18.0,
                        child: SizedBox(
                          height: 220,
                          child: GarmentImage(
                            path: garments[i].displayImage,
                            fit: BoxFit.contain,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            )
          else
            Center(
              child: Text(
                'full-body render placeholder — user wearing today\'s look —',
                textAlign: TextAlign.center,
                style: AppTypography.ui(
                  fontSize: 12,
                  color: AppColors.ink400.withValues(alpha: 0.7),
                  height: 1.5,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _BottomPanel extends StatelessWidget {
  const _BottomPanel({
    required this.outfit,
    required this.alternatives,
    required this.repo,
    required this.onWearToday,
    required this.onUndo,
    required this.onShuffle,
    required this.onSelectAlternative,
  });

  final Outfit outfit;
  final List<MapEntry<int, Outfit>> alternatives;
  final WardrobeRepository repo;
  final VoidCallback onWearToday;
  final VoidCallback onUndo;
  final VoidCallback onShuffle;
  final ValueChanged<int> onSelectAlternative;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            outfit.reason,
            style: AppTypography.display(
              fontSize: 26,
              height: 1.2,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _CircleIconButton(
                icon: Icons.refresh,
                onPressed: onUndo,
                borderColor: AppColors.surface.withValues(alpha: 0.45),
                iconColor: AppColors.surface,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Material(
                  color: AppColors.clay500,
                  borderRadius: BorderRadius.circular(28),
                  child: InkWell(
                    onTap: onWearToday,
                    borderRadius: BorderRadius.circular(28),
                    child: Container(
                      height: 52,
                      alignment: Alignment.center,
                      child: Text(
                        'Wear this today',
                        style: AppTypography.ui(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                          color: AppColors.surface,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 14),
              _CircleIconButton(
                icon: Icons.shuffle,
                onPressed: onShuffle,
                borderColor: AppColors.surface.withValues(alpha: 0.45),
                iconColor: AppColors.surface,
              ),
            ],
          ),
          const SizedBox(height: 28),
          Row(
            children: [
              Text(
                'Or try',
                style: AppTypography.label(
                  fontSize: 11,
                  letterSpacing: 1.4,
                  color: AppColors.surface.withValues(alpha: 0.75),
                ),
              ),
              const Spacer(),
              GestureDetector(
                onTap: onShuffle,
                child: Text(
                  'swap a piece  ›',
                  style: AppTypography.ui(
                    fontSize: 12,
                    color: AppColors.surface.withValues(alpha: 0.75),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 118,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: alternatives.length,
              separatorBuilder: (_, _) => const SizedBox(width: 12),
              itemBuilder: (_, i) {
                final entry = alternatives[i];
                final altOutfit = entry.value;
                final altGarments = repo.garmentsForOutfit(altOutfit);
                return _AlternativeCard(
                  name: altOutfit.name,
                  garments: altGarments,
                  onTap: () => onSelectAlternative(entry.key),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _AlternativeCard extends StatelessWidget {
  const _AlternativeCard({
    required this.name,
    required this.garments,
    required this.onTap,
  });

  final String name;
  final List<Garment> garments;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: SizedBox(
        width: 108,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.border),
                ),
                clipBehavior: Clip.antiAlias,
                child: garments.isEmpty
                    ? const StripeBackground(
                        baseColor: AppColors.surface,
                        opacity: 0.35,
                      )
                    : Stack(
                        alignment: Alignment.center,
                        children: [
                          const StripeBackground(
                            baseColor: AppColors.surface,
                            opacity: 0.25,
                          ),
                          Padding(
                            padding: const EdgeInsets.all(8),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: garments
                                  .take(3)
                                  .map(
                                    (g) => Expanded(
                                      child: GarmentImage(
                                        path: g.displayImage,
                                        fit: BoxFit.contain,
                                      ),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ),
                        ],
                      ),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: AppTypography.ui(
                fontSize: 12,
                color: AppColors.surface,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CircleIconButton extends StatelessWidget {
  const _CircleIconButton({
    required this.icon,
    required this.onPressed,
    this.borderColor,
    this.iconColor,
  });

  final IconData icon;
  final VoidCallback onPressed;
  final Color? borderColor;
  final Color? iconColor;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onPressed,
        customBorder: const CircleBorder(),
        child: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(
              color: borderColor ?? AppColors.border,
              width: 1,
            ),
          ),
          child: Icon(
            icon,
            size: 20,
            color: iconColor ?? AppColors.ink600,
          ),
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Your morning look starts here',
              textAlign: TextAlign.center,
              style: AppTypography.display(
                fontSize: 24,
                color: AppColors.ink900,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Digitize a few garments to get daily recommendations.',
              textAlign: TextAlign.center,
              style: AppTypography.ui(
                fontSize: 14,
                color: AppColors.ink600,
                height: 1.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
