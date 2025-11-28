# Simple-DuckDNS-Updater Android App

![Logo](https://github.com/dvdred/antipattern-bird/raw/refs/heads/main/SimpleDuckDNS-Updater.png)

This Android application allows you to update your DuckDNS domain records programmatically

![Configuration](https://github.com/dvdred/antipattern-bird/raw/refs/heads/main/demo01.png)
![ManualUpdate](https://github.com/dvdred/antipattern-bird/raw/refs/heads/main/demo02.png)
![AutoUpdate](https://github.com/dvdred/antipattern-bird/raw/refs/heads/main/demo03.png)

---

## Features

- Manual update of DuckDNS records
- Scheduled updates with customizable intervals
- Input fields for domains, token, and optional IP address
- Logging of all update activities with HTTP response details
- Background execution for scheduled updates
- Automatic APK building via GitHub Actions
- Improved error handling for HTTP requests
- Configuration persistence across app restarts
- Auto-detection of public IP address when no IP is specified
- Responsive UI with collapsible configuration section
- Real-time log viewing and clearing functionality

## How to Use

### Manual Update
1. Enter your DuckDNS domain(s) (comma separated)
2. Enter your DuckDNS token
3. Optionally enter an IP address (leave blank to auto-detect)
4. Tap "Update Now" to perform an immediate update

### Scheduled Updates
1. Set the interval in minutes for how often updates should occur
2. Tap "Start AutoUpdate" to start the scheduled updates
3. Tap "Stop AutoUpdate" to cancel the scheduled updates

## Implementation Details

The app works by:
1. Constructing a URL in the format: `https://www.duckdns.org/update?domains={domains}&token={token}&ip={ip}`
2. Performing real HTTP requests to the DuckDNS API using OkHttp
3. Logging all activities to a local file including HTTP status codes and response bodies
4. Using Android's AlarmManager for scheduled execution
5. Storing configuration in internal storage for persistence
6. Supporting both manual and automatic update modes

## Permissions Required

- Internet access (for making HTTP requests)
- Wake lock (to ensure updates happen even when screen is off)
- Receive boot completed (to restart scheduled updates after reboot)

## Building the App

To build this application:
1. Ensure you have Android Studio installed
2. Import the project into Android Studio
3. Build and run the application
4. The app will automatically include the required OkHttp dependency for HTTP requests

## GitHub Actions

This project includes a GitHub Actions workflow that automatically builds APKs:
- **Debug APK**:
- **Release APK**:

The APKs are automatically uploaded as build artifacts to Releases.

## Project Structure

- `MainActivity.java`: Main UI activity with configuration inputs and update controls
- `UpdateReceiver.java`: Broadcast receiver for scheduled updates
- `DuckDNSApplication.java`: Application-level initialization
- `AndroidManifest.xml`: App permissions and component declarations
- `build.gradle`: Gradle build configuration with OkHttp dependency

## Technical Details

The application uses:
- **OkHttp** for making HTTP requests to the DuckDNS API
- **AlarmManager** for scheduling periodic updates
- **SharedPreferences** for configuration storage
- **BroadcastReceiver** for handling scheduled update triggers
- **Background threads** for network operations to prevent UI blocking
- **Local file logging** for tracking update history

## Note

This implementation now includes:
- Actual HTTP requests to the DuckDNS API using OkHttp
- Detailed logging of HTTP status codes (200 OK, 400 Bad Request, etc.)
- Comprehensive error handling for network issues and invalid responses
- Improved feedback to the user about the success or failure of updates (OK/KO)
- Configuration persistence across app restarts
- Support for automatic IP address detection
- Clean UI with collapsible configuration section

Possible TODOs:
- Implement more robust error recovery mechanisms
- Add retry logic for failed requests
- Consider using WorkManager for better background execution handling
- Obfuscate sensitive data (API Token, IP addresses) in settings
- Implement more detailed logging and monitoring
- Add support for multiple DuckDNS accounts
- Implement push notifications for update results

## Author

This project was developed by **dvd.red@gmail.com**, a passionate FOSS enthusiast who has transitioned from sysadmin and devops roles into programming hobbyist.

## License

This project is licensed under the GPL(v3) License - see the LICENSE file for details.
