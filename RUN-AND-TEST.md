# SkillCircle - run instructions and manual test checklist (SQLite phase)

## Run on Windows (PowerShell, JDK 17+ installed)
```
cd C:\path\to\SkillCircle          (the folder that contains src, lib, run.ps1)
powershell -ExecutionPolicy Bypass -File .\run.ps1
```
* `-NoDemo` starts without the 5 demo students (they are only ever added once anyway).
* `-DbPath C:\SkillCircleData\skillcircle.db` keeps the database somewhere else
  (recommended if the project is inside OneDrive, which can lock the file).

## Where is my data?
`<project folder>\data\skillcircle.db` (created automatically on the first start; no SQL scripts to run).
The path does not depend on the folder your terminal is in. Normal startup never deletes or overwrites it.
* Backups: `data\backups\skillcircle-<time>.db` are created automatically BEFORE a schema upgrade.
* Start again from zero: close the program and delete `data\skillcircle.db`.
* The database file is not part of the ZIP, so your own data is never shipped.

## Schema (version 2, upgraded automatically from version 1)
students, skills, student_offered_skills, student_wanted_skills, student_availability,
saved_students, exchange_requests, sessions, activity_log, app_meta.
Primary/foreign keys (enforced on every connection), UNIQUE, NOT NULL and CHECK constraints,
a unique index that blocks a duplicate ACTIVE request, and `PRAGMA user_version` for versioned migrations.
Passwords are stored only as PBKDF2 salt + hash (+ algorithm and iteration count).

## Automated tests
```
powershell -ExecutionPolicy Bypass -File .\run-tests.ps1
```
They use temporary databases, never `data\skillcircle.db`. Expected:
DatabaseSmokeTest 35 passed, AuthenticationTest 146 passed, DatabaseFlowTest 100 passed.

## Manual test checklist
Demo logins (password `demo123`): CS2024001 Demo Student, IT2024002 Aarav, EC2024003 Priya, ME2024004 Rohan, CS2024005 Sneha.
1. Register NEW1 (Registration page), log in, add skills, save a student, send a request.
2. **Close the program completely and start it again.** Log in as NEW1: skills, saved student,
   request, availability and Dashboard numbers are all still there.
3. Duplicate Student ID or duplicate email on Registration -> clear error, nothing saved.
4. As CS2024001: Accept Aarav's request, schedule a session, log in as IT2024002 and Confirm it.
   Restart: request status and session are unchanged.
5. Students screen: Add, Edit, search, Remove (removing yourself or a student with active
   requests is refused). Removed students disappear from every screen and from Saved lists.
6. Discover, Matches, Requests, Sessions, Saved behave as in the previous phase.
7. Logout -> Login page.
8. If you ever see "database is locked", keep the project outside OneDrive or use `-DbPath` (see above).

## Known limitations
- Passwords cannot be changed or reset in the app.
- Any logged-in student can edit/remove records in Student Management (with safeguards); there are no admin roles.
- Demo sessions are dated relative to the first start; the "upcoming" demo session becomes "past" after 3 days.
- No Swing background threads: every operation measured at under 25 ms with 200 students, so the UI stays responsive.
