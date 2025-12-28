# DeepVoiceChat

A voice-first AI chat app for Android. Talk to GPT-5, Claude, and Gemini using your voice.

## Features

- 🎤 **Voice input** - Tap to speak, continuous listening
- 🔊 **Voice output** - AI responses read aloud (interruptible)
- 🤖 **Multi-provider** - OpenAI, Anthropic, Gemini
- 📱 **Simple UI** - One screen, minimal setup

## Screenshots

<!-- Add screenshots here -->

## Setup

### 1. Deploy the Proxy

This app requires a proxy server to handle API calls. See [deepvoice-proxy](https://github.com/ulapopov/deepvoice-proxy) for setup instructions.

### 2. Build the App

1. Clone this repo
2. Open in Android Studio
3. Update `baseUrl` in `MainViewModel.kt` with your proxy URL
4. Build and run on your device

### 3. Configure

- Select provider (OpenAI, Anthropic, Gemini)
- Select model
- Tap the mic button to start talking!

## Architecture

```
[Android App] → [Your Proxy] → [OpenAI/Anthropic/Gemini APIs]
```

The proxy handles API key management so they're not stored on-device.

## Requirements

- Android 8.0+ (API 26)
- Microphone permission
- Internet connection
- Deployed proxy with API keys

## License

MIT
