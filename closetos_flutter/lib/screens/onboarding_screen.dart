import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key, required this.onComplete});

  final VoidCallback onComplete;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final _pageController = PageController();
  int _step = 0;

  final _styles = <String>{};
  final _fits = <String>{};
  final _occasions = <String>{};
  final _avoidColors = <String>{};

  static const _styleOptions = [
    'Minimal',
    'Classic',
    'Streetwear',
    'Business',
    'Casual',
    'Avant-garde',
  ];
  static const _fitOptions = ['Slim', 'Regular', 'Relaxed', 'Oversized'];
  static const _occasionOptions = [
    'Office',
    'Weekend',
    'Travel',
    'Date',
    'Formal',
  ];
  static const _colorOptions = ['Neon', 'Bright patterns', 'All black', 'Pastels'];

  void _next() {
    if (_step < 2) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
      setState(() => _step++);
    } else {
      _finish();
    }
  }

  Future<void> _finish() async {
    final repo = context.read<WardrobeRepository>();
    await repo.completeOnboarding(
      UserTaste(
        preferredStyles: _styles.toList(),
        preferredFits: _fits.toList(),
        occasions: _occasions.toList(),
        colorsAvoided: _avoidColors.toList(),
      ),
      null,
    );
    widget.onComplete();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 0),
              child: Row(
                children: [
                  const Text(
                    'CLOSETOS',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 4,
                    ),
                  ),
                  const Spacer(),
                  Text(
                    '${_step + 1} / 3',
                    style: const TextStyle(
                      color: AppColors.gray400,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            LinearProgressIndicator(
              value: (_step + 1) / 3,
              backgroundColor: AppColors.gray800,
              color: AppColors.white,
              minHeight: 1,
            ),
            Expanded(
              child: PageView(
                controller: _pageController,
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  _buildStep(
                    'Your aesthetic',
                    'Select styles that define your wardrobe.',
                    _styleOptions,
                    _styles,
                  ),
                  _buildStep(
                    'Fit & occasions',
                    'How you dress for life.',
                    [..._fitOptions, ..._occasionOptions],
                    {..._fits, ..._occasions},
                    isMixed: true,
                  ),
                  _buildStep(
                    'Preferences',
                    'Colors and patterns to avoid.',
                    _colorOptions,
                    _avoidColors,
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(24),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _next,
                  child: Text(_step < 2 ? 'Continue' : 'Enter Closet'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStep(
    String title,
    String subtitle,
    List<String> options,
    Set<String> selected, {
    bool isMixed = false,
  }) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w300),
          ),
          const SizedBox(height: 8),
          Text(subtitle, style: const TextStyle(color: AppColors.gray400)),
          const SizedBox(height: 28),
          Expanded(
            child: SingleChildScrollView(
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: options.map((opt) {
                  final isSelected = isMixed
                      ? (_fits.contains(opt) || _occasions.contains(opt))
                      : selected.contains(opt);
                  return FilterChip(
                    label: Text(opt),
                    selected: isSelected,
                    onSelected: (v) {
                      setState(() {
                        if (isMixed) {
                          if (_fitOptions.contains(opt)) {
                            v ? _fits.add(opt) : _fits.remove(opt);
                          } else {
                            v ? _occasions.add(opt) : _occasions.remove(opt);
                          }
                        } else {
                          v ? selected.add(opt) : selected.remove(opt);
                        }
                      });
                    },
                    showCheckmark: false,
                    selectedColor: AppColors.white,
                    labelStyle: TextStyle(
                      color: isSelected ? AppColors.black : AppColors.white,
                      fontSize: 12,
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
