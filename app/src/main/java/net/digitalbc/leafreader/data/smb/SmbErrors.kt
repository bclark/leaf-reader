package net.digitalbc.leafreader.data.smb

import com.hierynomus.mssmb2.SMBApiException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object SmbErrors {
    fun kind(error: Throwable): SmbFailureKind {
        if (error is CancellationException) throw error
        return when (error) {
            is IllegalArgumentException -> SmbFailureKind.INVALID_CONFIGURATION
            is SMBApiException -> when (error.statusCode) {
                0xC000006DL, 0xC000006AL, 0xC0000064L -> SmbFailureKind.AUTHENTICATION
                0xC0000022L -> SmbFailureKind.ACCESS_DENIED
                0xC00000BBL -> SmbFailureKind.UNSUPPORTED
                else -> SmbFailureKind.IO
            }
            is SocketTimeoutException -> SmbFailureKind.TIMEOUT
            is ConnectException, is UnknownHostException -> SmbFailureKind.UNAVAILABLE
            else -> if (error.cause != null && error.cause !== error) kind(error.cause!!)
                    else if (error is IOException) SmbFailureKind.IO else SmbFailureKind.UNKNOWN
        }
    }

    /** Never surface native exception text containing server paths or credentials. */
    fun message(error: Throwable): String = when (kind(error)) {
        SmbFailureKind.INVALID_CONFIGURATION -> "Check the server address, port, share and folder."
        SmbFailureKind.AUTHENTICATION -> "The server rejected the account. Check your username and password."
        SmbFailureKind.ACCESS_DENIED -> "The account cannot access this folder or file."
        SmbFailureKind.TIMEOUT -> "The drive did not respond in time. Try again."
        SmbFailureKind.UNAVAILABLE -> "Cannot reach the drive. Check the server and network connection."
        SmbFailureKind.UNSUPPORTED -> "The server does not support this SMB operation."
        else -> "The drive operation failed. Check the connection and try again."
    }
}
