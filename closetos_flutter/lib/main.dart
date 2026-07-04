import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'screens/shell_screen.dart';
import 'services/api_service.dart';
import 'services/storage_service.dart';
import 'services/wardrobe_repository.dart';
import 'theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final storage = StorageService();
  await storage.init();

  final api = ApiService();
  final repo = WardrobeRepository(storage: storage, api: api);
  await repo.init();

  runApp(ClosetOSApp(storage: storage, repository: repo, api: api));
}

class ClosetOSApp extends StatelessWidget {
  const ClosetOSApp({
    super.key,
    required this.storage,
    required this.repository,
    required this.api,
  });

  final StorageService storage;
  final WardrobeRepository repository;
  final ApiService api;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: repository),
        Provider.value(value: api),
        Provider.value(value: storage),
      ],
      child: MaterialApp(
        title: 'ClosetOS',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark,
        home: ShellScreen(storage: storage),
      ),
    );
  }
}
