# VOID ENGINE – Fix Report (static edit pass, 9 Nov 2026)

NOTHING HERE HAS BEEN COMPILED OR RUN. The sandbox had no Gradle/Kotlin/network.
Only tools/static_check.py was run (37 files, OK). Build + tests must be run on your machine.

## Done in this pass
| Checklist | Change | Files | Test added |
|---|---|---|---|
| Stage 5 XXE | External entities/DTD loading disabled (best-effort features), `<!ENTITY` rejected in prologue (plain `<!DOCTYPE tv SYSTEM ...>` still allowed because real XMLTV uses it), empty entity resolver kept | XmltvParser.kt | xmltvRejectsEntityDeclarationsAndOversizedInput |
| Stage 5 limits | Max bytes (256 MB default), max programmes (1M), per-element text cap | XmltvParser.kt | same test |
| Stage 5 Vault crash-safety | Temp files + fsync + rename; leftovers `.tmp` removed on open | PrivateVault.kt | (needs instrumented/JVM test) |
| Stage 3 schemes | New SourceType: RTMP, RTP_UDP, MMS, FTP, NETWORK_SHARE. rtmp/rtp/udp/mms/mmsh/ftp/sftp/smb no longer fall into UNKNOWN | PlaybackSource.kt, SourceClassifier.kt | classifiesUrls |
| Stage 3 remote flag | `isRemote` true for every remote type, false for LOCAL_FILE/UNKNOWN | PlaybackSource.kt | classifiesUrls |
| Stage 3 selection | UNKNOWN no longer matches every backend; untyped sources are classified from URI; unknown/missing scheme -> no candidates | BackendSelector.kt, M3uParser.kt | untypedSourceIsClassifiedFromItsUri, unrecognisedSchemeIsRejected |
| Stage 3 validation | `validationError()`: control chars, embedded credentials, missing host; engine reports InvalidSource | SourceClassifier.kt, VoidEngineImpl.kt | validatesSourceUrls |
| Stage 3 errors | New InvalidSource, Authentication (401/403/407), HttpStatus(code). 404/410 -> SourceNotFound. None of these trigger backend fallback | PlaybackError.kt, Media3BackendAdapter.kt | – |

## NOT done (needs your decision or a real build)
- Stage 1: package rename needs your real package name (tools/rename_package.py exists); root Gradle project/wrapper is not in the zip.
- Stage 5 Vault truncation: removing whole chunks from the END of a file is still undetected. Fix needs a new file format (authenticated trailer with total length). Say if you have existing vault files, then I add VLT1 read-compat.
- Stage 2: reviewed VoidEngineImpl – generation counter, single dispatcher, reconnect cancel on pause/stop/release look right. Race tests not added.
- Stage 4: service process-recreation, POST_NOTIFICATIONS, PiP host Activity.
- Stage 6-8 and all device testing.
- VLC adapter still maps every remote failure to Network (no HTTP status available from libVLC).
- Media3 does not list rtsps; those sources fall to VLC.


## Second pass (after VOID_ENGINE_Fixed_Second_Audit.txt)
- FIXED P0 bug: PrivateVault.addFile closed the stream before fd.sync(); now flush + sync inside a single FileOutputStream.use. Regression tests: PrivateVaultTest (round trip, failed write cleanup, stale tmp cleanup).
- FIXED: INPUT_SCHEMES now matches classify() (adds rtmps, rtmpt, sftp, smb); fromUserInput also applies validationError(). udp://@host (multicast) is not treated as credentials. Test: userInputSchemesAgreeWithClassifier.
- FIXED: XMLTV prologue scan now fails closed when the root element is not inside the scanned 64 KiB. Test: xmltvRejectsLongPrologue.
- Backend support: rtmps/rtmpt/sftp/smb are classified and accepted as input, but VLC advertises only rtmp/sftp/smb (not rtmps/rtmpt) and Media3 none of them, so rtmps/rtmpt return "No backend can play this source" until verified on a device.
- Still open (unchanged): everything in P0 build/package, service recreation, connector contract, POST_NOTIFICATIONS, PiP, Vault truncation format, native backend, device tests. Still not compiled.
