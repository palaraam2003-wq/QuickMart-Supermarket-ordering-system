# Reliability review — 27 September 2026

## Findings and changes

| Finding | Change |
| --- | --- |
| A second manual Maven/IDE server can collide with port 8080. | Added a single-instance background launcher; repeat starts reuse it. Other listeners are reported without being killed. |
| The Oracle Java PATH shim starts a separate JVM; terminating the shim can leave the server alive. | Resolve `java.home` and launch the real JVM directly, with PID, creation-time and command-line ownership checks. |
| A terminal session was responsible for keeping the application running. | Added a packaged JAR supervisor with crash recovery, health monitoring, bounded retry delays and a Windows sign-in task installer. |
| Overdue history was persisted but absent from an already-loaded entity's history list. | Keep both sides of the delivery/history relationship synchronized so delayed-delivery reporting sees the event immediately. |
| Two delivery tests still expected old behavior or used the wrong COD amount. | Updated the expected on-time status and used the actual order total without weakening COD validation. |
| Test startup attempted unsupported alterations of indexed SQL Server columns. | Use schema validation against the manually provisioned test database, matching dev/prod. |
| Logs and redirected console output could grow indefinitely. | Add rotating application/supervisor logs and avoid a duplicate unbounded console stream in supervised mode. |
| SMTP validation could prevent the whole application from starting. | The email launcher now starts the managed server and checks SMTP during actual sends. |
| README claimed Flyway recreated the database, but this project has no Flyway dependency. | Corrected setup and schema-maintenance guidance to use the canonical SSMS script. |

## Verification performed

- Full Maven package: **64 tests, zero failures/errors/skips** after the changes.
- Staff-login JavaScript: **7 tests passed**; all **10 JavaScript files** passed syntax checks.
- Packaged application: homepage, staff login, public products/categories, liveness and SQL-backed readiness returned successfully.
- Repeated Start retained the same process identity.
- Simulated crash of the owned JVM recovered automatically and restored readiness.
- An occupied test port was refused; the existing listener remained running.
- Stop shut down the owned JVM and released its port.
- Installed the current-user sign-in task `RapidMart-0B6933370DD5`; verified the supervised application was live and database-ready on port 8080 afterward.

Build evidence is in `target/reliability-build-final.log`. Operational evidence is in
`runtime/supervisor.log`. Reusable smoke checks are in `src/test/powershell/reliability-smoke.ps1`;
`-TestCrashRecovery` and `-TestPortConflict` deliberately interrupt the managed instance and should
only be used during maintenance.

This was a startup/recovery and automated-regression review, not an exhaustive security audit or
proof that every browser interaction is error-free. Actual Windows reboot/sign-out, a real SQL Server
outage and SMTP delivery were not tested. Continuous availability still depends on the computer,
Windows session, database, network and configured credentials. See [the operations guide](reliable-running.md).
