# Memory Fix 8

This revision follows Memory Fix 7 (`05618eb35d3d4fe1afa3a68c50bdb1b33dbfb42a`). Memory Fix 9 and 10 remain experimental and are excluded from this revision.

## Changes

- Within one item occurrence in one finder scan, compute the item component hash once for multiple accepted holders. Do not cache item hashes across scans or events.
- Calculate `ItemProvidedHolder` hashes from constructor arguments instead of overridable getters during superclass construction. This fixes equal base/slotted holders producing different hashes.
- Preserve public constructor entry points, slot validation order, current item references, equipment refreshes, durability event dispatch and effect configuration.

The regression tests in `core/common/src/test/kotlin/com/willfp/libreforge/ItemProvidedHolderHashTest.kt` cover interchangeable map keys, distinct holder hashes, refresh freshness, validation-time item mutations and per-wrapper hash snapshots.

## Existing runtime evidence

Controlled Shiroha `26.2-DEV-f10fd16` tests compared the actual Memory Fix 7 and 8 JARs. Detached CraftItemStack fixtures had 24 lore lines and 16 string PDC entries. The number below is the number of holders returned by a finder, not the number of effect entries in one holder.

| Fixture | Old bytes/scan | New bytes/scan | Reduction |
| --- | ---: | ---: | ---: |
| No holders | 96 | 96 | 0% |
| Rich item, one holder | 8696 | 8664 | 0.37% |
| Rich item, four holders | 34280 | 8864 | 74.14% |
| Rich item, twenty holders | 170912 | 9624 | 94.37% |

In a twenty-effect durability-event replay, observed median event wall time changed from 139.77 to 80.93 microseconds. This was Bukkit event replay, not native mining throughput, full-server CPU or a production-wide reduction. Native durability behavior was checked separately. Timing varied between JVM runs; lightweight fixtures were not consistently faster.

Historical validation included thirty unit tests, 1598 explicit runtime checks and independent-region effect lifecycle checks. It did not cover every production configuration or unknown third-party extension. Publication-time build results are recorded in the GitHub release notes separately.

## Released artifact

The original tested artifact is retained without rebuilding or renaming its contents:

`libreforge-standalone-2026.27-memoryfix-8.jar`

SHA-256: `616A08996AE4029638455189C723EEEC2324C02AB7EA1AF63F19A06DF0F2BE0D`

No configuration or persistent-data migration is required. Replace the plugin during a normal shutdown, keep the old JAR outside the plugins directory for rollback, and avoid loading two libreforge copies.
