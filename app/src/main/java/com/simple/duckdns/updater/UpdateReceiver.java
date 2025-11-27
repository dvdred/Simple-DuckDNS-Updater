package com.simple.duckdns.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateReceiver extends BroadcastReceiver {

    private static final String LOG_FILE = "duckdns_log.txt";
    private static final String CONFIG_FILE = "duckdns_config.txt";
    private static final String LOG_UPDATED_ACTION =
        "com.simple.duckdns.updater.LOG_UPDATED";
    private static final DateTimeFormatter LOG_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            // Log that the receiver is starting
            String startMessage =
                "[" +
                LocalDateTime.now().format(LOG_DATE_FORMAT) +
                "] AutoUpdate triggered";
            writeLog(context, startMessage);

            // Notify MainActivity that there might be new logs
            notifyLogUpdate(context);

            // Read configuration from file
            String[] config = readConfigFromFile(context);
            String domains = config[0];
            String token = config[1];
            String ip = config[2];

            if (domains.isEmpty() || token.isEmpty()) {
                String message =
                    "[" +
                    LocalDateTime.now().format(LOG_DATE_FORMAT) +
                    "] AutoUpdate FAILED - No configuration found";
                writeLog(context, message);
                notifyLogUpdate(context);
                return;
            }

            // Perform the actual DuckDNS update
            performDuckDNSUpdate(context, domains, token, ip);
        } catch (Exception e) {
            e.printStackTrace();
            writeLog(
                context,
                "[" +
                    LocalDateTime.now().format(LOG_DATE_FORMAT) +
                    "] AutoUpdate ERROR: " +
                    e.getMessage()
            );
            notifyLogUpdate(context);
        }
    }

    private void performDuckDNSUpdate(
        Context context,
        String domains,
        String token,
        String ip
    ) {
        try {
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

            // Create HTTP client
            OkHttpClient httpClient = new OkHttpClient();

            // Create HTTP request
            Request request = new Request.Builder().url(url).build();

            // Make HTTP request and get response
            httpClient
                .newCall(request)
                .enqueue(
                    new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            String timestamp = LocalDateTime.now().format(
                                LOG_DATE_FORMAT
                            );
                            String errorMessage = String.format(
                                "[%s] %s - ERROR: Network failure - %s",
                                timestamp,
                                domains,
                                e.getMessage()
                            );
                            writeLog(context, errorMessage);
                            notifyLogUpdate(context);
                        }

                        @Override
                        public void onResponse(Call call, Response response)
                            throws IOException {
                            try {
                                // Get response code
                                int responseCode = response.code();

                                // Read response body
                                String responseBody = "";
                                if (response.body() != null) {
                                    responseBody = response.body().string();
                                }

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
                                String timestamp = LocalDateTime.now().format(
                                    LOG_DATE_FORMAT
                                );
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

                                // Notify MainActivity that there are new logs
                                notifyLogUpdate(context);
                            } catch (Exception e) {
                                String timestamp = LocalDateTime.now().format(
                                    LOG_DATE_FORMAT
                                );
                                String errorMessage = String.format(
                                    "[%s] %s - ERROR: %s",
                                    timestamp,
                                    domains,
                                    e.getMessage()
                                );
                                writeLog(context, errorMessage);
                                notifyLogUpdate(context);
                            }
                        }
                    }
                );
        } catch (Exception e) {
            e.printStackTrace();
            String timestamp = LocalDateTime.now().format(LOG_DATE_FORMAT);
            String errorMessage = String.format(
                "[%s] %s - ERROR: %s",
                timestamp,
                domains,
                e.getMessage()
            );
            writeLog(context, errorMessage);
            notifyLogUpdate(context);
        }
    }

    private String[] readConfigFromFile(Context context) {
        String[] config = new String[] { "", "", "" }; // domains, token, ip

        try {
            File configFile = new File(context.getFilesDir(), CONFIG_FILE);

            if (!configFile.exists()) {
                writeLog(
                    context,
                    "[" +
                        LocalDateTime.now().format(LOG_DATE_FORMAT) +
                        "] Config file not found"
                );
                return config;
            }

            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                new FileReader(configFile)
            );
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            String configContent = content.toString();

            // Extract domains
            int domainsIndex = configContent.indexOf("domains=");
            if (domainsIndex != -1) {
                int startIndex = domainsIndex + "domains=".length();
                int endIndex = configContent.indexOf("\n", startIndex);
                if (endIndex == -1) endIndex = configContent.length();
                config[0] = configContent
                    .substring(startIndex, endIndex)
                    .trim();
            }

            // Extract token
            int tokenIndex = configContent.indexOf("token=");
            if (tokenIndex != -1) {
                int startIndex = tokenIndex + "token=".length();
                int endIndex = configContent.indexOf("\n", startIndex);
                if (endIndex == -1) endIndex = configContent.length();
                config[1] = configContent
                    .substring(startIndex, endIndex)
                    .trim();
            }

            // Extract ip
            int ipIndex = configContent.indexOf("ip=");
            if (ipIndex != -1) {
                int startIndex = ipIndex + "ip=".length();
                int endIndex = configContent.indexOf("\n", startIndex);
                if (endIndex == -1) endIndex = configContent.length();
                config[2] = configContent
                    .substring(startIndex, endIndex)
                    .trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    private void writeLog(Context context, String message) {
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE);
            FileWriter writer = new FileWriter(logFile, true);
            writer.append(message).append("\n");
            writer.close();
        } catch (Exception e) {
            // Log to system log as fallback in case of file system issues
            android.util.Log.e(
                "UpdateReceiver",
                "Failed to write log: " + message,
                e
            );
        }
    }

    private void notifyLogUpdate(Context context) {
        Intent logUpdateIntent = new Intent(LOG_UPDATED_ACTION);
        context.sendBroadcast(logUpdateIntent);
    }
}
