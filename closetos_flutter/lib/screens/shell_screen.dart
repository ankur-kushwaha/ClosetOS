import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../services/storage_service.dart';
import '../services/wardrobe_repository.dart';
import '../theme/app_theme.dart';
import 'ingest_screen.dart';
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

  @override
  Widget build(BuildContext context) {
    if (!_onboarded) {
      return OnboardingScreen(
        onComplete: () => setState(() => _onboarded = true),
      );
    }

    final repo = context.watch<WardrobeRepository>();
    final counts = repo.categoryCounts;

    return Scaffold(
      drawer: Drawer(
        backgroundColor: AppColors.black,
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'CLOSETOS',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 4,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Smart Wardrobe',
                  style: TextStyle(color: AppColors.gray400, fontSize: 12),
                ),
                const SizedBox(height: 28),
                const Divider(),
                const SizedBox(height: 16),
                Text(
                  '${repo.totalItems} items',
                  style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w300),
                ),
                const SizedBox(height: 8),
                ...counts.entries.map(
                  (e) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Text(
                      '${e.key}: ${e.value}',
                      style: const TextStyle(
                        color: AppColors.gray400,
                        fontSize: 12,
                      ),
                    ),
                  ),
                ),
                const Spacer(),
                Text(
                  'Backend: connected',
                  style: TextStyle(
                    color: AppColors.gray600,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      appBar: AppBar(
        title: const Text('CLOSETOS'),
        leading: Builder(
          builder: (ctx) => IconButton(
            icon: const Icon(Icons.menu, size: 20),
            onPressed: () => Scaffold.of(ctx).openDrawer(),
          ),
        ),
      ),
      body: IndexedStack(
        index: _index,
        children: const [
          OotdScreen(),
          IngestScreen(),
          WardrobeScreen(),
          LookbookScreen(),
          TravelScreen(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        height: 64,
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
