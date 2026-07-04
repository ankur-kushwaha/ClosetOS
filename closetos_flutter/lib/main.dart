import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'screens/shell_screen.dart';
import 'services/api_service.dart';
import 'services/auth_service.dart';
import 'services/storage_service.dart';
import 'services/wardrobe_repository.dart';
import 'theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final storage = StorageService();
  await storage.init();

  final api = ApiService();
  if (storage.authToken != null) {
    api.setAuthToken(storage.authToken);
  }

  final auth = AuthService(storage: storage, api: api);
  await auth.init();

  final repo = WardrobeRepository(storage: storage, api: api);
  await repo.init();

  runApp(ClosetOSApp(
    storage: storage,
    repository: repo,
    api: api,
    auth: auth,
  ));
}

class ClosetOSApp extends StatelessWidget {
  const ClosetOSApp({
    super.key,
    required this.storage,
    required this.repository,
    required this.api,
    required this.auth,
  });

  final StorageService storage;
  final WardrobeRepository repository;
  final ApiService api;
  final AuthService auth;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: repository),
        ChangeNotifierProvider.value(value: auth),
        Provider.value(value: api),
        Provider.value(value: storage),
      ],
      child: MaterialApp(
        title: 'ClosetOS',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.light,
        home: ShellScreen(storage: storage),
      ),
    );
  }
}
