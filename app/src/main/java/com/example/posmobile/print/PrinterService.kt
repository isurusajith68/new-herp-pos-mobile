package com.example.posmobile.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.posmobile.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class BtPrinter(val name: String, val mac: String)

class PrinterException(message: String) : Exception(message)

/**
 * Sends ESC/POS payloads to a thermal printer over one of two transports,
 * chosen by [Settings.printerType]:
 *  - Bluetooth: a paired ("bonded") printer over the Serial Port Profile. The
 *    user pairs it once in Android settings; we connect on demand — no scanning,
 *    so no location permission.
 *  - WiFi: a raw TCP socket to the printer's IP on port 9100 (JetDirect/RAW).
 */
class PrinterService(
    private val context: Context,
    private val settings: Settings,
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun adapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun isBluetoothOn(): Boolean = adapter()?.isEnabled == true

    /** True when we may talk to bonded devices (BLUETOOTH_CONNECT on API 31+). */
    fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun bondedPrinters(): List<BtPrinter> {
        if (!hasConnectPermission()) throw PrinterException("Bluetooth permission not granted")
        val a = adapter() ?: throw PrinterException("No Bluetooth on this device")
        if (!a.isEnabled) throw PrinterException("Bluetooth is turned off")
        return a.bondedDevices.orEmpty().map { BtPrinter(it.name ?: it.address, it.address) }
    }

    /**
     * Sends a prepared ESC/POS payload to the active printer. The bytes are
     * transport-agnostic (built by [EscPos]); we dispatch to Bluetooth or WiFi
     * based on the user's selected connection type.
     */
    suspend fun print(bytes: ByteArray) {
        when (settings.printerType) {
            Settings.TYPE_WIFI -> printWifi(bytes)
            else -> printBluetooth(bytes)
        }
    }

    /** Raw ESC/POS over a TCP socket (port 9100 / JetDirect) to a networked printer. */
    private suspend fun printWifi(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val host = settings.printerHost?.takeIf { it.isNotBlank() }
            ?: throw PrinterException("No printer IP set — enter it in Printer settings")
        val port = settings.printerPort
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), WIFI_CONNECT_TIMEOUT_MS)
                socket.getOutputStream().apply {
                    write(bytes)
                    flush()
                }
                // Give the printer time to flush its buffer before the socket closes.
                Thread.sleep(400)
            }
        } catch (e: Exception) {
            throw PrinterException(
                "Could not reach printer at $host:$port — check it's powered on, " +
                    "on the same Wi-Fi network and the IP is correct.",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun printBluetooth(bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (!hasConnectPermission()) throw PrinterException("Bluetooth permission not granted")
        val mac = settings.printerMac
            ?: throw PrinterException("No printer selected — pick one in Printer settings")
        val a = adapter() ?: throw PrinterException("No Bluetooth on this device")
        if (!a.isEnabled) throw PrinterException("Bluetooth is turned off")

        val device = a.getRemoteDevice(mac)
        // Best-effort: discovery slows/kills RFCOMM connects, but cancelling it
        // needs BLUETOOTH_SCAN (which we don't request — we never scan), so a
        // SecurityException here is expected and must not abort the print.
        runCatching { a.cancelDiscovery() }
        var socket: BluetoothSocket? = null
        try {
            socket = connect(device)
            socket.outputStream.apply {
                write(bytes)
                flush()
            }
            // Give the printer time to flush its buffer before the socket closes.
            Thread.sleep(400)
        } catch (e: SecurityException) {
            throw PrinterException("Bluetooth permission denied")
        } catch (e: PrinterException) {
            throw e
        } catch (e: Exception) {
            throw PrinterException("Could not print: ${e.message ?: "connection failed"}")
        } finally {
            runCatching { socket?.close() }
        }
    }

    /**
     * Opens an RFCOMM/SPP connection, trying the strategies real-world printers
     * and Windows SPP servers need in order. The plain secure socket fails on a
     * lot of hardware with an instant "channel: -1, INIT" close; the insecure
     * socket and the reflection channel-1 method recover those cases.
     */
    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice): BluetoothSocket {
        val attempts: List<() -> BluetoothSocket> = listOf(
            { device.createRfcommSocketToServiceRecord(sppUuid) },
            { device.createInsecureRfcommSocketToServiceRecord(sppUuid) },
            {
                device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
        )
        var lastError: Exception? = null
        // Some printers (and Windows' incoming SPP port) refuse a reconnect that
        // arrives right after the previous socket closed, surfacing as a connect
        // timeout. Retry the whole strategy set a few times with a short backoff.
        repeat(CONNECT_RETRIES) { round ->
            for (open in attempts) {
                val socket = runCatching { open() }
                    .getOrElse { lastError = it as? Exception; null } ?: continue
                try {
                    socket.connect()
                    return socket
                } catch (e: SecurityException) {
                    runCatching { socket.close() }
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    runCatching { socket.close() }
                }
            }
            if (round < CONNECT_RETRIES - 1) Thread.sleep(CONNECT_RETRY_DELAY_MS)
        }
        throw PrinterException(
            "Could not connect to printer: ${lastError?.message ?: "connection failed"}. " +
                "Check it's powered on, in range and paired.",
        )
    }

    private companion object {
        const val CONNECT_RETRIES = 3
        const val CONNECT_RETRY_DELAY_MS = 400L
        const val WIFI_CONNECT_TIMEOUT_MS = 5000
    }
}
