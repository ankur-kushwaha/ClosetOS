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
  const OotdScreen({super.key, this.onOpenDrawer});

  final VoidCallback? onOpenDrawer;

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
    final repo = context.read<WardrobeRepository>();
    final path = await repo.renderTryOn(outfit);
    if (mounted) {
      setState(() {
        _tryOnPath = path;
        _tryOnLoading = false;
      });
      if (path != null) {
        _revealController
          ..reset()
          ..forward();
      } else {
        final err = repo.lastError ?? 'Try-on failed. Add a selfie in your profile first.';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(err, style: AppTypography.ui(color: AppColors.surface, fontSize: 14)),
            backgroundColor: AppColors.ink900,
            behavior: SnackBarBehavior.floating,
          ),
        );
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
                  AppColors.canvas.withValues(alpha: 0.0),
                  AppColors.canvas.withValues(alpha: 0.2),
                  const Color(0xFFE2E8F0).withValues(alpha: 0.4),
                  const Color(0xFFCBD5E1).withValues(alpha: 0.7),
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
                _TopBar(weather: _weather, onOpenDrawer: widget.onOpenDrawer),
                const SizedBox(height: 12),
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
                            onClearTryOn: () => setState(() => _tryOnPath = null),
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
  const _TopBar({this.weather, this.onOpenDrawer});

  final WeatherInfo? weather;
  final VoidCallback? onOpenDrawer;

  @override
  Widget build(BuildContext context) {
    final day = DateFormat('EEE').format(DateTime.now());
    final high = weather?.highFahrenheit.round() ?? 61;
    final low = weather?.lowFahrenheit.round() ?? 48;

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
      child: Row(
        children: [
          _CircleIconButton(
            icon: Icons.menu,
            onPressed: onOpenDrawer,
          ),
          const SizedBox(width: 8),
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
    required this.onClearTryOn,
  });

  final List<Garment> garments;
  final String? tryOnPath;
  final bool tryOnLoading;
  final VoidCallback onRequestTryOn;
  final VoidCallback onClearTryOn;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 24, right: 24, top: 12),
      child: Stack(
        alignment: Alignment.center,
        children: [
          if (tryOnLoading)
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const CircularProgressIndicator(color: AppColors.clay500),
                  const SizedBox(height: 16),
                  Text(
                    'Generating Try-on Outfit...',
                    style: AppTypography.ui(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: AppColors.ink900,
                    ),
                  ),
                ],
              ),
            )
          else if (tryOnPath != null)
            Stack(
              alignment: Alignment.center,
              children: [
                Positioned.fill(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(20),
                    child: GarmentImage(path: tryOnPath!, fit: BoxFit.contain),
                  ),
                ),
                Positioned(
                  top: 12,
                  right: 12,
                  child: Material(
                    color: Colors.black54,
                    shape: const CircleBorder(),
                    child: IconButton(
                      icon: const Icon(Icons.close, color: Colors.white, size: 18),
                      onPressed: onClearTryOn,
                    ),
                  ),
                ),
              ],
            )
          else if (garments.isNotEmpty)
            Builder(
              builder: (context) {
                final topGarments = garments.where((g) => g.category.toLowerCase() == 'top').toList();
                final outerwearGarments = garments.where((g) => g.category.toLowerCase() == 'outerwear').toList();
                final dressGarments = garments.where((g) => g.category.toLowerCase() == 'dress').toList();
                final bottomGarments = garments.where((g) => g.category.toLowerCase() == 'bottom').toList();
                final shoesGarments = garments.where((g) => g.category.toLowerCase() == 'shoes').toList();
                final accessoryGarments = garments.where((g) => g.category.toLowerCase() == 'accessory').toList();

                final topOuterDress = [...topGarments, ...outerwearGarments, ...dressGarments];
                final matchedIds = topOuterDress.map((g) => g.id).toSet()
                  ..addAll(bottomGarments.map((g) => g.id))
                  ..addAll(shoesGarments.map((g) => g.id))
                  ..addAll(accessoryGarments.map((g) => g.id));
                final leftovers = garments.where((g) => !matchedIds.contains(g.id)).toList();

                Widget buildGarmentCard(Garment g) {
                  return Container(
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppColors.border),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: Stack(
                      children: [
                        Positioned.fill(
                          child: ColoredBox(
                            color: Colors.white,
                            child: GarmentImage(
                              path: g.displayImage,
                              fit: BoxFit.contain,
                            ),
                          ),
                        ),
                        Positioned(
                          bottom: 8,
                          left: 8,
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: AppColors.canvas.withValues(alpha: 0.8),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              g.category,
                              style: AppTypography.ui(
                                fontSize: 9,
                                fontWeight: FontWeight.w600,
                                color: AppColors.ink900,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                }

                final List<Widget> rows = [];

                if (topOuterDress.isNotEmpty) {
                  rows.add(
                    Expanded(
                      child: Row(
                        children: topOuterDress.map((g) => Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6.0),
                            child: buildGarmentCard(g),
                          ),
                        )).toList(),
                      ),
                    ),
                  );
                }

                if (bottomGarments.isNotEmpty) {
                  if (rows.isNotEmpty) rows.add(const SizedBox(height: 12));
                  rows.add(
                    Expanded(
                      child: Row(
                        children: bottomGarments.map((g) => Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6.0),
                            child: buildGarmentCard(g),
                          ),
                        )).toList(),
                      ),
                    ),
                  );
                }

                if (shoesGarments.isNotEmpty) {
                  if (rows.isNotEmpty) rows.add(const SizedBox(height: 12));
                  rows.add(
                    Expanded(
                      child: Row(
                        children: shoesGarments.map((g) => Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6.0),
                            child: buildGarmentCard(g),
                          ),
                        )).toList(),
                      ),
                    ),
                  );
                }

                if (accessoryGarments.isNotEmpty) {
                  if (rows.isNotEmpty) rows.add(const SizedBox(height: 12));
                  rows.add(
                    Expanded(
                      child: Row(
                        children: accessoryGarments.map((g) => Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6.0),
                            child: buildGarmentCard(g),
                          ),
                        )).toList(),
                      ),
                    ),
                  );
                }

                if (leftovers.isNotEmpty) {
                  if (rows.isNotEmpty) rows.add(const SizedBox(height: 12));
                  rows.add(
                    Expanded(
                      child: Row(
                        children: leftovers.map((g) => Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6.0),
                            child: buildGarmentCard(g),
                          ),
                        )).toList(),
                      ),
                    ),
                  );
                }

                return Column(
                  children: [
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Column(
                          children: rows,
                        ),
                      ),
                    ),
                    Material(
                      color: AppColors.clay500,
                      borderRadius: BorderRadius.circular(24),
                      child: InkWell(
                        onTap: onRequestTryOn,
                        borderRadius: BorderRadius.circular(24),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.auto_awesome, color: AppColors.surface, size: 16),
                              const SizedBox(width: 8),
                              Text(
                                'Virtual Try-on',
                                style: AppTypography.ui(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.surface,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                );
              },
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
              color: AppColors.ink900,
            ),
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _CircleIconButton(
                icon: Icons.refresh,
                onPressed: onUndo,
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
                  color: AppColors.ink600,
                ),
              ),
              const Spacer(),
              GestureDetector(
                onTap: onShuffle,
                child: Text(
                  'swap a piece  ›',
                  style: AppTypography.ui(
                    fontSize: 12,
                    color: AppColors.ink600,
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
  final VoidCallback? onPressed;
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
