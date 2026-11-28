import 'dart:io';

import 'package:petstore_client/petstore_client.dart';

Future<void> main() async {
  final host = Platform.environment['SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL'];
  if (host == null) {
    throw StateError('SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL is not set');
  }
  final token = Platform.environment['SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN'];
  if (token == null) {
    throw StateError('SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN is not set');
  }
  final client = Client.withToken(host: host, accessToken: token);
  try {
    final result = await client.store.getDefaults(null);
    print(result);
  } finally {
    client.close();
  }
}
