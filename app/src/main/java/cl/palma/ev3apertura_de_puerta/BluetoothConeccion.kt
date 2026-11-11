package cl.palma.ev3apertura_de_puerta

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

// Objeto para mantener el socket Bluetooth activo entre las pantallas
object BluetoothConeccion {

    // UUID estándar para comunicación serial (SPP) con Arduino HC-05 o ESP32
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var escuchando = false
    private var listenerMensaje: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var isConnected: Boolean = false
        private set

    var connectedDeviceName: String? = null
        private set

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothDevice,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                cerrarStreamsYSocket()

                val adapter = BluetoothAdapter.getDefaultAdapter()
                try {
                    adapter?.cancelDiscovery()
                } catch (_: SecurityException) {
                }

                // Abrir conexión RFCOMM con el dispositivo seleccionado
                val uuid = device.uuids?.firstOrNull()?.uuid ?: SPP_UUID
                val nuevoSocket = device.createRfcommSocketToServiceRecord(uuid)
                nuevoSocket.connect()

                bluetoothSocket = nuevoSocket
                inputStream = nuevoSocket.inputStream
                outputStream = nuevoSocket.outputStream
                isConnected = true

                connectedDeviceName = try {
                    device.name ?: device.address
                } catch (_: SecurityException) {
                    device.address
                }

                mainHandler.post { onSuccess() }

            } catch (_: SecurityException) {
                cerrarStreamsYSocket()
                mainHandler.post { onError("Faltan permisos de Bluetooth en el teléfono.") }
            } catch (e: IOException) {
                e.printStackTrace()
                cerrarStreamsYSocket()
                mainHandler.post { onError("No se pudo conectar. Verifica que el dispositivo esté encendido.") }
            } catch (e: Exception) {
                e.printStackTrace()
                cerrarStreamsYSocket()
                mainHandler.post { onError("Error al conectar por Bluetooth.") }
            }
        }.start()
    }

    // Escucha en segundo plano los mensajes enviados por el ESP32 / Arduino
    fun startListening(onMessageReceived: (String) -> Unit) {
        listenerMensaje = onMessageReceived
        if (escuchando) return
        val stream = inputStream ?: return

        escuchando = true

        Thread {
            val buffer = ByteArray(1024)
            val mensajeAcumulado = StringBuilder()

            while (escuchando && isConnected) {
                try {
                    val bytesLeidos = stream.read(buffer)
                    if (bytesLeidos == -1) {
                        break
                    }

                    val texto = String(buffer, 0, bytesLeidos)
                    for (c in texto) {
                        if (c == '\n' || c == '\r' || c == '*') {
                            val tramaCompleta = mensajeAcumulado.toString().trim()
                            mensajeAcumulado.clear()

                            if (tramaCompleta.isNotEmpty()) {
                                mainHandler.post { listenerMensaje?.invoke(tramaCompleta) }
                            }
                        } else {
                            mensajeAcumulado.append(c)
                        }
                    }
                } catch (e: IOException) {
                    if (escuchando && isConnected) {
                        e.printStackTrace()
                        cerrarStreamsYSocket()
                        mainHandler.post { listenerMensaje?.invoke("DISCONNECTED") }
                    }
                    break
                } catch (_: Exception) {
                    break
                }
            }
            escuchando = false
        }.start()
    }

    // Envía el comando delimitado al microcontrolador
    fun sendCommand(command: String): Boolean {
        if (!isConnected || outputStream == null) {
            return false
        }

        return try {
            val trama = if (command.endsWith("\n")) command else "$command\n"
            outputStream?.write(trama.toByteArray())
            outputStream?.flush()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun disconnect() {
        cerrarStreamsYSocket()
    }

    private fun cerrarStreamsYSocket() {
        escuchando = false
        isConnected = false
        connectedDeviceName = null

        try {
            inputStream?.close()
        } catch (_: Exception) {
        }
        inputStream = null

        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        outputStream = null

        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {
        }
        bluetoothSocket = null
    }
}
