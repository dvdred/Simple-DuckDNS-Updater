# Simple-DuckDNS-Updater Android App

![Logo](https://github.com/dvdred/Simple-DuckDNS-Updater/raw/refs/heads/main/SimpleDuckDNS-Updater.png)

This Android application allows you to update your DuckDNS domain records programmatically

![Configuration](https://github.com/dvdred/Simple-DuckDNS-Updater/raw/refs/heads/main/demo01.png)
![ManualUpdate](https://github.com/dvdred/Simple-DuckDNS-Updater/raw/refs/heads/main/demo02.png)
![AutoUpdate](https://github.com/dvdred/Simple-DuckDNS-Updater/raw/refs/heads/main/demo03.png)

---

## Features

- Manual update of DuckDNS records
- Scheduled updates with customizable intervals (from 1 minute to unlimited)
- **Smart DNS Check**: Intelligent pre-update verification to prevent unnecessary API calls
  - Queries 3 DNS servers (Cloudflare, Google, OpenDNS) before each update
  - Auto-skips update if DNS records are already up to date
  - Reduces server load and keeps logs clean
- Input fields for domains, token, and optional IP address
- Logging of all update activities with HTTP response details
- Background execution for scheduled updates using WorkManager
- Automatic APK building via GitHub Actions
- Improved error handling for HTTP requests
- Configuration persistence across app restarts
- Auto-detection of public IP address when no IP is specified
- Responsive UI with collapsible configuration section
- Real-time log viewing and clearing functionality
- Survives app restarts and device reboots

## How to Use

### Manual Update
1. Enter your DuckDNS domain(s) (comma separated)
2. Enter your DuckDNS token
3. Optionally enter an IP address (leave blank to auto-detect)
4. Tap "Update Now" to perform an immediate update

> **Note**: for Ip AutoDetection, DuckDNS will automatically detect your public IP address if no IP is specified.

### Scheduled Updates
1. Set the interval in minutes for how often updates should occur (minimum 1 minute)
2. Tap "Start AutoUpdate" to start the scheduled updates
3. Tap "Stop AutoUpdate" to cancel the scheduled updates

> **Note**: Unlike standard WorkManager periodic tasks (which have a 15-minute minimum), this app supports intervals as low as 1 minute using a self-rescheduling OneTimeWorkRequest pattern.

## Implementation Details

The app works by:
1. **Smart DNS Check** (v0.4.0+): Before updating, queries 3 DNS servers to verify if update is needed
2. Constructing a URL in the format: `https://www.duckdns.org/update?domains={domains}&token={token}&ip={ip}`
3. Performing real HTTP requests to the DuckDNS API using OkHttp (singleton client with connection pooling)
4. Logging all activities to a local file including HTTP status codes and response bodies

### Smart DNS Check System
- Queries Cloudflare (1.1.1.1), Google (8.8.8.8), and OpenDNS (208.67.222.222)
- Compares results with current/configured IP address
- Skips update if 2 or more DNS servers already have correct IP
- Reduces unnecessary API calls and improves efficiency
- Fast timeouts (2 seconds) to avoid delays

For detailed documentation, see:
- **[Smart DNS Check README](SMART_DNS_CHECK_README.md)** - User guide and how it works
- **[DNS Check Implementation](DNS_CHECK_IMPLEMENTATION.md)** - Technical documentation
- **[Testing Guide](DNS_CHECK_TEST_GUIDE.md)** - Complete testing procedures
- **[Documentation Index](DNS_CHECK_INDEX.md)** - Navigate all documentation
4. Using Android's WorkManager with self-rescheduling OneTimeWorkRequest for flexible intervals
5. Storing configuration securely using encrypted SharedPreferences with Android Keystore
6. Supporting both manual and automatic update modes

### WorkManager Implementation

This app uses a custom implementation to support intervals under 15 minutes:

- **Self-rescheduling pattern**: Each worker execution schedules the next one upon completion
- **Flexible intervals**: Supports any interval from 1 minute to unlimited
- **Battery efficient**: Workers are released between executions (no Thread.sleep())
- **Survives restarts**: WorkManager automatically persists and restores the work chain
- **Robust error handling**: Reschedules even on failure to ensure continuous operation

## Permissions Required

- `INTERNET` - For making HTTP requests to DuckDNS API
- `WAKE_LOCK` - To ensure updates happen even when screen is off
- `RECEIVE_BOOT_COMPLETED` - To restart scheduled updates after device reboot

## Building the App

To build this application:
1. Ensure you have Android Studio installed
2. Import the project into Android Studio
3. Build and run the application
4. The app will automatically include the required dependencies

## GitHub Actions

This project includes a GitHub Actions workflow that automatically builds APKs:
- **Debug APK**: Built on every push
- **Release APK**: Built for releases

The APKs are automatically uploaded as build artifacts to Releases.

## Project Structure

```
app/src/main/java/com/simple/duckdns/updater/
├── MainActivity.java          # Main UI activity with configuration and controls
├── DuckDNSUpdateWorker.java   # WorkManager worker for background updates
└── DuckDNSApplication.java    # Application-level initialization
```

## Technical Details

The application uses:
- **OkHttp** - HTTP client with singleton pattern and configured timeouts (15s connect/read/write)
- **WorkManager** - Background task scheduling with self-rescheduling for flexible intervals
- **Android Keystore** - Hardware-backed secure key storage for encryption
- **AES-256-GCM** - Industry-standard authenticated encryption for token protection
- **SharedPreferences** - Configuration persistence with encrypted sensitive data
- **Try-with-resources** - Proper resource management to prevent memory leaks
- **Security best practices** - Token never logged, sanitized URL logging, encrypted storage

### Security Features

- **Encrypted Token Storage**: Token is encrypted using AES-256-GCM with Android Keystore
- Token is never exposed in logs (masked as `token=***`)
- Secure storage of configuration using SharedPreferences
- Hardware-backed key storage (when available)
- Automatic migration from plain-text to encrypted storage
- No sensitive data in crash reports

### Performance Optimizations

- Singleton OkHttpClient with connection pooling
- Configurable HTTP timeouts (15 seconds)
- Efficient resource management with try-with-resources
- No memory leaks from unclosed streams

## Compatibility

- **Minimum SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 33 (Android 13)
- **Tested on**: Android 5.0 - Android 14

## Note

This implementation now includes:
- Actual HTTP requests to the DuckDNS API using OkHttp
- Detailed logging of HTTP status codes (200 OK, 400 Bad Request, etc.)
- Comprehensive error handling for network issues and invalid responses
- Improved feedback to the user about the success or failure of updates (OK/KO)
- Configuration persistence across app restarts
- Support for automatic IP address detection
- Clean UI with collapsible configuration section
- Flexible update intervals (1 minute minimum)
- Production-ready code with proper resource management

## Possible Future Enhancements

- [ ] Implement exponential backoff for failed requests
- [ ] Add network constraints (WiFi only option)
- [ ] Add battery constraints (skip updates when battery is low)
- [ ] Implement adaptive intervals based on IP change detection
- [ ] Add support for multiple DuckDNS accounts
- [ ] Implement push notifications for update results
- [ ] Add widget for quick status view
- [ ] Export/import configuration

## Documentation

- [WorkManager Implementation Details](WORKMANAGER_IMPLEMENTATION.md) - Technical details about the self-rescheduling pattern
- [Changelog](CHANGELOG.md) - Version history and changes

## Author

This project was developed by **dvd.red@gmail.com**, a passionate FOSS enthusiast who has transitioned from sysadmin and devops roles into programming hobbyist.

## License

This project is licensed under the GPL(v3) License - see the LICENSE file for details.
