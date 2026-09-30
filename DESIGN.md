# Lector Personal Design System

## 1. Atmosphere & Identity

A quiet, reading-first Android app. The EPUB page owns the viewport; native Material 3 controls appear as compact helpers for orientation, progress, and reading preferences. Preserve publisher typography and layout.

## 2. Color

### Palette

| Role | Token | Source / usage |
|---|---|---|
| Primary | `MaterialTheme.colorScheme.primary` | Existing Material 3 default; primary actions |
| On primary | `MaterialTheme.colorScheme.onPrimary` | Labels on primary actions |
| Background | `MaterialTheme.colorScheme.background` | Library and app screens |
| Surface | `MaterialTheme.colorScheme.surface` | Reader bars, dialogs, menus |
| Surface variant | `MaterialTheme.colorScheme.surfaceVariant` | Secondary controls |
| Text | `MaterialTheme.colorScheme.onSurface` | App chrome text |
| Reading theme | `Readium Theme` | EPUB page and Compose chrome share the selected light, sepia, or dark surface/content colors |
| Dictionary marks | `DICTIONARY_COLORS` | Per-entry transparent overlapping highlights |
| Note mark | `BookNotesController` accent | Book-scoped note underline |

### Rules
- Use Material 3 semantic color roles for reader chrome.
- Resolve chrome `background`, `surface`, `onBackground`, and `onSurface` from the active Readium reading theme; do not leave bars on the default light scheme when the EPUB is dark or sepia.
- Status/navigation system bars and icon contrast follow the same active reading theme.
- Readium applies the selected Light/Sepia/Dark palette over EPUB author colors (`publisherStyles = false`); the OED layout, semantic emphasis, and dictionary marks remain intact in AVD validation.
- Dictionary marks remain translucent so overlapping terms blend visibly.

## 3. Typography

### Scale
- App titles use Material 3 `headlineSmall` or `titleLarge`.
- Reader actions/status use `bodyMedium` or `labelLarge`.
- Book content is rendered by Readium and follows EPUB/user preferences.

### Font Stack
- App chrome: Material 3 platform typography.
- EPUB body: publisher font by default.

### Rules
- Do not force app fonts into the publication.
- Page labels and controls must remain readable under Android font scaling.

## 4. Spacing & Layout

### Base Unit
Use a 4dp base. Library padding is 24dp, inline gaps are 8-16dp, and interactive touch targets are at least 48dp.

### Reader Shell
- A vertical native shell contains the top bar, a weighted Readium viewport, and the bottom bar as separate siblings.
- Bars reserve their measured height and system-bar insets; they do not overlay or hide EPUB text.
- TOC/bookmark/settings panels own their own scroll; reading content is not wrapped in another scroll container.
- The navigator fills the remaining space between controls, preserving the largest unobstructed reading area.

## 5. Components

### Reader Top Bar
- **Structure**: return to library, truncated book title, contents, current-book bookmark toggle.
- **States**: TOC open/closed; bookmarked/unbookmarked; no-TOC disabled state.
- **Surface**: material colors follow the active Readium theme.
- **Accessibility**: text labels and TalkBack descriptions.

### Reader Bottom Bar
- **Structure**: progress indicator, current resource page counter, whole-book percentage, previous/next, text size, theme.
- **States**: previous/next disable at boundaries; theme label reflects the active preference.
- **Surface**: material colors follow the active Readium theme; dark and sepia must remain legible.
- **Accessibility**: page and progress are textual, not color-only; controls have at least 48dp targets.

### Contents & Bookmarks Panel
- **Structure**: nested TOC entries and saved Locator bookmarks in a single scrollable dialog; bookmark rows expose a remove action.
- **States**: explicit empty states; selecting an item navigates to its Locator.

### Notes & Dictionary
- Notes are scoped to an EPUB and anchored to Readium Locators.
- Dictionary phrases/terms use per-entry colors and partial-match decorations limited to the current resource's page and three pages on either side.
- Seven rotating page slots preserve overlapping highlights; a one-page advance updates only the slot entering/leaving the window.
- Dictionary and note decoration updates share a serialized queue to avoid overlapping Readium WebView diffs during page turns.

## 6. Motion & Interaction

- Use standard Material 3 touch/ripple feedback and platform dialog transitions.
- No custom animation is added to page turns, controls, or typography updates; Readium owns page transitions.
- Respect Android system accessibility and reduced-motion behavior.

## 7. Depth & Surface

Use Material 3 tonal elevation for control bars and dialogs. Keep EPUB pages visually dominant; avoid full-screen opaque chrome or decorative gradients.

## 8. Accessibility Constraints & Accepted Debt

### Constraints
- Interactive targets are at least 48dp with descriptive labels.
- Current page, pages in current reflowable resource, and whole-publication progress are exposed as readable text.
- Preserve Readium/publisher accessibility and system font scaling.

### Accepted Debt
| Item | Location | Why accepted | Owner / Exit |
|---|---|---|---|
| Page counter reports the current resource; whole-book progress is separate | Reader bottom bar | Reflowable EPUB has no device-independent page count | Keep both labels explicit |
| Native UI visual QA uses Android Emulator/phone, not browser Lighthouse | Compose reader chrome | Lighthouse browser audits do not apply to native Android UI | Capture emulator screenshots and interaction results |
