package cl.palma.ev3apertura_de_puerta

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cl.palma.ev3apertura_de_puerta.databinding.ActivityInstructionsBinding

class InstructionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstructionsBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val listaDispositivos = mutableListOf<BluetoothDevice>()
    private val requestBluetoothConnect = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityInstructionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.instructionsRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        @Suppress("DEPRECATION")
        bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        configurarBotones()

        if (tienePermisoBluetooth()) {
            cargarDispositivosEmparejados()
        } else {
            solicitarPermisoBluetooth()
        }
    }

    private fun configurarBotones() {
        // Actualizar lista de dispositivos vinculados
        binding.btnScanOrRefresh.setOnClickListener {
            if (tienePermisoBluetooth()) {
                cargarDispositivosEmparejados()
                Toast.makeText(this, "Lista actualizada", Toast.LENGTH_SHORT).show()
            } else {
                solicitarPermisoBluetooth()
            }
        }

        // Abrir ajustes de Bluetooth para emparejar placa
        binding.btnOpenSettings.setOnClickListener {
            abrirConfiguracionBluetooth()
        }

        // Conectar o desconectar dispositivo seleccionado
        binding.btnConnectDevice.setOnClickListener {
            if (!tienePermisoBluetooth()) {
                solicitarPermisoBluetooth()
                return@setOnClickListener
            }

            if (bluetoothAdapter?.isEnabled == false) {
                Toast.makeText(this, "Encienda el Bluetooth del teléfono primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (BluetoothConeccion.isConnected) {
                BluetoothConeccion.disconnect()
                mostrarEstado(conectado = false, texto = "Desconectado")
                binding.btnConnectDevice.text = "Conectar al Dispositivo IoT"
                Toast.makeText(this, "Bluetooth desconectado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            conectarDispositivoSeleccionado()
        }

        // Volver al menú principal
        binding.btnBackToMain.setOnClickListener {
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun cargarDispositivosEmparejados() {
        if (!tienePermisoBluetooth()) return

        listaDispositivos.clear()
        val nombres = mutableListOf<String>()

        if (bluetoothAdapter?.isEnabled == false) {
            nombres.add("Bluetooth apagado. Enciéndalo para ver dispositivos.")
            binding.btnConnectDevice.isEnabled = false
        } else {
            val bondedDevices = bluetoothAdapter?.bondedDevices
            if (!bondedDevices.isNullOrEmpty()) {
                for (device in bondedDevices) {
                    listaDispositivos.add(device)
                    val nombre = device.name ?: "Sin nombre"
                    nombres.add("$nombre (${device.address})")
                }
                binding.btnConnectDevice.isEnabled = true
            } else {
                nombres.add("No hay dispositivos emparejados.")
                binding.btnConnectDevice.isEnabled = false
            }
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            nombres
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spnDevices.adapter = adapter
    }

    private fun conectarDispositivoSeleccionado() {
        val pos = binding.spnDevices.selectedItemPosition
        if (pos < 0 || pos >= listaDispositivos.size) {
            Toast.makeText(this, "Seleccione un dispositivo válido", Toast.LENGTH_SHORT).show()
            return
        }

        val dispositivo = listaDispositivos[pos]

        binding.tvConnectionStatus.text = "Estado: Conectando..."
        binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.state_warning_amber))
        binding.btnConnectDevice.isEnabled = false

        BluetoothConeccion.connect(
            device = dispositivo,
            onSuccess = {
                val nombre = BluetoothConeccion.connectedDeviceName ?: "Dispositivo IoT"
                mostrarEstado(conectado = true, texto = "Conectado a $nombre")
                binding.btnConnectDevice.isEnabled = true
                binding.btnConnectDevice.text = "Desconectar Dispositivo"
                Toast.makeText(this, "Conectado a $nombre", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                mostrarEstado(conectado = false, texto = "No conectado")
                binding.btnConnectDevice.isEnabled = true
                binding.btnConnectDevice.text = "Conectar al Dispositivo IoT"
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun mostrarEstado(conectado: Boolean, texto: String) {
        binding.tvConnectionStatus.text = "Estado: $texto"
        val color = if (conectado) R.color.state_active_green else R.color.state_inactive_red
        binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun tienePermisoBluetooth(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun solicitarPermisoBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                requestBluetoothConnect
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                requestBluetoothConnect
            )
        }
    }

    private fun abrirConfiguracionBluetooth() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestBluetoothConnect) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso Bluetooth concedido", Toast.LENGTH_SHORT).show()
                cargarDispositivosEmparejados()
            } else {
                Toast.makeText(this, "Se necesita permiso para usar Bluetooth", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BluetoothConeccion.isConnected) {
            val nombre = BluetoothConeccion.connectedDeviceName ?: "Dispositivo IoT"
            mostrarEstado(conectado = true, texto = "Conectado a $nombre")
            binding.btnConnectDevice.text = "Desconectar Dispositivo"
        } else {
            mostrarEstado(conectado = false, texto = "Desconectado")
            binding.btnConnectDevice.text = "Conectar al Dispositivo IoT"
        }

        if (tienePermisoBluetooth()) {
            cargarDispositivosEmparejados()
        }
    }
}
