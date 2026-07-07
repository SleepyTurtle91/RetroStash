# RetroStash

RetroStash is an Android application designed for retro gaming enthusiasts. It allows users to browse archive collections (specifically from Archive.org), intelligently filter them using AI, and download ROMs directly to their device (e.g., SD card).

## What's New (v1.1)

- **Gemini 2.5 Flash Integration**: Upgraded to the latest AI model for faster and more accurate filtering.
- **Robust AI Filtering**: Improved error handling to distinguish between network failures and "no matches" states. Existing search results are now preserved during and after filtering failures.
- **Optimized Search Performance**: Rewritten query engine to use optimized Lucene trailing wildcards, significantly reducing timeouts for complex searches (like "3DS pokemon xyz").
- **Enhanced UI Stability**: Added background threading for AI processing to ensure zero frame drops during heavy data sorting.
- **Improved Network Resilience**: Increased request timeouts to 30 seconds for better reliability with large archive datasets.

## Features

- **Collection Browsing**: Search for game collections by platform or identifier using optimized Lucene queries.
- **AI-Powered Filtering**: Use natural language prompts to filter through large lists of files (e.g., "Only RPGs", "Exclude duplicates").
- **Non-Destructive UI**: AI filtering acts as a "smart layer"—if the AI fails or finds nothing, your original search results remain untouched.
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

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Networking**: Retrofit, OkHttp (30s timeouts), Ktor (for Gemini SDK)
- **AI Engine**: Google Generative AI (**Gemini 2.5 Flash**)
- **Background Tasks**: WorkManager for downloads, `Dispatchers.Default` for AI data processing.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
