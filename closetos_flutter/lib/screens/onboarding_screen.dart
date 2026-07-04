import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../services/auth_service.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import '../widgets/stripe_background.dart';

const _totalSteps = 5;

class _QuizQuestion {
  const _QuizQuestion({
    required this.headline,
    required this.subtext,
    required this.options,
  });

  final String headline;
  final String subtext;
  final List<String> options;
}

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key, required this.onComplete});

  final VoidCallback onComplete;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  static const _questions = [
    _QuizQuestion(
      headline: 'Which mood do you get dressed for?',
      subtext: 'Pick the one that feels most like you.',
      options: [
        'Soft & romantic',
        'Clean & minimal',
        'Bold & graphic',
        'Relaxed & lived-in',
      ],
    ),
    _QuizQuestion(
      headline: 'What shape feels most natural?',
      subtext: 'The silhouette you reach for without thinking.',
      options: [
        'Slim & tailored',
        'Classic & balanced',
        'Relaxed & easy',
        'Oversized & draped',
      ],
    ),
    _QuizQuestion(
      headline: 'Where does most of your dressing happen?',
      subtext: 'Your everyday context.',
      options: [
        'Office & meetings',
        'Creative & fluid',
        'Weekends & errands',
        'Everywhere equally',
      ],
    ),
    _QuizQuestion(
      headline: 'How do you wear color?',
      subtext: 'Be honest — this stays between us.',
      options: [
        'Neutrals mostly',
        'Earth tones & navy',
        'One bold accent',
        'Full spectrum',
      ],
    ),
    _QuizQuestion(
      headline: 'What should getting dressed feel like?',
      subtext: 'Your morning ritual in one word.',
      options: [
        'Effortless',
        'Intentional',
        'Expressive',
        'Polished',
      ],
    ),
  ];

  int _step = 0;
  final List<String?> _answers = List.filled(_totalSteps, null);

  _QuizQuestion get _current => _questions[_step];

  bool get _hasSelection => _answers[_step] != null;

  void _select(String option) => setState(() => _answers[_step] = option);

  void _next() {
    if (!_hasSelection) return;
    if (_step < _totalSteps - 1) {
      setState(() => _step++);
    } else {
      _finish();
    }
  }

  Future<void> _skip() async {
    final repo = context.read<WardrobeRepository>();
    final auth = context.read<AuthService>();
    final taste = UserTaste();
    await repo.completeOnboarding(taste, null);
    await auth.syncOnboarding(taste);
    widget.onComplete();
  }

  Future<void> _finish() async {
    final repo = context.read<WardrobeRepository>();
    final auth = context.read<AuthService>();
    final taste = _buildTaste();
    await repo.completeOnboarding(taste, null);
    await auth.syncOnboarding(taste);
    widget.onComplete();
  }

  UserTaste _buildTaste() {
    return UserTaste(
      preferredStyles: [
        if (_answers[0] != null) _answers[0]!,
        if (_answers[4] != null) _answers[4]!,
      ],
      preferredFits: [
        if (_answers[1] != null) _answers[1]!,
      ],
      occasions: _answers[2] != null ? [_answers[2]!] : const [],
      colorsAvoided: [
        if (_answers[3] == 'Neutrals mostly') 'Bright patterns',
      ].where((s) => s.isNotEmpty).toList(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isLast = _step == _totalSteps - 1;

    return Scaffold(
      backgroundColor: AppColors.canvas,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 16, 0),
              child: Row(
                children: [
                  Expanded(child: _SegmentedProgress(current: _step)),
                  const SizedBox(width: 12),
                  _CloseButton(onPressed: _skip),
                ],
              ),
            ),
            Expanded(
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 320),
                switchInCurve: Curves.easeOut,
                switchOutCurve: Curves.easeIn,
                transitionBuilder: (child, animation) => FadeTransition(
                  opacity: animation,
                  child: SlideTransition(
                    position: Tween<Offset>(
                      begin: const Offset(0.04, 0),
                      end: Offset.zero,
                    ).animate(animation),
                    child: child,
                  ),
                ),
                child: _QuizStep(
                  key: ValueKey(_step),
                  step: _step,
                  total: _totalSteps,
                  question: _current,
                  selected: _answers[_step],
                  onSelect: _select,
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
              child: Material(
                color: _hasSelection
                    ? AppColors.clay500
                    : AppColors.clay500.withValues(alpha: 0.4),
                borderRadius: BorderRadius.circular(28),
                child: InkWell(
                  onTap: _hasSelection ? _next : null,
                  borderRadius: BorderRadius.circular(28),
                  child: SizedBox(
                    height: 52,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          isLast ? 'Enter Closet' : 'Continue',
                          style: AppTypography.ui(
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                            color: AppColors.surface,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Icon(
                          Icons.arrow_forward,
                          size: 18,
                          color: AppColors.surface,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SegmentedProgress extends StatelessWidget {
  const _SegmentedProgress({required this.current});

  final int current;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(_totalSteps, (i) {
        final filled = i <= current;
        return Expanded(
          child: Padding(
            padding: EdgeInsets.only(
              right: i < _totalSteps - 1 ? 5 : 0,
            ),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 280),
              curve: Curves.easeOut,
              height: 4,
              decoration: BoxDecoration(
                color: filled ? AppColors.clay500 : AppColors.greige,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
        );
      }),
    );
  }
}

class _CloseButton extends StatelessWidget {
  const _CloseButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onPressed,
        customBorder: const CircleBorder(),
        child: const SizedBox(
          width: 40,
          height: 40,
          child: Icon(
            Icons.close,
            size: 22,
            color: AppColors.ink600,
          ),
        ),
      ),
    );
  }
}

class _QuizStep extends StatelessWidget {
  const _QuizStep({
    super.key,
    required this.step,
    required this.total,
    required this.question,
    required this.selected,
    required this.onSelect,
  });

  final int step;
  final int total;
  final _QuizQuestion question;
  final String? selected;
  final ValueChanged<String> onSelect;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 28, 20, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Question ${step + 1} of $total',
            style: AppTypography.ui(
              fontSize: 13,
              color: AppColors.ink600,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            question.headline,
            style: AppTypography.display(
              fontSize: 30,
              color: AppColors.ink900,
              fontWeight: FontWeight.w500,
              height: 1.12,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            question.subtext,
            style: AppTypography.ui(
              fontSize: 14,
              color: AppColors.ink600,
              height: 1.45,
            ),
          ),
          const SizedBox(height: 28),
          Expanded(
            child: GridView.count(
              crossAxisCount: 2,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 0.82,
              physics: const NeverScrollableScrollPhysics(),
              children: question.options.map((option) {
                return _QuizOptionCard(
                  label: option,
                  selected: selected == option,
                  onTap: () => onSelect(option),
                );
              }).toList(),
            ),
          ),
        ],
      ),
    );
  }
}

class _QuizOptionCard extends StatelessWidget {
  const _QuizOptionCard({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOut,
          decoration: BoxDecoration(
            color: selected ? AppColors.clay100 : AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: selected ? AppColors.clay500 : AppColors.border,
              width: selected ? 1.5 : 1,
            ),
          ),
          clipBehavior: Clip.antiAlias,
          child: Stack(
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    flex: 11,
                    child: StripeBackground(
                      baseColor:
                          selected ? AppColors.clay100 : AppColors.surface,
                      opacity: 0.4,
                    ),
                  ),
                  Expanded(
                    flex: 9,
                    child: Container(
                      color: selected ? AppColors.clay100 : AppColors.surface,
                      alignment: Alignment.center,
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      child: Text(
                        label,
                        textAlign: TextAlign.center,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: AppTypography.display(
                          fontSize: 15,
                          color: AppColors.ink900,
                          fontWeight: FontWeight.w400,
                          height: 1.2,
                        ).copyWith(fontStyle: FontStyle.italic),
                      ),
                    ),
                  ),
                ],
              ),
              if (selected)
                Positioned(
                  top: 10,
                  right: 10,
                  child: Container(
                    width: 22,
                    height: 22,
                    decoration: const BoxDecoration(
                      color: AppColors.clay500,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.check,
                      size: 14,
                      color: AppColors.surface,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
