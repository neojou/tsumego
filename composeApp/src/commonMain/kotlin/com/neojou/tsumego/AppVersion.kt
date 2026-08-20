package com.neojou.tsumego

/**
 * Application product version — **single source of truth** for UI / About.
 *
 * When bumping a release, update these constants first, then follow
 * [docs/VERSIONING.md](../../../../../docs/VERSIONING.md) (repo root).
 *
 * Scheme (product-facing, not forced SemVer):
 * - [NAME]: `MAJOR.MINOR` (e.g. `"0.1"`) or `MAJOR.MINOR.PATCH` when needed
 * - [DISPLAY]: shown in About, typically `"v" + NAME`
 */
object AppVersion {
    /** Product name (Traditional Chinese). */
    const val APP_NAME: String = "詰碁"

    /** English / package short name. */
    const val APP_NAME_EN: String = "tsumego"

    /**
     * Marketing / product version string (no leading `v`).
     * Current release: **0.11**
     */
    const val NAME: String = "0.1"

    /** User-visible label, e.g. `v0.1`. */
    const val DISPLAY: String = "v$NAME"

    /** One-line blurb for About. */
    const val SUMMARY: String = "KMP 範本"
}
