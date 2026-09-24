#Action Required Before Running the Application

This PR introduces **asymmetric RSA JWT token signing** and **centralized environment configuration**.

Sensitive files (`.pem` keys and `.env`) are ignored by Git for security, so everyone pulling this branch must configure them locally before running the backend or frontend.

> **Note:** This manual setup is temporary. It will be replaced once configuration secrets are migrated to an environment vault / containerized secrets in Docker for production.

---

## 1. RSA Key Pair Setup (Backend)

The backend now signs JWTs using an RSA key pair. Without these files, Spring Boot will fail on startup.

### Option A: Copy the Team Dev Keys (Recommended)

1. Download `app-private.pem` and `app-public.pem` from the team chat so from ALI's chat .
2. Place both files in `src/main/resources/certs/` (or the configured certs directory).

### Option B: Generate Your Own Local Keys

Run these commands from your terminal (Git Bash / WSL / Linux):

```bash
# Generate a 2048-bit RSA private key in PKCS#8 format
openssl genpkey -algorithm RSA -out app-private.pem -pkeyopt rsa_keygen_bits:2048

# Extract the matching public key
openssl rsa -pubout -in app-private.pem -out app-public.pem
```

Move both `.pem` files into `src/main/resources/certs/`.

> **Never commit these files to Git.**

---

## 2. Environment Configuration (`.env`)

The backend and database credentials require a local `.env` file in the project root.

### Option A: Copy from Team Chat (also from ali) (Recommended) 

Grab the latest `.env` snippet shared in the team chat and save it as `.env` in the root directory.

### Option B: Set Up Your Own Local Values

Create a file named `.env` in the project root with your local database and service settings:

```env
DB_URL=jdbc:postgresql://localhost:5432/drtooth_db
DB_NAME=drtooth_db
DB_USER=pguser
DB_PASSWORD=pgpassword

ADMIN_SEED_ENABLED=true
ADMIN_SEED_FULL_NAME=System Administrator
ADMIN_SEED_EMAIL=ops-admin@drsna.com
ADMIN_SEED_PASSWORD=Unipass12!
ADMIN_SEED_CITY=AMMAN
```

---

## ✅ Checklist Before Starting

- [ ] `src/main/resources/certs/app-private.pem` exists
- [ ] `src/main/resources/certs/app-public.pem` exists
- [ ] `.env` file exists in the root with valid DB credentials
- [ ] `git status` shows no `.pem` or `.env` files staged
# Changelog & Fixes Summary

## 1. Authentication & Session Persistence
* **Resolved 500 Optimistic Lock Crash on Login**:
    * Fixed `StaleObjectStateException` / `ObjectOptimisticLockingFailureException` caused by Hibernate expecting 1 affected row when deleting obsolete refresh tokens.
    * Standardized `RefreshTokenRepository` to return nullable entities (`RefreshToken?`) and managed session deletion cleanly via JPA without unmanaged row-count conflicts.
    * Ensured `RefreshToken` entity alignment with correct parameter names (`expiryDate`).
* **Full-Stack Refresh Token Deletion on Logout (Admin & Clinic)**:
    * Resolved an issue where logging out as Clinic or Admin left active refresh token entries in PostgreSQL and will be implemented on doctor when dashboard is done/made.

---

## 2. API Query Filtering for clinics in patient dashboard
* **Clinic & Doctor Search Filters**:
    * Fixed an HTTP 500 Internal Server Error when filtering clinics (`/api/patient/clinics?name=&city=ALL+CITIES`) where they weren't the citys weren't matching in the fronted and backend.
    * Updated `cleanQueryParams` in `patientApi.js` using an explicit `Set` blacklist to strip empty strings (`""`), `"ALL"`, `"ALL CITIES"`, `"ALL SPECIALTIES"`, and `"ANYTIME"`.

---

## 3. User Profile Logic & UI Fixes (`UserProfile.jsx`)
* **Security Verification & Payload Integrity**:
    * Fixed profile update request payload to include `currentPassword` (`verifyPassword`) as required by `UpdateProfileRequest.kt`.
    * Updated city selection options to match valid Jordanian governorate enum values (`BALQA` and `TAFILEH` instead of non-existent `SALT`).
* **Backend Database Persistence**:
    * Added the missing assignment for `user.phoneNumber = request.phoneNumber?.trim()` inside `AuthService.kt` to ensure phone updates persist to the `users` table.

## 4.Code Cleanliness & Static Analysis**:
* Resolved 30+ WebStorm and ESLint errors/warnings (`no-unused-vars`, unhandled promises, invalid HTML attributes like `value` on `<p>` elements).
* Fixed variable hoisting / initialization order in `useEffect` hooks.
* Resolved ESLint `react-hooks/set-state-in-effect` errors by eliminating redundant synchronous state initializers.
* Cleaned up non-null assertion operators (`!`), redundant wrappers, and type inference mismatches across state setters.