package com.example.blekotlin

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class DeviceListAdapter(context: Context, private val mViewResourceId: Int, private val mDevices: ArrayList<BluetoothDevice>) : ArrayAdapter<BluetoothDevice>(context, mViewResourceId, mDevices) {

    private val mLayoutInflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        convertView = mLayoutInflater.inflate(mViewResourceId, null)

        val device = mDevices[position]

        if (device != null) {
            val deviceName = convertView.findViewById<TextView>(R.id.tvDeviceName)
            val deviceAddress = convertView.findViewById<TextView>(R.id.tvDeviceAddress)

            deviceName?.text = device.name
            deviceAddress?.text = device.address
        }

        return convertView
    }
}
