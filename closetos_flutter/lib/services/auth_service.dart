import 'package:flutter/foundation.dart';

import '../models/models.dart';
import 'api_service.dart';
import 'storage_service.dart';

class AuthService extends ChangeNotifier {
  AuthService({
    required StorageService storage,
    required ApiService api,
    this.onAuthenticated,
  })  : _storage = storage,
        _api = api;

  final StorageService _storage;
  final ApiService _api;
  final Future<void> Function()? onAuthenticated;

  AppUser? currentUser;
  bool isLoading = false;
  String? lastError;

  bool get isAuthenticated => currentUser != null && _storage.authToken != null;

  Future<void> init() async {
    final token = _storage.authToken;
    if (token == null) return;

    _api.setAuthToken(token);
    isLoading = true;
    notifyListeners();

    final user = await _api.fetchCurrentUser();
    if (user != null) {
      currentUser = user;
      if (user.onboardingCompleted) {
        await _storage.setOnboardingComplete();
      }
      await onAuthenticated?.call();
    } else {
      await _storage.clearAuth();
      _api.setAuthToken(null);
    }

    isLoading = false;
    notifyListeners();
  }

  Future<bool> signup({
    required String name,
    required String email,
    required String password,
  }) async {
    lastError = null;
    isLoading = true;
    notifyListeners();

    final result = await _api.signup(
      name: name,
      email: email,
      password: password,
    );

    isLoading = false;
    if (result == null) {
      lastError = _api.lastError ?? 'Sign up failed';
      notifyListeners();
      return false;
    }

    await _persistSession(result.token, result.user);
    notifyListeners();
    return true;
  }

  Future<bool> login({
    required String email,
    required String password,
  }) async {
    lastError = null;
    isLoading = true;
    notifyListeners();

    final result = await _api.login(email: email, password: password);

    isLoading = false;
    if (result == null) {
      lastError = _api.lastError ?? 'Login failed';
      notifyListeners();
      return false;
    }

    await _persistSession(result.token, result.user);
    notifyListeners();
    return true;
  }

  Future<void> logout() async {
    currentUser = null;
    await _storage.clearAuth();
    _api.setAuthToken(null);
    notifyListeners();
  }

  Future<bool> syncOnboarding(UserTaste taste) async {
    final user = await _api.updateOnboarding(taste);
    if (user == null) {
      lastError = _api.lastError;
      return false;
    }
    currentUser = user;
    notifyListeners();
    return true;
  }

  Future<void> _persistSession(String token, AppUser user) async {
    await _storage.setAuthToken(token);
    await _storage.setUserId(user.userId);
    await _storage.setUserEmail(user.email);
    await _storage.setUserName(user.name);
    if (user.onboardingCompleted) {
      await _storage.setOnboardingComplete();
    }
    _api.setAuthToken(token);
    currentUser = user;
    await onAuthenticated?.call();
  }
}