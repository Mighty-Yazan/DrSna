#Action Required Before Running the Application

This PR introduces **asymmetric RSA JWT token signing** and **centralized environment configuration**.

Sensitive files (`.pem` keys and `.env`) are ignored by Git for security, so everyone pulling this branch must configure them locally before running the backend or frontend.

> **Note:** This manual setup is temporary. It will be replaced once configuration secrets are migrated to an environment vault / containerized secrets in Docker for production.

---

## 1. RSA Key Pair Setup (Backend)

The backend now signs JWTs using an RSA key pair. Without these files, Spring Boot will fail on startup.

### Option A: Copy the Team Dev Keys (Recommended)

1. Download `private.pem` and `public.pem` from the team chat so from ALI's chat .
2. Place both files in `src/main/resources/certs/` (or the configured certs directory).

### Option B: Generate Your Own Local Keys

Run these commands from your terminal (Git Bash / WSL / Linux):

```bash
# Generate a 2048-bit RSA private key in PKCS#8 format
openssl genpkey -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:2048

# Extract the matching public key
openssl rsa -pubout -in private.pem -out public.pem
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
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/drtooth_db
DB_USERNAME=postgres
DB_PASSWORD=your_local_password
ADMIN_SEED_ENABLED=true
ADMIN_SEED_FULL_NAME=System Administrator
ADMIN_SEED_EMAIL=your_local_email
ADMIN_SEED_PASSWORD=your_local_password
ADMIN_SEED_CITY=AMMAN
```

---

## ✅ Checklist Before Starting

- [ ] `src/main/resources/certs/private.pem` exists
- [ ] `src/main/resources/certs/public.pem` exists
- [ ] `.env` file exists in the root with valid DB credentials
- [ ] `git status` shows no `.pem` or `.env` files staged
