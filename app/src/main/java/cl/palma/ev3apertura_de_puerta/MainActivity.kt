package cl.palma.ev3apertura_de_puerta

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cl.palma.ev3apertura_de_puerta.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        // Estados del sistema mostrados en TextView1
        const val ESTADO_DESACTIVADO = "SISTEMA DESACTIVADO"
        const val ESTADO_ACTIVADO = "SISTEMA ACTIVADO"
        const val ESTADO_MOVIMIENTO = "MOVIMIENTO DETECTADO"
        const val ESTADO_PUERTA_ABIERTA = "PUERTA ABIERTA"
        const val ESTADO_PUERTA_CERRADA = "PUERTA CERRADA"

        // Comandos enviados al microcontrolador
        const val CMD_ACTIVAR = "#CMD:ACT*"
        const val CMD_DESACTIVAR = "#CMD:DEACT*"
        const val CMD_ABRIR_PUERTA = "#CMD:OPEN*"
        const val CMD_CERRAR_PUERTA = "#CMD:CLOSE*"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        actualizarEstado(ESTADO_DESACTIVADO)
        configurarBotones()
    }

    private fun configurarBotones() {
        // Btn1: Ir a pantalla de instrucciones y conexión Bluetooth
        binding.Btn1.setOnClickListener {
            val intent = Intent(this, InstructionsActivity::class.java)
            startActivity(intent)
        }

        // Btn2: Activar sistema y habilitar sensor PIR
        binding.Btn2.setOnClickListener {
            enviarAccion(CMD_ACTIVAR, ESTADO_ACTIVADO, "Sistema activado")
        }

        // Btn3: Abrir puerta manualmente
        binding.Btn3.setOnClickListener {
            enviarAccion(CMD_ABRIR_PUERTA, ESTADO_PUERTA_ABIERTA, "Abriendo puerta")
        }

        // Btn4: Cerrar puerta manualmente
        binding.Btn4.setOnClickListener {
            enviarAccion(CMD_CERRAR_PUERTA, ESTADO_PUERTA_CERRADA, "Cerrando puerta")
        }

        // Btn5: Desactivar sistema y cerrar puerta
        binding.Btn5.setOnClickListener {
            enviarAccion(CMD_DESACTIVAR, ESTADO_DESACTIVADO, "Sistema desactivado")
        }
    }

    private fun enviarAccion(comando: String, estadoNuevo: String, aviso: String) {
        // Restricción: exigir conexión Bluetooth antes de operar el sistema
        if (!BluetoothConeccion.isConnected) {
            Toast.makeText(
                this,
                "Debes conectar el dispositivo primero en Instrucciones",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val enviado = BluetoothConeccion.sendCommand(comando)
        if (enviado) {
            actualizarEstado(estadoNuevo)
            Toast.makeText(this, aviso, Toast.LENGTH_SHORT).show()
        } else {
            actualizarEstadoConexion()
            Toast.makeText(this, "Error al enviar comando. Revisa la conexión.", Toast.LENGTH_SHORT).show()
        }
    }

    // Cambia el texto y color de TextView1 según el estado actual
    private fun actualizarEstado(nuevoEstado: String) {
        binding.TextView1.text = nuevoEstado

        val colorRes = when (nuevoEstado) {
            ESTADO_ACTIVADO -> R.color.state_active_green
            ESTADO_DESACTIVADO -> R.color.state_inactive_red
            ESTADO_MOVIMIENTO -> R.color.state_motion_blue
            ESTADO_PUERTA_ABIERTA -> R.color.tech_blue_primary
            ESTADO_PUERTA_CERRADA -> R.color.text_primary
            else -> R.color.text_primary
        }

        binding.TextView1.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    // Procesa los mensajes que llegan desde el ESP32 / Arduino
    private fun procesarMensajeRecibido(mensaje: String) {
        val msg = mensaje.trim().uppercase()

        when {
            msg.contains("MOVIMIENTO") || msg.contains("MOV_DETECTED") || msg.contains("PIR") -> {
                actualizarEstado(ESTADO_MOVIMIENTO)
            }
            msg.contains("PUERTA ABIERTA") || msg.contains("DOOR_OPEN") || msg.contains("OPENED") -> {
                actualizarEstado(ESTADO_PUERTA_ABIERTA)
            }
            msg.contains("PUERTA CERRADA") || msg.contains("DOOR_CLOSED") || msg.contains("CLOSED") -> {
                actualizarEstado(ESTADO_PUERTA_CERRADA)
            }
            msg.contains("SISTEMA ACTIVADO") || msg.contains("SYS_ACT") ||
            (msg.contains("ACTIVADO") && !msg.contains("DESACTIVADO")) -> {
                actualizarEstado(ESTADO_ACTIVADO)
            }
            msg.contains("SISTEMA DESACTIVADO") || msg.contains("DESACTIVADO") || msg.contains("SYS_DEACT") -> {
                actualizarEstado(ESTADO_DESACTIVADO)
            }
            msg.contains("DISCONNECTED") -> {
                actualizarEstado(ESTADO_DESACTIVADO)
                actualizarEstadoConexion()
                Toast.makeText(this, "Se perdió la conexión Bluetooth", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun actualizarEstadoConexion() {
        val conectado = BluetoothConeccion.isConnected
        val opacidad = if (conectado) 1.0f else 0.55f

        binding.Btn2.alpha = opacidad
        binding.Btn3.alpha = opacidad
        binding.Btn4.alpha = opacidad
        binding.Btn5.alpha = opacidad

        if (conectado) {
            val nombre = BluetoothConeccion.connectedDeviceName ?: "Dispositivo IoT"
            binding.tvBtConnectionBadge.text = "Conectado a: $nombre"
            binding.tvBtConnectionBadge.setTextColor(ContextCompat.getColor(this, R.color.state_active_green))
        } else {
            binding.tvBtConnectionBadge.text = "Sin conexión Bluetooth (Ir a Instrucciones)"
            binding.tvBtConnectionBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarEstadoConexion()

        if (BluetoothConeccion.isConnected) {
            BluetoothConeccion.startListening { mensaje ->
                procesarMensajeRecibido(mensaje)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            BluetoothConeccion.disconnect()
        }
    }
}
