# Sources and method

- Artwork: existing TangledLink four-theme package, generated on 2026-11-16; copied native PNG masters and unchanged static WebP fallbacks. No new logo, no regenerated scene identity.
- Motion: authored deterministic camera/lighting/particle paths; renderer source included. Cairo composites the scene at each phase, FFmpeg encodes H.264. No external image-to-video service or video generation model is used.
- Portal: native Compose Canvas geometry, no raster frames. The preview renderer illustrates the same idea; it is not a capture of a running Android app.
- [Compose graphics](https://developer.android.com/develop/ui/compose/graphics/draw/overview) — Canvas, DrawScope and paths.
- [Media3 getting started](https://developer.android.com/media/media3/exoplayer/hello-world) — player setup and release; integrating agent selects compatible library versions from the actual project.
- [Android supported media formats](https://developer.android.com/media/platform/supported-formats) — codec/container reference. Real decoder behaviour must still be checked on target devices.
