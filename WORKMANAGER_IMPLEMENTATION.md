# WorkManager Implementation for Intervals Under 15 Minutes

## Overview

This document explains how the Simple-DuckDNS-Updater app implements periodic background updates with intervals **less than 15 minutes** using Android's WorkManager API.

## The Problem

WorkManager's `PeriodicWorkRequest` has a hard-coded minimum interval of **15 minutes** (`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS = 900000ms`). This limitation is by design, as Google intends WorkManager for tasks that are deferrable and not time-critical.

However, for DuckDNS updates, users may want more frequent updates (e.g., every 1, 5, or 10 minutes) to ensure their dynamic DNS records stay current.

## The Solution

Instead of using `PeriodicWorkRequest`, we use a **self-rescheduling `OneTimeWorkRequest`** pattern. Each worker execution schedules the next execution at the end of its work cycle.

### Architecture

```
┌─────────────────────────────────────────────┐
│  MainActivity                               │
│  - User sets interval (e.g., 5 minutes)    │
│  - Calls DuckDNSUpdateWorker.start()       │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│  DuckDNSUpdateWorker (OneTimeWorkRequest)  │
│                                             │
│  1. Execute DuckDNS update                 │
│  2. Log results                            │
│  3. Schedule next execution                │
│     (setInitialDelay = interval)           │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
           ┌───────────────┐
           │  Repeats...   │
           └───────────────┘
```

## Implementation Details

### Key Components

#### 1. Worker Self-Rescheduling

```java
private void scheduleNextExecution(Context context, int intervalMinutes) {
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
```

#### 2. Interval Parameter Passing

The interval is passed as input data to each worker execution:

```java
int intervalMinutes = getInputData().getInt(KEY_INTERVAL_MINUTES, 15);
```

This ensures each worker knows when to schedule the next execution.

#### 3. Unique Work Naming

We use `enqueueUniqueWork()` with `ExistingWorkPolicy.REPLACE` to ensure:
- Only one worker chain runs at a time
- New schedules replace old ones
- No duplicate workers accumulate

### Starting Periodic Work

From MainActivity or any component:

```java
// Start updates every 5 minutes
DuckDNSUpdateWorker.startPeriodicWork(context, 5);
```

### Stopping Periodic Work

```java
// Stop all scheduled updates
DuckDNSUpdateWorker.stopPeriodicWork(context);
```

## Why This Approach?

### ✅ Advantages

1. **No Thread.sleep()**: Unlike some solutions found online, we don't block worker threads
2. **Efficient**: Workers only run when needed, not continuously
3. **Battery Friendly**: System can optimize when to run based on constraints
4. **Survives Restarts**: WorkManager persists across app restarts
5. **Reliable**: WorkManager handles system constraints and retries
6. **Future-Proof**: Uses official Android APIs that will be maintained
7. **Flexible Intervals**: Supports any interval from 1 minute to unlimited

### ❌ Rejected Alternatives

#### Thread.sleep() in doWork()
```java
// DON'T DO THIS!
public Result doWork() {
    while (true) {
        performUpdate();
        Thread.sleep(60000 * 5); // Blocks thread for 5 minutes
    }
}
```

**Problems:**
- Wastes system resources keeping thread alive
- Worker has a 10-minute execution limit
- Poor battery efficiency
- Doesn't survive app restarts properly

#### AlarmManager
```java
// Old approach, deprecated
AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
alarmManager.setRepeating(...);
```

**Problems:**
- Deprecated for background work
- Doesn't handle system constraints
- Less reliable on Android 6.0+
- Not compatible with modern battery optimization

## Behavior and Guarantees

### What to Expect

1. **First Execution**: After scheduling, first update runs after the specified interval
2. **Subsequent Executions**: Each execution automatically schedules the next one
3. **Stopping**: Cancels the entire chain, no more executions occur
4. **App Restart**: Chain continues after app restart (WorkManager persistence)
5. **System Constraints**: WorkManager respects battery saver, doze mode, etc.

### Timing Accuracy

- **Not Real-Time**: WorkManager is for deferrable tasks, not real-time updates
- **Approximate Timing**: Actual execution may vary by a few minutes based on system conditions
- **Guaranteed Execution**: Updates will eventually run, even if delayed
- **Best Effort**: Android optimizes timing for battery efficiency

### Edge Cases Handled

1. **Configuration Missing**: Worker completes successfully, reschedules anyway
2. **Network Failure**: Worker returns failure, still reschedules
3. **Crash/Exception**: Worker catches exceptions, reschedules before returning
4. **System Restart**: WorkManager automatically restores the work chain

## Configuration

### Minimum Interval

You can set any interval ≥ 1 minute:

```java
DuckDNSUpdateWorker.startPeriodicWork(context, 1);  // 1 minute
DuckDNSUpdateWorker.startPeriodicWork(context, 5);  // 5 minutes
DuckDNSUpdateWorker.startPeriodicWork(context, 15); // 15 minutes
DuckDNSUpdateWorker.startPeriodicWork(context, 60); // 1 hour
```

### System Constraints

Currently, no constraints are applied. To add constraints (e.g., require network):

```java
Constraints constraints = new Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build();

OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(...)
    .setConstraints(constraints)
    .build();
```

## Logging and Debugging

The implementation includes comprehensive logging:

```
D/DuckDNSUpdateWorker: Worker started - doWork() called
D/DuckDNSUpdateWorker: Working with interval: 5 minutes
D/DuckDNSUpdateWorker: Read config - domains: example, token: present
D/DuckDNSUpdateWorker: Starting DuckDNS update for domains: example
D/DuckDNSUpdateWorker: HTTP Response Code: 200
D/DuckDNSUpdateWorker: Response body: OK
D/DuckDNSUpdateWorker: Update completed successfully: true
D/DuckDNSUpdateWorker: Scheduling next execution in 5 minutes
D/DuckDNSUpdateWorker: Successfully scheduled next execution
```

## Testing

### Manual Testing

1. Set interval to 1 minute for quick testing
2. Schedule auto-update
3. Check logs every minute to verify execution
4. Verify each execution schedules the next one
5. Stop auto-update and verify no more executions

### Verification

```bash
# View WorkManager logs
adb logcat -s WM-WorkerWrapper DuckDNSUpdateWorker

# Check scheduled work
adb shell dumpsys jobscheduler | grep duckdns
```

## Performance Considerations

### Battery Impact

- **Minimal when idle**: Worker only runs at scheduled times
- **No constant polling**: Not continuously checking or looping
- **System optimized**: Android batches work when possible

### Memory Usage

- **Low footprint**: Worker released after each execution
- **No memory leaks**: No persistent threads or connections
- **Efficient**: Only loads when needed

### Network Usage

- **Single HTTP request**: One lightweight request per execution
- **Small payload**: DuckDNS API response is minimal (2-3 bytes)
- **Configurable**: Can add network constraints if needed

## Future Enhancements

Possible improvements for future versions:

1. **Exponential Backoff**: Increase interval on repeated failures
2. **Network Constraints**: Only run when connected to WiFi
3. **Battery Constraints**: Skip updates when battery is low
4. **Adaptive Intervals**: Adjust frequency based on IP change detection
5. **Foreground Service**: For very frequent updates (requires user notification)

## Compatibility

- **Minimum Android Version**: API 21 (Android 5.0 Lollipop)
- **Tested On**: Android 5.0 - Android 14
- **Future Compatible**: Uses stable WorkManager APIs
- **Recommended For**: All modern Android devices

## References

- [WorkManager Documentation](https://developer.android.com/topic/libraries/architecture/workmanager)
- [WorkManager Advanced Topics](https://developer.android.com/topic/libraries/architecture/workmanager/advanced)
- [Background Work Guide](https://developer.android.com/guide/background)
- [DuckDNS API Documentation](https://www.duckdns.org/spec.jsp)

## Conclusion

This implementation provides a reliable, efficient, and future-proof solution for periodic DuckDNS updates with intervals under 15 minutes. By using self-rescheduling OneTimeWorkRequests instead of PeriodicWorkRequests or deprecated approaches, we achieve the flexibility needed while maintaining compatibility with Android's background execution policies.