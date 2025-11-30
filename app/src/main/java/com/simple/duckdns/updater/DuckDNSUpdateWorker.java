package com.simple.duckdns.updater;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.File;
import java.io.FileWriter;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DuckDNSUpdateWorker extends Worker {

    private static final String LOG_FILE = "duckdns_log.txt";
    private static final String CONFIG_FILE = "duckdns_config.txt";
    private static final String LOG_UPDATED_ACTION =
        "com.simple.duckdns.updater.LOG_UPDATED";
    private static final DateTimeFormatter LOG_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String WORK_NAME = "duckdns_update_work";
    private static final String KEY_INTERVAL_MINUTES = "interval_minutes";

    // Singleton OkHttpClient instance to avoid resource leaks
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build();

    public DuckDNSUpdateWorker(
        @NonNull Context context,
        @NonNull WorkerParameters params
    ) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d("DuckDNSUpdateWorker", "Worker started - doWork() called");

            // Get the interval from input data
            int intervalMinutes = getInputData().getInt(
                KEY_INTERVAL_MINUTES,
                15
            );
            Log.d(
                "DuckDNSUpdateWorker",
                "Working with interval: " + intervalMinutes + " minutes"
            );

            // Log that the worker is starting
            String startMessage =
                "[" +
                LocalDateTime.now().format(LOG_DATE_FORMAT) +
                "] AutoUpdate triggered by WorkManager";
            writeLog(getApplicationContext(), startMessage);
            Log.d("DuckDNSUpdateWorker", "Started auto update log written");

            // Notify MainActivity that there might be new logs
            notifyLogUpdate(getApplicationContext());

            // Read configuration from file
            String[] config = readConfigFromFile(getApplicationContext());
            String domains = config[0];
            String token = config[1];
            String ip = config[2];

            Log.d(
                "DuckDNSUpdateWorker",
                "Read config - domains: " +
                    domains +
                    ", token: " +
                    (token != null && !token.isEmpty() ? "present" : "missing")
            );

            if (domains.isEmpty() || token.isEmpty()) {
                String message =
                    "[" +
                    LocalDateTime.now().format(LOG_DATE_FORMAT) +
                    "] AutoUpdate FAILED - No configuration found";
                writeLog(getApplicationContext(), message);
                notifyLogUpdate(getApplicationContext());
                Log.d(
                    "DuckDNSUpdateWorker",
                    "Configuration missing - worker completed with success"
                );
            } else {
                // Perform the actual DuckDNS update
                boolean success = performDuckDNSUpdate(
                    getApplicationContext(),
                    domains,
                    token,
                    ip
                );

                Log.d(
                    "DuckDNSUpdateWorker",
                    "DuckDNS update completed with success: " + success
                );
            }

            // Reschedule the next execution
            scheduleNextExecution(getApplicationContext(), intervalMinutes);

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(
                "DuckDNSUpdateWorker",
                "Exception in doWork(): " + e.getMessage(),
                e
            );
            writeLog(
                getApplicationContext(),
                "[" +
                    LocalDateTime.now().format(LOG_DATE_FORMAT) +
                    "] AutoUpdate ERROR: " +
                    e.getMessage()
            );
            notifyLogUpdate(getApplicationContext());

            // Even on failure, reschedule to try again
            int intervalMinutes = getInputData().getInt(
                KEY_INTERVAL_MINUTES,
                15
            );
            scheduleNextExecution(getApplicationContext(), intervalMinutes);

            return Result.failure();
        }
    }

    private void scheduleNextExecution(Context context, int intervalMinutes) {
        try {
            Log.d(
                "DuckDNSUpdateWorker",
                "Scheduling next execution in " + intervalMinutes + " minutes"
            );

            Data inputData = new Data.Builder()
                .putInt(KEY_INTERVAL_MINUTES, intervalMinutes)
                .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
                DuckDNSUpdateWorker.class
            )
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .setInputData(inputData)
                .build();

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            );

            Log.d(
                "DuckDNSUpdateWorker",
                "Successfully scheduled next execution"
            );
        } catch (Exception e) {
            Log.e(
                "DuckDNSUpdateWorker",
                "Failed to schedule next execution: " + e.getMessage(),
                e
            );
        }
    }

    private boolean performDuckDNSUpdate(
        Context context,
        String domains,
        String token,
        String ip
    ) {
        try {
            Log.d(
                "DuckDNSUpdateWorker",
                "Starting DuckDNS update for domains: " + domains
            );

            // Create the URL
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder
                .append("https://www.duckdns.org/update?domains=")
                .append(domains)
                .append("&token=")
                .append(token);
            if (ip != null && !ip.isEmpty()) {
                urlBuilder.append("&ip=").append(ip);
            }
            String url = urlBuilder.toString();

            // Log sanitized URL without exposing token
            String sanitizedUrl = url.replaceAll("token=[^&]*", "token=***");
            Log.d("DuckDNSUpdateWorker", "Constructed URL: " + sanitizedUrl);

            // Create HTTP request
            Request request = new Request.Builder().url(url).build();

            // Make HTTP request synchronously
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                // Get response code
                int responseCode = response.code();
                Log.d(
                    "DuckDNSUpdateWorker",
                    "HTTP Response Code: " + responseCode
                );

                // Read response body
                String responseBody = "";
                if (response.body() != null) {
                    responseBody = response.body().string();
                }
                Log.d("DuckDNSUpdateWorker", "Response body: " + responseBody);

                // Determine success based on response body content
                boolean isSuccess = false;
                String statusMessage = "";

                if (responseBody.contains("OK")) {
                    isSuccess = true;
                    statusMessage = "OK";
                } else if (responseBody.contains("KO")) {
                    isSuccess = false;
                    statusMessage = "KO";
                } else {
                    // Default to checking HTTP status code
                    isSuccess = (responseCode == 200);
                    statusMessage = "HTTP " + responseCode;
                }

                // Create compact log message without token
                String timestamp = LocalDateTime.now().format(LOG_DATE_FORMAT);
                String ipInfo = (ip != null && !ip.isEmpty())
                    ? " [IP: " + ip + "]"
                    : "";
                String result = String.format(
                    "[%s] AutoUpdate: %s%s - %s (%s)",
                    timestamp,
                    domains,
                    ipInfo,
                    isSuccess ? "SUCCESS" : "FAILED",
                    statusMessage
                );

                // Write to log file
                writeLog(context, result);
                Log.d("DuckDNSUpdateWorker", "Log written: " + result);

                // Notify MainActivity that there are new logs
                notifyLogUpdate(context);

                Log.d(
                    "DuckDNSUpdateWorker",
                    "Update completed successfully: " + isSuccess
                );
                return isSuccess;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(
                "DuckDNSUpdateWorker",
                "Exception in performDuckDNSUpdate: " + e.getMessage(),
                e
            );
            String timestamp = LocalDateTime.now().format(LOG_DATE_FORMAT);
            String errorMessage = String.format(
                "[%s] %s - ERROR: %s",
                timestamp,
                domains,
                e.getMessage()
            );
            writeLog(context, errorMessage);
            notifyLogUpdate(context);
            return false;
        }
    }

    private String[] readConfigFromFile(Context context) {
        String[] config = new String[] { "", "", "" }; // domains, token, ip

        try {
            Log.d(
                "DuckDNSUpdateWorker",
                "Reading config from SharedPreferences"
            );

            SharedPreferences prefs = context.getSharedPreferences(
                "config",
                Context.MODE_PRIVATE
            );

            String domains = prefs.getString("domains", "");
            String token = prefs.getString("token", "");
            String ip = prefs.getString("ip", "");

            if (domains.isEmpty() && token.isEmpty()) {
                writeLog(
                    context,
                    "[" +
                        LocalDateTime.now().format(LOG_DATE_FORMAT) +
                        "] No configuration found in SharedPreferences"
                );
                Log.d("DuckDNSUpdateWorker", "No configuration found");
                return config;
            }

            // Decrypt token if it exists
            if (!token.isEmpty()) {
                try {
                    token = decrypt(token);
                } catch (Exception e) {
                    Log.e("DuckDNSUpdateWorker", "Failed to decrypt token", e);
                    // Token might be in plain text format, check if valid
                    if (!isValidTokenFormat(token)) {
                        token = "";
                    }
                }
            }

            config[0] = domains;
            config[1] = token;
            config[2] = ip;

            Log.d(
                "DuckDNSUpdateWorker",
                "Parsed config - domains: " +
                    config[0] +
                    ", token: " +
                    (config[1] != null && !config[1].isEmpty()
                        ? "present"
                        : "missing") +
                    ", ip: " +
                    config[2]
            );
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(
                "DuckDNSUpdateWorker",
                "Error reading config: " + e.getMessage(),
                e
            );
            writeLog(
                context,
                "[" +
                    LocalDateTime.now().format(LOG_DATE_FORMAT) +
                    "] Error reading config: " +
                    e.getMessage()
            );
        }

        return config;
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

    private void writeLog(Context context, String message) {
        try {
            Log.d("DuckDNSUpdateWorker", "Writing log: " + message);
            File logFile = new File(context.getFilesDir(), LOG_FILE);
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.append(message).append("\n");
            }
        } catch (Exception e) {
            // Log to system log as fallback in case of file system issues
            Log.e("DuckDNSUpdateWorker", "Failed to write log: " + message, e);
        }
    }

    private void notifyLogUpdate(Context context) {
        try {
            Log.d("DuckDNSUpdateWorker", "Sending log update broadcast");
            Intent logUpdateIntent = new Intent(LOG_UPDATED_ACTION);
            context.sendBroadcast(logUpdateIntent);
        } catch (Exception e) {
            Log.e(
                "DuckDNSUpdateWorker",
                "Failed to send broadcast: " + e.getMessage(),
                e
            );
        }
    }

    // Static method to start the worker chain
    public static void startPeriodicWork(Context context, int intervalMinutes) {
        Log.d(
            "DuckDNSUpdateWorker",
            "Starting periodic work with interval: " +
                intervalMinutes +
                " minutes"
        );

        Data inputData = new Data.Builder()
            .putInt(KEY_INTERVAL_MINUTES, intervalMinutes)
            .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
            DuckDNSUpdateWorker.class
        )
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .setInputData(inputData)
            .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        );
    }

    // Static method to stop the worker chain
    public static void stopPeriodicWork(Context context) {
        Log.d("DuckDNSUpdateWorker", "Stopping periodic work");
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
