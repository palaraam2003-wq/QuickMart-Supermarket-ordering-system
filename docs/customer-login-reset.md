# Login and customer password reset

Customer login: `/login.html`. Staff login: `/staff-login.html`.
Only customer login offers registration and forgotten-password links.
Customer email must be valid and end in `.com`; staff email must end in
`@rapidmart.local`. Roles are checked before a session is created.
Existing customer `.local` demo addresses are no longer eligible; do not replace
real account addresses with invented email addresses.

Apply `database/customer-password-reset.sql` to existing RapidMartDB and
RapidMartTestDB databases before using reset. It preserves existing data.

Login POST `/api/v1/auth/login`: `{email,password,portal}` where portal is
`customer` or `staff`. CSRF remains required for all POST requests.

Reset POST `/api/v1/auth/password-reset/request`: `{email}`.
Confirm POST `/api/v1/auth/password-reset/confirm`:
`{email,otp,password,confirmPassword}`. New passwords require 8–72 characters.
Codes expire in 10 minutes, allow five guesses and are consumed after success.
Requests have a one-minute per-account cooldown. Only hashes are stored.
Sessions created by the updated login expire on their next request after a password change.

Configure SMTP before sending real email: `RAPIDMART_MAIL_HOST`,
`RAPIDMART_MAIL_PORT`, `RAPIDMART_MAIL_USERNAME`, `RAPIDMART_MAIL_PASSWORD`,
and `RAPIDMART_MAIL_FROM` (an address allowed by the mail provider).
No code is exposed in API responses or application logs. Missing SMTP returns
an unavailable message. Mail delivery requires a working provider and credentials.

## Gmail startup for this installation

`start-with-email.ps1` uses smtp.gmail.com:587 with STARTTLS, and
palaraam2003@gmail.com as the username and sender for both OTPs and bill PDFs.
Enable Google 2-Step Verification and create an app password at
https://myaccount.google.com/apppasswords. Stop the existing RapidMart server,
then run `powershell -File .\start-with-email.ps1` from the project folder.
Enter the app password at the masked terminal prompt, without spaces.
The script checks SMTP connectivity/authentication during application startup.
It does not save the password to disk; run it again after stopping the server.
Successful SMTP submission does not guarantee inbox delivery; check spam as well.
