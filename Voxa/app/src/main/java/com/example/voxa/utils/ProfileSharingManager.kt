package com.example.voxa.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 📡 ProfileSharingManager
 * Manages peer-to-peer sharing of Child Profiles and Audio Intents
 * using Google Nearby Connections API (BLE + WiFi Direct) with 4-Digit Security PIN Verification.
 */
class ProfileSharingManager(private val context: Context) {

    private val SERVICE_ID = "com.example.voxa.PROFILE_SHARING"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val gson = Gson()

    // State flows for UI binding
    private val _sharingState = MutableStateFlow<SharingState>(SharingState.Idle)
    val sharingState: StateFlow<SharingState> = _sharingState

    private var currentEndpointId: String? = null
    private var isSender: Boolean = false
    private var expectedPin: String? = null

    // Callbacks
    private var onProfileReceived: ((String) -> Unit)? = null
    private var onPinVerified: (() -> Unit)? = null

    fun setOnProfileReceivedListener(listener: (String) -> Unit) {
        onProfileReceived = listener
    }

    fun setExpectedPin(pin: String) {
        expectedPin = pin
    }

    fun setOnPinVerifiedListener(listener: () -> Unit) {
        onPinVerified = listener
    }

    fun reportError(message: String) {
        _sharingState.value = SharingState.Error(message)
    }

    // ==========================================
    // 📡 DISCOVERY (SENDER)
    // ==========================================
    fun startDiscovering() {
        isSender = true
        _sharingState.value = SharingState.Discovering
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("VoxaSharing", "Endpoint found: ${info.endpointName}")
                    _sharingState.value = SharingState.EndpointFound(endpointId, info.endpointName)
                    // Auto-connect for one-tap experience
                    requestConnection(endpointId, "Voxa Sender")
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d("VoxaSharing", "Endpoint lost: $endpointId")
                    if (_sharingState.value !is SharingState.Connected && 
                        _sharingState.value !is SharingState.WaitingForPin &&
                        _sharingState.value !is SharingState.Transferring) {
                        _sharingState.value = SharingState.Discovering
                    }
                }
            },
            options
        ).addOnFailureListener { e ->
            Log.e("VoxaSharing", "Discovery failed: ${e.message}")
            _sharingState.value = SharingState.Error("Discovery failed: ${e.message}")
        }
    }

    fun stopDiscovering() {
        connectionsClient.stopDiscovery()
    }

    // ==========================================
    // 📢 ADVERTISING (RECEIVER)
    // ==========================================
    fun startAdvertising(deviceName: String = "Voxa Receiver") {
        isSender = false
        _sharingState.value = SharingState.Advertising
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()

        connectionsClient.startAdvertising(
            deviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnFailureListener { e ->
            Log.e("VoxaSharing", "Advertising failed: ${e.message}")
            _sharingState.value = SharingState.Error("Advertising failed: ${e.message}")
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }

    // ==========================================
    // 🤝 CONNECTION LIFECYCLE
    // ==========================================
    private fun requestConnection(endpointId: String, name: String) {
        _sharingState.value = SharingState.Connecting(name)
        connectionsClient.requestConnection(name, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                Log.e("VoxaSharing", "Connection request failed: ${e.message}")
                _sharingState.value = SharingState.Error("Connection request failed: ${e.message}")
            }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("VoxaSharing", "Connection initiated with: ${connectionInfo.endpointName}")
            // Auto-accept connection on both sides
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                Log.d("VoxaSharing", "Connected to: $endpointId. isSender=$isSender")
                currentEndpointId = endpointId
                if (isSender) {
                    val pin = expectedPin ?: "0000"
                    _sharingState.value = SharingState.WaitingForPin(pin)
                } else {
                    _sharingState.value = SharingState.AwaitingPinEntry
                }
            } else {
                Log.e("VoxaSharing", "Connection failed with status: ${resolution.status.statusCode}")
                _sharingState.value = SharingState.Error("Connection failed")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("VoxaSharing", "Disconnected from: $endpointId")
            currentEndpointId = null
            if (_sharingState.value !is SharingState.TransferComplete) {
                _sharingState.value = SharingState.Idle
            }
        }
    }

    // ==========================================
    // 🔐 PIN VERIFICATION & SENDING PAYLOADS
    // ==========================================
    fun submitPin(enteredPin: String) {
        val endpoint = currentEndpointId ?: run {
            _sharingState.value = SharingState.Error("No active connection")
            return
        }
        _sharingState.value = SharingState.VerifyingPin
        val message = mapOf(
            "action" to "VERIFY_PIN",
            "pin" to enteredPin
        )
        val json = gson.toJson(message)
        val payload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpoint, payload)
    }

    fun sendProfileData(profileJson: String) {
        val licenseManager = com.example.voxa.data.license.LicenseManager.get(context)
        licenseManager.refresh()
        if (!licenseManager.license.value.canSendProfiles) {
            _sharingState.value = SharingState.Error(context.getString(com.example.voxa.R.string.license_handover_limit))
            return
        }
        val endpoint = currentEndpointId ?: run {
            _sharingState.value = SharingState.Error("No active connection to send data")
            return
        }
        _sharingState.value = SharingState.Transferring

        val message = mapOf(
            "action" to "PROFILE_DATA",
            "data" to profileJson
        )
        val json = gson.toJson(message)
        val payload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpoint, payload).addOnFailureListener { e ->
            Log.e("VoxaSharing", "Failed to send profile data: ${e.message}")
            _sharingState.value = SharingState.Error("Failed to send: ${e.message}")
        }
    }

    // ==========================================
    // 📥 RECEIVING PAYLOADS
    // ==========================================
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    val message = String(bytes, Charsets.UTF_8)
                    Log.d("VoxaSharing", "Payload received: ${message.take(100)}...")

                    try {
                        val obj = org.json.JSONObject(message)
                        val action = obj.optString("action")

                        when (action) {
                            "VERIFY_PIN" -> {
                                val receivedPin = obj.optString("pin")
                                Log.d("VoxaSharing", "Sender received VERIFY_PIN: $receivedPin (expected: $expectedPin)")
                                if (receivedPin == expectedPin) {
                                    val reply = org.json.JSONObject().apply { put("action", "PIN_ACCEPTED") }
                                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(reply.toString().toByteArray(Charsets.UTF_8)))
                                    _sharingState.value = SharingState.PinVerified
                                    onPinVerified?.invoke()
                                } else {
                                    val reply = org.json.JSONObject().apply { put("action", "PIN_REJECTED") }
                                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(reply.toString().toByteArray(Charsets.UTF_8)))
                                }
                            }
                            "PIN_ACCEPTED" -> {
                                Log.d("VoxaSharing", "PIN was accepted by sender!")
                                _sharingState.value = SharingState.PinVerified
                            }
                            "PIN_REJECTED" -> {
                                Log.w("VoxaSharing", "PIN was rejected by sender!")
                                _sharingState.value = SharingState.PinFailed
                            }
                            "PROFILE_DATA" -> {
                                val profileData = obj.optString("data")
                                Log.d("VoxaSharing", "Received PROFILE_DATA, triggering onProfileReceived")
                                _sharingState.value = SharingState.TransferComplete
                                onProfileReceived?.invoke(profileData)
                            }
                            else -> {
                                onProfileReceived?.invoke(message)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VoxaSharing", "Error parsing payload JSON: ${e.message}")
                        onProfileReceived?.invoke(message)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d("VoxaSharing", "Payload transfer update: SUCCESS")
                if (_sharingState.value is SharingState.Transferring) {
                    _sharingState.value = SharingState.TransferComplete
                }
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                _sharingState.value = SharingState.Error("Transfer failed")
            }
        }
    }

    fun disconnect() {
        currentEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
        stopDiscovering()
        stopAdvertising()
        currentEndpointId = null
        expectedPin = null
        _sharingState.value = SharingState.Idle
    }

    // ==========================================
    // 🚦 SHARING STATES
    // ==========================================
    sealed class SharingState {
        object Idle : SharingState()
        object Discovering : SharingState()
        object Advertising : SharingState()
        data class EndpointFound(val endpointId: String, val name: String) : SharingState()
        data class Connecting(val name: String) : SharingState()
        object Connected : SharingState()
        data class WaitingForPin(val pin: String) : SharingState()
        object AwaitingPinEntry : SharingState()
        object VerifyingPin : SharingState()
        object PinFailed : SharingState()
        object PinVerified : SharingState()
        object Transferring : SharingState()
        object TransferComplete : SharingState()
        data class Error(val message: String) : SharingState()
    }
}
