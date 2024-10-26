import 'dart:async';

import 'package:flutter/services.dart';

class ImageGallerySaver {
  static const MethodChannel _channel =
      const MethodChannel('image_gallery_saver');

  /// Saves an image represented by `imageBytes` to the device's image gallery.
  ///
  /// * `imageBytes`: The image data as a `Uint8List`. This parameter is required.
  /// * `quality`:  An optional integer value between 0 and 100 (inclusive)
  ///   representing the compression quality. Defaults to 80.
  /// * `name`: An optional file name for the saved image.
  /// * `isReturnImagePathOfIOS`: An iOS-specific flag. If `true`, the method
  ///   will return the file path of the saved image on iOS devices.
  ///
  /// Returns a `Future` that resolves to a `Map<String, dynamic>`:
  ///   * `isSuccess`: `true` if the image was saved successfully, `false` otherwise.
  ///   * `filePath`: (Optional) The path to the saved image on the device.
  ///     This is only returned if `isReturnImagePathOfIOS` is set to `true` on iOS.
  static FutureOr<dynamic> saveImage(Uint8List imageBytes,
      {int quality = 80,
      String? name,
      bool isReturnImagePathOfIOS = false}) async {
    final result =
        await _channel.invokeMethod('saveImageToGallery', <String, dynamic>{
      'imageBytes': imageBytes,
      'quality': quality,
      'name': name,
      'isReturnImagePathOfIOS': isReturnImagePathOfIOS
    });
    return result;
  }

  /// Saves the file located at [file] to the device's media gallery.
  ///
  /// * `file`: The path to the file to be saved.
  /// * `name`: An optional file name for the saved file.
  /// * `isReturnPathOfIOS`: An iOS-specific flag. If `true`, the method
  ///   will return the file path of the saved file on iOS devices.
  ///
  /// Returns a `Future`. The exact return value depends on the platform and
  /// the `isReturnPathOfIOS` flag. Consult platform-specific documentation for details.
  static Future<dynamic> saveFile(String file,
      {String? name, bool isReturnPathOfIOS = false}) async {
    final result = await _channel.invokeMethod(
        'saveFileToGallery', <String, dynamic>{
      'file': file,
      'name': name,
      'isReturnPathOfIOS': isReturnPathOfIOS
    });
    return result;
  }
}
