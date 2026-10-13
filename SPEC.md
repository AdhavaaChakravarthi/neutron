# Commander spec

Commander is a personal Android app that gives you orders for your day: when to wake, when you'll be sharpest, when to stop caffeine, when to wind down and when to sleep. You follow it for 7 months (October 2026 to May 2027). It is for one user, sideloaded, and never leaves the phone. The science and every number behind it are in [MODEL.md](MODEL.md); every choice is in [DECISIONS.md](DECISIONS.md).

## Rules of command

Following every order for 7 months only works if no order can hurt you and every order can be checked. These rules bind every module, now and later.

1. **Every order says why.** Each one comes from published research or a labelled assumption, and the reason is one tap away.
2. **Estimates are labelled.** Anything the model guesses (peak window, melatonin rise) is marked as an estimate.
3. **No sudden changes.** Sleep: bedtime and wake time move at most 30 min a day. Later modules: training load rises gradually, food rules stay moderate, nothing extreme.
4. **Aim for enough, never less.** Bedtime aims for your full sleep need. No module may order less sleep, a crash diet or training through pain.
5. **You report back and the Commander adjusts.** When a prediction doesn't match how you feel, the plan changes, not you.
6. **Pain, illness or exhaustion means stand down.** If you follow orders and still feel exhausted for weeks, the Commander says to see a doctor.
7. **No guilt mechanics.** A skipped order is logged, not punished. No streaks to protect.
8. **Wellness, not medicine.** Nothing diagnoses or treats a condition.

## Modules

| Module | Status |
|---|---|
| Sleep and energy | Built: real nights, debt, need learning, rhythm stats, morning report, reminders |
| Fuel (nutrition) | Built: food log on the USDA database, your foods and recipes, weigh-ins and trend, adaptive expenditure, weekly check-in, targets, weigh-in order (MODEL.md §10) |
| Train | Built: 4-day upper/lower gym program with double progression, set logger with rest timer, swim/bike/run log with effort-based load, a 70.3 plan with recovery weeks and taper, mobility routines, stand-down (MODEL.md §11) |
| Mobility | Built, inside Train |
| Gut log | Planned. Logging and patterns only, no diagnosis |

The order of the later modules is open; ARCHITECTURE.md lists the phases.

## Sleep and energy: scope

**In v1**

1. Phone-only sleep detection with a morning confirm, plus Health Connect when any app has written sleep, plus manual entry and editing.
2. Sleep need: an estimate that learns from natural-wake nights, with manual override.
3. Sleep debt over the last 14 nights.
4. Today's energy curve with grogginess, peak, sleepy and wind-down zones, a recommended bedtime and an estimated melatonin rise.
5. Nudges: caffeine cutoff, morning light, wind-down, bedtime.
6. Optional sleepiness check-ins (KSS 1–9) that calibrate the curve to you.
7. Export and import of all data as a JSON file.

**Not in v1:** accounts, cloud sync, any network access, iOS, Play Store release, wearables-specific integrations, automatic nap detection, sleep stages, smart alarm.

## Screens

### Onboarding (first launch only)

1. **What Commander is.** One screen: estimates based on published sleep research, a wellness tool not a medical device, data stays on the phone.
2. **Your usual schedule.** Typical bedtime and wake time for workdays and days off; target wake time. These seed the first week before real nights exist.
3. **Permissions,** each with a one-line reason and a skip:
   - Usage access (screen events) → "so Commander can tell when you put your phone down and picked it up". Skipping means manual logging.
   - Health Connect sleep (read) → "if another app or a future watch records sleep, Commander uses it".
   - Notifications → "for the morning check-in and nudges".

### Today (home)

Dark tactical HUD (D-37), top to bottom:

- **Top bar:** mark, COMMANDER, campaign day ("DAY 002 / 212"), clock, settings.
- **Silent banner** (amber) when notifications are off, with an Allow button.
- **Telemetry strip:** next order and countdown, lights out, sleep need, tomorrow's wake.
- **Mission panel** over contour lines: current phase, the "right now" line, next order, lights out, need, debt.
- **Mission brief** (the mint panel, read this first): what's left of today in two or three sentences, over a wireframe of today's curve.
- **Last 21 days** (MODEL.md §12): a hexagon with Sleep, Rhythm, Fuel, Strength, Endurance and Mobility corners scored 0–100 for orders followed, recalculated every day, with an Overall number and last week's shape as an outline. Below it, each corner's number and its change from a week ago; "How it's scored" opens one line per corner. A corner without 7 days of data shows "—".
- **Day radar:** a 24-hour dial with sleep, groggy, sharpest and wind-down bands, order blips and a sweep at now.
- **Energy telemetry:** the curve with groggy (hatched), sharpest (green) and wind-down (amber) zones and the sleepiness line.
- **Mission timeline:** every order with time, type (order / info / estimate), reason on tap, a reminder toggle for each order that can notify, and "Set alarm" on tomorrow's wake.

### Reminders

Notifications on one "Orders" channel, at the exact minute: up (with the light window), sharpest window opens, last caffeine (30 min warning), wind down, lights out; melatonin optional. Toggle per order on Today or in Settings; Settings can send a test order. Alarms are rebuilt on every settings change, every fired order, reboot, clock or time-zone change and app update. Orders more than 20 min late are skipped.
- **Sleep debt** in hours with its band (low / moderate / high) and a 14-night sparkline.
- **Next nudge** with its time.
- A text summary of the curve for TalkBack, e.g. "Groggy until 8:40 am. Peak 3:50 to 5:20 pm. Sleepy after 10:45 pm."

### Morning report (built)

A card at the top of Today (and a notification 1 h after your usual wake) until last night is in: the night from phone use, or your usual times if nothing was found. Fix "asleep" and "up", say whether you woke without an alarm, confirm. "I didn't sleep" counts the night as zero.

### Sleep tab (built)

Debt with its band and a 14-day bar chart against your need; need (learned or manual); rhythm (usual times, regularity, social jetlag, chronotype); detection status; log a night or nap; history you can fix or delete.

### Fuel tab (built)

- **Rations.** Calories left (or over) against today's target, bars for calories, protein, fat and carbs, fiber eaten, and carbs in g/kg beside the Burke et al. bands. On a fruit day: what you ate, no targets.
- **Log.** Breakfast, lunch, dinner and snacks, each with its own add button. Tap an entry to change the amount or meal, or delete it. Quick add (calories and optional macros), copy yesterday, and a "not fully logged" switch that keeps the day out of the expenditure estimate.
- **Add food.** Search your foods, your recipes and 7,448 USDA foods offline; recent foods first. Filters for no meat, vegan and fruit only start from the day's practice and can be changed. Pick a serving or grams, see the numbers, log. Stays open so a meal of several foods is quick.
- **Your foods and recipes.** A food from a label, per 100 g or per serving, tagged plant, dairy or egg, fish, or meat. A recipe from ingredients, with the cooked weight and number of servings.
- **Weight.** Today's weigh-in, trend, 7-day change, and six weeks of weigh-ins (dots) with the trend (line). A weigh-in more than 3 kg from the trend asks you to check it.
- **Coach.** What you burn, goal, daily target, resting energy, measured weekly change, next check-in, and one or two sentences on why the target moved. Losing faster than 1% a week turns into an amber "eat to target" note and order.
- **Setup.** Sex (for the equation), birth year, height, this morning's weight, activity band, goal, check-in day, and your practices: fruit days, vegan days, fish but no meat. A health note says who should check with a doctor or dietitian first.

On Today, Fuel adds the weigh-in order (15 min after waking; "Log weight" right on the order), the eat-to-target order when it applies, and a fuel line in the mission brief.

### Train tab (built)

- **Today.** The day's lift (Lower A, Upper A, Lower B or Upper B) with a Start or Continue button, the planned swim, bike or run with minutes and easy or quality, mobility, a 5-minute warm-up, "log other", and "stand down" for pain, illness or exhaustion. With high sleep debt the day is eased: quality becomes easy and lifts drop a set.
- **Lift logger.** Each exercise shows its sets × reps, rest, and today's target from double progression ("Up to 105 kg", "100 kg · beat 9, 9, 8", or how to pick a first weight). Log weight, reps and optional reps in reserve. Logging a set starts the rest timer, which also notifies with the screen locked. "How" shows instructions and lets you swap the exercise for one that trains the same main muscle. Finishing asks for the session rating (0–10).
- **Session log.** Swim, bike, run or other, with minutes, distance, rating and a brick flag.
- **This week.** Seven days with what each asks for and a tick when it's done.
- **Load.** Minutes done against planned per sport, weekly load (rating × minutes), the 4-week easy share against 80%, and sets per muscle against ~10.
- **Plan.** Race date, hours now and at most, this week's minutes, and a bar chart of every week to the race (dots for what you did). "Edit plan" changes it.
- **Lifts.** Every exercise in the program with its last session, best estimated 1RM and next target.
- **Mobility player.** One hold at a time: name, side, a big countdown, how to do it, what's next. The screen stays on. It logs when done.

On Today, Train adds the "Train" order after the groggy window, with today's session and an Open Train button, and a "Mobility" order 45 minutes before wind-down. Both tick themselves when logged. A "Train:" line joins the mission brief.

### Morning check-in (original plan)

Opened from a notification about an hour after detected wake-up, or from Today if unanswered.

- "Looks like you slept **11:52 pm – 7:18 am**. Right?" Confirm, or drag start/end.
- "Did you wake up without an alarm?" Yes / No.
- Optional: "How sleepy do you feel right now?" KSS 1–9.
- If no rest period was found: "Did you sleep last night?" → enter times, or "I didn't sleep".

### Sleep

- Last 14 nights as bars against your need, with source icon (phone, Health Connect, manual) and unconfirmed nights marked.
- Tap a night to edit or delete it. Add a night or a nap manually.
- Current sleep need and how it was estimated (prior vs. number of qualifying nights).

### Habits

Toggle and time-offset for each nudge: caffeine cutoff (default 8 h before bedtime), morning light (within 1 h of waking), wind-down (60 min before bedtime), bedtime. Quiet hours.

### Settings

- Target wake time (workdays and days off).
- Sleep need: estimated value or manual override.
- Permissions status with a fix button for each.
- Calibration status: number of KSS ratings, whether a personal fit is active, reset.
- Export / import data (JSON).
- **Why this curve?** Plain-language model explanation, current parameters, and the source list from MODEL.md.
- Crash log: view and share the local log.

## Key flows

**Every morning:** detection runs after the usual wake time → if confident, a check-in notification; if not, the check-in asks directly → confirmed night updates debt, need and today's curve → nudges are rescheduled.

**Calibration:** KSS check-ins accumulate → after 20 ratings over 7 days, Commander fits a personal curve, keeps it only if it predicts held-out ratings better, and says so on Today.

**Travel:** time-zone change detected → curve stays in body time and moves toward local time at up to 1 h per day; Today shows "Adjusting to new time zone".

## Edge cases v1 must handle

| Case | Behaviour |
|---|---|
| First days, no real nights | Use the onboarding schedule as provisional nights, labelled "estimated" |
| Check-in ignored | Unconfirmed inferred night is used, marked, and can be confirmed later |
| No rest period found (all-nighter, phone used all night) | Ask; "I didn't sleep" is a valid answer and adds to debt |
| Split night or long middle-of-night phone use | Detector finds one block; user adds the other as a nap |
| Phone left in another room or switched off | Rest period may be long; confirmation is the fix |
| Usage access revoked | Banner on Today; app keeps working in manual mode |
| Health Connect missing or permission revoked | Silently fall back to phone detection |
| Health Connect and phone disagree | Health Connect wins; manual edits beat both |
| Overlapping episodes | Rejected at entry with a message |
| DST night | Duration computed from instants, so a 23 h or 25 h night is handled correctly |
| Time-zone travel | See Travel flow |
| Phone reboot | Nudges rescheduled on boot |
| Clock changed manually | Recompute from stored instants |
| Very long sleep (> 14 h) or very short (< 1 h) | Accept but ask to confirm |

## Quality bar for "done"

Personal-only means no store compliance work, not lower quality.

- `:model` unit tests match the Python reference's golden vectors; data layer and ViewModels tested with fakes; Compose UI tests for check-in, editing a night and onboarding.
- CI on every push: lint, unit tests, debug build, and the Python reference checks.
- No `INTERNET` permission. Data cannot leave the phone except through the user's own export.
- Survives missing data, revoked permissions, reboot, DST and time-zone changes.
- TalkBack labels and a text summary for the chart; works at the largest font size; dark theme; contrast checked.
- Copy never claims to diagnose, treat or detect a sleep disorder.
- Crashes are written to a local log the user can view and share.
