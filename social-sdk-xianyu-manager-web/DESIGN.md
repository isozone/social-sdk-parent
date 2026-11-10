# DESIGN.md — AI鱼多宝 · 闲鱼多账号智能运营平台

> Design system for the Xianyu Manager admin UI. Built on the **Linear dark system**
> (VoltAgent/awesome-design-md) — near-black canvas, four-step surface ladder,
> hairline borders, a single chromatic accent. Data-dense, technical, quietly luxurious.
>
> Agent rule: read this file before touching any `.vue` file. All colors/shadows/radii
> MUST come from the CSS custom properties defined in `src/styles/theme.css`.

## 1. Visual Theme & Atmosphere

- **Dark-first operations console.** Near-black canvas with a faint blue tint, dense data surfaces, hairline separators. No decorative gradients; color is information, not ornament.
- The accent (`#5e6ad2` lavender-blue) appears on: primary actions, active navigation, focus rings, links, and brand marks — **never decoratively**.
- Typography is the loudest design element: 500–600 weight headings with negative tracking, muted gray body, mono for all numbers/IDs/emails/tokens.
- Density over airiness: tables and toolbars stay compact (12–16px padding). Whitespace is used to separate *sections*, not rows.

## 2. Color Palette & Roles

| Token | Hex | Role |
|---|---|---|
| `--bg-canvas` | `#0b0c0e` | App canvas (body), menus, dialogs backdrop base |
| `--bg-surface-1` | `#141518` | Cards, panels, table container |
| `--bg-surface-2` | `#1a1c20` | Lifted tiles, table header, hover fill, input background |
| `--bg-surface-3` | `#212328` | Popovers, dropdowns, tooltips |
| `--border-hairline` | `#26282e` | Card/table/divider borders (1px) |
| `--border-hairline-strong` | `#34363c` | Hovered borders, scrollbar thumb |
| `--text-1` | `#f7f8f8` | Headings, primary data |
| `--text-2` | `#d0d6e0` | Body, secondary data |
| `--text-3` | `#8a8f98` | Captions, placeholders, muted labels |
| `--brand` | `#5e6ad2` | Primary action, active nav, focus ring |
| `--brand-hover` | `#828fff` | Primary hover |
| `--color-success` | `#27a644` | Shipped / completed / ACTIVE |
| `--color-warning` | `#d97706` | Pending / expiring / cooldown |
| `--color-danger` | `#e5484d` | Failed / expired / destructive |
| `--color-info` | `#8a8f98` | Neutral status |

Semantic rule: success/warning/danger are used **only** in status tags, metric deltas, and icon chips — never as card backgrounds.

## 3. Typography

| Level | Size/Weight | Tracking | Use |
|---|---|---|---|
| Page title | 20px / 600 | -0.4px | `.page-head__title` |
| Card title | 16px / 600 | -0.2px | `.card-title` |
| Body | 14px / 400 | 0 | Tables, forms, default |
| Caption | 12px / 400 | 0 | `.card-sub`, labels, helper text |
| Metric | 26px / 700 | -0.5px | `.metric-value`, dashboard KPIs |
| Mono | 12.5px | 0 | IDs, cookies, emails, tokens, log lines |

Font stack: `-apple-system, "PingFang SC", "Segoe UI", "Microsoft YaHei", sans-serif`; mono: `"SF Mono", "JetBrains Mono", Menlo, Consolas, monospace`.

## 4. Component Stylings

- **Card**: `--bg-surface-1`, 1px `--border-hairline`, radius 12px, no drop shadow by default; hover = border brightens to `--border-hairline-strong` (no translateY bounce).
- **Table**: header row `--bg-surface-2` 12px captions uppercase-feel muted; row hover `#1c1e23`; row borders `--border-hairline`.
- **Primary button**: `--brand` bg, white text, radius 8px, padding 8px 14px; hover `--brand-hover`. Secondary: `--bg-surface-2` bg, `--text-1`. Danger: `--color-danger`.
- **Input/select**: `--bg-surface-2` bg, `--border-hairline` border, radius 8px; focus ring 2px `--brand` at 35% opacity, no glow.
- **Sidebar**: canvas bg, 224px; item radius 8px; active item `--bg-surface-2` + `--brand` left text/icon; group captions 11px uppercase muted.
- **Tag/status-badge**: pill radius, `--bg-surface-2` bg + semantic text color, 12px.
- **Dialog/dropdown**: `--bg-surface-3`, radius 12px, hairline border, shadow `0 16px 48px rgba(0,0,0,.5)`.

## 5. Layout Principles

- Fixed single-screen shell: sidebar 224px + topbar 56px; each page owns its scroll region (`.page-root`).
- Spacing scale: 4/8/12/16/24/32. Page padding 16px; card padding 20px; toolbar gap 10px.
- Toolbars: filters left, primary actions right. Tables fill remaining height and scroll internally.

## 6. Depth & Elevation

Flat ladder, shadows only for floating layers: dropdown/dialog `0 16px 48px rgba(0,0,0,.5)`; popper `0 8px 24px rgba(0,0,0,.4)`. Cards rely on hairline borders, never shadows.

## 7. Do's and Don'ts

- DO use semantic tokens; DO NOT hardcode hex in `.vue` files.
- DO keep one accent; DO NOT introduce new hues (orange/red only as semantics).
- DO NOT use light surfaces (`#fff`, `#f5f7fa`) anywhere in the admin shell.
- DO NOT add rounded > 12px on cards or gradient buttons.
- Empty states: icon + muted text, centered, no illustration dumps.

## 8. Responsive Behavior

Admin-first: min content width 1200px; sidebar collapses to icons <1440px (future). Tables scroll horizontally, never wrap cells.

## 9. Agent Prompt Guide

Palette quick ref: canvas `#0b0c0e`, surface `#141518/#1a1c20/#212328`, hairline `#26282e`, text `#f7f8f8/#d0d6e0/#8a8f98`, accent `#5e6ad2` (hover `#828fff`), success `#27a644`, warning `#d97706`, danger `#e5484d`. Prompt: "Build/adjust this page in the Linear dark admin style per DESIGN.md: near-black surfaces with hairline borders, single lavender accent, compact data density, mono for IDs."
