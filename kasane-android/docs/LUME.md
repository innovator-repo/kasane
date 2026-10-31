# Lume, the shared design system

Sigil, Vow, Ren, Kasane and Katachi share one design system, Lume. Its tokens live in
`app/src/main/java/.../designsystem/LumeTokens.kt`. That file is the same in all five apps apart
from its package line. Change a token in all five at once, and `LumeTokensTest` (also the same in
every app) checks that the change still follows the rules below.

## What the colours are for

The aim is a calm screen. It should not excite you, and it should not lull you into putting
things off. The pull to keep going should come from the work itself: a clear next step, quick
feedback, and motion that follows your finger. Colour is not there to win your attention. It is
there to say what matters and then get out of the way.

### The analysis

1. **Saturation drives arousal more than hue does.** Colour research has found that how
   saturated a colour is (its chroma) predicts how stirred up people feel more strongly than which
   colour it is. The iOS-style system colours the apps used before (`#30D158` green,
   `#FFD60A` yellow, `#FF453A` red, Tailwind's `#4ADE80`) sit at OKLCH chroma 0.18 to 0.23. Those
   are signage colours, and they make every screen feel urgent. Lume caps accents and meaning
   colours at **chroma 0.16**. That is still clearly coloured but no longer neon.
   `noColourShouts` enforces the cap.
2. **Red means threat, so it is kept for real threats.** A missed habit, a slipped rule or a
   wrong answer is shown in **terracotta**, never red. Shame works against habit change: people
   who feel judged for a lapse tend to give up on the streak. Red (`Destructive`) is reserved for
   deleting data and for real security faults, so it keeps its weight when it appears.
3. **Rewards are warm, not a jackpot.** Gold-rush yellow and neon green confetti feed the
   dopamine loop that makes apps compulsive. Lume's reward colour is **ochre** and its "done"
   colour is **sage**. Both are warm and satisfying, but quiet enough that the reward is finishing
   the task, not watching the colour.
4. **Low arousal is not low contrast.** Muddy grey text makes reading tiring, and a tired
   reader gives up early. Body ink must reach **7:1** on every page and secondary ink **4.5:1**
   (WCAG AAA and AA). Accents must reach 4.5:1 as text on a page, labels on accents must reach
   4.5:1, and meaning colours must reach 3:1 as shapes (WCAG 1.4.11).
5. **One accent per app, one family across the suite.** Each app owns one hue: Sigil sapphire
   (trust), Vow teal (steadiness), Ren akane (the vermilion of the 連 mark, toned down), Kasane
   sumi indigo (layered ink), Katachi jade. All five sit in the same lightness and chroma band, so
   moving between apps feels like moving between rooms of one house.
6. **The page follows the day.** Warm washi in the morning and plain paper by day. After 20:00 the
   page is warm graphite, and after 22:00 obsidian, whatever the system theme says. This cuts blue
   light before sleep and makes late use feel like winding down. Every app switches at the same
   hours (`LumeTokens.Hours`: 06, 12, 20, 22), so Vow's Sleep Lock, Kasane's sanctuary and Ren's
   night palette all change together.
7. **Dark is graphite, not black.** Pure `#000` behind white text smears on OLED screens and
   looks harsh. The deepest page is `#121214` and the usual dark page is `#1C1C1E`.

### The palette

| Role | Light page | Dark page | Use |
|---|---|---|---|
| Sigil sapphire | `#2A68B7` | `#76B2F5` | Sigil's accent |
| Vow teal | `#007D70` | `#5DD2BD` | Vow's accent |
| Ren akane | `#BB443A` | `#F18477` | Ren's accent |
| Kasane sumi | `#3F52AF` | `#97A8F7` | Kasane's accent |
| Katachi jade | `#1E7D68` | `#66D4B7` | Katachi's accent |
| Sage | `#2D8152` | `#70CB92` | Done, correct, on track |
| Ochre | `#A9730F` | `#E6BA5C` | Reward, stretch goal |
| Terracotta | `#C95B3C` | `#E5886E` | A miss or a slip |
| Seal | `#5D5CC2` | `#9B9DF4` | Sealed and time-locked things |
| Amber | `#B86A10` | `#F5AD5A` | Due soon, pending |
| Info | `#197CB3` | `#72BDF0` | Links, digits, radicals |
| Destructive | `#B3261E` | `#F2B8B5` | Deleting, security faults only |

Pages: Washi `#FAF9F6`, Paper `#FFFFFF`, grouped fills `#F1EFE9` / `#F4F4F6`; DayDark `#1C1C1E`,
WarmGraphite `#181715`, Obsidian `#0B0F14`. Inks: `#1D1D1F` and `#5E5E63` on light pages,
`#F2F2F5` and `#A1A1A6` on dark pages.

## Motion and speed

Smoothness is what makes an app feel fast. A screen that answers in the same frame as the touch
feels quicker than one that loads faster but answers late.

- **Two springs, nothing else.** `spatial` (damping 0.75, stiffness 380) is for things that move
  or scale. It overshoots slightly, which reads as physical. `effects` (damping 1.0, stiffness
  1500) is for colour, opacity and progress. It never overshoots, so nothing flickers. Both settle
  in about a quarter of a second. Springs pick up from wherever an interrupted animation stopped,
  so a quick second tap never jumps.
- **Answer the touch at once.** `pressable` shrinks to 0.97 with a haptic tick when the finger
  lands, before the action runs, so every press is acknowledged in the same frame.
- **Slow only where slowness is the point.** The 4-second breath (EaseInOutSine) is the one long
  animation, used for breathing and cooling-off screens.
- **Respect the system.** Compose's springs follow the system animation-scale setting, so turning
  animations off in Developer options or Accessibility applies to every app.
- **Keep frames cheap.** Theme colours are provided through `staticCompositionLocalOf`, so the
  whole tree recomposes only when the phase of the day changes, once every few hours.

## Shape and space

Corner radii come from one scale: `XXS 4`, `XS 8`, `S 12`, `M 16`, `L 24`, `XL 32` dp, used for
bars, tiles, buttons, cards, dialogs and sheets. The odd values that had built up (6, 7, 9, 10, 13,
14, 18, 20, 22) have been moved onto the nearest step. Spacing uses a 4 dp grid: 4, 8, 12, 16, 24,
32.

## App icons

Every app has an adaptive icon with a `monochrome` layer, so Android 13+ themed icons work and the
mark stays readable in black and white. The launcher reads only the alpha channel of that layer,
so:

- A mark drawn in one colour (Sigil, Ren) uses its foreground as the mask.
- A mark that relies on several tones keeps them as alpha steps. Vow's shield is 45% with the
  half-shield at full strength. Kasane's three layers are 100%, 70% and 45%.
- Shapes that touch in the colour icon get a seam in the mask so they don't fuse into one blob.
  Katachi's tangram pieces are each inset by 1.5 units.

The colour icons themselves are unchanged.

## Type

`LumeTokens.Type` holds one scale in sp (12, 13, 16, 17, 22, 28, 34), so text follows the user's
font size setting. Body text is 16 sp with a 1.4 line height, headings use 1.2, and weights stay at
Normal, Medium and SemiBold. New screens take sizes from the scale instead of writing a number.

## What the review concluded

The named design skills (perception-first-design, ui-ux-pro-max, uipro-design-system,
android-compose-styles, impeccable) are not installed in this environment, so the review applied
their published guidance by hand: Material 3 and WCAG 2.2, plus the calm-colour research above.

| Area | Finding | Result |
|---|---|---|
| Colour | Chroma, contrast and meaning already pass the tests. Hue stays per app, meaning colours are shared. | Kept as is |
| Type | Sizes were scattered numbers. | Added `Type` scale (16 sp body, 1.4 line height) |
| Touch targets | Buttons were 40 dp, chips 36 dp, below the 48 dp minimum. | Added `Control.MinTouch`, `ButtonHeight` 48, `ChipHeight` 40; applied in Kasane |
| Disabled and pressed states | Each screen picked its own alpha. | `Control.DISABLED_ALPHA` 0.38, `STATE_ALPHA` 0.12 |
| Shape | One scale already used (4 to 32). | Kept as is |
| Motion | Two springs, answers the touch at once, follows system animation scale. | Kept as is |
| Widgets, empty states | Not tokenised here to avoid clashing with the Kasane and Vow work. | Use `Shape.M` for cards, `Space.L` padding, `Type.Footnote` for hints |

How other work adopts it: take sizes from `Type`, corners from `Shape`, padding from `Space` and
control sizes and disabled alpha from `Control`.
