# CAT Daily 15 — Android app

Daily CAT practice built from the actual CAT 2017–2025 papers. Every day each user gets **5 VARC + 5 DILR + 5 QA** questions, with years and difficulties mixed. Solving all 15 grows a 🔥 streak, and a leaderboard ranks everyone who solved the same set.

## What's inside

| | |
|---|---|
| Question bank | **1,830 questions** from 9 papers (2017–2025, all slots): VARC 642 · DILR 576 · QA 612 |
| Mapping | Every question is tagged with **section → topic → sub-topic**, **year/slot**, **MCQ/TITA**, and **Easy/Medium/Hard**. The full map is in `docs/question_map.csv`. |
| Display | Each question is cropped straight out of the original PDF page, so passages, DILR tables/charts, fractions and exponents look exactly as printed. Passage and DILR set text is shared across all the questions that use it. |
| Today tab | 15 questions for the day, a streak bar (last 7 days), current and best streak, progress out of 15 |
| Sections tab | VARC / DILR / QA switcher with progress → overview, quick practice (Easy/Medium/Hard, untried only), topic cards → topic page with difficulty, status (not tried / attempted / wrong), year and sub-topic filters and a "Practice these" button that steps through the list with Next |
| Name | Asked on first launch; tap "Hi <name>" on Today to change it |
| Hidden for now | Leaderboard and Settings (admin PDF import, answer key) — the code is kept, the tabs are removed |

### Topics used
- **VARC:** Reading Comprehension (sub-types: Inference, Central Idea, Strengthen/Weaken, Tone, Vocabulary & Purpose, Specific Detail), Para Jumbles, Para Summary, Odd Sentence Out, Sentence Placement
- **DILR:** Tables & Charts (DI), Scores/Ratings & Rankings, Venn Diagrams & Sets, Scheduling & Routes, Selection & Distribution, Games & Tournaments, Seating & Arrangements, Grids/Cubes & Puzzles
- **QA:** Arithmetic (Percentages, Profit & Loss, SI/CI, Ratio, Mixtures, Averages, Time-Speed-Distance, Time & Work), Algebra, Geometry & Mensuration, Number System, Modern Maths

## Build the APK

**Option A: GitHub, no setup on your computer.** Push this folder to a GitHub repo. The included workflow (`.github/workflows/build-apk.yml`) builds the APK on every push. Download it from **Actions → latest run → Artifacts → CATDaily-apk**.

**Option B: Android Studio.** Open this folder, let Gradle sync, then use *Build → Build APK(s)*. The project uses Android Gradle Plugin 8.7, Kotlin 2.0, Jetpack Compose, and minSdk 26 (Android 8.0 or newer).

Before building, **change the admin PIN** in `gradle.properties` (`CAT_ADMIN_PIN`). The default is `2101`.

## Turn on the multi-user leaderboard (Firebase, free tier)

The app works offline without this step, but the leaderboard then only shows you.

1. Go to <https://console.firebase.google.com>, click **Add project**, and inside it **add an Android app** with package name `com.catdaily.app`.
2. Download `google-services.json` and put it in `app/`. If you build with GitHub, paste its contents into a repo secret named `GOOGLE_SERVICES_JSON` instead.
3. Go to **Build → Authentication → Sign-in method** and enable **Anonymous**.
4. Go to **Build → Firestore Database**, create the database, then open **Rules**, paste in the contents of `firestore.rules`, and publish.

Build again after these steps. Anonymous sign-in and the leaderboard now switch on automatically.

**Who is the admin?** The first phone that unlocks the admin tools with the PIN while connected to Firebase becomes the owner. Only the owner can publish PDFs, answer keys and tag edits to everyone else.

## Adding new papers (Settings → Admin)

1. Unlock the admin tools with the PIN.
2. Tap **Choose PDF**. The year is detected from the text ("CAT 2026"), or you can type it in.
3. The app copies the PDF and works through the same steps used for the bundled papers:
   - finds the slots, the `SECTION` headings, the `DIRECTIONS` blocks (passages and DILR sets), and the question numbers (`Question No. : N` or `N.`)
   - works out the crop region for each question
   - decides MCQ or TITA
   - assigns a topic using the keyword rules in `assets/taxonomy.json`
   - assigns Easy/Medium/Hard by scoring each question against the score cut-offs of the existing bank
4. A preview shows the counts per section and per difficulty. Tap **Add to app**. The new questions join the daily mix from that day. With Firebase on, tick **Publish to all users** to upload them (rendered as compressed images) so every phone gets them.

The importer handles PDFs laid out like the bundled ones (in testing on the 9 bundled papers it found all 1,830 questions, and agreed with the bundled MCQ/TITA and difficulty tags on 99.8% of them). Papers from other publishers need the same `SECTION` and question-numbering conventions.

## Answer keys

**The supplied PDFs contain questions only, with no answers.** Until a key is loaded, an answer is saved, it counts toward the 15 and the streak, and earns 1 point. Once a key is loaded, scoring follows CAT rules: **+3** correct, **−1** wrong MCQ, **0** wrong TITA.

To load answers, open `docs/answer_key_template.csv`. It already lists every question with columns year, slot, section, number and answer. Fill in the answers and import the file from *Settings → Admin → Import answer key*. For TITA questions, separate multiple accepted answers with `|`. Answers you entered before a key was loaded stay ungraded.

## How the daily 15 are chosen
- Every phone with the same bank gets the **same 15 questions** on the same day. Selection is deterministic from the date, so the daily leaderboard is fair.
- The difficulty mix per section rotates by day: E,E,M,M,H → E,M,M,H,H → E,M,M,M,H.
- The 5 questions in a section come from different years where possible, and never two from the same passage or DILR set.
- A question doesn't repeat until every question of that difficulty has been used. That is about 120 days of fresh questions.
- The streak counts a day only if all 15 are answered that same day. You earn one streak freeze for every 7 completed days (up to 2 banked). A banked freeze automatically covers a single missed day.

## A note on difficulty labels
CAT doesn't publish difficulty per question. The labels come from a scoring heuristic in `Classifier.kt` (and the Python equivalent) that looks at:
- **RC:** passage length and vocabulary, question type, and EXCEPT/NOT questions
- **DILR:** set length, number of conditions, TITA, and cues like "maximum possible" or "must be true"
- **QA:** stem length, TITA, topic, and the same kinds of cues

Each section is then split roughly 35% Easy, 40% Medium, 25% Hard. The admin can change any question's label from the question screen.

## Project layout
```
app/src/main/assets/        questions.json (bank), taxonomy.json (rules), pdfs/cat_20xx.pdf, answers.json
app/src/main/java/com/catdaily/app/
  data/        Models, QuestionBank (bundled + imported + cloud), ProgressStore (attempts, streak)
  logic/       DailyPicker, Classifier (topic + difficulty), PaperParser (PDF structure → questions)
  importer/    PdfImporter (PdfBox-Android text + coordinates)
  render/      PdfCropRenderer (crops questions out of PDF pages)
  remote/      Cloud (Firebase leaderboard + bank/answer sync)
  notify/      Daily reminder (WorkManager)
  ui/          Today, Sections, Question, Leaderboard, Settings screens (Jetpack Compose)
firestore.rules, docs/question_map.csv, docs/answer_key_template.csv
```
