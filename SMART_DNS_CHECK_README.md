# Smart DNS Check - README

## Overview

The **Smart DNS Check** is an intelligent feature that automatically verifies if your DuckDNS domains are already updated before making an API call. This reduces the load on DuckDNS servers, avoiding unnecessary updates. Why test with three different global DNS servers? Because a bird suggested we do it :)

## How It Works

### Control Flow

```
┌─────────────────────────────────────────┐
│  Update Requested (Manual or Auto)      │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  Determine Target IP                    │
│  • If not configured → v4.ident.me      │
│  • If configured → use set IP           │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  Resolve Domain on 3 DNS Servers        │
│  • 1.1.1.1 (Cloudflare, DoH)            │
│  • 8.8.8.8 (Google, DoH)                │
│  • 208.67.222.222 (OpenDNS, dns)        │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  Compare Target IP with DNS Results     │
└────────────────┬────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
        ▼                 ▼
┌──────────────┐  ┌───────────────────┐
│  2+ Mismatch │  │  0-1 Mismatch     │
│  EXECUTE     │  │  SKIP             │
│  UPDATE      │  │  (already updated)│
└──────────────┘  └───────────────────┘
```

## Use Cases

### Scenario A: IP Not Configured (Auto-detect)

**Configuration:**
- Domain: `mydomain`
- Token: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
- IP: `[empty]`

**What Happens:**

1. **First Execution:**
   ```
   → Get public IP from v4.ident.me: 123.45.67.89
   → DNS Query 1.1.1.1: 111.111.111.111 (different!)
   → DNS Query 8.8.8.8: 111.111.111.111 (different!)
   → DNS Query 208.67.222.222: 111.111.111.111 (different!)
   → 3 DNS with different IP → EXECUTE UPDATE
   
   Log: [2024-01-15 10:30:00] Manual Update: mydomain - SUCCESS (OK)
   ```

2. **Second Execution (after 30 seconds):**
   ```
   → Get public IP from v4.ident.me: 123.45.67.89
   → DNS Query 1.1.1.1: 123.45.67.89 (same!)
   → DNS Query 8.8.8.8: 123.45.67.89 (same!)
   → DNS Query 208.67.222.222: 123.45.67.89 (same!)
   → 0 DNS with different IP → SKIP UPDATE
   
   Log: [2024-01-15 10:30:30] Manual Update: mydomain - SKIPPED (DNS already up to date with IP: 123.45.67.89)
   ```

### Scenario B: Manually Configured IP

**Configuration:**
- Domain: `mydomain`
- Token: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
- IP: `192.168.1.100`

**What Happens:**

1. **First Execution:**
   ```
   → Use configured IP: 192.168.1.100
   → DNS Query 1.1.1.1: 123.45.67.89 (different!)
   → DNS Query 8.8.8.8: 123.45.67.89 (different!)
   → DNS Query 208.67.222.222: 123.45.67.89 (different!)
   → 3 DNS with different IP → EXECUTE UPDATE
   
   Log: [2024-01-15 10:40:00] Manual Update: mydomain [IP: 192.168.1.100] - SUCCESS (OK)
   ```

2. **Second Execution (after DNS propagation):**
   ```
   → Use configured IP: 192.168.1.100
   → DNS Query 1.1.1.1: 192.168.1.100 (same!)
   → DNS Query 8.8.8.8: 192.168.1.100 (same!)
   → DNS Query 208.67.222.222: 192.168.1.100 (same!)
   → 0 DNS with different IP → SKIP UPDATE
   
   Log: [2024-01-15 10:41:00] Manual Update: mydomain - SKIPPED (DNS already up to date with IP: 192.168.1.100)
   ```

## Multiple Domains

The system supports multiple domains separated by commas:

**Configuration:**
- Domains: `domain1,domain2,domain3`

**Behavior:**
- If **at least one domain** has 2+ DNS with different IP → UPDATE executed for all
- If **all domains** have 0-1 DNS with different IP → SKIP
- The log shows all domains together

**Example:**
```
[2024-01-15 11:00:00] AutoUpdate: domain1,domain2,domain3 - SKIPPED (DNS already up to date with IP: 123.45.67.89)
```

## DNS Servers Used

### 1. Cloudflare (1.1.1.1)
- **Method:** DNS-over-HTTPS
- **Endpoint:** `https://1.1.1.1/dns-query?name=<domain>&type=A`
- **Timeout:** 2 seconds
- **Pro:** Fast, privacy-focused, global

### 2. Google Public DNS (8.8.8.8)
- **Method:** DNS-over-HTTPS
- **Endpoint:** `https://8.8.8.8/resolve?name=<domain>&type=A`
- **Timeout:** 2 seconds
- **Pro:** Reliable, well-maintained, global

### 3. OpenDNS (208.67.222.222)
- **Method:** System DNS (fallback)
- **Endpoint:** Native `InetAddress.getByName()`
- **Timeout:** 2 seconds
- **Note:** No public DoH available, uses system resolution

## 2/3 Rule

The system uses a **majority rule** to decide whether to execute the update:

- ✅ **2 or more DNS with different IP** = UPDATE executed
- ⏭️ **0 or 1 DNS with different IP** = UPDATE skipped

### Why?

1. **DNS Propagation Tolerance:** DNS servers don't update instantaneously
2. **Reliability:** Avoids false positives from a single slow DNS
3. **Reduces Unnecessary Updates:** Avoids DuckDNS calls when not needed

### Examples

| DNS 1.1.1.1 | DNS 8.8.8.8 | DNS OpenDNS | Decision | Reason |
|-------------|-------------|-------------|-----------|------|
| 1.2.3.4 | 1.2.3.4 | 1.2.3.4 | SKIP | All updated |
| 1.2.3.4 | 1.2.3.4 | **5.6.7.8** | SKIP | Only 1 different (tolerance) |
| 1.2.3.4 | **5.6.7.8** | **5.6.7.8** | UPDATE | 2+ different |
| **5.6.7.8** | **5.6.7.8** | **5.6.7.8** | UPDATE | All different |
| 1.2.3.4 | null | null | SKIP | 2 DNS failed (fail-safe) |

## Timeout and Performance

### Configured Timeouts

| Operation | Timeout | Retry | Max Total |
|------------|---------|-------|------------|
| v4.ident.me | 2 sec | No | 2 sec |
| DNS 1.1.1.1 | 2 sec | No | 2 sec |
| DNS 8.8.8.8 | 2 sec | No | 2 sec |
| DNS OpenDNS | 2 sec | No | 2 sec |
| **Total Check** | - | - | **~8 sec** |
| DuckDNS Update | 15 sec | No | 15 sec |

### Typical Performance

- **Fast WiFi:** 2-4 seconds
- **4G Network:** 3-6 seconds
- **3G Network:** 5-8 seconds
- **With Timeout:** Max 8 seconds

### Battery Impact

- ⚡ **Minimal:** Only light HTTP requests
- 📊 **Data Traffic:** ~2-3 KB per check (vs ~1 KB per DuckDNS update)
- 🔋 **Background:** WorkManager handles efficiency

## Error Handling

### Fail-Safe Design

The system is designed to be **fail-safe**: in case of error, **it proceeds with the update**.

### Error Scenarios

1. **v4.ident.me unreachable:**
   - Log: `Failed to get public IP`
   - Action: Proceeds with UPDATE (safe)

2. **DNS Server not responding:**
   - Log: `Failed to resolve <domain> with DNS <server>`
   - Result: `null` for that DNS
   - Action: Evaluates other DNS, if unsure → UPDATE

3. **DNS Timeout:**
   - After 2 seconds: Stop query
   - Result: `null` for that DNS
   - Action: Continue with other DNS

4. **JSON parsing failed:**
   - Log: `Failed to parse DoH response`
   - Result: `null` for that DNS
   - Action: Continue with other DNS

5. **Generic exception:**
   - Log: `Error checking if update needed`
   - Action: Proceeds with UPDATE (safe)

### Error Logging

All errors are logged via Android Log:
```java
Log.e("MainActivity", "Failed to get public IP: " + e.getMessage());
Log.d("DuckDNSUpdateWorker", "DNS 1.1.1.1 failed to resolve mydomain");
```

## Benefits

### For the User

✅ **Less Log Spam:** Cleaner logs without unnecessary repeated updates  
✅ **Battery Savings:** Fewer API calls = less work  
✅ **Data Savings:** Reduces traffic on limited connections  
✅ **Transparency:** Clear logs show when and why updates are skipped  

### For DuckDNS

✅ **Reduced Server Load:** Fewer unnecessary API requests  
✅ **Efficiency:** Only update when truly needed  
✅ **Fair Use:** More responsible use of the free service  

## Limitations

### Technical Limitations

1. **OpenDNS:** Uses system DNS (no public DoH available)
2. **DNS Propagation:** May take 30-60 seconds
3. **Firewall:** Some firewalls might block DoH (port 443)
4. **IPv6:** Currently supports only IPv4 (A records)

### Android Limitations

- `InetAddress.getByName()` uses system DNS, not specific servers
- DoH is the only way to query specific DNS servers on Android
- Manual JSON parsing (no library to keep app lightweight)

## Advanced Configuration

### Changing DNS Servers

To use different DNS servers, modify the constants:

```java
// In DuckDNSUpdateWorker.java and MainActivity.java
private static final String[] DNS_SERVERS = {
    "1.1.1.1",      // Cloudflare
    "8.8.8.8",      // Google
    "208.67.222.222" // OpenDNS
};
```

### Changing Timeouts

For different timeouts, modify the HTTP client:

```java
private static final OkHttpClient QUICK_HTTP_CLIENT =
    new OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)  // Was 2
        .readTimeout(3, TimeUnit.SECONDS)     // Was 2
        .writeTimeout(3, TimeUnit.SECONDS)    // Was 2
        .build();
```

### Changing the 2/3 Rule

To change when to execute the update:

```java
// Instead of: if (mismatchCount >= 2)
// Use:
if (mismatchCount >= 1)  // Update if even just 1 DNS different
if (mismatchCount >= 3)  // Update only if all 3 DNS different
```

## Troubleshooting

### "Update always executed, never skipped"

**Diagnosis:**
```bash
adb logcat | grep "DNS.*resolved"
```

**Possible causes:**
- Public IP changes with each check (very dynamic ISP)
- DNS not propagated yet
- Wrong or non-existent domain

**Solutions:**
- Test with manually configured IP
- Wait 1-2 minutes between tests
- Verify domain on duckdns.org

### "App freezes during check"

**Diagnosis:**
```bash
adb logcat | grep -E "(MainActivity|DuckDNSUpdateWorker)"
```

**Possible causes:**
- quickHttpClient not initialized
- Operations on main thread

**Solutions:**
- Verify onCreate() initializes quickHttpClient
- Confirm performUpdate() runs in background thread

### "DNS check always fails"

**Diagnosis:**
```bash
adb logcat | grep "Failed to resolve"
```

**Possible causes:**
- Firewall blocks HTTPS (port 443)
- DNS over HTTPS not supported by network
- v4.ident.me unreachable

**Solutions:**
- Test on different network (WiFi vs 4G)
- Verify 1.1.1.1 and 8.8.8.8 from browser
- Check corporate/school firewall

## FAQ

**Q: Does it work with IPv6?**  
A: No, currently supports only IPv4 (A record). IPv6 (AAAA) is not implemented.

**Q: Can I disable DNS checking?**  
A: Not currently. The check is always active to optimize API calls.

**Q: Why 3 DNS servers?**  
A: To get a more reliable view of DNS propagation status and reduce false positives.

**Q: How much time does the DNS check add?**  
A: 2-8 seconds on average. Fast networks 2-4 seconds, slow networks up to 8 seconds (timeout).

**Q: What happens if all DNS fail?**  
A: The system is fail-safe: proceeds with normal update to ensure the domain is updated.

**Q: Does it work with custom domains (not .duckdns.org)?**  
A: Yes! The system automatically handles short domains (adds .duckdns.org) and full domains.

**Q: Can I see check details in the app logs?**  
A: Details are in Android logs (ADB). App logs only show final result (SUCCESS/SKIP/FAILED).

## Contributing

To improve this feature:

1. Add IPv6 support
2. Implement local DNS caching
3. Make DNS servers configurable from UI
4. Add skip/update statistics
5. Implement native DoH for OpenDNS

See `CONTRIBUTING.md` for details.

## References

- [DuckDNS API Documentation](https://www.duckdns.org/spec.jsp)
- [Cloudflare DoH](https://developers.cloudflare.com/1.1.1.1/encryption/dns-over-https/)
- [Google Public DNS JSON API](https://developers.google.com/speed/public-dns/docs/doh/json)
- [OkHttp Documentation](https://square.github.io/okhttp/)

---
