import 'dart:convert';

import 'package:http/http.dart' as http;

class WeatherInfo {
  WeatherInfo({
    required this.tempCelsius,
    required this.description,
    this.locationName = '',
  });

  final double tempCelsius;
  final String description;
  final String locationName;

  double get tempFahrenheit => tempCelsius * 9 / 5 + 32;
}

class WeatherService {
  static Future<WeatherInfo> fetch({double? lat, double? lon}) async {
    try {
      double latitude = lat ?? 28.6139;
      double longitude = lon ?? 77.2090;

      final url = Uri.parse(
        'https://api.open-meteo.com/v1/forecast'
        '?latitude=$latitude&longitude=$longitude'
        '&current=temperature_2m,weather_code'
        '&timezone=auto',
      );
      final res = await http.get(url).timeout(const Duration(seconds: 8));
      if (res.statusCode != 200) return _fallback();

      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final current = data['current'] as Map<String, dynamic>;
      final code = current['weather_code'] as int? ?? 0;
      return WeatherInfo(
        tempCelsius: (current['temperature_2m'] as num).toDouble(),
        description: _weatherLabel(code),
        locationName: '',
      );
    } catch (_) {
      return _fallback();
    }
  }

  static WeatherInfo _fallback() => WeatherInfo(
        tempCelsius: 23,
        description: 'Clear',
        locationName: '',
      );

  static String _weatherLabel(int code) {
    if (code == 0) return 'Clear';
    if (code <= 3) return 'Partly cloudy';
    if (code <= 48) return 'Foggy';
    if (code <= 67) return 'Rain';
    if (code <= 77) return 'Snow';
    if (code <= 82) return 'Showers';
    return 'Stormy';
  }
}
