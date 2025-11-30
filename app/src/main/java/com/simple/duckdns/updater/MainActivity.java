package com.simple.duckdns.updater;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {

    private EditText domainsEditText;
    private EditText tokenEditText;
    private EditText ipEditText;
    private EditText intervalEditText;
    private MaterialButton updateButton;
    private MaterialButton scheduleToggleButton;
    private MaterialButton clearLogButton;
    private TextView logTextView;
    private ScrollView logScrollView;
    private LinearLayout configurationHeader;
    private TextView configurationToggleIcon;
    private LinearLayout configurationFieldsContainer;
    private TextView versionTextView;
    private boolean isConfigurationExpanded = true;

    private static final String LOG_FILE = "duckdns_log.txt";
    private static final String CONFIG_FILE = "duckdns_config.txt";
    private static final String LOG_UPDATED_ACTION =
        "com.simple.duckdns.updater.LOG_UPDATED";
    private ExecutorService executorService;
    private OkHttpClient httpClient;
    private boolean isScheduled = false;
    private Handler mainHandler;
    private static final DateTimeFormatter LOG_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // BroadcastReceiver per ascoltare gli aggiornamenti del log
    private BroadcastReceiver logUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LOG_UPDATED_ACTION.equals(intent.getAction())) {
                loadLog();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        domainsEditText = findViewById(R.id.domainsEditText);
        tokenEditText = findViewById(R.id.tokenEditText);
        ipEditText = findViewById(R.id.ipEditText);
        intervalEditText = findViewById(R.id.intervalEditText);
        updateButton = findViewById(R.id.updateButton);
        scheduleToggleButton = findViewById(R.id.scheduleToggleButton);
        clearLogButton = findViewById(R.id.clearLogButton);
        logTextView = findViewById(R.id.logTextView);
        logScrollView = findViewById(R.id.logScrollView);
        configurationHeader = findViewById(R.id.configurationHeader);
        configurationToggleIcon = findViewById(R.id.configurationToggleIcon);
        configurationFieldsContainer = findViewById(
            R.id.configurationFieldsContainer
        );
        versionTextView = findViewById(R.id.versionTextView);

        // Initialize executor service for background tasks
        executorService = Executors.newFixedThreadPool(2);

        // Initialize HTTP client
        httpClient = new OkHttpClient();

        // Initialize handler for UI updates
        mainHandler = new Handler(Looper.getMainLooper());

        // Set click listeners
        updateButton.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performUpdate();
                }
            }
        );

        scheduleToggleButton.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isScheduled) {
                        stopSchedule();
                    } else {
                        String domains = domainsEditText
                            .getText()
                            .toString()
                            .trim();
                        String token = tokenEditText
                            .getText()
                            .toString()
                            .trim();

                        if (domains.isEmpty() || token.isEmpty()) {
                            showSnackbar(
                                "Please enter domains and token first",
                                "error"
                            );
                            return;
                        }
                        scheduleUpdate();
                    }
                }
            }
        );

        clearLogButton.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearLog();
                }
            }
        );

        // Configuration collapse/expand toggle
        configurationHeader.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleConfigurationSection();
                }
            }
        );

        // Load configuration from file
        boolean hasConfig = loadConfigFromFile();

        // Set initial state of configuration section
        if (hasConfig) {
            // Close configuration section if config exists
            isConfigurationExpanded = true; // Set to true so toggle will close it
            toggleConfigurationSection();
        } else {
            // Keep configuration section open if no config exists
            isConfigurationExpanded = true;
            configurationToggleIcon.setText("▼");
        }

        // Load log on startup
        loadLog();

        // Display footer information
        displayFooterInfo();
    }

    private boolean loadConfigFromFile() {
        try {
            // Load from encrypted SharedPreferences
            SharedPreferences prefs = getSharedPreferences(
                "config",
                Context.MODE_PRIVATE
            );

            String domains = prefs.getString("domains", "");
            String token = prefs.getString("token", "");
            String ip = prefs.getString("ip", "");
            String interval = prefs.getString("interval", "");

            // Check if we need to migrate from old config file
            if (domains.isEmpty() && token.isEmpty()) {
                boolean migrated = migrateFromOldConfigFile();
                if (migrated) {
                    // Reload from SharedPreferences after migration
                    domains = prefs.getString("domains", "");
                    token = prefs.getString("token", "");
                    ip = prefs.getString("ip", "");
                    interval = prefs.getString("interval", "");
                }
            }

            // Decrypt token if it exists
            if (!token.isEmpty()) {
                try {
                    token = decrypt(token);
                } catch (Exception e) {
                    // Token might be in plain text (old format or migration)
                    // Check if it looks like a valid DuckDNS token (UUID format)
                    if (isValidTokenFormat(token)) {
                        // It's a plain text token, re-encrypt and save it
                        Log.i(
                            "MainActivity",
                            "Migrating plain text token to encrypted format"
                        );
                        String encryptedToken = encrypt(token);
                        prefs.edit().putString("token", encryptedToken).apply();
                    } else {
                        Log.e(
                            "MainActivity",
                            "Failed to decrypt token and token format is invalid",
                            e
                        );
                        token = "";
                    }
                }
            }

            // Populate the EditText fields
            if (!domains.isEmpty()) {
                domainsEditText.setText(domains);
            }
            if (!token.isEmpty()) {
                tokenEditText.setText(token);
            }
            if (!ip.isEmpty()) {
                ipEditText.setText(ip);
            }
            if (!interval.isEmpty()) {
                intervalEditText.setText(interval);
            }

            // Return true if at least domains and token are present
            return !domains.isEmpty() && !token.isEmpty();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void toggleConfigurationSection() {
        if (isConfigurationExpanded) {
            // Collapse
            configurationFieldsContainer.setVisibility(View.GONE);
            configurationToggleIcon.setText("▶");
            isConfigurationExpanded = false;
        } else {
            // Expand
            configurationFieldsContainer.setVisibility(View.VISIBLE);
            configurationToggleIcon.setText("▼");
            isConfigurationExpanded = true;
        }
    }

    private void performUpdate() {
        String domains = domainsEditText.getText().toString().trim();
        String token = tokenEditText.getText().toString().trim();
        String ip = ipEditText.getText().toString().trim();

        // Save configuration to file
        saveConfigToFile(domains, token, ip);

        if (domains.isEmpty() || token.isEmpty()) {
            showSnackbar("Please enter domains and token", "error");
            return;
        }

        // Execute the update in background
        executorService.execute(
            new Runnable() {
                @Override
                public void run() {
                    String result;
                    boolean isSuccess = false;
                    String statusMessage = "";

                    try {
                        // Create the URL
                        String url =
                            "https://www.duckdns.org/update?domains=" +
                            domains +
                            "&token=" +
                            token;
                        if (!ip.isEmpty()) {
                            url += "&ip=" + ip;
                        }

                        // Create HTTP request
                        Request request = new Request.Builder()
                            .url(url)
                            .build();

                        // Make HTTP request and get response
                        Response response = httpClient
                            .newCall(request)
                            .execute();

                        // Get response code
                        int responseCode = response.code();

                        // Read response body
                        String responseBody = "";
                        if (response.body() != null) {
                            responseBody = response.body().string();
                        }

                        // Determine success based on response body content
                        if (responseBody.contains("OK")) {
                            isSuccess = true;
                            statusMessage = "OK";
                        } else if (responseBody.contains("KO")) {
                            isSuccess = false;
                            statusMessage = "KO";
                        } else {
                            isSuccess = (responseCode == 200);
                            statusMessage = "HTTP " + responseCode;
                        }

                        // Create compact log message without token
                        String timestamp = LocalDateTime.now().format(
                            LOG_DATE_FORMAT
                        );
                        String ipInfo = ip.isEmpty() ? "" : " [IP: " + ip + "]";
                        result = String.format(
                            "[%s] %s%s - %s (%s)",
                            timestamp,
                            domains,
                            ipInfo,
                            isSuccess ? "SUCCESS" : "FAILED",
                            statusMessage
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                        isSuccess = false;
                        statusMessage = e.getMessage();
                        String timestamp = LocalDateTime.now().format(
                            LOG_DATE_FORMAT
                        );
                        result = String.format(
                            "[%s] %s - ERROR: %s",
                            timestamp,
                            domains,
                            statusMessage
                        );
                    }

                    // Write to log file (synchronously)
                    writeLogSync(result);

                    // Update UI on main thread
                    final boolean finalIsSuccess = isSuccess;
                    final String finalStatusMessage = statusMessage;
                    mainHandler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                loadLog();
                                showSnackbar(
                                    finalIsSuccess
                                        ? "Update completed: " +
                                          finalStatusMessage
                                        : "Update failed: " +
                                          finalStatusMessage,
                                    finalIsSuccess ? "success" : "error"
                                );
                            }
                        }
                    );
                }
            }
        );
    }

    private void saveConfigToFile(String domains, String token, String ip) {
        try {
            String interval = intervalEditText.getText().toString().trim();

            // Encrypt token before saving
            String encryptedToken = token;
            if (!token.isEmpty()) {
                try {
                    encryptedToken = encrypt(token);
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to encrypt token", e);
                }
            }

            // Save to encrypted SharedPreferences
            SharedPreferences prefs = getSharedPreferences(
                "config",
                Context.MODE_PRIVATE
            );
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("domains", domains);
            editor.putString("token", encryptedToken);
            editor.putString("ip", ip);
            editor.putString("interval", interval);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void writeLogSync(String message) {
        try {
            File logFile = new File(getFilesDir(), LOG_FILE);
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(message + "\n");
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLog() {
        try {
            File logFile = new File(getFilesDir(), LOG_FILE);
            if (logFile.exists()) {
                List<String> lines = new ArrayList<>();
                try (
                    BufferedReader reader = new BufferedReader(
                        new FileReader(logFile)
                    )
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }

                // Show only the last 100 lines
                StringBuilder content = new StringBuilder();
                int start = Math.max(0, lines.size() - 100);
                for (int i = start; i < lines.size(); i++) {
                    content.append(lines.get(i)).append("\n");
                }

                logTextView.setText(content.toString());

                // Scroll to bottom to show the latest logs
                scrollToBottom();
            } else {
                logTextView.setText(
                    "Log file not found. Waiting for updates..."
                );
            }
        } catch (Exception e) {
            logTextView.setText("Error loading log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void scrollToBottom() {
        logScrollView.post(
            new Runnable() {
                @Override
                public void run() {
                    logScrollView.fullScroll(View.FOCUS_DOWN);
                }
            }
        );
    }

    private void clearLog() {
        try {
            File logFile = new File(getFilesDir(), LOG_FILE);
            if (logFile.exists()) {
                // Overwrite the file with empty content
                FileWriter writer = new FileWriter(logFile, false);
                writer.write("");
                writer.flush();
                writer.close();

                logTextView.setText("Log cleared. Waiting for updates...");
                showSnackbar("Log cleared successfully", "success");
            } else {
                showSnackbar("No log file to clear", "info");
            }
        } catch (IOException e) {
            e.printStackTrace();
            showSnackbar("Error clearing log: " + e.getMessage(), "error");
        }
    }

    private void scheduleUpdate() {
        String intervalStr = intervalEditText.getText().toString().trim();
        if (intervalStr.isEmpty()) {
            showSnackbar("Please enter an interval", "error");
            return;
        }

        try {
            int interval = Integer.parseInt(intervalStr);
            if (interval <= 0) {
                showSnackbar("Interval must be positive", "error");
                return;
            }

            String domains = domainsEditText.getText().toString().trim();
            String token = tokenEditText.getText().toString().trim();

            if (domains.isEmpty() || token.isEmpty()) {
                showSnackbar("Please save configuration first", "error");
                return;
            }

            // Save configuration before scheduling
            String domainsStr = domainsEditText.getText().toString().trim();
            String tokenStr = tokenEditText.getText().toString().trim();
            String ipStr = ipEditText.getText().toString().trim();
            saveConfigToFile(domainsStr, tokenStr, ipStr);

            // Start periodic work using self-rescheduling OneTimeWorkRequest
            // This allows intervals less than 15 minutes
            DuckDNSUpdateWorker.startPeriodicWork(this, interval);

            Log.d(
                "MainActivity",
                "Successfully scheduled periodic work with interval: " +
                    interval +
                    " minutes"
            );

            showSnackbar(
                "Scheduled update every " + interval + " minutes",
                "success"
            );

            isScheduled = true;
            scheduleToggleButton.setText("Stop AutoUpdate");
            scheduleToggleButton.setBackgroundColor(android.graphics.Color.RED);
        } catch (NumberFormatException e) {
            Log.e("MainActivity", "Invalid interval number format", e);
            showSnackbar("Invalid interval", "error");
        } catch (Exception e) {
            Log.e("MainActivity", "Error scheduling update", e);
            e.printStackTrace();
            showSnackbar("Error scheduling update: " + e.getMessage(), "error");
        }
    }

    private void stopSchedule() {
        // Stop periodic work using static method
        DuckDNSUpdateWorker.stopPeriodicWork(this);

        showSnackbar("AutoUpdate stopped", "info");

        isScheduled = false;
        scheduleToggleButton.setText("Start AutoUpdate");
        scheduleToggleButton.setBackgroundColor(android.graphics.Color.GREEN);
    }

    private void showSnackbar(String message, String type) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(
            rootView,
            message,
            Snackbar.LENGTH_SHORT
        );

        // Get the Snackbar view and change its position to top
        View snackbarView = snackbar.getView();
        FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) snackbarView.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = 50; // Add some top margin
        snackbarView.setLayoutParams(params);

        switch (type) {
            case "success":
                snackbar.setBackgroundTint(
                    android.graphics.Color.parseColor("#4CAF50")
                );
                break;
            case "error":
                snackbar.setBackgroundTint(
                    android.graphics.Color.parseColor("#F44336")
                );
                break;
            case "warning":
                snackbar.setBackgroundTint(
                    android.graphics.Color.parseColor("#FFC107")
                );
                break;
            case "info":
                snackbar.setBackgroundTint(
                    android.graphics.Color.parseColor("#2196F3")
                );
                break;
            default:
                snackbar.setBackgroundTint(android.graphics.Color.CYAN);
        }

        snackbar.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Registra il receiver per ascoltare gli aggiornamenti del log
        IntentFilter filter = new IntentFilter(LOG_UPDATED_ACTION);
        registerReceiver(
            logUpdateReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED
        );

        loadLog();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Deregistra il receiver
        try {
            unregisterReceiver(logUpdateReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver non era registrato
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private String getVersionFromAssets() {
        try {
            InputStream inputStream = getAssets().open("version.txt");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream)
            );
            String version = reader.readLine();
            reader.close();
            inputStream.close();
            return version != null ? version : "Unknown";
        } catch (IOException e) {
            e.printStackTrace();
            return "Unknown";
        }
    }

    private void displayFooterInfo() {
        // Get version from assets
        String version = getVersionFromAssets();

        // Set version text
        if (versionTextView != null) {
            versionTextView.setText("Version: " + version);
        }
    }

    // Migration from old config file format
    private boolean migrateFromOldConfigFile() {
        try {
            File configFile = new File(getFilesDir(), CONFIG_FILE);

            if (!configFile.exists()) {
                return false;
            }

            StringBuilder content = new StringBuilder();
            try (
                BufferedReader reader = new BufferedReader(
                    new FileReader(configFile)
                )
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            String configContent = content.toString();

            String domains = "";
            String token = "";
            String ip = "";
            String interval = "";

            // Extract domains
            int domainsIndex = configContent.indexOf("domains=");
            if (domainsIndex != -1) {
                int startIndex = domainsIndex + "domains=".length();
                int endIndex = configContent.indexOf("\n", startIndex);
                if (endIndex == -1) endIndex = configContent.length();
                domains = configContent.substring(startIndex, endIndex).trim();
            }

            // Extract token
            int tokenIndex = configContent.indexOf("token=");
            if (tokenIndex != -1) {
                int startIndex = tokenIndex + "token=".length();
                int endIndex = configContent.indexOf("\n", startIndex);
                if (endIndex == -1) endIndex = configContent.length();
                token = configContent.substring(startIndex, endIndex).trim();
            }

            // Extract ip
            int ipIndex = configContent.indexOf("ip=");
            if (ipIndex != -1) {
                int startIndex = ipIndex + "ip=".length();
                int endIndex = configContent.indexOf("\n", startIndex);
                if (endIndex == -1) endIndex = configContent.length();
                ip = configContent.substring(startIndex, endIndex).trim();
            }

            // Extract interval
            int intervalIndex = configContent.indexOf("interval=");
            if (intervalIndex != -1) {
                int startIndex = intervalIndex + "interval=".length();
                int endIndex = configContent.indexOf("\n", startIndex);
                if (endIndex == -1) endIndex = configContent.length();
                interval = configContent.substring(startIndex, endIndex).trim();
            }

            // If we found config data, migrate to encrypted SharedPreferences
            if (!domains.isEmpty() || !token.isEmpty()) {
                Log.i(
                    "MainActivity",
                    "Migrating from old config file to encrypted SharedPreferences"
                );

                // Encrypt token before saving
                String encryptedToken = token;
                if (!token.isEmpty()) {
                    try {
                        encryptedToken = encrypt(token);
                    } catch (Exception e) {
                        Log.e(
                            "MainActivity",
                            "Failed to encrypt token during migration",
                            e
                        );
                    }
                }

                SharedPreferences prefs = getSharedPreferences(
                    "config",
                    Context.MODE_PRIVATE
                );
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("domains", domains);
                editor.putString("token", encryptedToken);
                editor.putString("ip", ip);
                editor.putString("interval", interval);
                editor.apply();

                // Delete old config file after successful migration
                if (configFile.delete()) {
                    Log.i(
                        "MainActivity",
                        "Old config file deleted after migration"
                    );
                }

                return true;
            }

            return false;
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to migrate from old config file", e);
            return false;
        }
    }

    // Check if token looks like a valid DuckDNS token (UUID-like format)
    private boolean isValidTokenFormat(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        // DuckDNS tokens are UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        return token.matches(
            "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
        );
    }

    // Encryption helper methods
    private static final String KEY_ALIAS = "duckdns_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private String encrypt(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        byte[] iv = cipher.getIV();

        // Combine IV and encrypted data
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(
            encryptedBytes,
            0,
            combined,
            iv.length,
            encryptedBytes.length
        );

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    private String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);

        byte[] combined = Base64.decode(encryptedText, Base64.NO_WRAP);
        byte[] iv = new byte[12]; // GCM IV is typically 12 bytes
        byte[] encryptedBytes = new byte[combined.length - 12];

        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(
            combined,
            12,
            encryptedBytes,
            0,
            encryptedBytes.length
        );

        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        // Try to load existing key from Android KeyStore
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry secretKeyEntry =
                (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return secretKeyEntry.getSecretKey();
        }

        // Create new key
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        );
        KeyGenParameterSpec keyGenParameterSpec =
            new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();

        keyGenerator.init(keyGenParameterSpec);
        return keyGenerator.generateKey();
    }
}
