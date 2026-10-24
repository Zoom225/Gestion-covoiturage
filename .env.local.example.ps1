# Example only. Copy to .env.local.ps1 once; never overwrite an existing local file.
# Configure private values in the ignored copy, never in this example.
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:15432/carshare'
$env:DB_USERNAME = 'carshare'
# Reuse the password of an existing PostgreSQL volume. Do not change it here.
$env:DB_PASSWORD = ''
# Stable local-only Base64 key, at least 32 random bytes. Never use it in production.
$env:JWT_SECRET = ''
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:4200'
