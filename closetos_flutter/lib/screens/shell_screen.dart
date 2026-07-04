import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../services/auth_service.dart';
import '../services/storage_service.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import 'ingest_screen.dart';
import 'login_screen.dart';
import 'lookbook_screen.dart';
import 'onboarding_screen.dart';
import 'ootd_screen.dart';
import 'travel_screen.dart';
import 'wardrobe_screen.dart';

class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key, required this.storage});

  final StorageService storage;

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> {
  int _index = 0;
  late bool _onboarded;

  @override
  void initState() {
    super.initState();
    _onboarded = widget.storage.hasCompletedOnboarding;
  }

  static const _tabs = [
    (icon: Icons.wb_sunny_outlined, label: 'OOTD'),
    (icon: Icons.add_a_photo_outlined, label: 'Digitize'),
    (icon: Icons.grid_view, label: 'Wardrobe'),
    (icon: Icons.style_outlined, label: 'Lookbook'),
    (icon: Icons.flight_outlined, label: 'Travel'),
  ];

  bool get _isFullBleed => _index == 0 || _index == 1 || _index == 2;

  void _onOnboardingComplete() {
    setState(() => _onboarded = true);
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthService>();

    if (auth.isLoading && !auth.isAuthenticated) {
      return const Scaffold(
        backgroundColor: AppColors.canvas,
        body: Center(child: CircularProgressIndicator(color: AppColors.clay500)),
      );
    }

    if (!auth.isAuthenticated) {
      return const LoginScreen();
    }

    final userOnboarded =
        _onboarded || (auth.currentUser?.onboardingCompleted ?? false);

    if (!userOnboarded) {
      return OnboardingScreen(onComplete: _onOnboardingComplete);
    }

    final repo = context.watch<WardrobeRepository>();
    final counts = repo.categoryCounts;
    final userName = auth.currentUser?.name ?? 'there';

    return Scaffold(
      backgroundColor: AppColors.canvas,
      drawer: Drawer(
        backgroundColor: AppColors.surface,
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'ClosetOS',
                  style: AppTypography.ui(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 3,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Hi, $userName',
                  style: AppTypography.ui(fontSize: 12, color: AppColors.ink400),
                ),
                const SizedBox(height: 28),
                const Divider(color: AppColors.border),
                const SizedBox(height: 16),
                Text(
                  '${repo.totalItems} items',
                  style: AppTypography.display(
                    fontSize: 28,
                    color: AppColors.ink900,
                    fontWeight: FontWeight.w400,
                  ),
                ),
                const SizedBox(height: 8),
                ...counts.entries.map(
                  (e) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Text(
                      '${e.key}: ${e.value}',
                      style: AppTypography.ui(
                        fontSize: 12,
                        color: AppColors.ink400,
                      ),
                    ),
                  ),
                ),
                const Spacer(),
                TextButton.icon(
                  onPressed: () async {
                    Navigator.of(context).pop();
                    await auth.logout();
                  },
                  icon: const Icon(Icons.logout, size: 18, color: AppColors.ink600),
                  label: Text(
                    'Sign out',
                    style: AppTypography.ui(
                      fontSize: 13,
                      color: AppColors.ink600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      appBar: _isFullBleed
          ? null
          : AppBar(
              title: const Text('ClosetOS'),
              leading: Builder(
                builder: (ctx) => IconButton(
                  icon: const Icon(Icons.menu, size: 20),
                  onPressed: () => Scaffold.of(ctx).openDrawer(),
                ),
              ),
            ),
      body: SafeArea(
        bottom: false,
        child: IndexedStack(
          index: _index,
          children: [
            const OotdScreen(),
            IngestScreen(
              onReviewComplete: () => setState(() => _index = 2),
            ),
            WardrobeScreen(onAddGarment: () => setState(() => _index = 1)),
            const LookbookScreen(),
            const TravelScreen(),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: _tabs
            .map(
              (t) => NavigationDestination(
                icon: Icon(t.icon),
                label: t.label,
              ),
            )
            .toList(),
      ),
    );
  }
}
