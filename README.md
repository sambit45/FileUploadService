# File Upload Service

A production-style file upload backend built with **Spring Boot** and **AWS**.
Files are uploaded directly to S3 via pre-signed URLs — the backend never handles file bytes.

---

## Architecture

```
┌─────────────────┐         ┌──────────────────────────────┐
│     Client      │────────▶│    EC2 (Docker Container)    │
└─────────────────┘         │    Spring Boot :8080          │
          │                 └──────────────┬───────────────┘
          │                                │
          │                 ┌──────────────▼──────────────┐
          │                 │      RDS PostgreSQL          │
          │                 │      (File Metadata)         │
          │                 └─────────────────────────────┘
          │
          │  Direct upload via Pre-signed URL
          ▼
┌───────────────────────┐
│       AWS S3          │
│   (Private Bucket)    │
│   No public access    │
└───────────────────────┘
```

---

## Key Design Decisions

- **Pre-signed URLs** — files upload directly from client to S3, backend never handles file bytes, eliminating bandwidth bottleneck
- **Private S3 bucket** — all public access blocked, files only accessible via time-limited pre-signed URLs
- **Upload confirmation pattern** — backend calls S3 `headObject` to verify file actually exists before marking upload as COMPLETED
- **IAM Role on EC2** — zero hardcoded AWS credentials anywhere in code or config, SDK picks up credentials automatically via instance metadata
- **JWT stateless auth** — no server-side sessions, every request is self-contained
- **Least privilege IAM policy** — app only has `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`, `s3:HeadObject` on the specific bucket
- **User enumeration prevention** — same error message returned for wrong email and wrong password
- **UUID primary keys** — prevents sequential ID enumeration on user-facing identifiers

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 17 |
| Framework | Spring Boot 4 |
| Auth | Spring Security + JWT (jjwt 0.12.3) |
| Database | PostgreSQL (AWS RDS db.t3.micro) |
| File Storage | AWS S3 |
| AWS SDK | AWS SDK for Java v2 |
| ORM | Spring Data JPA + Hibernate |
| Containerization | Docker (multi-stage build) |
| Deployment | AWS EC2 t2.micro |
| Build Tool | Maven |

---

## API Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| POST | `/api/auth/register` | No | Register new user, returns JWT |
| POST | `/api/auth/login` | No | Login, returns JWT |
| POST | `/api/files/upload-url` | Yes | Generate pre-signed S3 PUT URL |
| PATCH | `/api/files/{id}/confirm` | Yes | Confirm upload completed |
| GET | `/api/files` | Yes | List all completed uploads |
| GET | `/api/files/{id}/download-url` | Yes | Generate pre-signed S3 GET URL |
| DELETE | `/api/files/{id}` | Yes | Delete file from S3 and DB |

---

## Upload Flow

```
1. Client  →  POST /api/files/upload-url  →  Backend
              { filename, contentType, fileSizeBytes }

2. Backend →  Validates JWT + file constraints
           →  Generates pre-signed S3 PUT URL (15 min expiry)
           →  Saves PENDING metadata to PostgreSQL
           →  Returns { fileId, uploadUrl, expiresIn }

3. Client  →  PUT <uploadUrl>  →  S3 directly
              Binary file in request body
              No backend involved

4. Client  →  PATCH /api/files/{fileId}/confirm  →  Backend

5. Backend →  Calls S3 headObject to verify file exists
           →  Updates metadata: PENDING → COMPLETED
           →  Stores actual file size from S3 metadata

6. Client  →  GET /api/files/{fileId}/download-url  →  Backend
           ←  Pre-signed GET URL (60 min expiry)
           →  Browser opens file directly from S3
```

---

## Database Schema

```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,        -- BCrypt hashed
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE files (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID REFERENCES users(id),
    original_filename VARCHAR(500) NOT NULL,
    s3_key            VARCHAR(1000) NOT NULL UNIQUE,
    content_type      VARCHAR(100),
    file_size_bytes   BIGINT,
    upload_status     VARCHAR(50) DEFAULT 'PENDING',  -- PENDING | COMPLETED | FAILED
    created_at        TIMESTAMP DEFAULT NOW()
);
```

---

## Running Locally

**Prerequisites:** Docker Desktop, Java 17, Maven

```bash
# 1. Clone the repository
git clone https://github.com/yourusername/file-upload-service.git
cd file-upload-service

# 2. Start PostgreSQL via Docker
docker compose up -d

# 3. Create application-local.yml in src/main/resources/
#    (see Environment Variables section below)

# 4. Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**Local `application-local.yml`** (never commit this file):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/file_upload_db?TimeZone=Asia/Kolkata
    username: postgres
    password: postgres

jwt:
  secret: your-256-bit-secret-key-minimum-32-characters-long
  expiry-ms: 86400000

aws:
  region: ap-south-1
  s3:
    bucket-name: your-bucket-name
  credentials:
    access-key: YOUR_ACCESS_KEY
    secret-key: YOUR_SECRET_KEY
```

---

## Running on EC2

**Prerequisites:** EC2 instance with IAM Role attached (S3 permissions), Docker installed

```bash
# 1. SSH into EC2
ssh -i your-key.pem ec2-user@your-ec2-ip

# 2. Clone the repository
git clone https://github.com/yourusername/file-upload-service.git
cd file-upload-service

# 3. Create .env file (never commit this)
nano .env

# 4. Build Docker image
sudo docker build -t file-upload-service .

# 5. Run container
sudo docker run -d -p 8080:8080 --env-file .env file-upload-service

# 6. Check logs
sudo docker logs -f $(sudo docker ps -q)
```

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | RDS PostgreSQL connection string | `jdbc:postgresql://host:5432/file_upload_db` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `yourpassword` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Hibernate DDL mode | `update` |
| `JWT_SECRET` | JWT signing secret (min 32 chars) | `your-256-bit-secret-key` |
| `JWT_EXPIRY_MS` | JWT expiry in milliseconds | `86400000` (24 hours) |
| `AWS_REGION` | AWS region | `ap-south-1` |
| `AWS_S3_BUCKET_NAME` | S3 bucket name | `your-bucket-name` |

> **Note:** `AWS_CREDENTIALS_ACCESS_KEY` and `AWS_CREDENTIALS_SECRET_KEY` are **not required** on EC2.
> The instance uses an attached IAM Role — AWS SDK resolves credentials automatically via EC2 instance metadata.
> This is the production-correct approach. Credentials are never stored in code, config files, or environment variables.

---

## Security Model

| Concern | Implementation |
|---------|----------------|
| Passwords | BCrypt hashed, never stored in plaintext |
| JWT | HS256 signed, stateless, 24hr expiry |
| S3 Access | Private bucket, all access via pre-signed URLs only |
| Upload URL expiry | 15 minutes |
| Download URL expiry | 60 minutes |
| AWS Credentials | IAM Role on EC2, zero hardcoded keys |
| IAM Policy | Least privilege — scoped to specific bucket and 4 actions only |
| File ownership | All queries include `userId` — users can only access their own files |
| User enumeration | Same error message for wrong email and wrong password |
| Sensitive fields | Passwords and S3 keys never returned in API responses |

---

## Project Structure

```
src/main/java/com/authorization/fileUploadService/
├── auth/
│   ├── dto/
│   │   ├── AuthResponse.java
│   │   ├── LoginRequest.java
│   │   └── RegisterRequest.java
│   ├── AuthController.java
│   ├── AuthService.java
│   └── JwtFilter.java
├── config/
│   ├── JwtConfig.java
│   ├── S3Config.java
│   └── SecurityConfig.java
├── exception/
│   ├── GlobalExceptionHandler.java
├── file/
│   ├── dto/
│   │   ├── FileResponse.java
│   │   ├── UploadUrlRequest.java
│   │   └── UploadUrlResponse.java
│   ├── FileController.java
│   ├── FileMetadata.java
│   ├── FileRepository.java
│   ├── FileService.java
│   ├── S3Service.java
│   └── UploadStatus.java
└── user/
    ├── User.java
    └── UserRepository.java
```

---

## Dockerfile

Multi-stage build — Maven build stage + lightweight JRE runtime stage.
Final image contains only the JAR, no source code or Maven tooling.

```dockerfile
# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## AWS Infrastructure

```
EC2: t2.micro (Amazon Linux) — free tier
  └── IAM Role: file-upload-service-ec2-role
        └── Policy: s3:PutObject, s3:GetObject,
                    s3:DeleteObject, s3:HeadObject
                    on arn:aws:s3:::bucket-name/*

RDS: PostgreSQL 15, db.t3.micro — free tier
  └── VPC Security Group: inbound 5432 from EC2 only

S3: Private bucket, ap-south-1
  └── Block all public access: enabled
  └── Access: pre-signed URLs only
```

---