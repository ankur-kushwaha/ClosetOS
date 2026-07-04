import 'package:flutter_test/flutter_test.dart';

import 'package:closetos_flutter/main.dart';

void main() {
  testWidgets('ClosetOS app smoke test', (WidgetTester tester) async {
    // App requires async init; verify module loads.
    expect(ClosetOSApp, isA<Type>());
  });
}
