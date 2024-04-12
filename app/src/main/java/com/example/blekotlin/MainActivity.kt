package com.example.blekotlin

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.nio.charset.Charset
import java.util.*

class MainActivity : AppCompatActivity(), AdapterView.OnItemClickListener {
    private val TAG = "MainActivity"

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private lateinit var btnEnableDisable_Discoverable: Button
    private var mBluetoothConnection: BluetoothConnectionService? = null
    private lateinit var btnStartConnection: Button
    private lateinit var btnSend: Button
    lateinit var incomingMessages: TextView
    lateinit var messages: StringBuilder

    private lateinit var etSend: EditText

    private val MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    private var mBTDevice: BluetoothDevice? = null
    private val mBTDevices = ArrayList<BluetoothDevice>()
    private var mDeviceListAdapter: DeviceListAdapter? = null
    private lateinit var lvNewDevices: ListView

    private val mBroadcastReceiver1 = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> Log.d(TAG, "onReceive: STATE OFF")
                    BluetoothAdapter.STATE_TURNING_OFF -> Log.d(TAG, "mBroadcastReceiver1: STATE TURNING OFF")
                    BluetoothAdapter.STATE_ON -> Log.d(TAG, "mBroadcastReceiver1: STATE ON")
                    BluetoothAdapter.STATE_TURNING_ON -> Log.d(TAG, "mBroadcastReceiver1: STATE TURNING ON")
                }
            }
        }
    }

    private val mBroadcastReceiver2 = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                when (mode) {
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.")
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.")
                    BluetoothAdapter.SCAN_MODE_NONE -> Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.")
                    BluetoothAdapter.STATE_CONNECTING -> Log.d(TAG, "mBroadcastReceiver2: Connecting....")
                    BluetoothAdapter.STATE_CONNECTED -> Log.d(TAG, "mBroadcastReceiver2: Connected.")
                }
            }
        }
    }

    private val mBroadcastReceiver3 = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "onReceive: ACTION FOUND.")
            if (action == BluetoothDevice.ACTION_FOUND) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    mBTDevices.add(device)
                }
                Log.d(TAG, "onReceive: ${device?.name}: ${device?.address}")
                mDeviceListAdapter = DeviceListAdapter(context, R.layout.device_adapter_view, mBTDevices)
                lvNewDevices.adapter = mDeviceListAdapter
            }
        }
    }

    private val mBroadcastReceiver4 = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val mDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                when (mDevice?.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        Log.d(TAG, "BroadcastReceiver: BOND_BONDED.")
                        mBTDevice = mDevice
                    }
                    BluetoothDevice.BOND_BONDING -> Log.d(TAG, "BroadcastReceiver: BOND_BONDING.")
                    BluetoothDevice.BOND_NONE -> Log.d(TAG, "BroadcastReceiver: BOND_NONE.")
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: called.")
        super.onDestroy()
        unregisterReceiver(mBroadcastReceiver1)
        unregisterReceiver(mBroadcastReceiver2)
        unregisterReceiver(mBroadcastReceiver3)
        unregisterReceiver(mBroadcastReceiver4)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnEnableDisable_Discoverable = findViewById(R.id.btnDiscoverable_on_off)
        lvNewDevices = findViewById(R.id.lvNewDevices)
        mBTDevices.clear()
        btnStartConnection = findViewById(R.id.btnStartConnection)
        btnSend = findViewById(R.id.btnSend)
        etSend = findViewById(R.id.editText)
        val incomingMessage = findViewById<TextView>(R.id.incomingMessage)
        messages = StringBuilder()

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(mBroadcastReceiver4, filter)

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        lvNewDevices.onItemClickListener = this

        mBluetoothConnection = BluetoothConnectionService(this)

        findViewById<Button>(R.id.btnONOFF).setOnClickListener {
            Log.d(TAG, "onClick: enabling/disabling bluetooth.")
            enableDisableBT()
        }

        btnStartConnection.setOnClickListener {
            startConnection()
        }

        btnSend.setOnClickListener {
            val bytes = etSend.text.toString().toByteArray(Charset.defaultCharset())
            mBluetoothConnection?.write(bytes)
        }
        val mReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val text = intent?.getStringExtra("theMessage")
                messages.append("$text\n")
                incomingMessage.text = messages.toString()
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, IntentFilter("incomingMessage"))
    }



    private fun startConnection() {
        startBTConnection(mBTDevice, MY_UUID_INSECURE)
    }

    private fun startBTConnection(device: BluetoothDevice?, uuid: UUID) {
        Log.d(TAG, "startBTConnection: Initializing RFCOM Bluetooth Connection.")
        mBluetoothConnection?.startClient(device, uuid)
    }

    @SuppressLint("MissingPermission")
    private fun enableDisableBT() {
        if (mBluetoothAdapter == null) {
            Log.d(TAG, "enableDisableBT: Does not have BT capabilities.")
        }
        if (!mBluetoothAdapter!!.isEnabled) {
            Log.d(TAG, "enableDisableBT: enabling BT.")
            val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBTIntent)
            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(mBroadcastReceiver1, BTIntent)
        }
        if (mBluetoothAdapter!!.isEnabled) {
            Log.d(TAG, "enableDisableBT: disabling BT.")
            mBluetoothAdapter!!.disable()
            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(mBroadcastReceiver1, BTIntent)
        }
    }

    @SuppressLint("MissingPermission")
    fun btnEnableDisable_Discoverable(view: View) {
        Log.d(TAG, "btnEnableDisable_Discoverable: Making device discoverable for 300 seconds.")
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(discoverableIntent)
        val intentFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        registerReceiver(mBroadcastReceiver2, intentFilter)
    }

    @SuppressLint("MissingPermission")
    fun btnDiscover(view: View) {
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.")
        if (mBluetoothAdapter!!.isDiscovering) {
            mBluetoothAdapter!!.cancelDiscovery()
            Log.d(TAG, "btnDiscover: Canceling discovery.")
            checkBTPermissions()
            mBluetoothAdapter!!.startDiscovery()
            val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
        }
        if (!mBluetoothAdapter!!.isDiscovering) {
            checkBTPermissions()
            mBluetoothAdapter!!.startDiscovery()
            val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
        }
    }

    private fun checkBTPermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            var permissionCheck = checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
            permissionCheck += checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
            if (permissionCheck != 0) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
            }
        } else {
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.")
        }
    }

    @SuppressLint("MissingPermission")
    override fun onItemClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
        mBluetoothAdapter!!.cancelDiscovery()
        Log.d(TAG, "onItemClick: You Clicked on a device.")
        val deviceName = mBTDevices[i].name
        val deviceAddress = mBTDevices[i].address
        Log.d(TAG, "onItemClick: deviceName = $deviceName")
        Log.d(TAG, "onItemClick: deviceAddress = $deviceAddress")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Log.d(TAG, "Trying to pair with $deviceName")
            mBTDevices[i].createBond()
            mBTDevice = mBTDevices[i]
            mBluetoothConnection = BluetoothConnectionService(this)
        }
    }
}
