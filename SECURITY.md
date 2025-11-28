# Security Policy

## 📱 Project Context

Simple DuckDNS Updater is an Android application that updates DuckDNS records. The app:
- ✅ Runs locally on your Android device
- ✅ Does not collect personal data
- ✅ Does not send information to external servers
- ✅ Only accesses network when updating DNS records

## 📦 Supported Versions

| Version | Supported          | Notes |
| ------- | ------------------ | ----- |
| Latest release | :white_check_mark: | Always recommended |
| Older releases | :x: | Security fixes only in latest |
| Development branch | :warning: | Use at your own risk |

**Recommendation:** Always use the [latest release](https://github.com/dvdred/Simple-DuckDNS-Updater/releases/latest).

## 🛡️ Security Considerations

### What This App Does
- ✅ Runs **locally** on your Android device
- ✅ Reads **only local configuration** and **network resources**
- ✅ **No data collection** or telemetry
- ✅ **No external dependencies** at runtime

### What This App Does NOT Do
- ❌ No network connections to external servers (except DuckDNS)
- ❌ No file system writes (except local preferences)
- ❌ No execution of external code
- ❌ No personal data handling

### Permissions
The app requires minimal permissions:
- **INTERNET**: To communicate with DuckDNS servers
- **ACCESS_NETWORK_STATE**: To check network connectivity
- **FOREGROUND_SERVICE**: To update DNS in background (optional)

## 🐛 Reporting a Vulnerability

If you discover a security issue, please report it responsibly:

### For Critical Issues (RCE, arbitrary code execution, etc.)
📧 **Email:** dvd.red@gmail.com  
⏱️ **Response time:** Within 48 hours  
🔒 **Please include:**
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### For Non-Critical Issues (crashes, resource leaks, etc.)
Open a [GitHub Issue](https://github.com/dvdred/Simple-DuckDNS-Updater/issues) with the 
`security` label.

## 🔐 Security Best Practices for Users

When downloading and using this app:

1. **Download from official sources only:**
   - ✅ [GitHub Releases](https://github.com/dvdred/Simple-DuckDNS-Updater/releases)
   - ✅ Google Play Store (if available)
   - ❌ Avoid third-party mirrors

2. **Verify integrity (optional):**
   ```bash
   # Check SHA256 hash of downloaded APK (provided in release notes)
   sha256sum SimpleDuckDNS-Updater.apk
   ```

3. **Review app permissions before installing**

4. **Keep the app updated**

## Privacy Notice

This app does not collect or transmit any personal information. All configuration data is stored locally on your device.

## License

This security policy is part of the Simple DuckDNS Updater project and is available under the MIT License.
