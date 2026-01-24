# Trip-Collab API (v0.1)

A token-based (no-login) travel collaboration backend API built with Spring Boot + PostgreSQL (Railway).

## Features (v0.1)
- Create a Trip (no login)
- Access Trip resources using **Invite Token** via `X-Trip-Token`
- Itinerary items:
  - CRUD (create/list/update/delete)
  - Move an item to another day
  - Reorder items within a day (server compresses sortOrder to 0..n-1)
  - List itinerary grouped by day within a date range

## Tech Stack
- Spring Boot 3
- Spring Data JPA (Hibernate)
- PostgreSQL (Railway)

---

## Architecture Overview

Client (Web/Mobile)
-> HTTP Request
-> TripTokenFilter (authz via X-Trip-Token)
-> Controller (REST)
-> Service (business rules + transaction)
-> Repository (JPA)
-> PostgreSQL (Railway)

### Security Model (No-login, token-based)
- A Trip is a collaboration workspace.
- When a Trip is created, the server returns an **inviteToken** (plaintext) **only once**.
- The database stores only `invite_token_hash` (SHA-256 hex string), not the plaintext token.
- All protected APIs require: `X-Trip-Token: <inviteToken>`.

---

## Database (v0.1 used tables)
- `trips`
- `itinerary_items`

---

## Run Locally

### Requirements
- Java 17+
- Postgres (Railway)

### Environment Variables
> Use Railway **Public** connection info locally (NOT `postgres.railway.internal`).

Example:
```bash
DB_URL='jdbc:postgresql://<PUBLIC_HOST>:<PORT>/<DBNAME>?sslmode=require' \
DB_USER='<USER>' \
DB_PASSWORD='<PASSWORD>' \
./mvnw spring-boot:run
```

### application.yml (important)
Make sure placeholders resolve correctly:
```yaml
spring:
  datasource:
    url: ${DB_URL:}
    username: ${DB_USER:}
    password: ${DB_PASSWORD:}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
```

---

## API

### 1) Create Trip (No token required)
**POST** `/api/trips`

Request:
```json
{
  "title": "Tottori & Okayama",
  "startDate": "2026-02-10",
  "endDate": "2026-02-14",
  "timezone": "Asia/Taipei",
  "notes": "v0.1 backend milestone"
}
```

Response (inviteToken returned only once):
```json
{
  "id": "UUID",
  "title": "Tottori & Okayama",
  "startDate": "2026-02-10",
  "endDate": "2026-02-14",
  "timezone": "Asia/Taipei",
  "notes": "v0.1 backend milestone",
  "inviteToken": "PLAINTEXT_TOKEN"
}
```

### 2) Get Trip (Token required)
**GET** `/api/trips/{tripId}`

Header: `X-Trip-Token: <inviteToken>`

---

## Itinerary

### Create item
**POST** `/api/trips/{tripId}/itinerary`

Header: `X-Trip-Token: <inviteToken>`

Request:
```json
{
  "dayDate": "2026-02-10",
  "title": "岡山車站到鳥取",
  "startTime": "10:30",
  "endTime": "13:30",
  "locationName": "岡山 → 鳥取",
  "mapUrl": "https://maps.google.com",
  "note": "JR 移動"
}
```

### List items by day
**GET** `/api/trips/{tripId}/itinerary?date=2026-02-10`

Header: `X-Trip-Token: <inviteToken>`

Sort order:
- `sortOrder` ASC
- `startTime` ASC
- `createdAt` ASC

### Move item to another day
**POST** `/api/trips/{tripId}/itinerary/{itemId}/move`

Header: `X-Trip-Token: <inviteToken>`

Request:
```json
{ "toDate": "2026-02-11" }
```

Behavior:
- Updates `dayDate`
- Automatically assigns `sortOrder` to the end of target day (max + 1 or 0)

### Reorder items within a day (id list)
**PUT** `/api/trips/{tripId}/itinerary/reorder?date=2026-02-11`

Header: `X-Trip-Token: <inviteToken>`

Request:
```json
[
  { "id": "UUID-3" },
  { "id": "UUID-1" },
  { "id": "UUID-2" }
]
```

Behavior:
- Validates all ids belong to the same `tripId` + `dayDate`
- Server compresses `sortOrder` to 0..n-1

### List all itinerary grouped by day
**GET** `/api/trips/{tripId}/itinerary/all?from=2026-02-10&to=2026-02-14`

Header: `X-Trip-Token: <inviteToken>`

Response:
```json
[
  { "dayDate": "2026-02-11", "items": [ ... ] },
  { "dayDate": "2026-02-12", "items": [ ... ] }
]
```

---

## Suggested Package Layout

```
com.killerplay13.tripcollab
  |- config/            # SecurityConfig, app configs
  |- security/          # TripTokenFilter, token utils
  |- domain/            # Trip, ItineraryItem, ...
  |- repo/              # TripRepository, ItineraryItemRepository, ...
  |- service/           # TripService, ItineraryService, ...
  `- web/               # TripController, ItineraryController, ...
```

---

## Roadmap (v0.2)
- Expenses / Expense splits (cost sharing)
- trip_members (optional member identity under no-login model)
- Search API
- Deploy Spring Boot service to Railway (use internal DB host in prod)
