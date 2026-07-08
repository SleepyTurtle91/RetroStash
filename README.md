# RetroStash

RetroStash is an Android application designed for retro gaming enthusiasts and archivists. It allows users to browse Archive.org collections, intelligently filter them using AI, and download files directly to their device with robust handling for handheld consoles.

## What's New (v1.2)

- **Gemini 3.1 Flash-Lite Integration**: Upgraded to the latest stable AI model for state-of-the-art filtering and metadata analysis.
- **Multi-Category Search**: Expanded beyond software to support **Roms, PC Games, Books, Movies, Audio, Images, and Data** with specialized Lucene query builders.
- **Landscape & Handheld Optimization**: Optimized UI for 16:9 screens like the **Anbernic RG556/RG557**. Features a 2-column grid layout in landscape mode to maximize screen real estate.
- **Reliable Downloader**: Implemented a "Move Fallback"—files that aren't Zips or fail extraction are moved safely to the destination instead of being deleted. **No more missing files.**
- **Smart Queue Management**: Active monitoring of the download queue; if a download fails, the app automatically unblocks and starts the next task.
- **AI-Powered Metadata & Box Art**: Enhanced scraper that targets the `RetroStash` subfolder and uses AI to identify systems and clean titles for Libretro compatibility.
- **Downloads Maintenance**: Added a "Clear Finished" feature to easily prune completed and failed tasks from the downloads list.

## Features

- **Collection Browsing**: Search for everything from Roms to E-books using a new category-aware search engine.
- **AI-Powered Filtering**: Use natural language prompts to filter and **auto-sort** large lists (e.g., "Only RPGs", "USA region only").
- **Handheld Ready**: Fully responsive design that thrives on both portrait phones and landscape portable consoles.
- **Real-time Download Monitoring**: Track active downloads and extraction progress with detailed status updates and progress bars.
- **Automatic Extraction & Safe Moves**: ZIP archives are unzipped, while other formats are safely moved to your library.
- **Metadata Sync**: Generates `gameList.xml` and downloads Named Boxarts for your local collection using Libretro conventions.

## Getting Started

### Prerequisites

- Android device or emulator running Android 8.0 (API level 26) or higher.
- A Gemini API key (you can get one for free from [Google AI Studio](https://aistudio.google.com/)).

### Configuration

1. Launch the app.
2. Tap the **Settings** (gear) icon in the top right.
3. Enter your **Gemini API Key**.
4. Tap the **Folder** icon in the top right to select your destination folder (e.g., a folder on your SD card for ROMs). RetroStash will create a `RetroStash` subfolder for your files.

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (with adaptive landscape layouts)
- **Networking**: Retrofit, OkHttp, Ktor
- **AI Engine**: Google Generative AI (**Gemini 3.1 Flash-Lite**)
- **Background Tasks**: WorkManager for unzipping, `DownloadManager` for robust background downloading.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
