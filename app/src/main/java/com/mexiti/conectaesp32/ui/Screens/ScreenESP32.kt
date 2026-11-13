package com.mexiti.conectaesp32.ui.Screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mexiti.conectaesp32.repositorio.ConnectionState
import com.mexiti.conectaesp32.viewmodel.BluetoothViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun  BluetoothEsp32Screen(
    modifier: Modifier = Modifier,
    bluetoothVM: BluetoothViewModel = viewModel(),
    preSelectedAddress: TextFieldValue = TextFieldValue("XX:XX:XX:XX:XX:XX")
){
    val context = LocalContext.current
    val connectionState by bluetoothVM.connectionState.collectAsState()
    val recivedDataLog by bluetoothVM.receivedDataLog.collectAsState()

    // NUEVO: Observamos los estados de los sensores
    val temperature by bluetoothVM.temperature.collectAsState()
    val humidity by bluetoothVM.humidity.collectAsState()

    var deviceAddressInput by remember { mutableStateOf(preSelectedAddress) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // --- Gestión de Permisos ---
    var hasPermissions by remember { mutableStateOf(hasRequiredBluetoothPermissions(context)) }
    val permissionsToRequest = if ( Build.VERSION.SDK_INT  >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }else{
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        hasPermissions = allGranted

        if (!allGranted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Se requieren permisos de Bluetooth para usar esta función.",
                    duration = SnackbarDuration.Long
                )
            }
            Log.e("BluetoothScreen","Permisos denegados")
        }else {
            Log.d("BluetoothScreen","Permisos concedidos")
        }
    }

    LaunchedEffect(key1 = Unit) {
        if(!hasPermissions){
            multiplePermissionsLauncher.launch(permissionsToRequest)
        }
    }
    // --- Fin Gestión de Permisos ---

    LaunchedEffect(recivedDataLog.size) {
        if(recivedDataLog.isNotEmpty()){
            listState.animateScrollToItem(recivedDataLog.size - 1  )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ESP32 Bluetooth MVVM") }
            )
        }
    )
    { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)

        ) {
            Text( bluetoothVM.adapterState,
                style = MaterialTheme.typography.titleMedium)

            if(bluetoothVM.adapterState != "Bluetooth activado" && bluetoothVM.adapterState != "No soporta Bluetooth"  ){
                Button(onClick = {
                    /* TODO: Implementar solicitud para activar BT */
                }) {
                    Text("Activar Bluetooth")
                }
            }

            OutlinedTextField(
                value = deviceAddressInput,
                onValueChange = {deviceAddressInput = it },
                label = { Text("Dirección MAC del ESP32") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row (
                modifier  = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ){
                Button(
                    onClick = {
                        if(!hasPermissions){
                            multiplePermissionsLauncher.launch(permissionsToRequest)
                        }else {
                            bluetoothVM.connect(deviceAddressInput.text)
                        }
                    },
                    enabled = connectionState !is ConnectionState.Connecting &&
                            connectionState !is ConnectionState.Connected
                ) {
                    Text("Conectar")
                }
                Button(
                    onClick = { bluetoothVM.disconnect() },
                    enabled = connectionState is ConnectionState.Connecting ||
                            connectionState is ConnectionState.Connected

                ) {
                    Text("Desconectar")
                }
            }
            when(val state = connectionState){
                is ConnectionState.Idle -> Text("Estado: Inactivo")
                is ConnectionState.Connecting ->Text("Estado: Conectando a ${state.deviceName}...")
                is ConnectionState.Connected -> Text("Estado: Conectado a ${state.deviceName}", color = Color(0xFF006400))
                is ConnectionState.Error -> Text("Estado: Error - ${state.message}", color = MaterialTheme.colorScheme.error)
                is ConnectionState.Disconnected -> Text("Estado: Desconectado")
            }

            val isConnected = connectionState is ConnectionState.Connected

            // --- ACTIVIDAD 1: Tarjeta de envío de datos (LEDs) ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Actividad 1: Envío de Datos (LEDs)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Controles LED 1
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = { bluetoothVM.setLed1(true) },
                            enabled = isConnected,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text("Encender L1") }

                        Button(
                            onClick = { bluetoothVM.setLed1(false) },
                            enabled = isConnected,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) { Text("Apagar L1") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Controles LED 2
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = { bluetoothVM.setLed2(true) },
                            enabled = isConnected,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text("Encender L2") }

                        Button(
                            onClick = { bluetoothVM.setLed2(false) },
                            enabled = isConnected,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) { Text("Apagar L2") }
                    }
                }
            }

            // --- ACTIVIDAD 2: Tarjeta de recepción de datos (DHT11) ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Actividad 2: Recepción de Datos (DHT11)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Temperatura: $temperature °C")
                    LinearProgressIndicator(
                        progress = { (temperature / 50f).coerceIn(0f, 1f) }, // Escala hasta 50°C
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = Color(0xFFFF9800) // Naranja
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Humedad: $humidity %")
                    LinearProgressIndicator(
                        progress = { (humidity / 100f).coerceIn(0f, 1f) }, // Escala hasta 100%
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = Color(0xFF03A9F4) // Azul
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Log de Comunicación:", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                items(recivedDataLog){ logEntry ->
                    Text(logEntry, style = MaterialTheme.typography.bodySmall)
                }
                // Corregido: Mostrar texto cuando la lista ESTÁ vacía
                if(recivedDataLog.isEmpty()) {
                    item { Text("No hay mensajes", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

fun hasRequiredBluetoothPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    }
}