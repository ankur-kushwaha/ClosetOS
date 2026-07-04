import 'dart:convert';

import 'package:http/http.dart' as http;

class WeatherInfo {
  WeatherInfo({
    required this.tempCelsius,
    required this.description,
    this.highCelsius,
    this.lowCelsius,
    this.locationName = '',
  });

  final double tempCelsius;
  final String description;
  final double? highCelsius;
  final double? lowCelsius;
  final String locationName;

  double get tempFahrenheit => tempCelsius * 9 / 5 + 32;

  double get highFahrenheit =>
      (highCelsius ?? tempCelsius) * 9 / 5 + 32;

  double get lowFahrenheit =>
      (lowCelsius ?? tempCelsius - 5) * 9 / 5 + 32;
}

class WeatherService {
  static Future<WeatherInfo> fetch({double? lat, double? lon}) async {
    try {
      final latitude = lat ?? 28.6139;
      final longitude = lon ?? 77.2090;

      final url = Uri.parse(
        'https://api.open-meteo.com/v1/forecast'
        '?latitude=$latitude&longitude=$longitude'
        '&current=temperature_2m,weather_code'
        '&daily=temperature_2m_max,temperature_2m_min'
        '&timezone=auto'
        '&forecast_days=1',
      );
      final res = await http.get(url).timeout(const Duration(seconds: 8));
      if (res.statusCode != 200) return _fallback();

      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final current = data['current'] as Map<String, dynamic>;
      final daily = data['daily'] as Map<String, dynamic>?;
      final code = current['weather_code'] as int? ?? 0;

      double? high;
      double? low;
      if (daily != null) {
        final highs = daily['temperature_2m_max'] as List<dynamic>?;
        final lows = daily['temperature_2m_min'] as List<dynamic>?;
        if (highs != null && highs.isNotEmpty) {
          high = (highs.first as num).toDouble();
        }
        if (lows != null && lows.isNotEmpty) {
          low = (lows.first as num).toDouble();
        }
      }

      return WeatherInfo(
        tempCelsius: (current['temperature_2m'] as num).toDouble(),
        description: _weatherLabel(code),
        highCelsius: high,
        lowCelsius: low,
        locationName: '',
      );
    } catch (_) {
      return _fallback();
    }
  }

  static WeatherInfo _fallback() => WeatherInfo(
        tempCelsius: 16,
        highCelsius: 18,
        lowCelsius: 9,
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
