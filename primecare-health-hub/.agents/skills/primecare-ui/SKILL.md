---
name: primecare-ui
description: Use this skill for PrimeCare frontend UI/UX review and implementation in Codex CLI. Trigger for React/Vite UI work, healthcare screens, patient booking, receptionist check-in, doctor encounter, admin dashboard, notification center, forms, tables, buttons, typography, spacing, dark mode, light mode, and frontend behavior that must follow backend API contracts. Enforce compact, professional healthcare UI with correct font sizes, button sizes, project color system, no unnecessary UI, no overly long pages, and no backend-disconnected frontend behavior.
---

# PrimeCare UI Skill

Use this skill when reviewing, improving, or implementing PrimeCare frontend UI/UX.

PrimeCare is a medical appointment management system. The UI must feel professional, compact, readable, healthcare-realistic, and tightly aligned with backend behavior.

## Core Rules

### 1. Keep UI compact and practical

Do not make pages feel like a long A4 document.

Avoid:

- oversized sections
- excessive vertical spacing
- too many stacked cards
- huge headers
- large empty areas
- long explanatory blocks
- unnecessary banners
- decorative sections without workflow value

Prefer:

- compact layouts
- clear grouping
- short headings
- useful summaries
- tables/lists where appropriate
- role-focused workflows
- visible key actions without excessive scrolling

Every screen should help the user finish the task quickly.

### 2. Button sizing must be restrained

Buttons must not feel oversized.

Default button guidance:

- Text should usually be `text-sm` or equivalent.
- Primary action buttons may use `text-sm` or at most `text-base` only when visually justified.
- Avoid large button text unless it is a hero/landing page context.
- Button height should be comfortable but not bulky.
- Avoid full-width buttons on desktop unless the layout requires it.
- Use clear visual hierarchy:
  - primary action
  - secondary action
  - destructive action
  - subtle/ghost action

Avoid:

- giant buttons
- too many primary buttons on one screen
- uppercase button labels unless already part of the design system
- vague labels like “Submit” when a specific action is better

Prefer labels like:

- “Book appointment”
- “Check in patient”
- “Start encounter”
- “Save diagnosis”
- “Cancel appointment”

### 3. Typography must be readable but not too large

The current project tends to feel too large. When editing UI, reduce typography to a professional app scale.

Recommended scale:

- Page title: `text-xl` or `text-2xl`
- Section title: `text-lg` or `text-xl`
- Card title: `text-base` or `text-lg`
- Body text: `text-sm`
- Helper text: `text-xs` or `text-sm`
- Table text: `text-sm`
- Button text: `text-sm`
- Badge/status text: `text-xs`

Avoid:

- `text-3xl`, `text-4xl`, or larger inside app pages unless it is a landing/marketing page
- large paragraph text
- huge empty hero-style headers inside operational dashboards
- making every card title visually dominant

Healthcare software should feel calm, precise, and operational.

### 4. Follow the project color system

Use the existing project theme and primary color.

Before changing colors:

1. Inspect existing theme files, Tailwind config, CSS variables, component styles, and shared UI components.
2. Reuse project tokens/classes instead of inventing new colors.
3. Keep light mode and dark mode both working.

Do not hardcode random colors.

Avoid:

- new unrelated blue/purple gradients
- inconsistent status colors
- hardcoded hex colors unless already used by the design system
- colors that break dark mode
- colors that reduce contrast

Prefer:

- project primary color
- existing semantic colors
- existing button variants
- existing badge/status variants
- CSS variables or Tailwind theme tokens

### 5. Respect dark mode and light mode

All UI changes must work in both modes.

When adding or changing components, check:

- background color
- card color
- border color
- text color
- muted text color
- hover state
- focus state
- disabled state
- table row state
- input state
- badge/status color
- modal/dropdown surface

Avoid:

- light-only backgrounds
- dark text on dark background
- white cards hardcoded in dark mode
- invisible borders in dark mode
- status badges that lose contrast

Use existing dark mode conventions in the project.

### 6. UI/UX must follow backend behavior

Frontend must not invent workflows that backend does not support.

Before implementing or changing UI behavior:

1. Inspect backend controller endpoints.
2. Inspect request/response DTOs.
3. Inspect enums such as appointment status, user role, encounter status, notification status.
4. Inspect validation rules.
5. Inspect error responses.
6. Align frontend states and actions with backend contracts.

Do not create fake frontend-only flows unless explicitly requested.

Avoid:

- frontend-only statuses that do not exist in backend
- mapping backend enums loosely or incorrectly
- hiding backend errors
- showing actions that backend cannot perform
- optimistic UI that can leave healthcare state misleading
- mock/demo data in production screens

If backend does not support an action, either:

- do not add the UI action, or
- clearly mark it as not implemented and recommend backend support first.

### 7. Do not add unnecessary UI

Do not make the app look fuller by adding things that are not needed.

Avoid adding:

- decorative cards
- fake metrics
- fake charts
- fake quick actions
- unnecessary icons
- marketing copy
- long helper text
- redundant summaries
- repeated patient/doctor information
- unrelated panels

Every added element must answer:

- Who uses this?
- What task does it support?
- Which backend data supports it?
- What happens when loading, empty, or error?
- Does it increase or reduce cognitive load?

If the element does not clearly improve the workflow, remove it.

## Role-Specific UI Guidance

### Patient

Patient UI should be simple, reassuring, and guided.

Prioritize:

- clear appointment booking steps
- readable doctor/specialty/date/time choices
- visible selected appointment summary
- understandable validation errors
- clear cancellation/reschedule status
- mobile-friendly layout

Avoid:

- overwhelming patients with admin-style tables
- showing raw backend enum names
- making unavailable slots look clickable
- long pages with too many sections

### Receptionist

Receptionist UI should support fast scanning and action.

Prioritize:

- appointment status clarity
- patient lookup
- check-in action
- queue visibility
- late/early arrival indicators
- clear disabled states for invalid actions

Avoid:

- hiding important status changes
- excessive card layouts
- slow multi-step actions for simple check-in
- ambiguous labels

### Doctor

Doctor UI should support clinical focus.

Prioritize:

- patient context
- appointment reason
- symptoms
- diagnosis
- prescription
- clinical notes
- save state clarity
- encounter status clarity

Avoid:

- giant headers
- too much vertical scrolling
- unrelated dashboard content inside encounter workspace
- making reopened encounters look like new check-ins
- hiding critical patient context

### Admin

Admin UI should be operational and compact.

Prioritize:

- useful metrics
- clear tables
- filters
- status distribution
- user/doctor/patient management
- appointment overview

Avoid:

- vanity metrics
- decorative charts
- giant dashboard cards
- fake data
- overly long analytics pages

## Component Rules

### Forms

Forms should be compact and clear.

Require:

- label
- validation message
- disabled/loading submit state
- backend error display
- success feedback when appropriate

Avoid:

- large gaps between fields
- unclear placeholders
- submit buttons without loading state
- generic error text when backend gives a useful message

### Tables and Lists

Tables/lists should include:

- loading state
- empty state
- error state
- compact row height
- readable status badges
- useful actions only
- filters/search only when they help

Avoid:

- oversized table text
- huge row padding
- too many columns on mobile
- action buttons that wrap awkwardly
- raw enum display

### Cards

Cards should group related information only.

Avoid:

- nested cards
- cards with one tiny piece of information
- too many cards stacked vertically
- excessive shadows
- large padding by default

Prefer:

- compact padding
- subtle border
- clear title
- direct action
- responsive grid only when it reduces scrolling

### Modals and Dialogs

Use dialogs for focused confirmation or short forms.

Avoid:

- long modal content
- multi-screen workflows inside a modal
- destructive actions without confirmation
- modals that duplicate full pages

### Status Badges

Status badges must reflect backend enum values accurately.

Badges should:

- use existing status color system
- be readable in light and dark mode
- avoid raw enum names when user-facing copy is needed

Example mapping style:

- `SCHEDULED` -> “Scheduled”
- `CHECKED_IN` -> “Checked in”
- `COMPLETED` -> “Completed”
- `CANCELLED` -> “Cancelled”
- `NO_SHOW` -> “No-show”

Do not invent statuses unless backend supports them.

## PrimeCare-Specific Checks

When changing appointment UI, verify:

- status enum alignment between frontend and backend
- allowed actions per status
- booking validation
- availability behavior
- duplicate submit prevention
- cancellation behavior
- check-in behavior
- encounter start/finish/reopen behavior

When changing encounter UI, verify:

- appointment context remains clear
- diagnosis/prescription save state is visible
- reopened encounter state does not mislead the doctor
- form data is not lost accidentally
- backend errors are shown to the user

When changing notification UI, verify:

- unread/read state
- empty state
- mark-as-read behavior
- backend error handling
- compact display
- no noisy decorative layout

## Visual Anti-Patterns to Remove

Actively remove or avoid:

- giant hero sections inside app pages
- long pages with too many vertical sections
- big typography everywhere
- oversized buttons
- excessive gradient backgrounds
- generic SaaS dashboard visuals
- card-inside-card layouts
- too many icons
- repeated information
- fake statistics
- hardcoded light mode colors
- frontend-only workflow assumptions
- mock data in production code
- decorative UI that increases scrolling

## Implementation Behavior

When editing code:

1. Inspect existing components and style conventions first.
2. Reuse shared components where possible.
3. Prefer small, safe patches.
4. Keep backend contracts unchanged unless explicitly asked.
5. Do not redesign the entire app unless explicitly requested.
6. Do not add new libraries unless necessary.
7. Do not add fake data.
8. Do not expand pages vertically without a strong workflow reason.
9. Keep changes consistent with dark mode and light mode.
10. After editing, summarize what changed and why.

## Audit Output Format

When auditing UI/UX, respond using this structure:

1. Overall verdict
2. Issues caused by oversized UI
3. Button and typography fixes
4. Layout/scrolling issues
5. Backend contract alignment issues
6. Dark mode/light mode risks
7. Unnecessary UI to remove
8. Recommended small patches
9. Files likely to change

## Code Change Output Format

When implementing changes, respond using this structure:

1. Changed files
2. What was improved
3. Button/font/spacing adjustments
4. Backend alignment notes
5. Dark mode/light mode notes
6. Remaining risks or follow-up checks

## Suggested Improvements for This Skill

When using this skill, consider improving it over time by adding project-specific references:

- `references/design-system.md`
  - primary color
  - secondary color
  - status colors
  - typography scale
  - button sizes
  - spacing scale
  - dark mode tokens

- `references/backend-contracts.md`
  - appointment statuses
  - encounter statuses
  - notification statuses
  - role permissions
  - important API endpoints

- `references/role-workflows.md`
  - patient booking flow
  - receptionist check-in flow
  - doctor encounter flow
  - admin management flow

- `references/ui-anti-patterns.md`
  - examples of UI patterns to avoid in this project
  - screenshots or file references if available

If these reference files exist, read them before making major UI changes.