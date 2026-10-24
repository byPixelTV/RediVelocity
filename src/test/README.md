# Redis lifecycle regression tests

These tests execute the production Lua resources against real Redis, including
concurrent elections, registration, reconnects, and cleanup. Python uses only its
standard library. Kotlin compilation is checked separately with `gradlew compileKotlin`.

Use a **disposable** Redis instance: the suite flushes database 15 before every test.

PowerShell:

```powershell
docker run --detach --rm --name redivelocity-regression-redis -p 127.0.0.1:16389:6379 redis:8-alpine redis-server --save "" --appendonly no
$env:REDIVELOCITY_TEST_REDIS_PORT = '16389'
python -m unittest discover -s src/test/python -v
docker stop redivelocity-regression-redis
```

The suite skips if the environment variable is absent. No production Redis
connection settings are read.

## Lifecycle behavior

- Redis time determines liveness. Heartbeats and registration are atomic and do
  not rely on hash-field expiration. Cleanup considers a heartbeat stale after
  90 seconds; election requires a registered proxy seen within 30 seconds.
- A healthy leader remains leader; the previous random vote/forced rotation
  mechanism is replaced by atomic selection of the first live, sorted proxy ID.
- Startup atomically claims a process-specific ownership token. Simultaneous
  claims of the same ID cannot both succeed. A stale process cannot mutate or
  shut down its replacement. A process that loses ownership logs an error and
  must restart to acquire a new registration.
- Startup and periodic cleanup discover stale proxy metadata even without a
  registry entry, and remove partial player records lacking proxy ownership.
  Each deletion rechecks current ownership/liveness inside Redis.
- Login settings and anti-VPN configuration/cache keys are persistent and are
  intentionally outside transient lifecycle cleanup.

Deploy this protocol change to all proxies together. Older versions still write
without ownership checks and use the old voting/cleanup protocol, so mixed-version
operation cannot provide these guarantees. The scripts target the existing
standalone Redis database configuration, not Redis Cluster.

## Lowering the player limit

- Successful logins record Redis time (Unix microseconds) in the sorted set
  `redivelocity:player:joined-at`. Server switches keep this time; reconnects
  replace it. Disconnect, shutdown, and crash cleanup remove it.
- Lowering an enforced limit selects the newest players across all proxies.
  Bypass players count toward the total, as with the login check, but cannot be
  kicked. If only bypass players remain, the total may exceed the limit.
- Each command carries a request ID. Redis freezes its selection for five
  minutes so every proxy uses the same player/session pairs even while selected
  players disconnect. A stale selection cannot target a replacement session.
- Bypass status is recorded at login and refreshed every two seconds. The
  configured permission is checked again immediately before disconnecting.
  The disconnect reason uses the same `kick-network-full` message as login.
- Increasing or repeating the limit does not kick players. `-1` disables the
  limit; zero removes all eligible players. Both login configuration and limit
  enforcement must be enabled for kicks.
