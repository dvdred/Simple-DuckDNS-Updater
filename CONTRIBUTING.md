# Contributing to Simple DuckDNS Updater

Thank you for your interest in contributing to Simple DuckDNS Updater! 📱

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Running Tests](#running-tests)
- [Coding Guidelines](#coding-guidelines)
- [Pull Request Process](#pull-request-process)
- [Adding New Features](#adding-new-features)

## Code of Conduct

This project follows a simple code of conduct:
- Be respectful and constructive
- Welcome newcomers and help them learn
- Focus on what is best for the community
- Show empathy towards other community members

## How Can I Contribute?

### Reporting Bugs 🐛

Before creating bug reports, please check existing issues. When creating a bug report, include:

- **Description**: Clear description of the problem
- **Steps to reproduce**: Step-by-step instructions
- **Expected behavior**: What should happen
- **Actual behavior**: What actually happens
- **Device info**: Android version, device model
- **Screenshots**: If applicable

### Suggesting Enhancements 💡

Enhancement suggestions are tracked as GitHub issues. When suggesting:

- Use a clear and descriptive title
- Provide detailed description of the suggested enhancement
- Explain why this would be useful
- Include mockups/examples if applicable

### Contributing Code 💻

1. **Bug fixes**: Always welcome!
2. **New features**: Open an issue first to discuss
3. **Performance improvements**: Include benchmarks
4. **Documentation**: Always appreciated
5. **UI/UX improvements**: Focus on user experience

## Development Setup

### Prerequisites

```bash
Android Studio
Android SDK
Gradle 8.0+
Java 17+
```

### Installation

1. Fork and clone the repository:
```bash
git clone https://github.com/YOUR-USERNAME/Simple-DuckDNS-Updater.git
cd Simple-DuckDNS-Updater
```

2. Open the project in Android Studio
3. Sync the project with Gradle files
4. Build and run the application

## Running Tests

```bash
./gradlew test
```

### Test Coverage

When adding new features, please include tests:
- Unit tests for new classes
- Integration tests for app functionality
- Ensure all tests pass before submitting PR

## Coding Guidelines

### Android Style

- Follow **Android Code Style** guidelines
- Use **4 spaces** for indentation (no tabs)
- Maximum line length: **100 characters** (flexible for readability)
- Use descriptive variable and method names

### Code Structure

```java
// Good
public class DuckDNSUpdater {
    private static final String TAG = "DuckDNSUpdater";
    
    public void updateDuckDNS() {
        // Implementation
    }
    
// Bad
public class DuckDNSUpdater {
    private static final String TAG = "DuckDNSUpdater";
    
    public void updateDuckDNS() {
        // Implementation
    }
}
```

### Constants

- Place all constants at the **top of the class**
- Use **UPPER_CASE** for constants
- Group related constants together with comments

```java
// ---------- APP CONFIG ----------
private static final String PREFERENCE_NAME = "duckdns_prefs";
private static final String PREF_USERNAME = "username";
private static final String PREF_TOKEN = "token";
private static final int DEFAULT_UPDATE_INTERVAL = 300; // 5 minutes
```

### Comments

- Use comments for **why**, not **what**
- Document complex algorithms
- Keep comments up-to-date

## Pull Request Process

### Before Submitting

- [ ] Code follows the style guidelines
- [ ] Self-review of your code
- [ ] Comments added for complex code
- [ ] Tests added/updated
- [ ] All tests pass
- [ ] Documentation updated (if needed)

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
Describe tests performed

## Screenshots (if applicable)

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-reviewed code
- [ ] Commented complex areas
- [ ] Updated documentation
- [ ] Added tests
- [ ] All tests pass
```

### Review Process

1. Maintainers will review within **7 days**
2. Address review comments
3. Once approved, maintainer will merge
4. Your contribution will be credited in releases

## Adding New Features

### Adding New Functionality

Follow this pattern:

```java
// 1. Add new feature class or method
public class NetworkUtils {
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }
}

// 2. Update relevant components
// 3. Add proper error handling
// 4. Include unit tests
// 5. Update documentation
```

### Adding New Permissions

1. Add permission to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

2. Request runtime permissions when needed:
```java
if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) 
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this, 
        new String[]{Manifest.permission.INTERNET}, REQUEST_CODE);
}
```

## Project Structure

```
Simple-DuckDNS-Updater/
├── app/                    # Main application code
│   ├── src/main/java/      # Java source files
│   ├── src/main/res/       # Resources (layouts, drawables, values)
│   └── AndroidManifest.xml # App manifest
├── build.gradle            # Gradle build configuration
├── CONTRIBUTING.md         # This file
├── README.md               # Project readme
├── LICENSE                 # License file
└── .github/                # GitHub workflows and issue templates
```

## Resources Needed

When adding features requiring resources:

### Images
- Format: PNG with transparency
- Icon: 48x48dp (mdpi) and higher density versions

### Strings
- All user-facing strings in `strings.xml`
- Use proper localization for internationalization

### Permissions
- Request only necessary permissions
- Document why each permission is required

## Questions?

- Open an issue with the **question** label
- Contact: dvd.red@gmail.com

## License

By contributing, you agree that your contributions will be licensed under the **MIT License**.

---

**Thank you for contributing! 🚀**

Made with ❤️ by the Simple DuckDNS Updater community
