# Changelog

All notable changes to Simple-DuckDNS-Updater will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [0.3.0] - 2025-11-30

### Added

- **Encrypted Token Storage**: DuckDNS token is now encrypted using AES-256-GCM with Android Keystore
- **Hardware-Backed Security**: Encryption keys are stored in Android Keystore (hardware-backed when available)
- **Automatic Migration**: Existing plain-text configurations are automatically migrated to encrypted storage

### Changed

- **Configuration Storage**: Migrated from plain-text file (`duckdns_config.txt`) to encrypted SharedPreferences
- **DuckDNSUpdateWorker**: Updated to read configuration from encrypted SharedPreferences instead of plain-text file

### Security

- Token is now encrypted at rest using AES-256-GCM authenticated encryption
- Encryption keys are protected by Android Keystore system
- Old plain-text configuration file is automatically deleted after successful migration
- Token remains protected even if device storage is compromised

### Migration Notes

No user action required. The app will automatically:
- Detect existing plain-text configuration
- Encrypt the token and migrate to SharedPreferences
- Delete the old plain-text configuration file
- Continue working seamlessly with scheduled updates

---

## [0.2.0] - 2025-11-30

### Added

- **Flexible Update Intervals**: Support for update intervals from 1 minute to unlimited (bypassing WorkManager's 15-minute minimum)
- **Self-Rescheduling Pattern**: Implemented OneTimeWorkRequest chain that automatically reschedules after each execution
- **HTTP Timeouts**: Configured connect, read, and write timeouts (15 seconds each) to prevent indefinite hangs
- **Comprehensive Logging**: Detailed debug logging throughout the worker execution lifecycle
- **Documentation**: Added `WORKMANAGER_IMPLEMENTATION.md` with technical implementation details

### Changed

- **WorkManager Implementation**: Migrated from `PeriodicWorkRequest` to self-rescheduling `OneTimeWorkRequest` pattern
- **OkHttpClient**: Changed from creating new instance per request to singleton pattern with connection pooling
- **Resource Management**: All file I/O operations now use try-with-resources for guaranteed cleanup

### Fixed

- **Critical: OkHttpClient Resource Leak**
  - Previously created a new OkHttpClient for every worker execution
  - Now uses a singleton instance with proper configuration
  - Benefits: Connection pooling, reduced memory usage, better performance

- **Critical: BufferedReader Resource Leaks** (3 occurrences)
  - `DuckDNSUpdateWorker.readConfigFromFile()` - reader not closed on exception
  - `MainActivity.loadConfigFromFile()` - reader not closed on exception
  - `MainActivity.loadLog()` - reader not closed on exception
  - All fixed using try-with-resources pattern

- **Critical: FileWriter Resource Leaks** (3 occurrences)
  - `DuckDNSUpdateWorker.writeLog()` - writer not closed on exception
  - `MainActivity.saveConfigToFile()` - writer not closed on exception
  - `MainActivity.writeLogSync()` - writer not closed on exception
  - All fixed using try-with-resources pattern

- **Security: Token Exposed in Logs**
  - Previously logged full URL including DuckDNS token in plaintext
  - Now sanitizes URL before logging, displaying as `token=***`
  - Prevents credential exposure in logcat, crash reports, and monitoring systems

### Removed

- **UpdateReceiver from Manifest**: Removed unused BroadcastReceiver declaration
- **Unused workManager Field**: Removed unused instance variable from MainActivity
- **Unused shouldReschedule Variable**: Removed always-true variable from worker logic

### Security

- Token is now masked in all log output
- No sensitive data exposure in debug logs
- Secure storage of configuration in app's internal storage

### Performance

- Singleton OkHttpClient reduces memory allocations
- Connection pooling improves HTTP request performance
- Proper resource cleanup prevents memory leaks
- No file descriptor leaks from unclosed streams

---

## [0.1.4] - 2025-11-28

### Added

- Initial WorkManager implementation for background updates
- OkHttp integration for HTTP requests
- Configuration persistence across app restarts
- Log viewing and clearing functionality
- Collapsible configuration section in UI

### Changed

- Replaced AlarmManager with WorkManager for better reliability
- Improved error handling for network operations

---

## [0.1.0] - Initial Release

### Added

- Manual DuckDNS record updates
- Scheduled updates with configurable intervals
- Domain, token, and IP address configuration
- Activity logging with HTTP response details
- GitHub Actions for automatic APK builds

---

## Migration Notes

### From 0.1.x to 0.2.0

No user action required. The app will automatically:
- Continue using existing configuration
- Maintain scheduled update settings
- Preserve log history

Internal changes are transparent to users but provide:
- Better battery efficiency
- More reliable background execution
- Support for shorter update intervals (1+ minutes)
- Improved security (token no longer in logs)

---

## Technical Details

### Resource Leak Fixes

**Before (Problematic):**
```java
BufferedReader reader = new BufferedReader(new FileReader(file));
String line;
while ((line = reader.readLine()) != null) {
    content.append(line);
}
reader.close(); // Not called if exception occurs above
```

**After (Fixed):**
```java
try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
    String line;
    while ((line = reader.readLine()) != null) {
        content.append(line);
    }
} // Automatically closed even on exception
```

### OkHttpClient Singleton

**Before (Problematic):**
```java
// Created every execution - wastes resources
OkHttpClient httpClient = new OkHttpClient();
```

**After (Fixed):**
```java
// Singleton with configured timeouts
private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build();
```

### Token Sanitization

**Before (Security Risk):**
```java
Log.d(TAG, "URL: " + url);
// Output: https://www.duckdns.org/update?domains=example&token=abc123
```

**After (Secure):**
```java
String sanitizedUrl = url.replaceAll("token=[^&]*", "token=***");
Log.d(TAG, "URL: " + sanitizedUrl);
// Output: https://www.duckdns.org/update?domains=example&token=***
```

---

## Verification

To verify the fixes are working:

```bash
# Monitor memory usage (should be stable over time)
adb shell dumpsys meminfo com.simple.duckdns.updater

# Check for file descriptor leaks (count should not grow)
adb shell ls -la /proc/$(pidof com.simple.duckdns.updater)/fd | wc -l

# Verify token is not in logs
adb logcat | grep -i "token=" | grep -v "token=\*\*\*"
# Should return no results
```

---

## References

- [Java Try-with-Resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)
- [OkHttp Best Practices](https://square.github.io/okhttp/)
- [Android WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
