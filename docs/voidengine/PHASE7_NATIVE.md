# Phase 7: replacing components one at a time

Rule: replace one component, run the same acceptance tests against old and new backend, only then widen
`NativeBackendAdapter.provider()` capabilities and put it in the provider list.

Order I recommend:
1. Source and format detection: done as `nativecore/FormatDetector` (MediaExtractor + MediaCodecList). Use it to set `PlaybackSource.preferredBackend`.
2. Demuxing / stream handling: FFmpeg `libavformat` (check licensing: dynamic LGPL builds are fine; GPL components are not for a closed app).
3. Audio decode + output: MediaCodec decoder + Oboe/AAudio output, audio clock as the master clock.
4. Video decode + render: MediaCodec to a `Surface`, frame dropping/delay against the audio clock.
5. Subtitle rendering and sync.

Acceptance for each step (same tests for every backend): plays the format matrix, seek is accurate, pause never restarts,
A/V drift under 40 ms after 10 minutes, memory returns to baseline after release, no crash on rotation/background.
