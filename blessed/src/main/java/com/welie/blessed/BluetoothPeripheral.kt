/*
 *   Copyright (c) 2021 Martijn van Welie
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *   SOFTWARE.
 *
 */
package com.welie.blessed

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.welie.blessed.BluetoothPeripheral
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents a remote Bluetooth peripheral and replaces BluetoothDevice and BluetoothGatt
 *
 *
 * A [BluetoothPeripheral] lets you create a connection with the peripheral or query information about it.
 * This class is a wrapper around the [BluetoothDevice] and takes care of operation queueing, some Android bugs, and provides several convenience functions.
 */
class BluetoothPeripheral internal constructor(context: Context, device: BluetoothDevice, listener: InternalCallback, peripheralCallback: BluetoothPeripheralCallback, callbackHandler: Handler) {
    private val context: Context
    private val callbackHandler: Handler
    private var device: BluetoothDevice
    private val listener: InternalCallback

    internal var peripheralCallback: BluetoothPeripheralCallback
    private val commandQueue: Queue<Runnable> = ConcurrentLinkedQueue()

    @Volatile
    private var bluetoothGatt: BluetoothGatt? = null
    private var cachedName = ""
    private var currentWriteBytes = ByteArray(0)
    private val notifyingCharacteristics: MutableSet<BluetoothGattCharacteristic> = HashSet()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var discoverServicesRunnable: Runnable? = null

    @Volatile
    private var commandQueueBusy = false
    private var isRetrying = false
    private var bondLost = false
    private var manuallyBonding = false
    private var discoveryStarted = false

    @Volatile
    private var state = BluetoothProfile.STATE_DISCONNECTED
    private var nrTries = 0
    private var connectTimestamp: Long = 0

    /**
     * Returns the currently set MTU
     *
     * @return the MTU
     */
    var currentMtu = DEFAULT_MTU
        private set

    /**
     * This abstract class is used to implement BluetoothGatt callbacks.
     */
    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            cancelConnectionTimer()
            val previousState = state
            state = newState

            val hciStatus = HciStatus.fromValue(status)
            if (hciStatus == HciStatus.SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> successfullyConnected()
                    BluetoothProfile.STATE_DISCONNECTED -> successfullyDisconnected(previousState)
                    BluetoothProfile.STATE_DISCONNECTING -> Timber.i("peripheral is disconnecting")
                    BluetoothProfile.STATE_CONNECTING -> Timber.i("peripheral is connecting")
                    else -> Timber.e("unknown state received")
                }
            } else {
                connectionStateChangeUnsuccessful(hciStatus, previousState, newState)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("service discovery failed due to internal error '%s', disconnecting", gattStatus)
                disconnect()
                return
            }

            val services = gatt.services
            Timber.i("discovered %d services for '%s'", services.size, name)

            // Issue 'connected' since we are now fully connect including service discovery
            listener.connected(this@BluetoothPeripheral)
            callbackHandler.post { peripheralCallback.onServicesDiscovered(this@BluetoothPeripheral) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            val parentCharacteristic = descriptor.characteristic
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("failed to write <%s> to descriptor of characteristic <%s> for device: '%s', status '%s' ", BluetoothBytesParser.bytes2String(currentWriteBytes), parentCharacteristic.uuid, address, gattStatus)
                if (failureThatShouldTriggerBonding(gattStatus)) return
            }

            // Check if this was the Client Characteristic Configuration Descriptor
            if (descriptor.uuid == CCC_DESCRIPTOR_UUID) {
                if (gattStatus == GattStatus.SUCCESS) {
                    val value = nonnullOf(descriptor.value)
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                            Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                        notifyingCharacteristics.add(parentCharacteristic)
                    } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        notifyingCharacteristics.remove(parentCharacteristic)
                    }
                }
                callbackHandler.post { peripheralCallback.onNotificationStateUpdate(this@BluetoothPeripheral, parentCharacteristic, gattStatus) }
            } else {
                callbackHandler.post { peripheralCallback.onDescriptorWrite(this@BluetoothPeripheral, currentWriteBytes, descriptor, gattStatus) }
            }
            completedCommand()
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("reading descriptor <%s> failed for device '%s, status '%s'", descriptor.uuid, address, gattStatus)
                if (failureThatShouldTriggerBonding(gattStatus)) return
            }

            val value = nonnullOf(descriptor.value)
            callbackHandler.post { peripheralCallback.onDescriptorRead(this@BluetoothPeripheral, value, descriptor, gattStatus) }
            completedCommand()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = nonnullOf(characteristic.value)
            callbackHandler.post { peripheralCallback.onCharacteristicUpdate(this@BluetoothPeripheral, value, characteristic, GattStatus.SUCCESS) }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("read failed for characteristic <%s>, status '%s'", characteristic.uuid, gattStatus)
                if (failureThatShouldTriggerBonding(gattStatus)) return
            }

            val value = nonnullOf(characteristic.value)
            callbackHandler.post { peripheralCallback.onCharacteristicUpdate(this@BluetoothPeripheral, value, characteristic, gattStatus) }
            completedCommand()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("writing <%s> to characteristic <%s> failed, status '%s'", BluetoothBytesParser.bytes2String(currentWriteBytes), characteristic.uuid, gattStatus)
                if (failureThatShouldTriggerBonding(gattStatus)) return
            }

            val value = currentWriteBytes
            currentWriteBytes = ByteArray(0)
            callbackHandler.post { peripheralCallback.onCharacteristicWrite(this@BluetoothPeripheral, value, characteristic, gattStatus) }
            completedCommand()
        }

        private fun failureThatShouldTriggerBonding(gattStatus: GattStatus): Boolean {
            if (gattStatus == GattStatus.AUTHORIZATION_FAILED || gattStatus == GattStatus.INSUFFICIENT_AUTHENTICATION || gattStatus == GattStatus.INSUFFICIENT_ENCRYPTION) {
                // Characteristic/descriptor is encrypted and needs bonding, bonding should be in progress already
                // Operation must be retried after bonding is completed.
                // This only seems to happen on Android 5/6/7.
                // On newer versions Android will do retry internally
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    Timber.i("operation will be retried after bonding, bonding should be in progress")
                    return true
                }
            }
            return false
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("reading RSSI failed, status '%s'", gattStatus)
            }

            callbackHandler.post { peripheralCallback.onReadRemoteRssi(this@BluetoothPeripheral, rssi, gattStatus) }
            completedCommand()
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("change MTU failed, status '%s'", gattStatus)
            }

            currentMtu = mtu
            callbackHandler.post { peripheralCallback.onMtuChanged(this@BluetoothPeripheral, mtu, gattStatus) }
            completedCommand()
        }

        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("read Phy failed, status '%s'", gattStatus)
            } else {
                Timber.i("updated Phy: tx = %s, rx = %s", PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy))
            }

            callbackHandler.post { peripheralCallback.onPhyUpdate(this@BluetoothPeripheral, PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy), gattStatus) }
            completedCommand()
        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus != GattStatus.SUCCESS) {
                Timber.e("update Phy failed, status '%s'", gattStatus)
            } else {
                Timber.i("updated Phy: tx = %s, rx = %s", PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy))
            }

            callbackHandler.post { peripheralCallback.onPhyUpdate(this@BluetoothPeripheral, PhyType.fromValue(txPhy), PhyType.fromValue(rxPhy), gattStatus) }
        }

        /**
         * This callback is only called from Android 8 (Oreo) or higher
         */
        fun onConnectionUpdated(gatt: BluetoothGatt, interval: Int, latency: Int, timeout: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus == GattStatus.SUCCESS) {
                val msg = String.format(Locale.ENGLISH, "connection parameters: interval=%.1fms latency=%d timeout=%ds", interval * 1.25f, latency, timeout / 100)
                Timber.d(msg)
            } else {
                Timber.e("connection parameters update failed with status '%s'", gattStatus)
            }

            callbackHandler.post { peripheralCallback.onConnectionUpdated(this@BluetoothPeripheral, interval, latency, timeout, gattStatus) }
        }
    }

    private fun successfullyConnected() {
        val bondstate = bondState
        val timePassed = SystemClock.elapsedRealtime() - connectTimestamp
        Timber.i("connected to '%s' (%s) in %.1fs", name, bondstate, timePassed / 1000.0f)

        if (bondstate == BondState.NONE || bondstate == BondState.BONDED) {
            delayedDiscoverServices(getServiceDiscoveryDelay(bondstate))
        } else if (bondstate == BondState.BONDING) {
            // Apparently the bonding process has already started, so let it complete. We'll do discoverServices once bonding finished
            Timber.i("waiting for bonding to complete")
        }
    }

    private fun delayedDiscoverServices(delay: Long) {
        discoverServicesRunnable = Runnable {
            Timber.d("discovering services of '%s' with delay of %d ms", name, delay)
            if (bluetoothGatt != null && bluetoothGatt!!.discoverServices()) {
                discoveryStarted = true
            } else {
                Timber.e("discoverServices failed to start")
            }
            discoverServicesRunnable = null
        }
        mainHandler.postDelayed(discoverServicesRunnable!!, delay)
    }

    private fun getServiceDiscoveryDelay(bondstate: BondState): Long {
        var delayWhenBonded: Long = 0
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            // It seems delays when bonded are only needed in versions Nougat or lower
            // This issue was observed on a Nexus 5 (M) and Sony Xperia L1 (N) when connecting to a A&D UA-651BLE
            // The delay is needed when devices have the Service Changed Characteristic.
            // If they don't have it the delay isn't needed but we do it anyway to keep code simple
            delayWhenBonded = 1000L
        }
        return if (bondstate == BondState.BONDED) delayWhenBonded else 0
    }

    private fun successfullyDisconnected(previousState: Int) {
        if (previousState == BluetoothProfile.STATE_CONNECTED || previousState == BluetoothProfile.STATE_DISCONNECTING) {
            Timber.i("disconnected '%s' on request", name)
        } else if (previousState == BluetoothProfile.STATE_CONNECTING) {
            Timber.i("cancelling connect attempt")
        }
        if (bondLost) {
            completeDisconnect(false, HciStatus.SUCCESS)

            // Consider the loss of the bond a connection failure so that a connection retry will take place
            callbackHandler.postDelayed({ listener.connectFailed(this@BluetoothPeripheral, HciStatus.SUCCESS) }, DELAY_AFTER_BOND_LOST) // Give the stack some time to register the bond loss internally. This is needed on most phones...
        } else {
            completeDisconnect(true, HciStatus.SUCCESS)
        }
    }

    private fun connectionStateChangeUnsuccessful(status: HciStatus, previousState: Int, newState: Int) {
        cancelPendingServiceDiscovery()
        val servicesDiscovered = services.isNotEmpty()

        // See if the initial connection failed
        if (previousState == BluetoothProfile.STATE_CONNECTING) {
            val timePassed = SystemClock.elapsedRealtime() - connectTimestamp
            val isTimeout = timePassed > timoutThreshold
            val adjustedStatus = if (status == HciStatus.ERROR && isTimeout) HciStatus.CONNECTION_FAILED_ESTABLISHMENT else status
            Timber.i("connection failed with status '%s'", adjustedStatus)
            completeDisconnect(false, adjustedStatus)
            listener.connectFailed(this@BluetoothPeripheral, adjustedStatus)
        } else if (previousState == BluetoothProfile.STATE_CONNECTED && newState == BluetoothProfile.STATE_DISCONNECTED && !servicesDiscovered) {
            // We got a disconnection before the services were even discovered
            Timber.i("peripheral '%s' disconnected with status '%s' (%d) before completing service discovery", name, status, status.value)
            completeDisconnect(false, status)
            listener.connectFailed(this@BluetoothPeripheral, status)
        } else {
            // See if we got connection drop
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.i("peripheral '%s' disconnected with status '%s' (%d)", name, status, status.value)
            } else {
                Timber.i("unexpected connection state change for '%s' status '%s' (%d)", name, status, status.value)
            }
            completeDisconnect(true, status)
        }
    }

    private fun cancelPendingServiceDiscovery() {
        if (discoverServicesRunnable != null) {
            mainHandler.removeCallbacks(discoverServicesRunnable!!)
            discoverServicesRunnable = null
        }
    }

    private val bondStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val receivedDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return

            // Ignore updates for other devices
            if (!receivedDevice.address.equals(address, ignoreCase = true)) return

            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                handleBondStateChange(bondState, previousBondState)
            }
        }
    }

    private fun handleBondStateChange(bondState: Int, previousBondState: Int) {
        when (bondState) {
            BluetoothDevice.BOND_BONDING -> {
                Timber.d("starting bonding with '%s' (%s)", name, address)
                callbackHandler.post { peripheralCallback.onBondingStarted(this@BluetoothPeripheral) }
            }
            BluetoothDevice.BOND_BONDED -> {
                Timber.d("bonded with '%s' (%s)", name, address)
                callbackHandler.post { peripheralCallback.onBondingSucceeded(this@BluetoothPeripheral) }

                // If bonding was started at connection time, we may still have to discover the services
                // Also make sure we are not starting a discovery while another one is already in progress
                if (services.isEmpty() && !discoveryStarted) {
                    delayedDiscoverServices(0)
                }

                // If bonding was triggered by a read/write, we must retry it
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (commandQueueBusy && !manuallyBonding) {
                        mainHandler.postDelayed({
                            Timber.d("retrying command after bonding")
                            retryCommand()
                        }, 50)
                    }
                }

                // If we are doing a manual bond, complete the command
                if (manuallyBonding) {
                    manuallyBonding = false
                    completedCommand()
                }
            }
            BluetoothDevice.BOND_NONE -> {
                if (previousBondState == BluetoothDevice.BOND_BONDING) {
                    Timber.e("bonding failed for '%s', disconnecting device", name)
                    callbackHandler.post { peripheralCallback.onBondingFailed(this@BluetoothPeripheral) }
                } else {
                    Timber.e("bond lost for '%s'", name)
                    bondLost = true

                    // Cancel the discoverServiceRunnable if it is still pending
                    cancelPendingServiceDiscovery()
                    callbackHandler.post { peripheralCallback.onBondLost(this@BluetoothPeripheral) }
                }
                disconnect()
            }
        }
    }

    private val pairingRequestBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pairingDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return

            // Skip other devices
            if (!pairingDevice.address.equals(address, ignoreCase = true)) return
            val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
            Timber.d("pairing request received: " + pairingVariantToString(variant) + " (" + variant + ")")
            if (variant == PAIRING_VARIANT_PIN) {
                val pin = listener.getPincode(this@BluetoothPeripheral)
                if (pin != null) {
                    Timber.d("setting PIN code for this peripheral using '%s'", pin)
                    pairingDevice.setPin(pin.toByteArray())
                    abortBroadcast()
                }
            }
        }
    }

    fun setPeripheralCallback(peripheralCallback: BluetoothPeripheralCallback) {
        this.peripheralCallback = Objects.requireNonNull(peripheralCallback, "no valid peripheral callback provided")
    }

    fun setDevice(bluetoothDevice: BluetoothDevice) {
        device = Objects.requireNonNull(bluetoothDevice, "bluetoothdevice is not valid")
    }

    /**
     * Connect directly with the bluetooth device. This call will timeout in max 30 seconds (5 seconds on Samsung phones)
     */
    fun connect() {
        // Make sure we are disconnected before we start making a connection
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.postDelayed({ // Connect to device with autoConnect = false
                Timber.i("connect to '%s' (%s) using TRANSPORT_LE", name, address)
                registerBondingBroadcastReceivers()
                state = BluetoothProfile.STATE_CONNECTING
                discoveryStarted = false
                bluetoothGatt = connectGattHelper(device, false, bluetoothGattCallback)
                connectTimestamp = SystemClock.elapsedRealtime()
                startConnectionTimer(this@BluetoothPeripheral)
            }, DIRECT_CONNECTION_DELAY_IN_MS.toLong())
        } else {
            Timber.e("peripheral '%s' not yet disconnected, will not connect", name)
        }
    }

    /**
     * Try to connect to a device whenever it is found by the OS. This call never times out.
     * Connecting to a device will take longer than when using connect()
     */
    fun autoConnect() {
        // Note that this will only work for devices that are known! After turning BT on/off Android doesn't know the device anymore!
        // https://stackoverflow.com/questions/43476369/android-save-ble-device-to-reconnect-after-app-close
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.post {
                // Connect to device with autoConnect = true
                Timber.i("autoConnect to '%s' (%s) using TRANSPORT_LE", name, address)
                registerBondingBroadcastReceivers()
                state = BluetoothProfile.STATE_CONNECTING
                discoveryStarted = false
                bluetoothGatt = connectGattHelper(device, true, bluetoothGattCallback)
                connectTimestamp = SystemClock.elapsedRealtime()
            }
        } else {
            Timber.e("peripheral '%s' not yet disconnected, will not connect", name)
        }
    }

    private fun registerBondingBroadcastReceivers() {
        context.registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        context.registerReceiver(pairingRequestBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST))
    }

    /**
     * Create a bond with the peripheral.
     *
     *
     * If a (auto)connect has been issued, the bonding command will be enqueued and you will
     * receive updates via the [BluetoothPeripheralCallback]. Otherwise the bonding will
     * be done immediately and no updates via the callback will happen.
     *
     * @return true if bonding was started/enqueued, false if not
     */
    fun createBond(): Boolean {
        // Check if we have a Gatt object
        if (bluetoothGatt == null) {
            // No gatt object so no connection issued, do create bond immediately
            return device.createBond()
        }

        // Enqueue the bond command because a connection has been issued or we are already connected
        val result = commandQueue.add(Runnable {
            manuallyBonding = true
            if (!device.createBond()) {
                Timber.e("bonding failed for %s", address)
                completedCommand()
            } else {
                Timber.d("manually bonding %s", address)
                nrTries++
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue bonding command")
        }
        return result
    }

    /**
     * Cancel an active or pending connection.
     *
     *
     * This operation is asynchronous and you will receive a callback on onDisconnectedPeripheral.
     */
    fun cancelConnection() {
        // Check if we have a Gatt object
        if (bluetoothGatt == null) {
            Timber.w("cannot cancel connection because no connection attempt is made yet")
            return
        }

        // Check if we are not already disconnected or disconnecting
        if (state == BluetoothProfile.STATE_DISCONNECTED || state == BluetoothProfile.STATE_DISCONNECTING) {
            return
        }

        // Cancel the connection timer
        cancelConnectionTimer()

        // Check if we are in the process of connecting
        if (state == BluetoothProfile.STATE_CONNECTING) {
            // Cancel the connection by calling disconnect
            disconnect()

            // Since we will not get a callback on onConnectionStateChange for this, we issue the disconnect ourselves
            mainHandler.postDelayed({ bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.SUCCESS.value, BluetoothProfile.STATE_DISCONNECTED) }, 50)
        } else {
            // Cancel active connection and onConnectionStateChange will be called by Android
            disconnect()
        }
    }

    /**
     * Disconnect the bluetooth peripheral.
     *
     *
     * When the disconnection has been completed [BluetoothCentralManagerCallback.onDisconnectedPeripheral] will be called.
     */
    private fun disconnect() {
        if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
            state = BluetoothProfile.STATE_DISCONNECTING
            mainHandler.post {
                if (state == BluetoothProfile.STATE_DISCONNECTING) {
                    bluetoothGatt?.disconnect()
                    Timber.i("force disconnect '%s' (%s)", name, address)
                }
            }
        } else {
            listener.disconnected(this@BluetoothPeripheral, HciStatus.SUCCESS)
        }
    }

    fun disconnectWhenBluetoothOff() {
        bluetoothGatt = null
        completeDisconnect(true, HciStatus.SUCCESS)
    }

    /**
     * Complete the disconnect after getting connectionstate == disconnected
     */
    private fun completeDisconnect(notify: Boolean, status: HciStatus) {
        bluetoothGatt?.close()
        bluetoothGatt = null
        commandQueue.clear()
        commandQueueBusy = false
        notifyingCharacteristics.clear()
        try {
            context.unregisterReceiver(bondStateReceiver)
            context.unregisterReceiver(pairingRequestBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
            // In case bluetooth is off, unregistering broadcast receivers may fail
        }
        bondLost = false
        if (notify) {
            listener.disconnected(this@BluetoothPeripheral, status)
        }
    }

    /**
     * Get the mac address of the bluetooth peripheral.
     *
     * @return Address of the bluetooth peripheral
     */
    val address: String
        get() = device.address

    /**
     * Get the type of the peripheral.
     *
     * @return the PeripheralType
     */
    val type: PeripheralType
        get() = PeripheralType.fromValue(device.type)// Cache the name so that we even know it when bluetooth is switched off

    /**
     * Get the name of the bluetooth peripheral.
     *
     * @return name of the bluetooth peripheral
     */
    val name: String
        get() {
            val name = device.name
            if (name != null) {
                // Cache the name so that we even know it when bluetooth is switched off
                cachedName = name
                return name
            }
            return cachedName
        }

    /**
     * Get the bond state of the bluetooth peripheral.
     *
     * @return the bond state
     */
    val bondState: BondState
        get() = BondState.fromValue(device.bondState)

    /**
     * Get the services supported by the connected bluetooth peripheral.
     * Only services that are also supported by [BluetoothCentralManager] are included.
     *
     * @return Supported services.
     */
    val services: List<BluetoothGattService>
        get() = if (bluetoothGatt != null) {
            bluetoothGatt!!.services
        } else emptyList()

    /**
     * Get the BluetoothGattService object for a service UUID.
     *
     * @param serviceUUID the UUID of the service
     * @return the BluetoothGattService object for the service UUID or null if the peripheral does not have a service with the specified UUID
     */
    fun getService(serviceUUID: UUID): BluetoothGattService? {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        return bluetoothGatt?.getService(serviceUUID)
    }

    /**
     * Get the BluetoothGattCharacteristic object for a characteristic UUID.
     *
     * @param serviceUUID        the service UUID the characteristic is part of
     * @param characteristicUUID the UUID of the chararacteristic
     * @return the BluetoothGattCharacteristic object for the characteristic UUID or null if the peripheral does not have a characteristic with the specified UUID
     */
    fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED)
        val service = getService(serviceUUID)
        return service?.getCharacteristic(characteristicUUID)
    }

    /**
     * Returns the connection state of the peripheral.
     *
     * @return the connection state.
     */
    fun getState(): ConnectionState {
        return ConnectionState.fromValue(state)
    }

    /**
     * Get maximum length of byte array that can be written depending on WriteType
     *
     *
     *
     * This value is derived from the current negotiated MTU or the maximum characteristic length (512)
     */
    fun getMaximumWriteValueLength(writeType: WriteType): Int {
        Objects.requireNonNull(writeType, "writetype is null")
        return when (writeType) {
            WriteType.WITH_RESPONSE -> 512
            WriteType.SIGNED -> currentMtu - 15
            else -> currentMtu - 3
        }
    }

    /**
     * Boolean to indicate if the specified characteristic is currently notifying or indicating.
     *
     * @param characteristic the characteristic to check
     * @return true if the characteristic is notifying or indicating, false if it is not
     */
    fun isNotifying(characteristic: BluetoothGattCharacteristic): Boolean {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED)
        return notifyingCharacteristics.contains(characteristic)
    }

    /**
     * Get all notifying/indicating characteristics
     *
     * @return Set of characteristics or empty set
     */
    fun getNotifyingCharacteristics(): Set<BluetoothGattCharacteristic> {
        return Collections.unmodifiableSet(notifyingCharacteristics)
    }

    private val isConnected: Boolean
        get() = bluetoothGatt != null && state == BluetoothProfile.STATE_CONNECTED

    private fun notConnected(): Boolean {
        return !isConnected
    }

    /**
     * Read the value of a characteristic.
     *
     *
     * The characteristic must support reading it, otherwise the operation will not be enqueued.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @return true if the operation was enqueued, false if the characteristic does not support reading or the characteristic was not found
     */
    fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }

        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { readCharacteristic(it) } ?: false
    }

    /**
     * Read the value of a characteristic.
     *
     *
     * The characteristic must support reading it, otherwise the operation will not be enqueued.
     *
     *
     * [BluetoothPeripheralCallback.onCharacteristicUpdate]   will be triggered as a result of this call.
     *
     * @param characteristic Specifies the characteristic to read.
     * @return true if the operation was enqueued, false if the characteristic does not support reading or the characteristic was invalid
     */
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        if (doesNotSupportReading(characteristic)) {
            Timber.e("characteristic does not have read property")
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                if (!bluetoothGatt!!.readCharacteristic(characteristic)) {
                    Timber.e("readCharacteristic failed for characteristic: %s", characteristic.uuid)
                    completedCommand()
                } else {
                    Timber.d("reading characteristic <%s>", characteristic.uuid)
                    nrTries++
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue read characteristic command")
        }
        return result
    }

    private fun doesNotSupportReading(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     *
     * All parameters must have a valid value in order for the operation
     * to be enqueued. If the characteristic does not support writing with the specified writeType, the operation will not be enqueued.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param value              the byte array to write
     * @param writeType          the write type to use when writing. Must be WRITE_TYPE_DEFAULT, WRITE_TYPE_NO_RESPONSE or WRITE_TYPE_SIGNED
     * @return true if the operation was enqueued, false if the characteristic does not support reading or the characteristic was not found
     */
    fun writeCharacteristic(serviceUUID: UUID, characteristicUUID: UUID, value: ByteArray, writeType: WriteType): Boolean {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED)
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED)
        Objects.requireNonNull(writeType, NO_VALID_WRITE_TYPE_PROVIDED)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { writeCharacteristic(it, value, writeType) } ?: false
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     *
     * All parameters must have a valid value in order for the operation
     * to be enqueued. If the characteristic does not support writing with the specified writeType, the operation will not be enqueued.
     * The length of the byte array to write must be between 1 and getMaximumWriteValueLength(writeType).
     *
     *
     * [BluetoothPeripheralCallback.onCharacteristicWrite] will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to write to
     * @param value          the byte array to write
     * @param writeType      the write type to use when writing.
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: WriteType): Boolean {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED)
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED)
        Objects.requireNonNull(writeType, NO_VALID_WRITE_TYPE_PROVIDED)

        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }

        require(value.isNotEmpty()) { VALUE_BYTE_ARRAY_IS_EMPTY }
        require(value.size <= getMaximumWriteValueLength(writeType)) { VALUE_BYTE_ARRAY_IS_TOO_LONG }

        if (doesNotSupportWriteType(characteristic, writeType)) {
            Timber.e("characteristic <%s> does not support writeType '%s'", characteristic.uuid, writeType)
            return false
        }

        // Copy the value to avoid race conditions
        val bytesToWrite = copyOf(value)
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                currentWriteBytes = bytesToWrite
                characteristic.writeType = writeType.writeType
                if (willCauseLongWrite(bytesToWrite, writeType)) {
                    // Android will turn this into a Long Write because it is larger than the MTU - 3.
                    // When doing a Long Write the byte array will be automatically split in chunks of size MTU - 3.
                    // However, the peripheral's firmware must also support it, so it is not guaranteed to work.
                    // Long writes are also very inefficient because of the confirmation of each write operation.
                    // So it is better to increase MTU if possible. Hence a warning if this write becomes a long write...
                    // See https://stackoverflow.com/questions/48216517/rxandroidble-write-only-sends-the-first-20b
                    Timber.w("value byte array is longer than allowed by MTU, write will fail if peripheral does not support long writes")
                }
                characteristic.value = bytesToWrite
                if (!bluetoothGatt!!.writeCharacteristic(characteristic)) {
                    Timber.e("writeCharacteristic failed for characteristic: %s", characteristic.uuid)
                    completedCommand()
                } else {
                    Timber.d("writing <%s> to characteristic <%s>", BluetoothBytesParser.bytes2String(bytesToWrite), characteristic.uuid)
                    nrTries++
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue write characteristic command")
        }
        return result
    }

    private fun willCauseLongWrite(value: ByteArray, writeType: WriteType): Boolean {
        return value.size > currentMtu - 3 && writeType == WriteType.WITH_RESPONSE
    }

    private fun doesNotSupportWriteType(characteristic: BluetoothGattCharacteristic, writeType: WriteType): Boolean {
        return characteristic.properties and writeType.property == 0
    }

    /**
     * Read the value of a descriptor.
     *
     * @param descriptor the descriptor to read
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    fun readDescriptor(descriptor: BluetoothGattDescriptor): Boolean {
        Objects.requireNonNull(descriptor, NO_VALID_DESCRIPTOR_PROVIDED)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                if (!bluetoothGatt!!.readDescriptor(descriptor)) {
                    Timber.e("readDescriptor failed for characteristic: %s", descriptor.uuid)
                    completedCommand()
                } else {
                    Timber.d("reading descriptor <%s>", descriptor.uuid)
                    nrTries++
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue read descriptor command")
        }
        return result
    }

    /**
     * Write a value to a descriptor.
     *
     *
     * For turning on/off notifications use [BluetoothPeripheral.setNotify] instead.
     *
     * @param descriptor the descriptor to write to
     * @param value      the value to write
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        Objects.requireNonNull(descriptor, NO_VALID_DESCRIPTOR_PROVIDED)
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        require(value.isNotEmpty()) { VALUE_BYTE_ARRAY_IS_EMPTY }
        require(value.size <= getMaximumWriteValueLength(WriteType.WITH_RESPONSE)) { VALUE_BYTE_ARRAY_IS_TOO_LONG }

        // Copy the value to avoid race conditions
        val bytesToWrite = copyOf(value)
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                currentWriteBytes = bytesToWrite
                descriptor.value = bytesToWrite
                adjustWriteTypeIfNeeded(descriptor)
                if (!bluetoothGatt!!.writeDescriptor(descriptor)) {
                    Timber.e("writeDescriptor failed for descriptor: %s", descriptor.uuid)
                    completedCommand()
                } else {
                    Timber.d("writing <%s> to descriptor <%s>", BluetoothBytesParser.bytes2String(bytesToWrite), descriptor.uuid)
                    nrTries++
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue write descriptor command")
        }
        return result
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param enable             true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, false the characteristic could not be found or does not support notifications
     */
    fun setNotify(serviceUUID: UUID, characteristicUUID: UUID, enable: Boolean): Boolean {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { setNotify(it, enable) } ?: false
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     *
     * [BluetoothPeripheralCallback.onNotificationStateUpdate] will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to turn notification on/off for
     * @param enable         true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, false if the characteristic doesn't support notification or indications or
     */
    fun setNotify(characteristic: BluetoothGattCharacteristic, enable: Boolean): Boolean {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }

        // Get the Client Characteristic Configuration Descriptor for the characteristic
        val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
        if (descriptor == null) {
            Timber.e("could not get CCC descriptor for characteristic %s", characteristic.uuid)
            return false
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        val value: ByteArray
        val properties = characteristic.properties
        value = if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            Timber.e("characteristic %s does not have notify or indicate property", characteristic.uuid)
            return false
        }
        val finalValue = if (enable) value else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        val result = commandQueue.add(Runnable {
            if (notConnected()) {
                completedCommand()
                return@Runnable
            }

            // First try to set notification for Gatt object
            if (!bluetoothGatt!!.setCharacteristicNotification(characteristic, enable)) {
                Timber.e("setCharacteristicNotification failed for characteristic: %s", characteristic.uuid)
                completedCommand()
                return@Runnable
            }

            // Then write to CCC descriptor
            currentWriteBytes = finalValue
            descriptor.value = finalValue
            adjustWriteTypeIfNeeded(descriptor)
            if (!bluetoothGatt!!.writeDescriptor(descriptor)) {
                Timber.e("writeDescriptor failed for descriptor: %s", descriptor.uuid)
                completedCommand()
            } else {
                nrTries++
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue setNotify command")
        }
        return result
    }

    private fun adjustWriteTypeIfNeeded(descriptor: BluetoothGattDescriptor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Up to Android 6 there is a bug where Android takes the writeType of the parent characteristic instead of always WRITE_TYPE_DEFAULT
            // See: https://android.googlesource.com/platform/frameworks/base/+/942aebc95924ab1e7ea1e92aaf4e7fc45f695a6c%5E%21/#F0
            val parentCharacteristic = descriptor.characteristic
            parentCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
    }

    /**
     * Read the RSSI for a connected remote peripheral.
     *
     *
     * [BluetoothPeripheralCallback.onReadRemoteRssi] will be triggered as a result of this call.
     *
     * @return true if the operation was enqueued, false otherwise
     */
    fun readRemoteRssi(): Boolean {
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                if (!bluetoothGatt!!.readRemoteRssi()) {
                    Timber.e("readRemoteRssi failed")
                    completedCommand()
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue readRemoteRssi command")
        }
        return result
    }

    /**
     * Request an MTU size used for a given connection.
     *
     *
     * When performing a write request operation (write without response),
     * the data sent is truncated to the MTU size. This function may be used
     * to request a larger MTU size to be able to send more data at once.
     *
     *
     * [BluetoothPeripheralCallback.onMtuChanged] will be triggered as a result of this call.
     *
     * @param mtu the desired MTU size
     * @return true if the operation was enqueued, false otherwise
     */
    fun requestMtu(mtu: Int): Boolean {
        require(!(mtu < DEFAULT_MTU || mtu > MAX_MTU)) { "mtu must be between 23 and 517" }
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                if (!bluetoothGatt!!.requestMtu(mtu)) {
                    Timber.e("requestMtu failed")
                    completedCommand()
                } else {
                    Timber.i("requesting MTU of %d", mtu)
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue requestMtu command")
        }
        return result
    }

    /**
     * Request a different connection priority.
     *
     * @param priority the requested connection priority
     * @return true if request was enqueued, false if not
     */
    fun requestConnectionPriority(priority: ConnectionPriority): Boolean {
        Objects.requireNonNull(priority, PRIORITY_IS_NULL)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                if (!bluetoothGatt!!.requestConnectionPriority(priority.value)) {
                    Timber.e("could not request connection priority")
                } else {
                    Timber.d("requesting connection priority %s", priority)
                }
            }

            // complete command immediately as this command is not blocking
            completedCommand()
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue request connection priority command")
        }
        return result
    }

    /**
     * Set the preferred connection PHY for this app. Please note that this is just a
     * recommendation, whether the PHY change will happen depends on other applications preferences,
     * local and remote controller capabilities. Controller can override these settings.
     *
     *
     * [BluetoothPeripheralCallback.onPhyUpdate] will be triggered as a result of this call, even
     * if no PHY change happens. It is also triggered when remote device updates the PHY.
     *
     * @param txPhy the desired TX PHY
     * @param rxPhy the desired RX PHY
     * @param phyOptions the desired optional sub-type for PHY_LE_CODED
     * @return true if request was enqueued, false if not
     */
    fun setPreferredPhy(txPhy: PhyType, rxPhy: PhyType, phyOptions: PhyOptions): Boolean {
        Objects.requireNonNull(txPhy)
        Objects.requireNonNull(rxPhy)
        Objects.requireNonNull(phyOptions)
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Timber.e("setPreferredPhy requires Android 8.0 or newer")
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Timber.i("setting preferred Phy: tx = %s, rx = %s, options = %s", txPhy, rxPhy, phyOptions)
                    bluetoothGatt!!.setPreferredPhy(txPhy.mask, rxPhy.mask, phyOptions.value)
                }
            }

            // complete command immediately as this command is not blocking
            completedCommand()
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue setPreferredPhy command")
        }
        return result
    }

    /**
     * Read the current transmitter PHY and receiver PHY of the connection. The values are returned
     * in [BluetoothPeripheralCallback.onPhyUpdate]
     */
    fun readPhy(): Boolean {
        if (notConnected()) {
            Timber.e(PERIPHERAL_NOT_CONNECTED)
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Timber.e("setPreferredPhy requires Android 8.0 or newer")
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bluetoothGatt!!.readPhy()
                    Timber.d("reading Phy")
                    return@Runnable
                }
            }

            // complete command immediately as this command is not blocking
            completedCommand()
        })
        if (result) {
            nextCommand()
        } else {
            Timber.e("could not enqueue readyPhy command")
        }
        return result
    }

    /**
     * Asynchronous method to clear the services cache. Make sure to add a delay when using this!
     *
     * @return true if the method was executed, false if not executed
     */
    fun clearServicesCache(): Boolean {
        if (bluetoothGatt == null) return false
        var result = false
        try {
            val refreshMethod = bluetoothGatt!!.javaClass.getMethod("refresh")
            if (refreshMethod != null) {
                result = refreshMethod.invoke(bluetoothGatt) as Boolean
            }
        } catch (e: Exception) {
            Timber.e("could not invoke refresh method")
        }
        return result
    }

    /**
     * The current command has been completed, move to the next command in the queue (if any)
     */
    private fun completedCommand() {
        isRetrying = false
        commandQueue.poll()
        commandQueueBusy = false
        nextCommand()
    }

    /**
     * Retry the current command. Typically used when a read/write fails and triggers a bonding procedure
     */
    private fun retryCommand() {
        commandQueueBusy = false
        val currentCommand = commandQueue.peek()
        if (currentCommand != null) {
            if (nrTries >= MAX_TRIES) {
                // Max retries reached, give up on this one and proceed
                Timber.d("max number of tries reached, not retrying operation anymore")
                commandQueue.poll()
            } else {
                isRetrying = true
            }
        }
        nextCommand()
    }

    /**
     * Execute the next command in the subscribe queue.
     * A queue is used because the calls have to be executed sequentially.
     * If the read or write fails, the next command in the queue is executed.
     */
    private fun nextCommand() {
        synchronized(this) {

            // If there is still a command being executed, then bail out
            if (commandQueueBusy) return

            // Check if there is something to do at all
            val bluetoothCommand = commandQueue.peek() ?: return

            // Check if we still have a valid gatt object
            if (bluetoothGatt == null) {
                Timber.e("gatt is 'null' for peripheral '%s', clearing command queue", address)
                commandQueue.clear()
                commandQueueBusy = false
                return
            }

            // Execute the next command in the queue
            commandQueueBusy = true
            if (!isRetrying) {
                nrTries = 0
            }
            mainHandler.post {
                try {
                    bluetoothCommand.run()
                } catch (ex: Exception) {
                    Timber.e(ex, "command exception for device '%s'", name)
                    completedCommand()
                }
            }
        }
    }

    private fun pairingVariantToString(variant: Int): String {
        return when (variant) {
            PAIRING_VARIANT_PIN -> "PAIRING_VARIANT_PIN"
            PAIRING_VARIANT_PASSKEY -> "PAIRING_VARIANT_PASSKEY"
            PAIRING_VARIANT_PASSKEY_CONFIRMATION -> "PAIRING_VARIANT_PASSKEY_CONFIRMATION"
            PAIRING_VARIANT_CONSENT -> "PAIRING_VARIANT_CONSENT"
            PAIRING_VARIANT_DISPLAY_PASSKEY -> "PAIRING_VARIANT_DISPLAY_PASSKEY"
            PAIRING_VARIANT_DISPLAY_PIN -> "PAIRING_VARIANT_DISPLAY_PIN"
            PAIRING_VARIANT_OOB_CONSENT -> "PAIRING_VARIANT_OOB_CONSENT"
            else -> "UNKNOWN"
        }
    }

    internal interface InternalCallback {
        /**
         * [BluetoothPeripheral] has successfully connected.
         *
         * @param device [BluetoothPeripheral] that connected.
         */
        fun connected(device: BluetoothPeripheral)

        /**
         * Connecting with [BluetoothPeripheral] has failed.
         *
         * @param device [BluetoothPeripheral] of which connect failed.
         */
        fun connectFailed(device: BluetoothPeripheral, status: HciStatus)

        /**
         * [BluetoothPeripheral] has disconnected.
         *
         * @param device [BluetoothPeripheral] that disconnected.
         */
        fun disconnected(device: BluetoothPeripheral, status: HciStatus)
        fun getPincode(device: BluetoothPeripheral): String?
    }

    /////////////////
    private fun connectGattHelper(remoteDevice: BluetoothDevice?, autoConnect: Boolean, bluetoothGattCallback: BluetoothGattCallback): BluetoothGatt? {
        if (remoteDevice == null) {
            return null
        }

        /*
          This bug workaround was taken from the Polidea RxAndroidBle
          Issue that caused a race condition mentioned below was fixed in 7.0.0_r1
          https://android.googlesource.com/platform/frameworks/base/+/android-7.0.0_r1/core/java/android/bluetooth/BluetoothGatt.java#649
          compared to
          https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r72/core/java/android/bluetooth/BluetoothGatt.java#739
          issue: https://android.googlesource.com/platform/frameworks/base/+/d35167adcaa40cb54df8e392379dfdfe98bcdba2%5E%21/#F0
          */return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || !autoConnect) {
            connectGattCompat(bluetoothGattCallback, remoteDevice, autoConnect)
        } else try {
            val iBluetoothGatt = getIBluetoothGatt(iBluetoothManager)
            if (iBluetoothGatt == null) {
                Timber.e("could not get iBluetoothGatt object")
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true)
            }
            val bluetoothGatt = createBluetoothGatt(iBluetoothGatt, remoteDevice)
            if (bluetoothGatt == null) {
                Timber.e("could not create BluetoothGatt object")
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true)
            }
            val connectedSuccessfully = connectUsingReflection(remoteDevice, bluetoothGatt, bluetoothGattCallback, true)
            if (!connectedSuccessfully) {
                Timber.i("connection using reflection failed, closing gatt")
                bluetoothGatt.close()
            }
            bluetoothGatt
        } catch (exception: NoSuchMethodException) {
            Timber.e("error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: IllegalAccessException) {
            Timber.e("error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: IllegalArgumentException) {
            Timber.e("error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: InvocationTargetException) {
            Timber.e("error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: InstantiationException) {
            Timber.e("error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: NoSuchFieldException) {
            Timber.e("error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        }
    }

    private fun connectGattCompat(bluetoothGattCallback: BluetoothGattCallback, device: BluetoothDevice, autoConnect: Boolean): BluetoothGatt {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.connectGatt(context, autoConnect, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            // Try to call connectGatt with TRANSPORT_LE parameter using reflection
            try {
                val connectGattMethod = device.javaClass.getMethod("connectGatt", Context::class.java, Boolean::class.javaPrimitiveType, BluetoothGattCallback::class.java, Int::class.javaPrimitiveType)
                try {
                    return connectGattMethod.invoke(device, context, autoConnect, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE) as BluetoothGatt
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                }
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            }
        }
        // Fallback on connectGatt without TRANSPORT_LE parameter
        return device.connectGatt(context, autoConnect, bluetoothGattCallback)
    }

    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class, NoSuchFieldException::class)
    private fun connectUsingReflection(device: BluetoothDevice, bluetoothGatt: BluetoothGatt, bluetoothGattCallback: BluetoothGattCallback, autoConnect: Boolean): Boolean {
        setAutoConnectValue(bluetoothGatt, autoConnect)
        val connectMethod = bluetoothGatt.javaClass.getDeclaredMethod("connect", Boolean::class.java, BluetoothGattCallback::class.java)
        connectMethod.isAccessible = true
        return connectMethod.invoke(bluetoothGatt, true, bluetoothGattCallback) as Boolean
    }

    @Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class)
    private fun createBluetoothGatt(iBluetoothGatt: Any, remoteDevice: BluetoothDevice): BluetoothGatt {
        val bluetoothGattConstructor = BluetoothGatt::class.java.declaredConstructors[0]
        bluetoothGattConstructor.isAccessible = true
        return if (bluetoothGattConstructor.parameterTypes.size == 4) {
            bluetoothGattConstructor.newInstance(context, iBluetoothGatt, remoteDevice, BluetoothDevice.TRANSPORT_LE) as BluetoothGatt
        } else {
            bluetoothGattConstructor.newInstance(context, iBluetoothGatt, remoteDevice) as BluetoothGatt
        }
    }

    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    private fun getIBluetoothGatt(iBluetoothManager: Any?): Any? {
        if (iBluetoothManager == null) {
            return null
        }
        val getBluetoothGattMethod = getMethodFromClass(iBluetoothManager.javaClass, "getBluetoothGatt")
        return getBluetoothGattMethod.invoke(iBluetoothManager)
    }

    @get:Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    private val iBluetoothManager: Any?
        private get() {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            val getBluetoothManagerMethod = getMethodFromClass(bluetoothAdapter.javaClass, "getBluetoothManager")
            return getBluetoothManagerMethod.invoke(bluetoothAdapter)
        }

    @Throws(NoSuchMethodException::class)
    private fun getMethodFromClass(cls: Class<*>, methodName: String): Method {
        val method = cls.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun setAutoConnectValue(bluetoothGatt: BluetoothGatt, autoConnect: Boolean) {
        val autoConnectField = bluetoothGatt.javaClass.getDeclaredField("mAutoConnect")
        autoConnectField.isAccessible = true
        autoConnectField.setBoolean(bluetoothGatt, autoConnect)
    }

    private fun startConnectionTimer(peripheral: BluetoothPeripheral) {
        cancelConnectionTimer()
        timeoutRunnable = Runnable {
            Timber.e("connection timout, disconnecting '%s'", peripheral.name)
            disconnect()
            mainHandler.postDelayed({ bluetoothGattCallback.onConnectionStateChange(bluetoothGatt, HciStatus.CONNECTION_FAILED_ESTABLISHMENT.value, BluetoothProfile.STATE_DISCONNECTED) }, 50)
            timeoutRunnable = null
        }
        mainHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT_IN_MS.toLong())
    }

    private fun cancelConnectionTimer() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable)
            timeoutRunnable = null
        }
    }

    private val timoutThreshold: Int
        private get() {
            val manufacturer = Build.MANUFACTURER
            return if (manufacturer.equals("samsung", ignoreCase = true)) {
                TIMEOUT_THRESHOLD_SAMSUNG
            } else {
                TIMEOUT_THRESHOLD_DEFAULT
            }
        }

    /**
     * Make a safe copy of a nullable byte array
     *
     * @param source byte array to copy
     * @return non-null copy of the source byte array or an empty array if source was null
     */
    private fun copyOf(source: ByteArray?): ByteArray {
        return if (source == null) ByteArray(0) else Arrays.copyOf(source, source.size)
    }

    /**
     * Make a byte array nonnull by either returning the original byte array if non-null or an empty bytearray
     *
     * @param source byte array to make nonnull
     * @return the source byte array or an empty array if source was null
     */
    fun nonnullOf(source: ByteArray?): ByteArray {
        return source ?: ByteArray(0)
    }

    companion object {
        private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Max MTU that Android can handle
         */
        const val MAX_MTU = 517

        // Minimal and default MTU
        private const val DEFAULT_MTU = 23

        // Maximum number of retries of commands
        private const val MAX_TRIES = 2

        // Delay to use when doing a connect
        private const val DIRECT_CONNECTION_DELAY_IN_MS = 100

        // Timeout to use if no callback on onConnectionStateChange happens
        private const val CONNECTION_TIMEOUT_IN_MS = 35000

        // Samsung phones time out after 5 seconds while most other phone time out after 30 seconds
        private const val TIMEOUT_THRESHOLD_SAMSUNG = 4500

        // Most other phone time out after 30 seconds
        private const val TIMEOUT_THRESHOLD_DEFAULT = 25000

        // When a bond is lost, the bluetooth stack needs some time to update its internal state
        private const val DELAY_AFTER_BOND_LOST = 1000L
        private const val NO_VALID_SERVICE_UUID_PROVIDED = "no valid service UUID provided"
        private const val NO_VALID_CHARACTERISTIC_UUID_PROVIDED = "no valid characteristic UUID provided"
        private const val NO_VALID_CHARACTERISTIC_PROVIDED = "no valid characteristic provided"
        private const val NO_VALID_WRITE_TYPE_PROVIDED = "no valid writeType provided"
        private const val NO_VALID_VALUE_PROVIDED = "no valid value provided"
        private const val NO_VALID_DESCRIPTOR_PROVIDED = "no valid descriptor provided"
        private const val PERIPHERAL_NOT_CONNECTED = "peripheral not connectected"
        private const val VALUE_BYTE_ARRAY_IS_EMPTY = "value byte array is empty"
        private const val VALUE_BYTE_ARRAY_IS_TOO_LONG = "value byte array is too long"
        private const val PRIORITY_IS_NULL = "priority is null"
        private const val PAIRING_VARIANT_PIN = 0
        private const val PAIRING_VARIANT_PASSKEY = 1
        private const val PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2
        private const val PAIRING_VARIANT_CONSENT = 3
        private const val PAIRING_VARIANT_DISPLAY_PASSKEY = 4
        private const val PAIRING_VARIANT_DISPLAY_PIN = 5
        private const val PAIRING_VARIANT_OOB_CONSENT = 6
    }

    /**
     * Constructs a new device wrapper around `device`.
     *
     * @param context  Android application environment.
     * @param device   Wrapped Android bluetooth device.
     * @param listener Callback to [BluetoothCentralManager].
     */
    init {
        this.context = Objects.requireNonNull(context, "no valid context provided")
        this.device = Objects.requireNonNull(device, "no valid device provided")
        this.listener = Objects.requireNonNull(listener, "no valid listener provided")
        this.peripheralCallback = Objects.requireNonNull(peripheralCallback, "no valid peripheral callback provided")
        this.callbackHandler = Objects.requireNonNull(callbackHandler, "no valid callback handler provided")
    }
}