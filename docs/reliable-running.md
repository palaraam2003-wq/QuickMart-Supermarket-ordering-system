# Running RapidMart reliably on Windows

RapidMart requires a running Windows computer, Java 21+, and an accessible SQL Server database.
No local application can keep serving while the computer is powered off or asleep. This setup starts
at the current user's Windows sign-in; it is not a service that runs before sign-in or after sign-out.
For availability independent of this PC, deploy the application and database on an always-on server.

## Daily commands

Run these from the project folder in PowerShell. If script execution is restricted, prefix the command
with `powershell -NoProfile -ExecutionPolicy Bypass -File`.

| Command | Purpose |
| --- | --- |
| `.\rapidmart.ps1 -Action Start` | Start in the background, or reuse the managed instance. |
| `.\rapidmart.ps1 -Action Status` | Show process identity, liveness, database readiness, and automatic startup. |
| `.\rapidmart.ps1 -Action Stop` | Stop this project's supervisor and Java process. |
| `.\rapidmart.ps1 -Action Restart` | Stop and start the packaged application. |
| `.\rapidmart.ps1 -Action Build` | Run Maven tests, package and deploy an updated JAR; requires a stopped instance. |
| `.\rapidmart.ps1 -Action Install` | Register the current-user sign-in task and start now. |
| `.\rapidmart.ps1 -Action Uninstall` | Remove that task and stop the app; retain the database and files. |

Double-click `Start-RapidMart.cmd` for the same behavior as `Action Start`.
After changing source code: stop, build, then start. Restart alone reuses the packaged version.
Build failures preserve the previous deployed JAR. A successful deployment retains one previous JAR
as `runtime/rapidmart.jar.previous`. Runtime files are independent of Maven's `target` directory.

The default URL is <http://localhost:8080>. Choose another port with `-Action Start -Port 8081`
after stopping the managed instance. The selected port and profile are saved in `runtime/settings.json`.
An already-running instance retains its current port until stopped. `-Profile prod` requires production
database credentials and an already-provisioned schema; the default `dev` profile uses Windows authentication.

Once installed, normal starts use the scheduled task and its user environment. Use `-CurrentEnvironment`
with Start/Restart to inherit variables set only in the current terminal; this directly starts a supervisor
outside Task Scheduler, so Java recovery remains active but the task cannot restart that supervisor itself.

Do not also launch `mvnw spring-boot:run` or the IDE server on the same port. A different listener is
reported without being stopped. Manual Maven runs remain available for development but do not use this supervisor.

## Recovery and monitoring

The supervisor uses an exclusive file lock to prevent duplicate supervisors. Java is identified by PID,
creation time and this project's command line before an existing process is adopted or stopped.
An unexpected Java exit is retried with a delay that increases from 5 to 60 seconds.
Startup has a 180-second grace period. Three failed liveness checks after that period restart the owned
Java process. Windows Task Scheduler also retries a failed supervisor once per minute (up to 999 times).

`GET /actuator/health/liveness` checks the application process. `GET /actuator/health/readiness` also
checks SQL Server. A database outage reports unready without triggering repeated Java restarts;
the connection pool can reconnect when SQL Server returns. SMTP is optional and does not control readiness.
Only health endpoints are exposed, with component details hidden. See the
[Spring Boot health documentation](https://docs.spring.io/spring-boot/3.3/reference/actuator/endpoints.html).

The supervisor's forced stop/recovery ends Java immediately on Windows; in-flight requests can fail and
in-memory sessions are lost. Spring's graceful shutdown is enabled for environments that deliver a
supported shutdown signal. The launcher does not expose a public shutdown endpoint.

## Logs and resources

- `logs/rapidmart.log`: application log, rolling at 10 MB, retaining seven days with a 100 MB archive cap.
- `runtime/supervisor.log`: restart/health events, with one previous 1 MB file.
- `runtime/java-error.log` and `java-console.log`: JVM startup output, retaining the previous launch.
- Java defaults to a 512 MB maximum heap. Set `RAPIDMART_JAVA_MAX_HEAP=1g` before launching to change it.

Normal background execution writes Spring logs only to the rolling log, not an unlimited console copy.
Older `rapidmart-*.log` files have been left untouched. Java exits on an out-of-memory error so the
supervisor can recover; investigate repeated memory failures rather than relying on restarts alone.

## Database and email

The dev profile accepts `RAPIDMART_DB_URL`, `RAPIDMART_DB_USERNAME`, and `RAPIDMART_DB_PASSWORD` overrides.
Its default remains the local `SQLEXPRESS` instance and `RapidMartDB` with Windows authentication.
The sign-in task runs as the installing Windows user, who must have SQL Server access.
SQL Server and SQL Browser should start automatically. Schema changes are applied with the canonical
SSMS script, not Flyway; keep regular database backups separately.

`Start-RapidMart-Email.cmd` now restarts the managed server with SMTP credentials entered at the prompt.
Those credentials are inherited only by that supervisor and are not saved. They survive Java crash
restarts but not supervisor exit, sign-out, or reboot. Run the email launcher again after signing in,
or configure the documented mail environment variables securely for the task's user.
SMTP failure is reported when sending mail and no longer prevents the whole app from starting.

## Verification scope

The automated suite covers authentication, profiles, catalogue, inventory races, checkout, pricing,
payments, billing, refunds, loyalty, delivery/COD, driver limits and reporting. Health tests check
anonymous probes and restricted management endpoints. Browser JavaScript includes staff-login tests.
These checks reduce known failures; they do not prove every browser workflow or future infrastructure
condition is error-free. Database backups, dependency updates and ongoing monitoring are still needed.
