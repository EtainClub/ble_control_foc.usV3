package club.etain.blecontrol;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import static club.etain.blecontrol.Constants.MAC_ADDR;
import static club.etain.blecontrol.Constants.REQUEST_ENABLE_BT;
import static club.etain.blecontrol.Constants.REQUEST_FINE_LOCATION;
import static club.etain.blecontrol.Constants.SCAN_PERIOD;
import static club.etain.blecontrol.Constants.TAG;


public class MainActivity extends AppCompatActivity {

    ////// member variables
    //// ble variables
    // ble adapter
    private BluetoothAdapter ble_adapter_;
    // flag for scanning
    private boolean is_scanning_ = false;
    // flag for connection
    private boolean connected_ = false;
    // flag for stimulation started
    private boolean stimulation_started_ = false;
    // scan results
    private Map<String, BluetoothDevice> scan_results_;
    // scan callback
    private ScanCallback scan_cb_;
    // ble scanner
    private BluetoothLeScanner ble_scanner_;
    // BLE Gatt
    private BluetoothGatt ble_gatt_;
    // scan handler
    private Handler scan_handler_;

    //// GUI variables
    // text view for status
    private TextView tv_status_;
    // text view for read
    private TextView tv_read_;
    // button for start scan
    private Button btn_scan_;
    // button for stop connection
    private Button btn_stop_;
    // button for send data
    private Button btn_send_;
    // button for stop program
    private Button btn_stop_program_;
    // button for show paired devices
    private Button btn_show_;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //// get instances of gui objects
        // status textview
        tv_status_ = findViewById(R.id.tv_status);
        // read textview
        tv_read_ = findViewById(R.id.tv_read);
        // scan button
        btn_scan_ = findViewById(R.id.btn_scan);
        // stop button
        btn_stop_ = findViewById(R.id.btn_stop);
        // send button
        btn_send_ = findViewById(R.id.btn_send);
        // stop program button
        btn_stop_program_ = findViewById(R.id.btn_stop_program);
        // show button
        btn_show_ = findViewById(R.id.btn_show);

        // ble manager
        BluetoothManager ble_manager;
        ble_manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        // set ble adapter
        ble_adapter_ = ble_manager.getAdapter();

        //// set click event handler
        // start scan button handler
        btn_scan_.setOnClickListener((v) -> {
            startScan(v);
        });
        // stop connection button handler
        btn_stop_.setOnClickListener((v) -> {
            stopConnection(v);
        });
        // send data button handler
        btn_send_.setOnClickListener((v) -> {
            sendData(v);
        });
        // stop program button handler
        btn_stop_program_.setOnClickListener((v) -> {
            stopProgram(v);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // finish app if the BLE is not supported
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();
        }
    }

    /*
    Start BLE scan
     */
    private void startScan(View v) {
        tv_status_.setText("Scanning...");
        // check ble adapter and ble enabled
        if (ble_adapter_ == null || !ble_adapter_.isEnabled()) {
            requestEnableBLE();
            tv_status_.setText("Scanning Failed: ble not enabled");
            return;
        }
        // check if location permission
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            tv_status_.setText("Scanning Failed: no fine location permission");
            return;
        }

        // disconnect gatt server
        disconnectGattServer();

        ////// BLE scan
        // set ble scanner
        ble_scanner_ = ble_adapter_.getBluetoothLeScanner();
        //// set scan filter
/*
        // create scan filter list
        List<ScanFilter> filters= new Vector<>();
        // create a scan filter builder
        ScanFilter.Builder scan_filter= new ScanFilter.Builder();
        // set mac address of the device to the scan filter
        scan_filter.setDeviceAddress( MAC_ADDR );
        // build a scan filter
        ScanFilter scan= scan_filter.build();
        filters.add( scan );
*/

        //// set scan filters
        // create scan filter list
        List<ScanFilter> filters = new ArrayList<>();
        // create a scan filter with device mac address
        ScanFilter scan_filter = new ScanFilter.Builder()
                .setDeviceAddress(MAC_ADDR)
                .build();
        // add the filter to the list
        filters.add(scan_filter);
        //// scan settings
        // set low power scan mode
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        //// set scan callback
        // callback for handling scanning results
        scan_results_ = new HashMap<>();
        scan_cb_ = new BLEScanCallback(scan_results_);
        //// now ready to scan
        // start scan
        ble_scanner_.startScan(filters, settings, scan_cb_);
        // set scanning flag
        is_scanning_ = true;

        scan_handler_ = new Handler();
        scan_handler_.postDelayed(this::stopScan, SCAN_PERIOD);
    }

    /*
    Request BLE enable
    */
    private void requestEnableBLE() {
        Intent ble_enable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(ble_enable_intent, REQUEST_ENABLE_BT);

    }

    /*
    Request Fine Location permission
     */
    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    /*
    BLE Scan Callback class
    */
    private class BLEScanCallback extends ScanCallback {
        private Map<String, BluetoothDevice> cb_scan_results_;

        /*
        Constructor
         */
        BLEScanCallback(Map<String, BluetoothDevice> _scan_results) {
            cb_scan_results_ = _scan_results;
        }

        @Override
        public void onScanResult(int _callback_type, ScanResult _result) {
            Log.d(TAG, "onScanResult");
            addScanResult(_result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> _results) {
            for (ScanResult result : _results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int _error) {
            Log.e(TAG, "BLE scan failed with code " + _error);
        }

        /*
        Add scan result
         */
        private void addScanResult(ScanResult _result) {
            // get scanned device
            BluetoothDevice device = _result.getDevice();
            // get scanned device MAC address
            String device_address = device.getAddress();
            // add the device to the result list
            cb_scan_results_.put(device_address, device);
            // log
            Log.d(TAG, "scan results device: " + device);
            tv_status_.setText("add scanned device: " + device_address);
        }

    }

    /*
    Stop scanning
     */
    private void stopScan() {
        // check pre-conditions
        if (is_scanning_ && ble_adapter_ != null && ble_adapter_.isEnabled() && ble_scanner_ != null) {
            // stop scanning
            ble_scanner_.stopScan(scan_cb_);
            scanComplete();
        }

        // reset flags
        scan_cb_ = null;
        is_scanning_ = false;
        scan_handler_ = null;
        // update the status
        tv_status_.setText("scanning stopped");
    }

    /*
    Handle scan results after scan stopped
     */
    private void scanComplete() {
        // check if nothing found
        if (scan_results_.isEmpty()) {
            tv_status_.setText("scan results is empty");
            Log.d(TAG, "scan results is empty");
            return;
        }
        // loop over the scan results and connect to them
        for (String device_addr : scan_results_.keySet()) {
            Log.d(TAG, "Found device: " + device_addr);
            // get device instance using its MAC address
            BluetoothDevice device = scan_results_.get(device_addr);
            if (MAC_ADDR.equals(device_addr)) {
                Log.d(TAG, "connecting device: " + device_addr);
                // connect to the device
                connectDevice(device);
            }
        }
    }

    /*
    Connect to the ble device
    */
    private void connectDevice(BluetoothDevice _device) {
        tv_status_.setText("Connecting to " + _device.getAddress());
        GattClientCallback gatt_client_cb = new GattClientCallback();
        ble_gatt_ = _device.connectGatt(this, false, gatt_client_cb);
    }

    /*
    Gatt Client Callback class
    */
    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt _gatt, int _status, int _new_state) {
            super.onConnectionStateChange(_gatt, _status, _new_state);
            if (_status == BluetoothGatt.GATT_FAILURE) {
                disconnectGattServer();
                return;
            } else if (_status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer();
                return;
            }
            if (_new_state == BluetoothProfile.STATE_CONNECTED) {
                // set the connection flag
                connected_ = true;
                Log.d(TAG, "Connected to the GATT server");
                _gatt.discoverServices();
            } else if (_new_state == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt _gatt, int _status) {
            super.onServicesDiscovered(_gatt, _status);
            // check if the discovery failed
            if (_status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Device service discovery failed, status: " + _status);
                return;
            }
            // find discovered characteristics
            List<BluetoothGattCharacteristic> matching_characteristics = BluetoothUtils.findBLECharacteristics(_gatt);
            if (matching_characteristics.isEmpty()) {
                Log.e(TAG, "Unable to find characteristics");
                return;
            }
            // log for successful discovery
            Log.d(TAG, "Services discovery is successful");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt _gatt, BluetoothGattCharacteristic _characteristic) {
            super.onCharacteristicChanged(_gatt, _characteristic);

            Log.d(TAG, "characteristic changed: " + _characteristic.getUuid().toString());
            readCharacteristic(_characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt _gatt, BluetoothGattCharacteristic _characteristic, int _status) {
            super.onCharacteristicWrite(_gatt, _characteristic, _status);
            if (_status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic written successfully");
            } else {
                Log.e(TAG, "Characteristic write unsuccessful, status: " + _status);
                disconnectGattServer();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic read successfully");
                readCharacteristic(characteristic);
            } else {
                Log.e(TAG, "Characteristic read unsuccessful, status: " + status);
                // Trying to read from the Time Characteristic? It doesnt have the property or permissions
                // set to allow this. Normally this would be an error and you would want to:
                // disconnectGattServer();
            }
        }

        /*
        Log the value of the characteristic
        @param characteristic
         */
        private void readCharacteristic(BluetoothGattCharacteristic _characteristic) {
            byte[] msg = _characteristic.getValue();
            Log.d(TAG, "read: " + msg.toString());
        }

    }

    /*
    Disconnect Gatt Server
    */
    public void disconnectGattServer() {
        Log.d(TAG, "Closing Gatt connection");
        // reset the connection flag
        connected_ = false;
        // disconnect and close the gatt
        if (ble_gatt_ != null) {
            ble_gatt_.disconnect();
            ble_gatt_.close();
        }
    }

    /*
    Stop connection
     */
    private void stopConnection(View v) {
        // disconnect gatt server
        disconnectGattServer();
        // reset flags
        scan_cb_ = null;
        is_scanning_ = false;
        scan_handler_ = null;
        // update the status
        tv_status_.setText("disconnected");
    }

    /*
    Send Data
    @todo create a member variable to keep send data protocol, then read the value at sendData.
    @todo Then call proper function for the command
     */
    private void sendData(View v) {
        // check connection
        if (!connected_) {
            Log.e(TAG, "Failed to sendData due to no connection");
            return;
        }
        // find command characteristics from the GATT server
        BluetoothGattCharacteristic cmd_characteristic = BluetoothUtils.findCommandCharacteristic(ble_gatt_);
        // disconnect if the characteristic is not found
        if (cmd_characteristic == null) {
            Log.e(TAG, "Unable to find cmd characteristic");
            disconnectGattServer();
            return;
        }
        // start stimulation
        startStimulation(cmd_characteristic, 2);
    }

    /*
    Start stimulation
    @param cmd_characteristic command characteristic instance
    @param program_id stimulation program id
     */
    private void startStimulation(BluetoothGattCharacteristic _cmd_characteristic, final int _program_id) {
        // build protocol
        byte[] cmd_bytes = new byte[6];
        cmd_bytes[0] = 2;
        cmd_bytes[1] = 7;
        cmd_bytes[2] = (byte) (_program_id);
        cmd_bytes[3] = 0;
        cmd_bytes[4] = 0;
        cmd_bytes[5] = 0;

        // set values to the characteristic
        _cmd_characteristic.setValue(cmd_bytes);
        // write the characteristic
        boolean success = ble_gatt_.writeCharacteristic(_cmd_characteristic);
        // check the result
        if (success) {
            Log.d(TAG, "Wrote: 02 07 " + _program_id + " 00 00 00");
            // set flag
            stimulation_started_ = true;
        } else {
            Log.e(TAG, "Failed to start stimulation");
        }
    }

    /*
    Stop running stimulation program
    */
    private void stopProgram(View v) {
        // check connection
        if (!connected_) {
            Log.e(TAG, "Failed to sendData due to no connection");
            return;
        }
        // check stimulation started flag
        if (!stimulation_started_) {
            Log.e(TAG, "Failed to stopProgram since stimulation has not been started");
            return;
        }
        // find command characteristics from the GATT server
        BluetoothGattCharacteristic cmd_characteristic = BluetoothUtils.findCommandCharacteristic(ble_gatt_);
        // disconnect if the characteristic is not found
        if (cmd_characteristic == null) {
            Log.e(TAG, "Unable to find cmd characteristic");
            disconnectGattServer();
            return;
        }
        // stop stimulation
        // build protocol
        byte[] cmd_bytes = new byte[6];
        cmd_bytes[0] = 2;
        cmd_bytes[1] = 8;
        cmd_bytes[2] = 0;
        cmd_bytes[3] = 0;
        cmd_bytes[4] = 0;
        cmd_bytes[5] = 0;

        // set values to the characteristic
        cmd_characteristic.setValue(cmd_bytes);
        // write the characteristic
        boolean success = ble_gatt_.writeCharacteristic( cmd_characteristic );
        // check the result
        if (success) {
            Log.d(TAG, "Wrote: 02 08 00 00 00");
            // set flag
            stimulation_started_ = true;
        } else {
            Log.e(TAG, "Failed to stop stimulation");
        }

    }
}


