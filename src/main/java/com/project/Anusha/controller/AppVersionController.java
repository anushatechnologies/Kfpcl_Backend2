package com.project.Anusha.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * GET /api/app/version
 *
 * Returns the latest published app version so the customer app can compare
 * against its own bundled version and prompt the user to update if needed.
 *
 * Update latestVersion / minVersion / releaseNotes here (or wire to DB later)
 * whenever a new build is pushed to the Play Store / App Store.
 */
@RestController
@RequestMapping("/api/app")
@CrossOrigin(origins = "*")
public class AppVersionController {

    // ── Update these values with every release ─────────────────────────────
    private static final String LATEST_VERSION  = "1.0.64";
    private static final String MIN_VERSION     = "1.0.0";
    private static final boolean FORCE_UPDATE   = false;
    private static final String RELEASE_NOTES   =
            "Bug fixes and performance improvements. Faster checkout, better search experience.";
    private static final String PLAY_STORE_URL  =
            "https://play.google.com/store/apps/details?id=com.anusha.deliveryapp";
    private static final String APP_STORE_URL   =
            "https://apps.apple.com/app/anusha-bazaar/id000000000";
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getLatestVersion() {
        return ResponseEntity.ok(Map.of(
                "latestVersion",  LATEST_VERSION,
                "minVersion",     MIN_VERSION,
                "forceUpdate",    FORCE_UPDATE,
                "releaseNotes",   RELEASE_NOTES,
                "android",        Map.of("storeUrl", PLAY_STORE_URL),
                "ios",            Map.of("storeUrl", APP_STORE_URL)
        ));
    }
}
