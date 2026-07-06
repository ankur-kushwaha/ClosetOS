import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
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
  final _scaffoldKey = GlobalKey<ScaffoldState>();

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



    return Scaffold(
      key: _scaffoldKey,
      backgroundColor: AppColors.canvas,
      drawer: const ProfileDrawer(),
      appBar: _isFullBleed
          ? null
          : AppBar(
              title: const Text('ClosetOS'),
              leading: IconButton(
                icon: const Icon(Icons.menu, size: 20),
                onPressed: () => _scaffoldKey.currentState?.openDrawer(),
              ),
            ),
      body: SafeArea(
        bottom: false,
        child: IndexedStack(
          index: _index,
          children: [
            OotdScreen(onOpenDrawer: () => _scaffoldKey.currentState?.openDrawer()),
            IngestScreen(
              onReviewComplete: () => setState(() => _index = 2),
              onOpenDrawer: () => _scaffoldKey.currentState?.openDrawer(),
            ),
            WardrobeScreen(
              onAddGarment: () => setState(() => _index = 1),
              onOpenDrawer: () => _scaffoldKey.currentState?.openDrawer(),
            ),
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

class ProfileDrawer extends StatefulWidget {
  const ProfileDrawer({super.key});

  @override
  State<ProfileDrawer> createState() => _ProfileDrawerState();
}

class _ProfileDrawerState extends State<ProfileDrawer> {
  late TextEditingController _nameController;
  late TextEditingController _emailController;

  String? _preferredStyle;
  String? _preferredFit;
  String? _occasion;
  String? _colorPreference;
  String? _morningMood;

  bool _isSaving = false;
  bool _isPickingSelfie = false;

  final List<String> _styles = [
    'Soft & romantic',
    'Clean & minimal',
    'Bold & graphic',
    'Relaxed & lived-in',
  ];

  final List<String> _fits = [
    'Slim & tailored',
    'Classic & balanced',
    'Relaxed & easy',
    'Oversized & draped',
  ];

  final List<String> _occasions = [
    'Office & meetings',
    'Creative & fluid',
    'Weekends & errands',
    'Everywhere equally',
  ];

  final List<String> _colorOptions = [
    'Neutrals mostly',
    'Full spectrum',
  ];

  final List<String> _mornings = [
    'Effortless',
    'Intentional',
    'Expressive',
    'Polished',
  ];

  @override
  void initState() {
    super.initState();
    final auth = context.read<AuthService>();
    final repo = context.read<WardrobeRepository>();

    _nameController = TextEditingController(text: auth.currentUser?.name ?? '');
    _emailController = TextEditingController(text: auth.currentUser?.email ?? '');

    final taste = repo.taste;
    if (taste.preferredStyles.isNotEmpty) {
      _preferredStyle = taste.preferredStyles.first;
      if (taste.preferredStyles.length > 1) {
        _morningMood = taste.preferredStyles[1];
      }
    }
    if (taste.preferredFits.isNotEmpty) {
      _preferredFit = taste.preferredFits.first;
    }
    if (taste.occasions.isNotEmpty) {
      _occasion = taste.occasions.first;
    }
    if (taste.colorsAvoided.contains('Bright patterns')) {
      _colorPreference = 'Neutrals mostly';
    } else {
      _colorPreference = 'Full spectrum';
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _pickSelfie(ImageSource source) async {
    setState(() => _isPickingSelfie = true);
    try {
      final picker = ImagePicker();
      final picked = await picker.pickImage(
        source: source,
        imageQuality: 80,
      );
      if (picked != null) {
        final bytes = await picked.readAsBytes();
        final repo = context.read<WardrobeRepository>();
        final success = await repo.updateSelfie(bytes);
        if (success) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Selfie updated successfully!')),
            );
          }
        } else {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(repo.lastError ?? 'Failed to upload selfie')),
            );
          }
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error picking image: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _isPickingSelfie = false);
    }
  }

  Future<void> _saveProfile() async {
    setState(() => _isSaving = true);
    try {
      final auth = context.read<AuthService>();
      final repo = context.read<WardrobeRepository>();

      final newTaste = UserTaste(
        preferredStyles: [
          if (_preferredStyle != null) _preferredStyle!,
          if (_morningMood != null) _morningMood!,
        ],
        preferredFits: [
          if (_preferredFit != null) _preferredFit!,
        ],
        occasions: [
          if (_occasion != null) _occasion!,
        ],
        colorsAvoided: [
          if (_colorPreference == 'Neutrals mostly') 'Bright patterns',
        ],
        selfie: repo.taste.selfie,
      );

      final success = await auth.updateProfile(
        taste: newTaste,
        name: _nameController.text.trim(),
        email: _emailController.text.trim(),
      );

      if (success) {
        repo.taste = newTaste;
        await repo.syncWithCloud();

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Profile saved successfully!')),
          );
          Navigator.of(context).pop();
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(auth.lastError ?? 'Failed to update profile')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error saving profile: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  void _showImageSourceSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.camera_alt_outlined, color: AppColors.ink900),
              title: Text('Take Photo', style: AppTypography.ui(fontSize: 15)),
              onTap: () {
                Navigator.of(context).pop();
                _pickSelfie(ImageSource.camera);
              },
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined, color: AppColors.ink900),
              title: Text('Choose from Gallery', style: AppTypography.ui(fontSize: 15)),
              onTap: () {
                Navigator.of(context).pop();
                _pickSelfie(ImageSource.gallery);
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(top: 24, bottom: 8),
      child: Text(
        title,
        style: AppTypography.ui(
          fontSize: 12,
          fontWeight: FontWeight.w700,
          color: AppColors.ink900,
          letterSpacing: 1.5,
        ),
      ),
    );
  }

  Widget _buildChipSelector<T>({
    required List<T> options,
    required T? selected,
    required ValueChanged<T> onSelected,
    required String Function(T) labelBuilder,
  }) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: options.map((option) {
        final isSelected = option == selected;
        return ChoiceChip(
          label: Text(
            labelBuilder(option),
            style: AppTypography.ui(
              fontSize: 12,
              color: isSelected ? AppColors.surface : AppColors.ink900,
            ),
          ),
          selected: isSelected,
          onSelected: (_) => onSelected(option),
          selectedColor: AppColors.clay500,
          backgroundColor: AppColors.surface,
          checkmarkColor: AppColors.surface,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: BorderSide(
              color: isSelected ? AppColors.clay500 : AppColors.border,
            ),
          ),
        );
      }).toList(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final repo = context.watch<WardrobeRepository>();
    final auth = context.watch<AuthService>();

    return Drawer(
      backgroundColor: AppColors.canvas,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 20, 24, 8),
              child: Row(
                children: [
                  Text(
                    'Profile & Settings',
                    style: AppTypography.display(
                      fontSize: 22,
                      fontWeight: FontWeight.w500,
                      color: AppColors.ink900,
                    ),
                  ),
                  const Spacer(),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
            ),
            const Divider(color: AppColors.border),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Selfie / Digital Twin Section
                    Center(
                      child: Stack(
                        children: [
                          Container(
                            width: 100,
                            height: 100,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: AppColors.surface,
                              border: Border.all(color: AppColors.border, width: 2),
                              image: repo.digitalTwinPath != null
                                  ? DecorationImage(
                                      image: FileImage(File(repo.digitalTwinPath!)),
                                      fit: BoxFit.cover,
                                    )
                                  : null,
                            ),
                            child: repo.digitalTwinPath == null
                                ? const Icon(
                                    Icons.person,
                                    size: 50,
                                    color: AppColors.ink400,
                                  )
                                : null,
                          ),
                          if (_isPickingSelfie)
                            Positioned.fill(
                              child: Container(
                                decoration: const BoxDecoration(
                                  color: Colors.black26,
                                  shape: BoxShape.circle,
                                ),
                                child: const Center(
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: AppColors.surface,
                                  ),
                                ),
                              ),
                            ),
                          Positioned(
                            bottom: 0,
                            right: 0,
                            child: Material(
                              color: AppColors.clay500,
                              shape: const CircleBorder(),
                              elevation: 2,
                              child: InkWell(
                                onTap: _showImageSourceSheet,
                                customBorder: const CircleBorder(),
                                child: const Padding(
                                  padding: EdgeInsets.all(8),
                                  child: Icon(
                                    Icons.camera_alt,
                                    size: 16,
                                    color: AppColors.surface,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),

                    // Profile editing details
                    TextField(
                      controller: _nameController,
                      decoration: InputDecoration(
                        labelText: 'Name',
                        labelStyle: AppTypography.ui(fontSize: 13, color: AppColors.ink600),
                        floatingLabelBehavior: FloatingLabelBehavior.always,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: AppColors.border),
                        ),
                      ),
                      style: AppTypography.ui(fontSize: 14),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _emailController,
                      keyboardType: TextInputType.emailAddress,
                      decoration: InputDecoration(
                        labelText: 'Email',
                        labelStyle: AppTypography.ui(fontSize: 13, color: AppColors.ink600),
                        floatingLabelBehavior: FloatingLabelBehavior.always,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: const BorderSide(color: AppColors.border),
                        ),
                      ),
                      style: AppTypography.ui(fontSize: 14),
                    ),

                    // Preferences Selection Section
                    _buildSectionTitle('PREFERRED STYLE'),
                    _buildChipSelector<String>(
                      options: _styles,
                      selected: _preferredStyle,
                      onSelected: (val) => setState(() => _preferredStyle = val),
                      labelBuilder: (val) => val,
                    ),

                    _buildSectionTitle('PREFERRED FIT'),
                    _buildChipSelector<String>(
                      options: _fits,
                      selected: _preferredFit,
                      onSelected: (val) => setState(() => _preferredFit = val),
                      labelBuilder: (val) => val,
                    ),

                    _buildSectionTitle('OCCASION'),
                    _buildChipSelector<String>(
                      options: _occasions,
                      selected: _occasion,
                      onSelected: (val) => setState(() => _occasion = val),
                      labelBuilder: (val) => val,
                    ),

                    _buildSectionTitle('COLORS'),
                    _buildChipSelector<String>(
                      options: _colorOptions,
                      selected: _colorPreference,
                      onSelected: (val) => setState(() => _colorPreference = val),
                      labelBuilder: (val) => val,
                    ),

                    _buildSectionTitle('MORNING MOOD'),
                    _buildChipSelector<String>(
                      options: _mornings,
                      selected: _morningMood,
                      onSelected: (val) => setState(() => _morningMood = val),
                      labelBuilder: (val) => val,
                    ),

                    const SizedBox(height: 40),
                  ],
                ),
              ),
            ),
            const Divider(color: AppColors.border),
            Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Material(
                    color: AppColors.clay500,
                    borderRadius: BorderRadius.circular(28),
                    child: InkWell(
                      onTap: _isSaving ? null : _saveProfile,
                      borderRadius: BorderRadius.circular(28),
                      child: SizedBox(
                        height: 52,
                        child: Center(
                          child: _isSaving
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: AppColors.surface,
                                  ),
                                )
                              : Text(
                                  'Save Profile',
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
                  const SizedBox(height: 12),
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
          ],
        ),
      ),
    );
  }
}
