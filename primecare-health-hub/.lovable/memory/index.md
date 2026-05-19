# Memory: index.md
Updated: now

PrimeCare healthcare system - design system, architecture, and key decisions

## Design System
- Primary: deep medical blue HSL(210, 100%, 20%) (#003366)
- Accent: teal HSL(180, 100%, 25%), champagne HSL(40, 46%, 55%)
- Font: Inter via Google Fonts
- Card radius: 1rem, shadow: rgba(0,51,102,0.08)
- Vinmec-inspired: clean, premium, lots of whitespace

## Architecture
- Public website (PublicLayout) + Internal portal (InternalLayout)
- Auth: Zustand store, axios interceptor with refresh token
- API client: src/lib/api-client.ts, backend response format: {success, code, message, timestamp, data}
- Route guard: src/components/RouteGuard.tsx, role-based
- Roles: SYSTEM_ADMIN, OPERATIONS_ADMIN, STAFF, CASHIER, DOCTOR
- Mock data: src/lib/mock-data.ts (fallback when API unavailable)
- Backend: Spring Boot, VITE_API_BASE_URL env var
- i18n: react-i18next, VI (default) + EN, translations in src/i18n/

## Key Files
- Design tokens: src/index.css + tailwind.config.ts
- Types: src/types/api.ts
- Auth store: src/stores/auth-store.ts
- Theme store: src/stores/theme-store.ts
- Hooks: use-auth, use-public-data, use-admin-data, use-doctor-data, use-cashier-data
- Reusable: DataTable, FilterBar, JsonViewer, UploadField, PdfJobStatusCard

## Status
- ✅ Design system, layouts, routing, auth infra, i18n
- ✅ Public pages: Home, Doctors, Specialties, Branches, Services, Availability, Booking, About, FAQ, Contact
- ✅ Login page, Dashboard with charts
- ✅ All CRUD modules: Branches, Specialties, BranchSpecialties, Doctors, Staff, Patients, Medications, MedicalServices, DoctorSchedules, DoctorLeaves, AuditLogs
- ✅ Appointments management with actions (claim, confirm, cancel, check-in, no-show, complete, reschedule)
- ✅ Reception: Queue + Walk-in
- ✅ Doctor workspace: Appointments, Encounters, Prescriptions, Schedules, Leave requests
- ✅ Cashier: Invoices, PDF Job polling
