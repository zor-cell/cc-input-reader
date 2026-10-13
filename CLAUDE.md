# CLAUDE.md

Java template for the [Cloudflight Coding Contest (CCC)](https://codingcontest.org). The framework handles
file discovery, input/output files and (optionally) checking solutions against the contest's HTML visualizer.
During a contest, the only things that normally change are `Solver.solve()`, `Solver.verify()` and `config.xml`.

## CCC format (typical — the level statement always wins)

- A contest runs ~4 hours and consists of sequential **levels** (usually 5–7). Each level extends the previous
  one: same story, more rules, bigger inputs. Keep the solver code reusable across levels.
- Each level comes with a **statement** (PDF/web page) and a **zip of input files**:
  `levelN_example.in`, `levelN_example.out` (expected output for the example) and `levelN_1.in` … `levelN_5.in`.
  Newer contests name them `in_level-N_0-example.txt`, `out_level-N_0-example.txt` (expected output) and
  `in_level-N_1-small.txt` … `in_level-N_k-large.txt`, often with a `sandbox-level-N.html` visualizer. Both
  schemes are picked up; `out_*` files are never treated as input.
- For every `levelN_k.in` an output file is uploaded to the contest platform, which answers valid/invalid
  immediately. All files of a level must be accepted to unlock the next level; usually the source code is
  uploaded as well.
- Input files usually start with a count (number of test cases, or dimensions) followed by the cases. Output is
  usually one line per case, in later levels sometimes several lines per case.
- Later levels often accept **any valid** solution (paths, placements, schedules), so `levelN_example.out` is
  then just one possible answer and a plain diff proves nothing. Some contests ship a `visualizer.html` that
  validates a solution for exactly this reason.
- The statement is provided by the user (PDF or pasted text). If it is missing, ask for it — do not infer the
  task from the input files alone.

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/solver/Solver.java` | `solve()` and `verify()` — the contest code. Do not change `main` or the method signatures. |
| `src/main/resources/config.xml` | Paths, level filter, log level, verifier settings. |
| `src/main/java/io/IOUtils.java` | `readList` (one line → list), `readGrid` (height lines → 2D list). Delimiter is a **regex**. |
| `src/main/java/io/IOManager.java` | Finds input files, creates output files, calls `solve`/`verify` per file. |
| `src/main/java/solver/SolutionVerifier.java` | Selenium/Chrome driver around the visualizer. |
| `input/levelN/` | Unzipped level files. |
| `output/` | `<input basename>.txt` per input file (a leading `in_` becomes `out_`), plus `<basename>.ver` when the verifier is on. Scratch: created if missing, wiped on every run. |
| `solutions/` | Only if `<solutionPath>` is set: a copy of every non-empty output file, in the same sub-folders as below `input/` (`input/contest/level1/` → `solutions/contest/level1/`). Never cleaned, meant to be committed. |

`input/level99/` is a dummy level (sum of each line) matching the template code in `Solver.java`.
`input/visualizer.html` is a sample from CCC 41 ("Spaceship") and is unrelated to level99.

## Run

Always from the repo root (paths in `config.xml` are relative to the working directory):

```bash
mvn -q compile exec:java
```

`config.xml` is read from the classpath, so it only takes effect after `mvn compile` — the command above covers
that. Requires JDK 21 and Maven.

## Workflow per level

1. Unzip the level into `input/levelN/`.
2. Read the statement and `levelN_example.in` / `.out`, then implement `solve()`.
3. Set `<targetSpecificLevel>Ne</targetSpecificLevel>` (example only) and run.
4. Compare with the expected example output:
   ```bash
   diff --strip-trailing-cr output/levelN_example.txt input/levelN/levelN_example.out
   ```
   With the newer naming both files are called `out_level-N_0-example.txt`:
   ```bash
   diff --strip-trailing-cr output/out_level-N_0-example.txt input/levelN/out_level-N_0-example.txt
   ```
   No output means identical. If the level accepts multiple valid answers, use the visualizer instead (below).
5. Set `<targetSpecificLevel>N</targetSpecificLevel>`, run, and sanity-check the outputs (line count matches the
   number of cases, no empty files). The user uploads `output/levelN_*.txt`.

`targetSpecificLevel`: `N` = all files of level N, `Ne` = only its example, `e` = all examples, `-1` = everything.

## Verifying with a visualizer HTML

`SolutionVerifier` opens the HTML in headless Chrome, types one test case into the input textarea and its
solution into the output textarea, then reads the status element. Setup for a new contest:

1. Put the file into `input/` and set `<visualizerPath>`.
2. Find the element ids and adjust `<elementLocators>` (CSS selector, or XPath if it starts with `//`):
   ```bash
   grep -n -o -E '<(textarea|input|span|div|strong)[^>]*id="[^"]*"' input/visualizer.html
   ```
   Needed: input field, solution field, status element; optional: info/message element.
3. Read the validation code in the HTML's `<script>`. It tells you
   - what the page expects as input — normally **one test case**, not the whole `.in` file,
   - the status strings (`SUCCESS` / `FAILURE` in the sample),
   - when validation runs. It must run on the textarea's `input` event; a visualizer that needs a button click
     is not supported by `SolutionVerifier` as is.

   This script is also the most precise spec of what counts as a valid solution — read it when the statement
   is ambiguous.
4. Write `verify()` so that it slices the input file and the output file per test case and calls
   `verifier.checkValidity(run, inputCase, outputCase)`. Multi-line cases are joined with `\n`:
   ```java
   for (int run = 0; run < maxRuns; run++) {
       String inputCase = input.nextLine() + "\n" + input.nextLine();
       String outputCase = output.nextLine() + "\n" + output.nextLine();
       verifier.checkValidity(run, inputCase, outputCase);
   }
   ```
5. Set `<enabled>true</enabled>`, run, and check the result files:
   ```bash
   grep -c "exited with SUCCESS" output/*.ver
   grep "exited with FAILURE" output/*.ver
   ```
   Each case produces one entry `Validation run <i> exited with <status>! Additional Info: '<message>' | ...`.
   A case whose check threw (console shows `SEVERE: Visualizer failed`) writes **no** entry, so the SUCCESS
   count must equal the number of cases.

Verifier limits:

- Chrome must be installed; the first run downloads a matching chromedriver (needs internet, ~8 s startup).
- Text is typed with `sendKeys`, and the page revalidates on every keystroke: ~1.5 s per 1 000 characters,
  growing faster than linearly (10 000 chars ≈ 22 s, ~40 000 chars hit the 3-minute timeout). For big cases
  verify only the example and the small files, or set the value via `JavascriptExecutor` and dispatch a
  single `input` event.
- Set `<headless>false</headless>` to watch the visualizer while debugging.

## Gotchas

- After `reader.nextInt()` call `reader.nextLine()` before reading whole lines.
- `IOUtils.readList` does not trim: leading or double spaces produce empty tokens (`NumberFormatException`).
  Use `"\\s+"` as delimiter when the spacing is irregular. `readGrid(reader, "", w, h, ...)` splits a line into
  single characters.
- Only `IOException` is caught around `solve()`. Any other exception aborts the whole run: later files get no
  output, and with the verifier enabled the Chrome process stays open.
- `<cleanupOutput>true</cleanupOutput>` deletes everything inside `outputPath` on every run, recursively.
  Never point `outputPath` at a directory with anything worth keeping; outputs that should survive go to
  `<solutionPath>`.
- `<solutionPath>` is overwritten by **every** run, including runs of a half-finished solver — check
  `git diff` before committing. The sub-folder is taken relative to the first folder of `inputPath`.
- The level filter looks for `levelN` / `level-N` / `level_N` in the **file name** (not the folder) and does not
  match longer numbers (`1` skips `level10_*`). The example filter is a plain substring match on `example`.
- Output files are named by input basename only; two inputs with the same name in different folders overwrite
  each other.
- Debug output: `log.fine(...)` in `solve()` plus `<logLevel>FINE</logLevel>`; `FINER` also lists the files found.
- Sums and products in later levels overflow `int` quickly — default to `long`.
