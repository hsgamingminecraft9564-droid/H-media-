package com.hmedia.voidengine.service

import com.hmedia.voidengine.VoidEngine

/** Process-wide handle so the playback service and the UI control the very same engine. */
object VoidEngineHolder {
    @Volatile
    var engine: VoidEngine? = null
}
