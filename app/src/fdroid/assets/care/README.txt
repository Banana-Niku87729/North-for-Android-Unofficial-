F-Droid版では、端末内判定モデルを外部から取得しません。
ここ（src/fdroid/assets/care/）に nsfw.tflite を置くとAPKに同梱され、初回判定時に端末内へ展開されます。

  ファイル名 : nsfw.tflite
  SHA-256    : a476ef325f4f776602d944fd28133e945f77a74e37dafba37936110b35b7810d
  入手元     : https://github.com/hoyaaaa/nsfw_detector_flutter (rev 77f6c72397113c7facb7ba4783ccfe70caec7c81) の assets/nsfw.tflite
  ライセンス : src/main/assets/care/LICENSE-model.txt / LICENSE-flutter.txt

ファイルが無い場合、配慮モードの端末内判定は「判定不能」となり、画像は非表示のままになります。
