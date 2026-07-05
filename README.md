# RetroStash

RetroStash is an Android application designed for retro gaming enthusiasts. It allows users to browse archive collections (specifically from Archive.org), intelligently filter them using AI, and download ROMs directly to their device (e.g., SD card).

## Features

- **Collection Browsing**: Search for game collections by platform or identifier.
- **AI-Powered Filtering**: Use natural language prompts (powered by Gemini 1.5 Flash) to filter through large lists of files (e.g., "Only RPGs", "Exclude duplicates").
- **Real-time Download Monitoring**: A dedicated Downloads screen to track active ROM downloads and extraction progress with live progress bars.
- **Automatic Extraction**: Downloaded ZIP/RAR/7z archives are automatically unzipped to your RetroStash folder.
- **Clean UI**: Built with Jetpack Compose for a modern and responsive user experience.

## Getting Started

### Prerequisites

- Android device or emulator running Android 8.0 (API level 26) or higher.
- A Gemini API key (you can get one for free from [Google AI Studio](https://aistudio.google.com/)).

### Configuration

1. Launch the app.
2. Tap the **Settings** (gear) icon in the top right.
3. Enter your **Gemini API Key** and tap **Save API Key**.
4. Tap the **Folder** icon in the top right to select your destination folder (e.g., a folder on your SD card for ROMs).

## Troubleshooting

### AI Features Crashing
If you encounter crashes when using the AI filter, ensure you are using the latest version of the app. We recently resolved a `NoClassDefFoundError` related to missing Ktor dependencies which are required by the Google Generative AI SDK.

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Networking**: Retrofit, OkHttp, Ktor (for Gemini SDK)
- **AI Engine**: Google Generative AI (Gemini 1.5 Flash)
- **Background Tasks**: WorkManager (for downloading and processing)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details (if applicable).
