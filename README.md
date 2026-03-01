# PixelWatchAppTest (Wear OS 録音アプリ)

PixelWatchなどのWear OSデバイス向けのシンプルな音声録音アプリケーションのプロトタイプです。Jetpack Compose for Wear OSを使用して構築されています。

## 主な機能

- **Wear OS スタンドアロンアプリ**: ペアリングされたスマートフォンなしで単独動作します。
- **録音機能**: Wear OS デバイスのマイクを使用して音声を録音します。
- **マイク権限リクエスト**: アプリ内で録音に必要な権限（`RECORD_AUDIO`）を動的に要求します。
- **シンプルなUI**: 自動的に録音待機中と録音中で画面が切り替わり、ボタンタップのみで録音の開始・停止が可能です。

## 技術スタック

- **言語**: Kotlin
- **UIフレームワーク**: Jetpack Compose for Wear OS
- **音声録音API**: `MediaRecorder` API
- **Minimum SDK**: 30 (Wear OS 3)
- **Target SDK**: 34 (Wear OS 4)

## 録音ファイルの保存場所とフォーマット

録音された音声データは、デバイスの外部キャッシュディレクトリに保存されます。
- **ファイル名**: `test_recording.3gp`
- **保存パス**: `${externalCacheDir?.absolutePath}/test_recording.3gp`
- **出力フォーマット**: 3GPP (`.3gp`)
- **オーディオエンコーダ**: AMR_NB

## アプリのビルド・実行方法

1. Android Studio を使用して本プロジェクトを開きます。
2. Wear OS エミュレータ、または開発者モード・Wi-Fiデバッグを有効にした実機 (Pixel Watch等) をPCに接続します。
3. Android Studio上で「Run (再生ボタン)」をクリックして実行するか、ターミナルで以下のコマンドを実行してビルド・インストールします。

```bash
# Debugビルドの作成とインストール
./gradlew installDebug
```

## 権限について

このアプリを動作させるためには、マイクへのアクセス権限 (`android.permission.RECORD_AUDIO`) が必要です。アプリの初回起動・録音時に権限許可のダイアログが表示されます。

## ライセンス

[LICENSE](./LICENSE) ファイルをご参照ください。
