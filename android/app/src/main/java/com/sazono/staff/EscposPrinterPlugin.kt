package com.sazono.staff

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.Executors

@CapacitorPlugin(
    name = "EscposPrinter",
    permissions = [Permission(strings = [Manifest.permission.BLUETOOTH_CONNECT], alias = "bluetooth")]
)
class EscposPrinterPlugin : Plugin() {

    private val executor = Executors.newCachedThreadPool()

    private fun bluetoothGranted() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @PluginMethod
    fun printTcp(call: PluginCall) {
        val host = call.getString("host")
        val port = call.getInt("port") ?: 9100
        val encoded = call.getString("bytesBase64")
        val timeoutMs = (call.getInt("timeoutMs") ?: 5000).coerceIn(500, 60000)
        if (host.isNullOrBlank()) {
            call.reject("host requerido", "INVALID_ARGUMENT")
            return
        }
        if (port !in 1..65535) {
            call.reject("puerto invalido", "INVALID_ARGUMENT")
            return
        }
        if (encoded.isNullOrEmpty()) {
            call.reject("payload vacio", "INVALID_ARGUMENT")
            return
        }
        val data = decode(encoded)
        if (data == null) {
            call.reject("base64 invalido", "INVALID_ARGUMENT")
            return
        }
        if (data.isEmpty()) {
            call.reject("payload vacio", "INVALID_ARGUMENT")
            return
        }
        if (data.size > MAX_PAYLOAD_BYTES) {
            call.reject("payload demasiado grande (${data.size} bytes)", "PAYLOAD_TOO_LARGE")
            return
        }
        executor.execute {
            val startedAt = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = timeoutMs
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    socket.getOutputStream().apply {
                        write(data)
                        flush()
                    }
                    socket.shutdownOutput()
                }
                call.resolve(JSObject().put("bytes", data.size))
            } catch (e: SocketTimeoutException) {
                call.reject("Tiempo de espera agotado", "TIMEOUT", e)
            } catch (e: ConnectException) {
                val msg = e.message.orEmpty()
                val refused = msg.contains("refused", true) || msg.contains("ECONNREFUSED", true)
                val unreachable = msg.contains("unreachable", true) || msg.contains("No route", true) ||
                    msg.contains("EHOSTUNREACH", true) || msg.contains("ENETUNREACH", true)
                val code = if (unreachable) "UNREACHABLE" else if (refused) "REFUSED" else "UNREACHABLE"
                val elapsed = System.currentTimeMillis() - startedAt
                val detail = when (code) {
                    "REFUSED" -> "Conexion rechazada por la impresora tras ${elapsed}ms"
                    else -> "Impresora inalcanzable tras ${elapsed}ms"
                }
                call.reject(detail, code, e)
            } catch (e: NoRouteToHostException) {
                call.reject(e.message ?: "Sin ruta al host", "UNREACHABLE", e)
            } catch (e: UnknownHostException) {
                call.reject(e.message ?: "Host desconocido", "UNREACHABLE", e)
            } catch (e: IOException) {
                call.reject(e.message ?: "Error de red", "IO_ERROR", e)
            } catch (e: Exception) {
                call.reject(e.message ?: "Error de impresion", "IO_ERROR", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @PluginMethod
    fun listBluetoothPrinters(call: PluginCall) {
        withBluetooth(call) {
            val adapter = adapter()
            if (adapter == null) {
                call.reject("Bluetooth no disponible", "BT_UNAVAILABLE")
                return@withBluetooth
            }
            val devices = JSArray()
            for (device in adapter.bondedDevices.orEmpty()) {
                devices.put(JSObject().put("name", device.name ?: device.address).put("address", device.address))
            }
            call.resolve(JSObject().put("devices", devices))
        }
    }

    @SuppressLint("MissingPermission")
    @PluginMethod
    fun printBluetooth(call: PluginCall) {
        val address = call.getString("address")
        val encoded = call.getString("bytesBase64")
        if (address.isNullOrBlank()) {
            call.reject("address requerido", "INVALID_ARGUMENT")
            return
        }
        if (encoded.isNullOrEmpty()) {
            call.reject("payload vacio", "INVALID_ARGUMENT")
            return
        }
        val data = decode(encoded)
        if (data == null) {
            call.reject("base64 invalido", "INVALID_ARGUMENT")
            return
        }
        if (data.isEmpty()) {
            call.reject("payload vacio", "INVALID_ARGUMENT")
            return
        }
        if (data.size > MAX_PAYLOAD_BYTES) {
            call.reject("payload demasiado grande (${data.size} bytes)", "PAYLOAD_TOO_LARGE")
            return
        }
        withBluetooth(call) {
            val adapter = adapter()
            if (adapter == null || !adapter.isEnabled) {
                call.reject("Bluetooth apagado o no disponible", "BT_UNAVAILABLE")
                return@withBluetooth
            }
            executor.execute {
                try {
                    val device = adapter.bondedDevices.orEmpty().firstOrNull { it.address.equals(address, true) }
                    if (device == null) {
                        call.reject("Impresora no emparejada", "BT_DEVICE_NOT_FOUND")
                        return@execute
                    }
                    device.createRfcommSocketToServiceRecord(SPP_UUID).use { socket ->
                        socket.connect()
                        socket.outputStream.apply {
                            write(data)
                            flush()
                        }
                        Thread.sleep(FLUSH_GRACE_MS)
                    }
                    call.resolve(JSObject().put("bytes", data.size))
                } catch (e: SecurityException) {
                    call.reject("Permiso Bluetooth denegado", "BT_PERMISSION_DENIED", e)
                } catch (e: IOException) {
                    call.reject(e.message ?: "No se pudo conectar", "BT_CONNECT_FAILED", e)
                } catch (e: Exception) {
                    call.reject(e.message ?: "Error de impresion", "IO_ERROR", e)
                }
            }
        }
    }

    @PluginMethod
    fun keepAwake(call: PluginCall) {
        val enabled = call.getBoolean("enabled") ?: true
        activity.runOnUiThread {
            if (enabled) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            call.resolve()
        }
    }

    private fun adapter() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun withBluetooth(call: PluginCall, block: () -> Unit) {
        if (bluetoothGranted()) {
            block()
            return
        }
        requestPermissionForAlias("bluetooth", call, "bluetoothPermissionResult")
    }

    @PermissionCallback
    private fun bluetoothPermissionResult(call: PluginCall) {
        if (!bluetoothGranted()) {
            call.reject("Permiso Bluetooth denegado", "BT_PERMISSION_DENIED")
            return
        }
        when (call.methodName) {
            "listBluetoothPrinters" -> listBluetoothPrinters(call)
            "printBluetooth" -> printBluetooth(call)
            else -> call.resolve()
        }
    }

    private fun decode(value: String?): ByteArray? =
        try {
            if (value.isNullOrEmpty()) null else Base64.decode(value, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val FLUSH_GRACE_MS = 400L
        private const val MAX_PAYLOAD_BYTES = 512 * 1024
    }
}
