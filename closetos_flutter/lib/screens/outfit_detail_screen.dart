import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/common.dart';
import '../widgets/stripe_background.dart';

class OutfitDetailScreen extends StatefulWidget {
  const OutfitDetailScreen({super.key, required this.outfit});

  final Outfit outfit;

  @override
  State<OutfitDetailScreen> createState() => _OutfitDetailScreenState();
}

class _OutfitDetailScreenState extends State<OutfitDetailScreen> {
  late Outfit _currentOutfit;
  int? _expandedGarmentIndex; // Track which piece is expanded for swapping
  String? _tryOnPath;
  bool _tryOnLoading = false;

  @override
  void initState() {
    super.initState();
    _currentOutfit = widget.outfit;
    // Load cached try-on if any
    final repo = context.read<WardrobeRepository>();
    _tryOnPath = repo.tryOnCache[_currentOutfit.id];
  }

  Future<void> _runTryOn() async {
    setState(() {
      _tryOnLoading = true;
      _tryOnPath = null;
    });

    final repo = context.read<WardrobeRepository>();
    final path = await repo.renderTryOn(_currentOutfit);

    if (mounted) {
      setState(() {
        _tryOnPath = path;
        _tryOnLoading = false;
      });

      if (path == null) {
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

  void _wearToday() {
    HapticFeedback.lightImpact();
    context.read<WardrobeRepository>().recordWear(_currentOutfit);
    setState(() {
      // Refresh the outfit from repository to update wear statistics
      final repo = context.read<WardrobeRepository>();
      final updatedIdx = repo.outfits.indexWhere((o) => o.id == _currentOutfit.id);
      if (updatedIdx >= 0) {
        _currentOutfit = repo.outfits[updatedIdx];
      } else {
        // Fallback: increment locally if not persisted
        _currentOutfit = Outfit(
          id: _currentOutfit.id,
          garmentIds: _currentOutfit.garmentIds,
          name: _currentOutfit.name,
          overallScore: _currentOutfit.overallScore,
          reason: _currentOutfit.reason,
          isFavorite: _currentOutfit.isFavorite,
          isSaved: _currentOutfit.isSaved,
          isAiGenerated: _currentOutfit.isAiGenerated,
          tags: _currentOutfit.tags,
          wearCount: _currentOutfit.wearCount + 1,
          lastWorn: DateTime.now(),
        );
      }
    });

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Marked as worn today! Wear count updated.',
          style: AppTypography.ui(color: AppColors.surface, fontSize: 14),
        ),
        backgroundColor: AppColors.clay500,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  void _swapGarment(int index, Garment newGarment) {
    setState(() {
      final newGarmentIds = List<String>.from(_currentOutfit.garmentIds);
      newGarmentIds[index] = newGarment.id;

      _currentOutfit = Outfit(
        id: _currentOutfit.id,
        garmentIds: newGarmentIds,
        name: _currentOutfit.name,
        overallScore: _currentOutfit.overallScore,
        reason: _currentOutfit.reason,
        isFavorite: _currentOutfit.isFavorite,
        isSaved: _currentOutfit.isSaved,
        isAiGenerated: _currentOutfit.isAiGenerated,
        tags: _currentOutfit.tags,
        wearCount: _currentOutfit.wearCount,
        lastWorn: _currentOutfit.lastWorn,
      );

      _tryOnPath = null; // Clear try-on for new combination
      _expandedGarmentIndex = null; // Collapse swap list
    });
  }

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    final garments = repo.garmentsForOutfit(_currentOutfit);
    final isFavorite = repo.outfits.any((o) => o.id == _currentOutfit.id && o.isFavorite);

    // Warm minimalist palette matching the mockups
    const accentColor = AppColors.clay500;
    const customCanvas = AppColors.canvas;
    const customCard = AppColors.surface;
    const customBorder = AppColors.border;

    String wearText = 'Never worn';
    if (_currentOutfit.wearCount > 0) {
      final times = _currentOutfit.wearCount == 1 ? 'once' : '${_currentOutfit.wearCount} times';
      String lastWornStr = '';
      if (_currentOutfit.lastWorn != null) {
        lastWornStr = ' · last worn ${DateFormat('MMM d').format(_currentOutfit.lastWorn!)}';
      }
      wearText = 'Worn $times$lastWornStr';
    }

    return Scaffold(
      backgroundColor: customCanvas,
      body: Stack(
        children: [
          // Content
          Positioned.fill(
            child: SingleChildScrollView(
              padding: const EdgeInsets.only(bottom: 180),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Header section
                  Container(
                    height: 360,
                    decoration: const BoxDecoration(
                      border: Border(
                        bottom: BorderSide(color: customBorder, width: 1),
                      ),
                    ),
                    child: Stack(
                      children: [
                        const Positioned.fill(
                          child: StripeBackground(
                            baseColor: customCanvas,
                            opacity: 0.45,
                          ),
                        ),
                        if (_tryOnLoading)
                          Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const CircularProgressIndicator(color: accentColor),
                                const SizedBox(height: 16),
                                Text(
                                  'Generating Virtual Try-on...',
                                  style: AppTypography.ui(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w500,
                                    color: AppColors.ink900,
                                  ),
                                ),
                              ],
                            ),
                          )
                        else if (_tryOnPath != null)
                          Positioned.fill(
                            child: InteractiveViewer(
                              child: GarmentImage(
                                path: _tryOnPath!,
                                fit: BoxFit.contain,
                              ),
                            ),
                          )
                        else if (garments.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.fromLTRB(40, 60, 40, 40),
                            child: Center(
                              child: Hero(
                                tag: 'outfit_render_${_currentOutfit.id}',
                                child: Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: garments
                                      .map(
                                        (g) => Expanded(
                                          child: Padding(
                                            padding: const EdgeInsets.symmetric(horizontal: 8.0),
                                            child: Container(
                                              decoration: BoxDecoration(
                                                color: Colors.white,
                                                borderRadius: BorderRadius.circular(16),
                                                border: Border.all(color: customBorder),
                                                boxShadow: [
                                                  BoxShadow(
                                                    color: Colors.black.withValues(alpha: 0.03),
                                                    blurRadius: 10,
                                                    offset: const Offset(0, 4),
                                                  ),
                                                ],
                                              ),
                                              clipBehavior: Clip.antiAlias,
                                              child: GarmentImage(
                                                path: g.displayImage,
                                                fit: BoxFit.contain,
                                              ),
                                            ),
                                          ),
                                        ),
                                      )
                                      .toList(),
                                ),
                              ),
                            ),
                          )
                        else
                          Center(
                            child: Icon(
                              Icons.style_outlined,
                              size: 64,
                              color: AppColors.ink400.withValues(alpha: 0.5),
                            ),
                          ),
                      ],
                    ),
                  ),

                  // Metadata section
                  Padding(
                    padding: const EdgeInsets.fromLTRB(24, 24, 24, 8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _currentOutfit.name,
                          style: AppTypography.display(
                            fontSize: 32,
                            fontWeight: FontWeight.w600,
                            color: AppColors.ink900,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          wearText,
                          style: AppTypography.ui(
                            fontSize: 14,
                            color: AppColors.ink600,
                            fontWeight: FontWeight.w400,
                          ),
                        ),
                        if (_currentOutfit.reason.isNotEmpty) ...[
                          const SizedBox(height: 12),
                          Text(
                            _currentOutfit.reason,
                            style: AppTypography.ui(
                              fontSize: 14,
                              color: AppColors.ink600,
                              height: 1.4,
                            ),
                          ),
                        ],
                        const SizedBox(height: 32),
                        Text(
                          'PIECES',
                          style: AppTypography.label(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: AppColors.ink400,
                            letterSpacing: 1.5,
                          ),
                        ),
                      ],
                    ),
                  ),

                  // Pieces List
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
                    itemCount: garments.length,
                    separatorBuilder: (context, index) => const SizedBox(height: 16),
                    itemBuilder: (context, idx) {
                      final garment = garments[idx];
                      final isExpanded = _expandedGarmentIndex == idx;

                      // Cost per wear calculation
                      final double costPerWear = garment.wearCount > 0
                          ? garment.price / garment.wearCount
                          : garment.price;

                      // Find alternative garments for swapping
                      final alternatives = repo.garments
                          .where((g) => g.category == garment.category && g.id != garment.id)
                          .toList();

                      return Column(
                        children: [
                          Container(
                            decoration: BoxDecoration(
                              color: customCard,
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(color: customBorder),
                            ),
                            child: InkWell(
                              onTap: () {
                                if (alternatives.isNotEmpty) {
                                  setState(() {
                                    _expandedGarmentIndex = isExpanded ? null : idx;
                                  });
                                }
                              },
                              borderRadius: BorderRadius.circular(16),
                              child: Padding(
                                padding: const EdgeInsets.all(14),
                                child: Row(
                                  children: [
                                    // Garment image
                                    Container(
                                      width: 64,
                                      height: 64,
                                      decoration: BoxDecoration(
                                        color: Colors.white,
                                        borderRadius: BorderRadius.circular(12),
                                        border: Border.all(color: customBorder),
                                      ),
                                      clipBehavior: Clip.antiAlias,
                                      child: GarmentImage(
                                        path: garment.displayImage,
                                        fit: BoxFit.contain,
                                      ),
                                    ),
                                    const SizedBox(width: 16),

                                    // Garment details
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            garment.subcategory.isNotEmpty
                                                ? garment.subcategory
                                                : garment.category,
                                            style: AppTypography.ui(
                                              fontSize: 16,
                                              fontWeight: FontWeight.w600,
                                              color: AppColors.ink900,
                                            ),
                                          ),
                                          const SizedBox(height: 4),
                                          Text(
                                            '\$${costPerWear.toStringAsFixed(2)}/wear',
                                            style: AppTypography.ui(
                                              fontSize: 13,
                                              color: AppColors.ink600,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),

                                    // Swap Button
                                    if (alternatives.isNotEmpty)
                                      TextButton(
                                        onPressed: () {
                                          setState(() {
                                            _expandedGarmentIndex = isExpanded ? null : idx;
                                          });
                                        },
                                        style: TextButton.styleFrom(
                                          foregroundColor: accentColor,
                                          textStyle: AppTypography.ui(
                                            fontSize: 14,
                                            fontWeight: FontWeight.w600,
                                          ),
                                        ),
                                        child: const Text('Swap'),
                                      ),
                                  ],
                                ),
                              ),
                            ),
                          ),

                          // Alternatives Drawer/Row
                          if (isExpanded && alternatives.isNotEmpty)
                            Container(
                              margin: const EdgeInsets.only(top: 8),
                              height: 120,
                              decoration: BoxDecoration(
                                color: Colors.white.withValues(alpha: 0.4),
                                borderRadius: BorderRadius.circular(16),
                              ),
                              child: ListView.separated(
                                scrollDirection: Axis.horizontal,
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                                itemCount: alternatives.length,
                                separatorBuilder: (context, index) => const SizedBox(width: 12),
                                itemBuilder: (context, altIdx) {
                                  final altGarment = alternatives[altIdx];
                                  return GestureDetector(
                                    onTap: () => _swapGarment(idx, altGarment),
                                    child: Column(
                                      children: [
                                        Container(
                                          width: 60,
                                          height: 60,
                                          decoration: BoxDecoration(
                                            color: Colors.white,
                                            borderRadius: BorderRadius.circular(12),
                                            border: Border.all(color: customBorder),
                                          ),
                                          clipBehavior: Clip.antiAlias,
                                          child: GarmentImage(
                                            path: altGarment.displayImage,
                                            fit: BoxFit.contain,
                                          ),
                                        ),
                                        const SizedBox(height: 6),
                                        SizedBox(
                                          width: 68,
                                          child: Text(
                                            altGarment.subcategory.isNotEmpty
                                                ? altGarment.subcategory
                                                : altGarment.category,
                                            style: AppTypography.ui(
                                              fontSize: 10,
                                              fontWeight: FontWeight.w500,
                                              color: AppColors.ink600,
                                            ),
                                            textAlign: TextAlign.center,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                      ],
                                    ),
                                  );
                                },
                              ),
                            ),
                        ],
                      );
                    },
                  ),
                ],
              ),
            ),
          ),

          // Top action navigation overlay
          Positioned(
            top: MediaQuery.of(context).padding.top + 8,
            left: 16,
            right: 16,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                // Back Button
                Container(
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                  ),
                  child: IconButton(
                    icon: const Icon(Icons.arrow_back, color: AppColors.ink900, size: 20),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ),

                // Favorite Button
                Container(
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                  ),
                  child: IconButton(
                    icon: Icon(
                      isFavorite ? Icons.favorite : Icons.favorite_border,
                      color: isFavorite ? accentColor : AppColors.ink600,
                      size: 20,
                    ),
                    onPressed: () => repo.toggleFavoriteOutfit(_currentOutfit),
                  ),
                ),
              ],
            ),
          ),

          // Bottom Action Bar
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              padding: EdgeInsets.fromLTRB(
                24,
                16,
                24,
                MediaQuery.of(context).padding.bottom + 16,
              ),
              decoration: BoxDecoration(
                color: customCanvas,
                border: const Border(
                  top: BorderSide(color: customBorder, width: 1),
                ),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Virtual Try-on Button
                  OutlinedButton.icon(
                    onPressed: _tryOnLoading ? null : _runTryOn,
                    icon: const Icon(Icons.auto_awesome, size: 18, color: accentColor),
                    label: Text(
                      'Virtual Try-on',
                      style: AppTypography.ui(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: accentColor,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: accentColor, width: 1.5),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Wear this today primary button
                  Material(
                    color: accentColor,
                    borderRadius: BorderRadius.circular(28),
                    child: InkWell(
                      onTap: _wearToday,
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
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
