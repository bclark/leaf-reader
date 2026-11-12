package net.digitalbc.leafreader.data.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals

class JellyfinSyncPolicyTest {
    @Test fun missingDataDoesNotResetProgress() {
        assertEquals(JellyfinSyncAction.UNAVAILABLE, JellyfinSyncPolicy.decide(10000, false, null, false, 0, false, true, false))
        assertEquals(JellyfinSyncAction.UNAVAILABLE, JellyfinSyncPolicy.decide(10000, false, 0, null, 0, false, true, false))
    }
    @Test fun equalStateAcknowledgesWithoutWritingAgain() {
        assertEquals(JellyfinSyncAction.EQUAL, JellyfinSyncPolicy.decide(0, false, 0, false, null, null, true, true))
    }
    @Test fun unchangedRemoteAllowsLocalRereadAndCompletion() {
        assertEquals(JellyfinSyncAction.UPLOAD, JellyfinSyncPolicy.decide(0, false, 90000, true, 90000, true, true, false))
        assertEquals(JellyfinSyncAction.UPLOAD, JellyfinSyncPolicy.decide(0, true, 0, false, 0, false, true, false))
    }
    @Test fun concurrentOrUntrackedChangesNeedChoice() {
        assertEquals(JellyfinSyncAction.CONFLICT, JellyfinSyncPolicy.decide(20000, false, 40000, false, 0, false, true, false))
        assertEquals(JellyfinSyncAction.CONFLICT, JellyfinSyncPolicy.decide(20000, false, 0, false, null, null, false, true))
    }
    @Test fun remoteResetReachesCleanDownload() {
        assertEquals(JellyfinSyncAction.DOWNLOAD, JellyfinSyncPolicy.decide(90000, true, 0, false, 90000, true, false, false))
    }
    @Test fun userDataRouteKeepsAccountAndProxyPrefix() {
        assertEquals("https://example.test/proxy/UserItems/book/UserData?userId=user",
            JellyfinEndpoints.itemUserData("https://example.test/proxy/", "user", "book"))
    }
}
