package com.mexiti.conectaesp32.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mexiti.conectaesp32.repositorio.BluetoothRepository
import com.mexiti.conectaesp32.repositorio.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Date
import java.util.Locale

class BluetoothViewModel(application: Application): AndroidViewModel(application){
    private val bluetoothRepository = BluetoothRepository(application.applicationContext)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _receivedDataLog = MutableStateFlow<List<String>>(emptyList())
    val receivedDataLog: StateFlow<List<String>> = _receivedDataLog.asStateFlow()

    // NUEVO: Estados para la Actividad 2 (Lecturas del DHT11)
    private val _temperature = MutableStateFlow(0f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _humidity = MutableStateFlow(0f)
    val humidity: StateFlow<Float> = _humidity.asStateFlow()

    val adapterState: String
        get() = bluetoothRepository.getAdapterState()

    init {
        // Observar el estado de conexión del repositorio
        bluetoothRepository.connectionState
            .onEach { state -> _connectionState.value = state }
            .launchIn(viewModelScope)

        // Observar los datos recibidos del repositorio
        bluetoothRepository.receivedData
            .onEach { data ->
                // Corregí "TX" por "RX" para indicar que es recepción
                _receivedDataLog.value = _receivedDataLog.value + "${getCurrentTimestamp()} RX: $data"

                // NUEVO: Procesamiento de datos recibidos (Temperatura y Humedad)
                try {
                    val cleanData = data.trim()
                    if (cleanData.contains(",")) {
                        val parts = cleanData.split(",")
                        if (parts.size >= 2) {
                            val temp = parts[0].toFloatOrNull()
                            val hum = parts[1].toFloatOrNull()

                            if (temp != null) _temperature.value = temp
                            if (hum != null) _humidity.value = hum
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothViewModel", "Error al procesar sensor: ${e.message}")
                }
            }
            .launchIn(viewModelScope)
    }

    fun connect(deviceAddress: String){
        if(deviceAddress.isBlank() || !deviceAddress.matches(Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"))){
            _connectionState.value = ConnectionState.Error("Dirección MAC inválida")
            return
        }
        _receivedDataLog.value = emptyList()
        bluetoothRepository.connectToDevice(deviceAddress, viewModelScope)
    }

    fun disconnect(){
        bluetoothRepository.disconnect(viewModelScope)
    }

    // Método original para enviar mensajes crudos
    fun sendMessage(message: String){
        if(message.isBlank()) return
        val fullMessage = message + "\n"
        bluetoothRepository.sendData(fullMessage, viewModelScope)
        _receivedDataLog.value = _receivedDataLog.value + "${getCurrentTimestamp()} TX: $message"
    }

    // NUEVO: Funciones de la Actividad 1 para control de LEDs
    fun setLed1(isOn: Boolean) {
        sendMessage(if (isOn) "1" else "0")
    }

    fun setLed2(isOn: Boolean) {
        sendMessage(if (isOn) "3" else "2")
    }

    private fun getCurrentTimestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothRepository.disconnect(viewModelScope)
        Log.d("BluetoothViewModel", "ViewModel cleared")
    }
}