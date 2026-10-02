# Non-player hits cleanup regression

## Why this patch is safe

`TriggerPlaceholderHits.trackHits` stores victim UUID -> player UUID -> hit count.
Only a `Player` can populate the attacker key. `PaperEffectDataFixer.purgeOnRemove`
already excludes players, so this path only needs to remove the victim entry.
Previously it called the dual-role `clearEntity` and scanned every victim even
when removing an item entity. Player quit still uses the unchanged dual-role
cleanup. UUIDs, concurrent collections, hit increments and full-health reset
semantics are unchanged.

## Isolated server results (2026-09-28)

Tested with mizuki 26.2 `9daa83b`, Java 25.0.3, eco 2026.28, 512 MB initial / 2 GB
maximum heap. Three separate JVM runs per version each passed 20 assertions.
Baseline is the previously released `2026.27-memoryfix-3`, not the original plugin.

| Actual removal event | Before: inner remove calls | After |
| --- | ---: | ---: |
| One armor stand, 5,000 victim records | 5,000 | 0 |
| One item, 5,000 victim records | 5,000 | 0 |
| 100 armor stands at four locations, 1,000 records | 100,000 | 0 |

The probe covers hit accumulation, independent attackers, full-health reset,
placeholder lookup, both player roles, empty inner-map removal, victim removal,
recreation on a new hit, unrelated-record preservation and final cleanup.
Eight Java workers inserted/removed 8,000 distinct UUIDs with no residual entries.

Method-only microbenchmark: warm up 1,000 calls, five rounds of 1,000 calls per
JVM; compare medians of three JVM medians. Uses `nanoTime` and platform-thread
`ThreadMXBean` allocation counters. Measures cleanup for an absent non-player UUID.

| Records | Before ns/call | After ns/call | Before allocated B/call | After |
| --- | ---: | ---: | ---: | ---: |
| 1,000 | 30,535.5 | 112.3 | 32,000 | 0 |
| 10,000 | 241,833.8 | 44.1 | 320,000 | 0 |

Before JVM medians at 10,000 records: 241,833.8 / 230,606.8 / 248,402.9 ns.
After: 33.5 / 44.1 / 46.9 ns. Nanosecond timing is sensitive to JIT warmup;
these are not whole-server speedups, retained-heap measurements or production MSPT.

A separate existing effect regression probe also passed six scenarios: empty,
enable bonus health, stable refresh without duplicate enable, false condition,
reactivation and holder removal. Its 20,000 synthetic dispatchers retained zero
previous states. This probe's allocation counter was unavailable, not zero.

## Running this probe

**Never install this probe on production: it clears internal hits state, creates
entities, runs synthetic load and automatically shuts down the server.** Use a
fresh isolated server directory with only eco, libreforge and this probe. Bind to
127.0.0.1, disable RCON/query, use a fresh world and normal difficulty.

Compile with Java 25 against the tested libreforge JAR, eco JAR and the target
server's extracted libraries. Example PowerShell 7, from this directory:

```powershell
$server = 'C:\isolated-libreforge-test' # A prepared disposable server, not production.
$plugin = Join-Path $server 'plugins\libreforge.jar'
$eco = Join-Path $server 'plugins\eco.jar'
$libs = Get-ChildItem "$server\libraries" -Recurse -File -Filter '*.jar'
$cp = (@($plugin, $eco) + @($libs.FullName)) -join ';'
New-Item -ItemType Directory -Path probe-classes -Force | Out-Null
javac -encoding UTF-8 -cp $cp -d probe-classes HitsCleanupProbe.java
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
Copy-Item plugin.yml probe-classes/plugin.yml
jar --create --file "$server\plugins\HitsCleanupProbe.jar" -C probe-classes .
if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
Push-Location $server
try { java -Dhits.fixed=true -Xms512M -Xmx2G -jar server.jar --nogui }
finally { Pop-Location }
```

For the old version use a separate directory and `-Dhits.fixed=false`. Require
`[HITS] SUMMARY ... assertions=20 failures=0 retained=0`, normal exit and no probe
exceptions. The true/false property selects the expected cleanup method and scan
counts, not an alternate plugin implementation. Repeat in fresh JVMs.

Limits: Player objects are proxy fixtures; hit methods and player cleanup are
called directly, while entity removals are real server events. No connected
clients or complete production plugin stack were tested. Four location ownership
checks do not prove four independently ticking regions ran simultaneously. The
eight-worker test operates only on UUID data, not live cross-region entities.
Tests ran on Windows with a JDK socket-compatibility agent; production OS/JVM
conditions are not reproduced. The project Gradle test tasks currently report
NO-SOURCE; this is a manually run isolated integration probe, not CI coverage.

## Release artifact provenance

The published memoryfix-4 binary preserves the previous release's entries except
`PaperEffectDataFixer.class` and `TriggerPlaceholderHits.class`, replaced with the
classes from a successful clean build of this patch. This excludes unrelated
snapshot-dependency bytecode differences in three brewing/smelting classes. The
resulting minimal binary, not the full rebuild, was used in the seven server runs.
Plugin descriptor version remains `2026.27`.

SHA-256 of the tested memoryfix-4 JAR:
`6CCC955F56D995B3C4319600BC82489C8D2CD39F28F3E49BF2BB4AEEC9FB30E0`.
