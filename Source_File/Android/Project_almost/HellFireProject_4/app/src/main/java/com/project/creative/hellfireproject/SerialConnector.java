package com.project.creative.hellfireproject;

/**
 * Created by krilin on 15. 12. 24.
 */

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.List;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

public class SerialConnector {
    public static final String tag = "SerialConnector";

    private Context mContext;

    //private SerialListener mListener;
    private Handler mHandler;

    //private SerialMonitorThread mSerialThread;

    private UsbSerialDriver mDriver;
    private UsbSerialPort mPort;

    public static final int TARGET_VENDOR_ID = 9025; // Arduino
    public static final int TARGET_VENDOR_ID2 = 1659; // PL2303
    public static final int TARGET_VENDOR_ID3 = 1027; // FT232R
    public static final int TARGET_VENDOR_ID4 = 6790; // CH340G
    public static final int TARGET_VENDOR_ID5 = 4292; // CP210x
    public static final int BAUD_RATE = 115200;

    /*****************************************************
     * Constructor, Initialize
     ******************************************************/
    public SerialConnector(Context c/*, SerialListener l, Handler h*/) {
        mContext = c;
        //mListener = l;
        //mHandler = h;
    }

    public void initialize() {
        UsbManager manager = (UsbManager) mContext.getSystemService(android.content.Context.USB_SERVICE);

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            //mListener.onReceive(Constants.MSG_SERIAL_ERROR, 0, 0, "Error: There is no available device. \n", null);
            return;
        }

        mDriver = availableDrivers.get(0);
        if (mDriver == null) {
            // mListener.onReceive(Constants.MSG_SERIAL_ERROR, 0, 0, "Error: Driver is Null \n", null);
            return;
        }

        // Report to UI
        StringBuilder sb = new StringBuilder();
        UsbDevice device = mDriver.getDevice();
        sb.append(" DName : ").append(device.getDeviceName()).append("\n").append(" DID : ")
                .append(device.getDeviceId()).append("\n").append(" VID : ").append(device.getVendorId()).append("\n")
                .append(" PID : ").append(device.getProductId()).append("\n").append(" IF Count : ")
                .append(device.getInterfaceCount()).append("\n");
        //mListener.onReceive(Constants.MSG_DEVICD_INFO, 0, 0, sb.toString(), null);

        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) {
            //mListener.onReceive(Constants.MSG_SERIAL_ERROR, 0, 0, "Error: Cannot connect to device. \n", null);
            return;
        }

        // Read some data! Most have just one port (port 0).
        mPort = mDriver.getPorts().get(0);
        if (mPort == null) {
            //mListener.onReceive(Constants.MSG_SERIAL_ERROR, 0, 0, "Error: Cannot get port. \n", null);
            return;
        }

        try {
            mPort.open(connection);
            mPort.setParameters(9600, 8, 1, 0); // baudrate:9600, dataBits:8,
            // stopBits:1, parity:N
            // byte buffer[] = new byte[16];
            // int numBytesRead = mPort.read(buffer, 1000);
            // Log.d(TAG, "Read " + numBytesRead + " bytes.");
        } catch (IOException e) {
            // Deal with error.
            //mListener.onReceive(Constants.MSG_SERIAL_ERROR, 0, 0, "Error: Cannot open port \n" + e.toString() + "\n",null);
        } finally {
        }

        // Everything is fine. Start serial monitoring thread.
        //startThread();
    } // End of initialize()

    public void finalize() {
        try {
            mDriver = null;
            //stopThread();

            mPort.close();
            mPort = null;
        } catch (Exception ex) {
            //mListener.onReceive(Constants.MSG_SERIAL_ERROR, 0, 0, "Error: Cannot finalize serial connector \n" + ex.toString() + "\n", null);
        }
    }

    /*****************************************************
     * public methods
     ******************************************************/
    // send string to remote
	/*
	public void sendCommand(String cmd) {
		if (mPort != null && cmd != null) {
			try {
				mPort.write(cmd.getBytes(), cmd.length());
				// Send to remote device
			} catch (IOException e) {
				mListener.onReceive(Constants.MSG_SERIAL_ERROR, 0, 0, "Failed in sending command. : IO Exception \n",
						null);
			}
		}
	}
	*/


    public void sendCommand(byte[] cmd) {
        byte[] buf = { 0x00, 0x00 };
        buf[0] = cmd[0];
        buf[1] = cmd[1];
        if (mPort != null && cmd != null) {
            try {
                mPort.write(buf, -1);
                // Send to remote device
            } catch (IOException e) {
                Log.i("Sending Command", "Failed e");
                //mListener.onReceive(Constants.MSG_SERIAL_ERROR, 0, 0, "Failed in sending command. : IO Exception \n",null);
            }
        }
        else{
            Log.i("Sending Command", "Failed");
        }
    }


}