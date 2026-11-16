package net.digitalbc.leafreader.data.smb

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.connection.SessionTable
import com.hierynomus.smbj.event.ConnectionClosed
import com.hierynomus.smbj.event.SMBEventBus
import com.hierynomus.smbj.event.SessionLoggedOff
import com.hierynomus.smbj.event.TreeDisconnected
import com.hierynomus.smbj.session.Session
import org.junit.Assert.*
import org.junit.Test

/** Exercises MBassador discovery and dispatch, without opening a socket or using credentials. */
class SmbEventCallbacksTest {
    private val bus = SMBEventBus()
    private val config = SmbConfig.createDefaultConfig()
    private val client = SMBClient(config, bus)
    private val connection = Connection(config, client, bus, client.serverList)
    private val session = Session(connection, config, null, bus, null, null, null).apply { sessionId = 7 }

    @Test fun connectionCloseRemovesOnlyTheClosedEndpoint() {
        val table = field<MutableMap<String, Connection>>(client, "connectionTable")
        table["fixture:445"] = connection
        table["other:445"] = connection
        bus.publish(ConnectionClosed("fixture", 445))
        assertFalse(table.containsKey("fixture:445"))
        assertTrue(table.containsKey("other:445"))
    }

    @Test fun sessionLogoffRemovesTheRegisteredSession() {
        val table = field<SessionTable>(connection, "sessionTable")
        val sessions = field<MutableMap<Long, Session>>(table, "lookup")
        sessions[7] = session
        sessions[8] = session
        bus.publish(SessionLoggedOff(7))
        assertNull(table.find(7))
        assertSame(session, table.find(8))
    }

    @Test fun treeDisconnectIgnoresOtherSessionsAndRemovesTheMatchingTree() {
        val table = field<Any>(session, "treeConnectTable")
        // HashMap accepts null; cleanup must still remove the ID without opening a share.
        val trees = field<MutableMap<Long, Any?>>(table, "lookupById")
        trees[42] = null
        bus.publish(TreeDisconnected(8, 42))
        assertTrue(trees.containsKey(42))
        bus.publish(TreeDisconnected(7, 42))
        assertFalse(trees.containsKey(42))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T
}
