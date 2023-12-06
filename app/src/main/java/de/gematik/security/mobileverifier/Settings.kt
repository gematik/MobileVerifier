package de.gematik.security.mobileverifier

import android.util.Log
import de.gematik.security.credentialExchangeLib.connection.DidCommV2OverHttp.createPeerDID
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI

object Settings {

    private val tag = Settings::class.java.name

    val trustedIssuer =
        URI.create("did:key:zUC78bhyjquwftxL92uP5xdUA7D7rtNQ43LZjvymncP2KTXtQud1g9JH4LYqoXZ6fyiuDJ2PdkNU9j6cuK1dsGjFB2tEMvTnnHP7iZJomBmmY1xsxBqbPsCMtH6YmjP4ocfGLwv")

    val wsServerPort = 9090

    val localEndpoint = URI("ws", null, "0.0.0.0", wsServerPort, "/ws", null, null)

    val ownInternetAddress = NetworkInterface.getNetworkInterfaces()
        .toList().first { it.name.lowercase().startsWith("wlan") }
        .inetAddresses.toList().first { it is Inet4Address }
        .hostAddress.also {
            Log.i(tag,"hostAdress: $it")
        }

    val ownWsUri = URI(
        "ws",
        null,
        ownInternetAddress,
        wsServerPort,
        "/ws",
        null,
        null
    ).also {
        Log.i(tag,"ownWsUri: $it")
    }

    val ownServiceEndpoint = URI(
        "http",
        null,
        ownInternetAddress,
        wsServerPort + 5,
        "/didcomm",
        null,
        null
    ).also {
        Log.i(tag,"ownServicepoint: $it")
    }

    val ownDid = URI.create(
        createPeerDID(
            serviceEndpoint = ownServiceEndpoint.toString()
        )
    ).also {
        Log.i(tag,"ownDid: $it")
    }

}

