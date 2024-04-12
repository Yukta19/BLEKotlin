package com.example.blekotlin

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*

class BluetoothConnectionService(private val mContext: Context) {
    private val TAG = "BluetoothConnectionServ"
    private val appName = "MYAPP"
    private val MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private val mBluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mmDevice: BluetoothDevice? = null
    private var deviceUUID: UUID? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mConnectedThread: ConnectedThread? = null

    init {
        start()
    }

    private inner class AcceptThread : Thread {

        private val mmServerSocket: BluetoothServerSocket?

        @SuppressLint("MissingPermission")
        constructor() : super() {
            var tmp: BluetoothServerSocket? = null
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID_INSECURE)
                Log.d(TAG, "AcceptThread: Setting up Server using: $MY_UUID_INSECURE")
            } catch (e: IOException) {
                Log.e(TAG, "AcceptThread: IOException: " + e.message)
            }
            mmServerSocket = tmp
        }

        override fun run() {
            Log.d(TAG, "run: AcceptThread Running.")
            var socket: BluetoothSocket? = null
            try {
                Log.d(TAG, "run: RFCOM server socket start.....")
                socket = mmServerSocket!!.accept()
                Log.d(TAG, "run: RFCOM server socket accepted connection.")
            } catch (e: IOException) {
                Log.e(TAG, "AcceptThread: IOException: " + e.message)
            }
            if (socket != null) {
                connected(socket, mmDevice)
            }
            Log.i(TAG, "END mAcceptThread ")
        }

        fun cancel() {
            Log.d(TAG, "cancel: Canceling AcceptThread.")
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "cancel: Close of AcceptThread ServerSocket failed. " + e.message)
            }
        }
    }

    private inner class ConnectThread(device: BluetoothDevice?, uuid: UUID?) : Thread() {
        private var mmSocket: BluetoothSocket? = null

        @SuppressLint("MissingPermission")
        override fun run() {
            var tmp: BluetoothSocket? = null
            Log.i(TAG, "RUN mConnectThread ")
            try {
                Log.d(TAG, "ConnectThread: Trying to create InsecureRfcommSocket using UUID: $MY_UUID_INSECURE")
                tmp = mmDevice!!.createRfcommSocketToServiceRecord(deviceUUID)
            } catch (e: IOException) {
                Log.e(TAG, "ConnectThread: Could not create InsecureRfcommSocket " + e.message)
            }
            mBluetoothAdapter.cancelDiscovery()
            mmSocket = tmp
            try {
                mmSocket!!.connect()
                Log.d(TAG, "run: ConnectThread connected.")
            } catch (e: IOException) {
                try {
                    mmSocket!!.close()
                    Log.d(TAG, "run: Closed Socket.")
                } catch (e1: IOException) {
                    Log.e(TAG, "mConnectThread: run: Unable to close connection in socket " + e1.message)
                }
                Log.d(TAG, "run: ConnectThread: Could not connect to UUID: $MY_UUID_INSECURE")
            }
            connected(mmSocket, mmDevice)
        }

        fun cancel() {
            try {
                Log.d(TAG, "cancel: Closing Client Socket.")
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "cancel: close() of mmSocket in Connectthread failed. " + e.message)
            }
        }

        init {
            Log.d(TAG, "ConnectThread: started.")
            mmDevice = device
            deviceUUID = uuid
        }
    }

    @Synchronized
    fun start() {
        Log.d(TAG, "start")
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = AcceptThread()
            mInsecureAcceptThread!!.start()
        }
    }

    fun startClient(device: BluetoothDevice?, uuid: UUID?) {
        Log.d(TAG, "startClient: Started.")
        mProgressDialog = ProgressDialog.show(mContext, "Connecting Bluetooth", "Please Wait...", true)
        mConnectThread = ConnectThread(device, uuid)
        mConnectThread!!.start()
    }

    private inner class ConnectedThread(socket: BluetoothSocket) : Thread() {
        private val mmSocket: BluetoothSocket?
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (true) {
                try {
                    bytes = mmInStream!!.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)
                    Log.d(TAG, "InputStream: $incomingMessage")

                    val incomingMessageIntent = Intent("incomingMessage")
                    incomingMessageIntent.putExtra("theMessage", incomingMessage)
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(incomingMessageIntent)


                } catch (e: IOException) {
                    Log.e(TAG, "write: Error reading Input Stream. " + e.message)
                    break
                }
            }
        }

        fun write(bytes: ByteArray?) {
            val text = String(bytes!!, Charset.defaultCharset())
            Log.d(TAG, "write: Writing to outputstream: $text")
            try {
                mmOutStream!!.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "write: Error writing to output stream. " + e.message)
            }
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (ignored: IOException) {
            }
        }

        init {
            Log.d(TAG, "ConnectedThread: Starting.")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            try {
                mProgressDialog!!.dismiss()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
            try {
                tmpIn = mmSocket!!.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    private fun connected(mmSocket: BluetoothSocket?, mmDevice: BluetoothDevice?) {
        Log.d(TAG, "connected: Starting.")
        mConnectedThread = ConnectedThread(mmSocket!!)
        mConnectedThread!!.start()
    }

    fun write(out: ByteArray?) {
        mConnectedThread!!.write(out)
    }
}
