package com.simple.duckdns.updater;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    }

    private boolean loadConfigFromFile() {
        try {
            File configFile = new File(getFilesDir(), CONFIG_FILE);

            if (!configFile.exists()) {
                return false;
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
            File configFile = new File(getFilesDir(), CONFIG_FILE);
            FileWriter writer = new FileWriter(configFile);
            writer.write("domains=" + domains + "\n");
            writer.write("token=" + token + "\n");
            writer.write("ip=" + ip + "\n");
            writer.write("interval=" + interval + "\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void writeLogSync(String message) {
        try {
            File logFile = new File(getFilesDir(), LOG_FILE);
            FileWriter writer = new FileWriter(logFile, true);
            writer.write(message + "\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLog() {
        try {
            File logFile = new File(getFilesDir(), LOG_FILE);
            if (logFile.exists()) {
                BufferedReader reader = new BufferedReader(
                    new FileReader(logFile)
                );
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                reader.close();

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

            Intent intent = new Intent(this, UpdateReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager alarmManager = (AlarmManager) getSystemService(
                Context.ALARM_SERVICE
            );
            long intervalMillis = interval * 60 * 1000L;

            // Start first update after 10 seconds, then repeat at the specified interval
            long firstUpdateTime = System.currentTimeMillis() + 10000;

            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                firstUpdateTime,
                intervalMillis,
                pendingIntent
            );

            showSnackbar(
                "Scheduled update every " +
                    interval +
                    " minutes (first in 10s)",
                "success"
            );

            isScheduled = true;
            scheduleToggleButton.setText("Stop AutoUpdate");
            scheduleToggleButton.setBackgroundColor(android.graphics.Color.RED);
        } catch (NumberFormatException e) {
            showSnackbar("Invalid interval", "error");
        } catch (Exception e) {
            e.printStackTrace();
            showSnackbar("Error scheduling update: " + e.getMessage(), "error");
        }
    }

    private void stopSchedule() {
        Intent intent = new Intent(this, UpdateReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(
            Context.ALARM_SERVICE
        );
        alarmManager.cancel(pendingIntent);

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
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
