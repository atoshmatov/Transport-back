# Transport Observer — Texnik Topshiriq (TZ)

**Versiya:** 1.0 · **Sana:** 2026-08-12 · **Holat:** Implementatsiyadan oldingi arxitektura + TZ hujjati (hali kod yozilmagan)
**Manba:** `Transport Observer yangi.pptx` taqdimoti tahlili + arxitektura sessiyasi + mahsulot oqimi (UX) tekshiruvi
**Board:** TASK-562 (parent) · TASK-563 (arxitektura sub-task)

---

## 1. Loyiha qisqacha tavsifi va maqsadi

**Transport Observer** — transport xavfsizligi nazoratini raqamlashtirish tizimi. Dala inspektorlari faoliyatini real vaqtda kuzatish, transport sohasidagi huquqbuzarlik va xavfli holatlarni geolokatsiya + foto-dalil bilan qayd etish, favqulodda hodisalar bo'yicha tezkor xabar berish va markaziy boshqaruv paneli orqali statistik nazoratni ta'minlaydi.

| Maqsad | Tavsif |
|---|---|
| Real vaqt nazorati | Inspektorlar joylashuvi, faoliyati va hodisalar xaritada jonli ko'rinadi |
| Dalilli qayd | Har bir hodisa foto + GPS + vaqt bilan o'zgarmas tarzda saqlanadi |
| Offline ishlash | Internet yo'q hududlarda ham qayd davom etadi, aloqa tiklangach sinxronlanadi |
| Markaziy analitika | Hodisa, tekshiruv, huquqbuzarlik bo'yicha avtomatik statistika va reyting |
| AI kamera (RailSafe) | Temir yo'l pereezdlarida computer vision + PLC integratsiyasi orqali avtomatik hodisa aniqlash |

**Tizim 5 komponentdan iborat:** Backend · Web Admin panel · Web Operator panel · Mobil ilova (Android + iOS) · RailSafe edge-desktop modul (integratsiya sifatida).

---

## 2. Foydalanuvchi rollari va ruxsatlar

| Rol | Kim | Asosiy vazifasi |
|---|---|---|
| **Super Admin** | Tizim egasi / IT | To'liq nazorat, rol/sozlama boshqaruvi |
| **Admin** | Departament rahbari | Xodim yaratish, hisobot, hudud boshqaruvi |
| **Operator/Navbatchi** | Ofis navbatchisi | Hodisa dispetcherligi, RailSafe kuzatuvi, xarita monitoring |
| **Inspektor (Hodim)** | Dala xodimi | Hodisa qaydi, foto dalil, favqulodda xabar — **asosan mobildan** |

### Ruxsatlar matritsasi

| Amal | Super Admin | Admin | Operator | Inspektor |
|---|:---:|:---:|:---:|:---:|
| Xodim (account) yaratish | ✅ | ✅ | ❌ | ❌ |
| Parolni reset qilish | ✅ | ✅ | ❌ | ❌ |
| Rol/ruxsatlarni sozlash | ✅ | ❌ | ❌ | ❌ |
| Barcha hududlar dashboard | ✅ | ✅ | ✅ (read) | ❌ |
| Hisobot generatsiya/eksport | ✅ | ✅ | ✅ | o'ziniki |
| Xaritada barcha inspektorlar | ✅ | ✅ | ✅ | ❌ |
| Hodisa qayd etish (foto+GPS) | ❌ | ❌ | qo'lda | ✅ |
| Favqulodda xabar yuborish | ❌ | ❌ | ✅ (dispetch) | ✅ |
| Audit log ko'rish | ✅ | qisman | ❌ | ❌ |

> **Arxitektura qarori:** dala inspektorlari — mobil (GPS/kamera/offline kerak). Web'dagi ikkinchi interfeys — **Operator paneli**: ofis navbatchilari uchun, faqat monitoring + dispetcherlik. *(11-bo'limdagi ochiq savol — mijoz bilan tasdiqlanishi kerak.)*

---

## 3. Tizim arxitekturasi

```
                          ┌───────────────────────────────────────────────┐
                          │              BACKEND (markaziy)                │
   ┌──────────────┐ HTTPS │  ┌──────────────┐   ┌───────────────────────┐ │
   │  Web Admin   │◄─────►│  │  REST API    │   │  WebSocket (STOMP)    │ │
   │  (Vue.js)    │ WSS   │  │  (Spring     │   │  jonli xarita /       │ │
   └──────────────┘◄─────►│  │   Boot)      │   │  bildirishnoma        │ │
                          │  └──────┬───────┘   └──────────┬────────────┘ │
   ┌──────────────┐ HTTPS │         │                      │              │
   │Web Operator  │◄─────►│  ┌──────▼──────────────────────▼───────────┐  │
   │  (Vue.js)    │ WSS   │  │        Service / Domain layer            │  │
   └──────────────┘◄─────►│  │ Auth · Employees · Incidents · Map ·     │  │
                          │  │ Reports · RailSafe · Notifications ·     │  │
   ┌──────────────┐ HTTPS │  │ Audit                                     │  │
   │ Mobile app   │◄─────►│  └──────┬─────────────┬──────────┬─────────┘  │
   │Android + iOS │ WSS   │         │             │          │            │
   │ (KMM+native) │◄─────►│   ┌─────▼─────┐  ┌────▼────┐ ┌───▼────────┐   │
   └──────────────┘       │   │PostgreSQL │  │  MinIO  │ │  Redis     │   │
        │ FCM/APNs        │   │ +PostGIS  │  │(S3 foto)│ │(cache/     │   │
        ▼                 │   └───────────┘  └─────────┘ │session/WS) │   │
   Push servislari        │                              └────────────┘   │
                          │   ┌──────────────────────────────────────┐    │
                          │   │  RabbitMQ — RailSafe events · sync   │    │
                          │   │  jobs · push                          │    │
                          │   └──────────────▲───────────────────────┘    │
                          └──────────────────┼───────────────────────────┘
                                             │ REST webhook + MQTT (ixtiyoriy)
                          ┌──────────────────┴───────────────────────────┐
                          │      RailSafe edge-desktop (pereezd PK)        │
                          │  Computer Vision (YOLO) + PLC integratsiyasi  │
                          └───────────────────────────────────────────────┘
```

| Kanal | Kim ↔ kim | Texnologiya | Nima uchun |
|---|---|---|---|
| REST API | Barcha klient ↔ Backend | JSON/HTTPS | CRUD, auth, sync |
| WebSocket | Web + Mobile ↔ Backend | STOMP/WSS | Jonli xarita, pozitsiya, real-time hodisa |
| Push | Backend → Mobile | FCM / APNs | Ilova yopiq bo'lganda favqulodda xabar |
| Webhook | RailSafe → Backend | HTTPS POST | Aniqlangan hodisa/statistika |
| MQTT *(ixtiyoriy)* | RailSafe → Backend | MQTT/TLS | Yuqori chastotali telemetriya (2-faza) |
| Message Queue | Ichki | RabbitMQ | Push, sync, RailSafe buferlash |

**Arxitektura uslubi:** MVP uchun **modular monolith** (bitta Spring Boot deploy, aniq modul chegaralari) — mikroservisdan sodda, keyin RailSafe/notification qismini alohida servisga ajratish oson qoladi.

---

## 4. Texnologik stack va asoslash

### 4.1 Backend — **Spring Boot (Kotlin)** ✅ tavsiya

| | Spring Boot | Ktor |
|---|---|---|
| Auth/RBAC/audit/lockout | Tayyor (Spring Security) | Qo'lda yozish kerak |
| PostGIS geolokatsiya | Hibernate Spatial tayyor | Qo'lda mapping |
| Boilerplate | Ko'proq, lekin ishonchli | Kamroq, nazorat ko'p |

**Sabab:** loyihaning eng murakkab va xavfli qismi — OTP'siz auth, RBAC, parol siyosati, audit — Spring Security bilan xavfsizroq va tezroq yopiladi. Ktor faqat kelajakda RailSafe ingestion/WebSocket gateway kabi yengil yordamchi servis sifatida qo'shilishi mumkin.

### 4.2 Frontend (web) — **Vue 3 + Pinia + Vue Router**

- UI komponent kutubxonasi (PrimeVue/Element Plus), xarita — **Leaflet/MapLibre** (on-premise'ga mos), grafik — ApexCharts/ECharts.
- Bitta SPA, rolga qarab menyu/route farqlanadi: **Admin** (to'liq) va **Operator** (monitoring+dispetcherlik).

### 4.3 Mobile — **KMM shared logic + native UI** ✅ tavsiya

| | To'liq native (Android+iOS alohida) | KMM + native UI |
|---|---|---|
| Offline-sync/GPS/model | 2 marta yoziladi, drift xavfi | 1 marta commonMain'da |
| iOS biznes-logika riski | Yuqori (Alimardon iOS'da yangi) | Past — faqat UI iOS'da |
| Setup murakkabligi | Past | O'rtacha (CocoaPods/SPM) |

**Sabab:** eng xatoga moyil qism (offline-sync, geolokatsiya, hodisa modeli) bir marta Kotlin'da yoziladi va ikki platformada bir xil ishlaydi; iOS tomoni faqat SwiftUI ekranlariga qisqaradi — bu Alimardonning eng katta riskini (iOS biznes-logika) yo'q qiladi. Android = Jetpack Compose, iOS = SwiftUI. Platform-specific: GPS/kamera/push/secure storage — expect/actual yoki to'liq native.

### 4.4 Boshqa stack qarorlari

| Ehtiyoj | Tanlov | Asoslash |
|---|---|---|
| DB | **PostgreSQL + PostGIS** | Geospatial so'rovlar (hudud, eng yaqin post, klaster) |
| Fayl/foto | **MinIO (S3-compatible)** | Self-hostable, on-premise talabiga mos |
| Cache/session | **Redis** | WS session, KPI cache, refresh-token blacklist, login-attempt counter |
| Real-time | **WebSocket (STOMP)** | Jonli xarita — polling sekin/qimmat |
| Push | **FCM + APNs** | Standard, RabbitMQ orqali yuboriladi |
| RailSafe integratsiya | **REST webhook + RabbitMQ** (asosiy), MQTT (ixtiyoriy) | Hodisa kam chastotali, ishonchli yetishi kerak |
| Container | **Docker + docker-compose** | On-premise deploy uchun bir stack |

---

## 5. Autentifikatsiya va avtorizatsiya

> **Cheklov:** OTP/SMS/email tasdiqlash **YO'Q**. Faqat login (username) + parol. Account **faqat admin** tomonidan yaratiladi. Self-registration yo'q, email orqali parol tiklash yo'q.

### 5.1 Account yaratish (admin)

```
Admin → "Xodim qo'shish" → F.I.Sh, lavozim, hudud, rol
      → username generatsiya (yoki admin kiritadi)
      → vaqtinchalik parol generatsiya
      → parol ekranda BIR MARTA ko'rsatiladi — keyin qayta ko'rinmaydi,
        faqat "Reset" tugmasi orqali yangisi generatsiya qilinadi
      → account "mustChangePassword = true" bilan yaratiladi
      → audit log: kim, qachon, kimga yaratdi
```

### 5.2 Login oqimi

```
Klient → POST /auth/login {username, password}
  1. account bloklangan/o'chirilgan? → 403 + aniq xabar
  2. parol tekshiruvi (BCrypt) — noto'g'ri → failedAttempts++
     limitdan oshsa → vaqtincha lock + "N urinishdan keyin bloklandi,
     administratorga murojaat qiling" xabari
  3. to'g'ri → access token (qisqa muddat) + refresh token
  4. mustChangePassword=true bo'lsa → klient MAJBURAN parol
     o'zgartirish ekraniga o'tadi; bu ekrandan orqaga qaytib/yopib
     chiqib ketish BLOKLANADI (boshqa API'ga ruxsat yo'q, keyingi
     ochilishda ham shu ekran chiqadi)
```

### 5.3 Token strategiyasi

| Token | Muddat | Saqlash |
|---|---|---|
| Access (JWT) | 15–30 daqiqa | Mobil: Keystore/Keychain; Web: memory/httpOnly cookie |
| Refresh | 7–30 kun | Server (Redis) — revoke qilinadi |

- Logout / admin bloklashda refresh token Redis'da darhol revoke qilinadi.
- **Sessiya-blok bog'lanishi:** admin xodimni bloklasa, mobil klient keyingi so'rovda (yoki WS orqali darhol) `403 ACCOUNT_BLOCKED` oladi va **shu zahoti** logout qilinadi — bloklangan xodim eski access token bilan hodisa yubora olmaydi (access token muddati qisqa, 15-30 daqiqada baribir tugaydi; kritik amallarda serverda qo'shimcha "is_active" tekshiruvi).

### 5.4 Parol reset (admin) — OTP yo'qligi sababli kritik nuqta

```
Xodim parolni unutdi → adminga tashkiliy kanal orqali murojaat
Admin → xodim shaxsini tasdiqlaydi (tashkiliy protsedura) → "Reset"
      → yangi vaqtinchalik parol, mustChangePassword=true
      → audit log: kim kimning parolini qachon reset qildi
```

> Bu — texnik ikkinchi faktor yo'qligi sababli **ijtimoiy injiniring riski** bor jarayon. Kuchli parol siyosati + to'liq audit log bilan qoplanadi (9-bo'lim).

### 5.5 Hisob bloklash (brute-force himoyasi)

Ketma-ket 5 noto'g'ri urinish → 15 daqiqa vaqtincha lock (Redis counter) + foydalanuvchiga aniq xabar. Takroriy lock → doimiy lock, faqat admin ochadi.

### 5.6 RBAC

Har endpoint rol/permission tekshiradi (`@PreAuthorize`); `regionId` bo'yicha data-scoping ochiq savol (11-bo'lim, #6).

---

## 6. Ma'lumotlar bazasi sxemasi (asosiy jadvallar)

**Auth & foydalanuvchilar:** `accounts` (username, password_hash, role_id, must_change_password, is_active, failed_attempts, locked_until, created_by), `roles`, `permissions`+`role_permissions`, `employees` (account_id, full_name, position, employee_code, region_id, rating, rank, avatar_url)

**Hudud:** `regions` (parent_id — viloyat→tuman, geometry PostGIS, type), `departments`

**Hodisalar:** `incidents` (type, status, employee_id, region_id, location — PostGIS point, severity, client_uuid — offline dedup), `evidence` (incident_id, file_url, file_type, captured_at, location), `incident_status_history`

**Nazorat/transport:** `checkpoints`, `vehicles`, `inspections`

**RailSafe:** `railsafe_crossings` (pereezd, location, plc_status), `railsafe_cameras`, `railsafe_detections` (object_class, bbox_json, in_zone, detected_at), `railsafe_stats`

**Boshqa:** `ratings`, `notifications`, `audit_logs` (actor, action, target, ip, created_at), `device_tokens` (fcm/apns)

**Asosiy bog'lanishlar:** `accounts 1—1 employees` · `employees 1—* incidents` · `incidents 1—* evidence` · `regions 1—* employees/incidents/checkpoints` · `railsafe_crossings 1—* cameras 1—* detections`

---

## 7. Asosiy API endpoint guruhlari

| Guruh | Endpointlar (namuna) |
|---|---|
| Auth | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/change-password`, `/auth/reset-password` (admin) |
| Employees | `GET·POST /employees`, `PUT /employees/{id}`, `POST /employees/{id}/reset-password`, `PATCH /employees/{id}/status` |
| Incidents | `GET·POST /incidents`, `PATCH /incidents/{id}/status`, `POST /incidents/{id}/evidence`, `POST /incidents/sync` (batch offline) |
| Files | `POST /files` (presigned/multipart), `GET /files/{id}` |
| Map/Geo | `GET /map/employees`, `GET /map/incidents?bbox=`, `GET /map/checkpoints`, `GET /map/heatmap`, `POST /map/position` |
| Reports | `GET /reports/dashboard`, `/reports/activity?range=7d`, `/reports/regions-distribution`, `/reports/export` |
| Ratings | `GET /ratings/top`, `/ratings/me` |
| RailSafe | `POST /railsafe/detections` (webhook), `/railsafe/heartbeat`, `GET /railsafe/crossings/{id}/stats` |
| Notifications | `GET /notifications`, `PATCH /notifications/{id}/read`, `POST /notifications/emergency`, `POST /device-tokens` |
| Admin | `GET·PUT /settings`, `GET /audit-logs`, `GET·POST /regions`, `/checkpoints`, `/vehicles` |
| WebSocket | `/ws` (STOMP) — `/topic/positions`, `/topic/incidents`, `/topic/emergency`, `/topic/railsafe` |

---

## 8. Mobil ilova arxitekturasi (offline-first)

### 8.1 KMM tuzilishi

```
shared/ (commonMain)
├── data/remote     → Ktor client, DTO
├── data/local      → SQLDelight (offline store)
├── data/repository → source-of-truth (local DB) + sync mantiq
├── domain          → model, use-case
└── sync            → SyncQueue, retry policy

androidApp/ → Jetpack Compose UI + GPS/kamera/FCM
iosApp/     → SwiftUI UI + CoreLocation/kamera/APNs
```

### 8.2 Offline-first va sync-status (UX tekshiruvidan aniqlashtirildi)

1. **Local DB = source of truth** — UI SQLDelight'dan reaktiv o'qiydi, server sync fonda.
2. **Hodisa yaratish:** avval lokal DB'ga yoziladi (`client_uuid` bilan, offline dedup uchun), foto qurilmada saqlanadi, SyncQueue'ga qo'shiladi.
3. **Har hodisa uchun ko'rinadigan sync-status** (admin panel bilan bir xil terminologiya/rang): `Saqlandi (offline)` (kulrang) → `Yuborilmoqda` (sariq) → `Yuborildi` (yashil) / `Xato — qayta urinish` (qizil). Ro'yxat ustida umumiy "N ta hodisa navbatda" ko'rsatkichi.
4. **Foto sekin yuklansa** — matn/metadata avval, foto alohida progress bilan; foydalanuvgiga "ma'lumot yuborildi, foto yuklanmoqda" aniq ko'rsatiladi (hodisa "yo'qolib ketdi" degan taassurot oldini olish uchun).
5. **Conflict resolution:** hodisa yaratish — append-only (konflikt kam); status o'zgarishi — server-wins + `updated_at`.
6. **Retry:** eksponensial backoff, cheklangan urinish soni, keyin foydalanuvchiga aniq xato ko'rsatiladi.

### 8.3 Geolokatsiya

- "Smenada" bo'lganda background location (Android: FusedLocationProvider + foreground service; iOS: CoreLocation background updates), 15-30s/masofa oralig'ida serverga yuboriladi.
- **GPS o'chirilgan holat:** hodisa GPS'siz ham yuborilishi mumkin, lekin "joylashuv aniqlanmadi" belgisi bilan — favqulodda vaziyatda GPS yo'qligi sababli hodisa qayd etishni to'sib qo'yish yaramaydi (11-bo'lim, #7 bilan bog'liq — retention siyosati mijoz bilan aniqlanadi).
- Faqat smena vaqtida, foydalanuvchi roziligi bilan (permission priming — 8.5).

### 8.4 Ruxsatlarni so'rash (onboarding)

Birinchi loginda (majburiy parol almashtirishdan keyin) — kamera, joylashuv, bildirishnoma ruxsatlari **tushuntirish bilan** so'raladi ("Nega kerak" matni bilan). Rad etilsa — sozlamalarga yo'naltiruvchi tushuntirish ekrani (funksiya cheklanganini aniq ko'rsatadi, ilovani qulflab qo'ymaydi).

### 8.5 Favqulodda xabar (SOS)

**Uzoq bosish (long-press, ~2 soniya)** + 3 soniyalik bekor qilish oynasi (tasodifiy bosishdan himoya, lekin tezkorlikni saqlaydi). Yuborilgach ovoz/vibratsiya bilan tasdiqlanadi.

---

## 9. Xavfsizlik va cheklovlar

> OTP yo'qligi — eng katta xavfsizlik og'irligini **parol siyosati va audit**ga ko'chiradi.

| Chora | Tavsif |
|---|---|
| Parol siyosati | ≥10 belgi, katta/kichik/raqam/belgi; vaqtinchalik parol bir martalik; BCrypt/Argon2 hash |
| Login himoyasi | Attempt-limit + lock (5.5), refresh-token revoke, TLS majburiy |
| Audit log | Account yaratish/parol reset/bloklash/login (muvaffaqiyat/xato)/rol o'zgarishi — **kim, kimga, qachon** |
| Fayl xavfsizligi | MIME/hajm validatsiyasi, MinIO private bucket + presigned URL, server-side metadata tekshiruvi |
| Geolokatsiya maxfiyligi | Faqat smena vaqtida, faqat vakolatli rollarga ko'rinadi, retention siyosati (11-bo'lim) |
| RailSafe xavfsizligi | Webhook — har pereezd o'z API key/mTLS bilan, rate limiting |

---

## 10. Bosqichma-bosqich implementatsiya rejasi

| Bosqich | Qamrov | Agent(lar) |
|---|---|---|
| **1 — Core & Auth** | Backend skeleton, PostgreSQL+PostGIS, RBAC, login/parol oqimi (majburiy almashtirish, admin reset, audit), Admin panel MVP (login, xodim CRUD, dashboard skeleton) | `spring-agent`, `frontend-agent` |
| **2 — Mobil asosiy oqim** | KMM shared (DTO, Ktor client, SQLDelight, sync queue, sync-status UI), Android+iOS: login, hodisa qayd (foto+GPS+offline), xarita, ruxsat onboarding | `android-agent`, `ios-agent` |
| **3 — Web analitika & Operator** | KPI/grafik/hudud taqsimoti, jonli xarita (WS), Operator paneli, reyting, hisobot eksport | `frontend-agent`, `spring-agent` |
| **4 — RailSafe integratsiya** | Webhook, RabbitMQ ingestion, pereezd/kamera/detection modeli, dashboard/xaritada ko'rsatish | `spring-agent`, `frontend-agent` |
| **5 — Favqulodda & push & kengaytirilgan** | SOS oqimi, FCM/APNs, real-time notification, kengaytirilgan filtrlar/statistika | `spring-agent`, `android-agent`, `ios-agent`, `frontend-agent` |

Har bosqich oxirida: QA/review + git commit (`git-agent`); user-facing oqimlar `product-reviewer-agent`dan qayta o'tadi. **MVP chegarasi: 1+2-bosqich.**

---

## 11. Ochiq savollar / taxminlar (mijoz bilan aniqlashtirilishi kerak)

| # | Savol / taxmin | Ta'siri |
|---|---|---|
| 1 | Hodim web paneli haqiqatan kerakmi, yoki inspektorlar faqat mobil? (Taxmin: web = Operator/dispetcher) | Frontend qamrovi |
| 2 | Server joylashuvi: bulut yoki davlat talabi bo'yicha on-premise? (Taxmin: on-premise ehtimoli yuqori) | Deploy, storage, push |
| 3 | Miqyos: nechta inspektor/onlayn foydalanuvchi (screenshotda 542/387)? | Scaling, monolith yetarlimi |
| 4 | RailSafe: kamera soni, hodisa chastotasi, PLC protokoli, desktop dasturni kim yozadi? | 4-bosqich hajmi |
| 5 | Parol reset protsedurasi — xodim shaxsini admin qanday tasdiqlaydi? | Xavfsizlik/tashkiliy jarayon |
| 6 | Data-scoping: Admin faqat o'z hududini ko'radimi? | RBAC murakkabligi |
| 7 | Geolokatsiya/hodisa ma'lumotlari qancha muddat saqlanadi? | DB hajmi, huquqiy |
| 8 | FCM/APNs davlat tarmog'ida ishlaydimi? | 5-bosqich |
| 9 | iOS tarqatish: App Store yoki ichki (MDM/enterprise)? | Release jarayoni |
| 10 | Moviy/yashil ikki mavzu — turli tashkilotlar (multi-tenant) uchunmi yoki shunchaki tema? | Multi-tenant bo'lsa arxitektura jiddiy o'zgaradi |
| 11 | RailSafe video oqimi markazda ko'rinishi kerakmi, yoki faqat hodisa/statistika? | Video streaming infratuzilmasi |

---

## 12. Mahsulot oqimi (UX) tekshiruvi — aniqlangan va yechilgan nuqtalar

`product-reviewer-agent` login, admin va mobil oqimlarini alohida tekshirdi. Kritik/muhim topilmalar yuqoridagi bo'limlarga (5.1–5.3, 8.2–8.5) allaqachon **yechim sifatida** kiritildi. Implementatsiyadan oldin yana tasdiqlanishi kerak bo'lgan qoldiq nuqtalar:

| Daraja | Topilma | Holat |
|---|---|---|
| Kritik | Vaqtinchalik parolni admin qanday xavfsiz yetkazadi (og'zaki/yozma) — texnik yechim emas, tashkiliy protsedura kerak | TZ'da qayd etildi (5.4), protsedura mijoz tomonidan belgilanadi |
| Kritik | Bloklangan xodimning mobil sessiyasi | ✅ Yechildi — 5.3 ga qo'shildi |
| Kritik | Majburiy parol almashtirishni chetlab o'tish | ✅ Yechildi — 5.2 ga qo'shildi |
| Muhim | Offline sync-status ko'rinishi | ✅ Yechildi — 8.2 ga qo'shildi |
| Muhim | GPS o'chirilgan holat | ✅ Yechildi — 8.3 ga qo'shildi (retention #7 ochiq qoladi) |
| Muhim | SOS tugmasi — tasodifiy bosish vs tezkorlik | ✅ Yechildi — 8.5 (uzoq bosish + bekor qilish oynasi) |
| Muhim | Ruxsatlarni so'rash oqimi | ✅ Yechildi — 8.4 |
| Muhim | Admin/mobil terminologiya izchilligi | ✅ Yechildi — 8.2'da bir xil status/rang tizimi belgilandi |
| Kichik | Mobilda "mening hodisalarim" tarixi kerakmi | 2-bosqich implementatsiyasida android/ios-agent aniqlaydi |
| Kichik | Hodisa qayd jarayonida "bekor qilish"da qoralama saqlanishi | 2-bosqich implementatsiyasida hal qilinadi |
| Kichik | Bo'sh holat (empty state) — yangi tashkilot uchun dashboard/ro'yxatlar | 1 va 3-bosqich implementatsiyasida hal qilinadi |

---

## Xulosa — asosiy qarorlar

| Qaror | Tavsiya |
|---|---|
| Backend | **Spring Boot (Kotlin), modular monolith** |
| Mobile | **KMM shared logic + native UI** (Compose/SwiftUI) |
| Frontend | **Vue 3 + Pinia + Leaflet/MapLibre**, rolga qarab Admin/Operator |
| DB | **PostgreSQL + PostGIS** |
| Fayl | **MinIO (S3)** |
| Real-time | **WebSocket (STOMP) + Redis**, push FCM/APNs |
| RailSafe | **REST webhook + RabbitMQ** |
| Auth | Login/parol, OTP yo'q, admin-managed accounts, majburiy birinchi almashtirish, admin reset, kuchli parol siyosati + audit |
| MVP | **1+2-bosqich** (auth + admin core + mobil hodisa/offline/xarita) |

**Eng katta risklar:** (1) OTP yo'qligi → parol yetkazish/reset — tashkiliy protsedura + audit bilan qoplanadi; (2) RailSafe integratsiya kontrakti hali noaniq (11-bo'lim #4) — shu sababli 4-bosqichga qoldirilgan; (3) iOS Alimardon uchun yangi — KMM strategiyasi bu riskni maqsadli kamaytiradi.

**Keyingi qadam:** 11-bo'limdagi ochiq savollar (ayniqsa #1, #2, #4, #10) mijoz/tashkilot bilan aniqlansa, 1-bosqich (`spring-agent` + `frontend-agent`) implementatsiyasi boshlanishi mumkin.
