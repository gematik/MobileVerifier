/*
 * Copyright 2022-2024, gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

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

