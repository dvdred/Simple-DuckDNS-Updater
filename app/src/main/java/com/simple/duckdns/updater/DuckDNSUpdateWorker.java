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
import java.net.InetAddress;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    // DNS servers to check
    private static final String[] DNS_SERVERS = {
        "1.1.1.1",
        "8.8.8.8",
        "208.67.222.222",
    };

    // Singleton OkHttpClient instance to avoid resource leaks
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build();

    // OkHttpClient for quick checks with shorter timeouts
    private static final OkHttpClient QUICK_HTTP_CLIENT =
        new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
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
                // Check if update is needed before performing it
                if (shouldPerformUpdate(getApplicationContext(), domains, ip)) {
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
                } else {
                    Log.d(
                        "DuckDNSUpdateWorker",
                        "Skipping update - DNS already up to date"
                    );
                }
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

    /**
     * Check if DuckDNS update should be performed by comparing current/configured IP
     * with DNS resolution results from multiple DNS servers.
     *
     * @param context Application context
     * @param domains Comma-separated list of domains
     * @param configuredIp IP configured by user (may be null/empty)
     * @return true if update should be performed, false if DNS is already up to date
     */
    private boolean shouldPerformUpdate(
        Context context,
        String domains,
        String configuredIp
    ) {
        try {
            String targetIp;

            // Case A: No IP configured - get current public IP
            if (configuredIp == null || configuredIp.isEmpty()) {
                Log.d(
                    "DuckDNSUpdateWorker",
                    "No IP configured, getting current public IP"
                );
                targetIp = getCurrentPublicIp();

                if (targetIp == null || targetIp.isEmpty()) {
                    Log.w(
                        "DuckDNSUpdateWorker",
                        "Failed to get public IP, proceeding with update"
                    );
                    return true; // If we can't get IP, proceed with update
                }

                Log.d("DuckDNSUpdateWorker", "Current public IP: " + targetIp);
            } else {
                // Case B: IP is configured
                targetIp = configuredIp;
                Log.d(
                    "DuckDNSUpdateWorker",
                    "Using configured IP: " + targetIp
                );
            }

            // Split domains and check each one
            String[] domainList = domains.split(",");
            for (String domain : domainList) {
                domain = domain.trim();
                if (!domain.isEmpty()) {
                    // Add .duckdns.org if not present
                    String fullDomain = domain.contains(".")
                        ? domain
                        : domain + ".duckdns.org";

                    List<String> dnsResults = resolveDomainOnDnsServers(
                        fullDomain
                    );

                    // Count how many DNS servers returned different IP
                    int mismatchCount = 0;
                    for (String dnsIp : dnsResults) {
                        if (dnsIp != null && !dnsIp.equals(targetIp)) {
                            mismatchCount++;
                            Log.d(
                                "DuckDNSUpdateWorker",
                                "DNS mismatch for " +
                                    fullDomain +
                                    ": got " +
                                    dnsIp +
                                    ", expected " +
                                    targetIp
                            );
                        }
                    }

                    // If 2 or more DNS servers have different IP, update is needed
                    if (mismatchCount >= 2) {
                        Log.d(
                            "DuckDNSUpdateWorker",
                            "Update needed: " +
                                mismatchCount +
                                " DNS servers have outdated IP for " +
                                fullDomain
                        );
                        return true;
                    }
                }
            }

            // All domains are up to date
            String timestamp = LocalDateTime.now().format(LOG_DATE_FORMAT);
            String skipMessage = String.format(
                "[%s] AutoUpdate: %s - SKIPPED (DNS already up to date with IP: %s)",
                timestamp,
                domains,
                targetIp
            );
            writeLog(context, skipMessage);
            notifyLogUpdate(context);
            Log.d(
                "DuckDNSUpdateWorker",
                "DNS already up to date, skipping update"
            );
            return false;
        } catch (Exception e) {
            Log.e(
                "DuckDNSUpdateWorker",
                "Error checking if update needed: " + e.getMessage(),
                e
            );
            return true; // On error, proceed with update to be safe
        }
    }

    /**
     * Get current public IP address from v4.ident.me
     *
     * @return Public IP address or null if failed
     */
    private String getCurrentPublicIp() {
        try {
            Request request = new Request.Builder()
                .url("https://v4.ident.me")
                .build();

            try (
                Response response = QUICK_HTTP_CLIENT.newCall(request).execute()
            ) {
                if (response.isSuccessful() && response.body() != null) {
                    String ip = response.body().string().trim();
                    Log.d(
                        "DuckDNSUpdateWorker",
                        "Got public IP from v4.ident.me: " + ip
                    );
                    return ip;
                }
            }
        } catch (Exception e) {
            Log.e(
                "DuckDNSUpdateWorker",
                "Failed to get public IP: " + e.getMessage()
            );
        }
        return null;
    }

    /**
     * Resolve domain using multiple DNS servers
     *
     * @param domain Domain to resolve (e.g., mydomain.duckdns.org)
     * @return List of IP addresses resolved by each DNS server (may contain nulls)
     */
    private List<String> resolveDomainOnDnsServers(String domain) {
        List<String> results = new ArrayList<>();

        for (String dnsServer : DNS_SERVERS) {
            String resolvedIp = resolveDomainWithDns(domain, dnsServer);
            results.add(resolvedIp);

            if (resolvedIp != null) {
                Log.d(
                    "DuckDNSUpdateWorker",
                    "DNS " +
                        dnsServer +
                        " resolved " +
                        domain +
                        " to " +
                        resolvedIp
                );
            } else {
                Log.d(
                    "DuckDNSUpdateWorker",
                    "DNS " + dnsServer + " failed to resolve " + domain
                );
            }
        }

        return results;
    }

    /**
     * Resolve domain using a specific DNS server
     * Note: Android doesn't natively support specifying DNS servers for resolution,
     * so we use the system's default DNS which may not give us the control we want.
     * For a production implementation, consider using a DNS library or making
     * HTTP requests to DNS-over-HTTPS services.
     *
     * @param domain Domain to resolve
     * @param dnsServer DNS server IP (currently not directly used due to Android limitations)
     * @return Resolved IP address or null if failed
     */
    private String resolveDomainWithDns(String domain, String dnsServer) {
        try {
            // Note: InetAddress.getByName uses system DNS, not the specified server
            // For true per-server DNS resolution, we'd need to use DNS-over-HTTPS
            // or implement raw DNS queries, which is beyond basic Android APIs

            // Using DoH (DNS over HTTPS) as a workaround for specific DNS servers
            String dohUrl = null;
            if (dnsServer.equals("1.1.1.1")) {
                dohUrl = "https://1.1.1.1/dns-query?name=" + domain + "&type=A";
            } else if (dnsServer.equals("8.8.8.8")) {
                dohUrl = "https://8.8.8.8/resolve?name=" + domain + "&type=A";
            } else if (dnsServer.equals("208.67.222.222")) {
                // OpenDNS doesn't have public DoH, fallback to system DNS
                InetAddress addr = InetAddress.getByName(domain);
                return addr.getHostAddress();
            }

            if (dohUrl != null) {
                Request request = new Request.Builder()
                    .url(dohUrl)
                    .addHeader("accept", "application/dns-json")
                    .build();

                try (
                    Response response = QUICK_HTTP_CLIENT.newCall(
                        request
                    ).execute()
                ) {
                    if (response.isSuccessful() && response.body() != null) {
                        String body = response.body().string();
                        // Simple parsing for IP address in JSON response
                        // Looking for "Answer":[{"data":"x.x.x.x"}]
                        int dataIndex = body.indexOf("\"data\":\"");
                        if (dataIndex > 0) {
                            int startIndex = dataIndex + 8;
                            int endIndex = body.indexOf("\"", startIndex);
                            if (endIndex > startIndex) {
                                return body.substring(startIndex, endIndex);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(
                "DuckDNSUpdateWorker",
                "Failed to resolve " +
                    domain +
                    " with DNS " +
                    dnsServer +
                    ": " +
                    e.getMessage()
            );
        }
        return null;
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
