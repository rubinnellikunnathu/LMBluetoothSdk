/*
 * The MIT License (MIT)

 * Copyright (c) 2015 LinkMob.cc

 * Contributors: lujun

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package co.lujun.lmbluetoothsdk;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import co.lujun.lmbluetoothsdk.base.BaseController;
import co.lujun.lmbluetoothsdk.base.BluetoothLEListener;
import co.lujun.lmbluetoothsdk.base.BluetoothListener;
import co.lujun.lmbluetoothsdk.receiver.BlueToothReceiver;
import co.lujun.lmbluetoothsdk.service.BluetoothLEService;

/**
 * Author: lujun(http://blog.lujun.co)
 * Date: 2016-1-21 15:18
 */
@TargetApi(21)
public class BluetoothLEController implements BaseController {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private BluetoothLEListener mBluetoothLEListener;
    private BlueToothReceiver mReceiver;
    private BluetoothLEService mBluetoothLEService;
    private ScanSettings mLeSettings;
    private List<ScanFilter> mLeFilters;

    private Context mContext;
    private Handler mHandler;

    private int mScanTime = 120000;

    private static BluetoothLEController sBluetoothLEController;

    /**
     * Get current instance as singleton.
     * @return BluetoothLEController instance
     */
    public static BluetoothLEController getInstance(){
        if (sBluetoothLEController == null){
            synchronized (BluetoothLEController.class){
                if (sBluetoothLEController == null){
                    sBluetoothLEController = new BluetoothLEController();
                }
            }
        }
        return sBluetoothLEController;
    }

    /**
     * Build this instance.
     * @param context the current context you use
     * @return BluetoothLEController instance
     */
    public BluetoothLEController build(Context context){
        mContext = context;
        mHandler = new Handler();
        final BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLEService = new BluetoothLEService();
        return this;
    }

    /**
     * Set bluetooth listener, you can check all bluetooth status and read data with this listener's callback.
     * @param listener a BluetoothListener
     */
    public void setBluetoothListener(BluetoothLEListener listener){
        this.mBluetoothLEListener = listener;
        registerReceiver();
        if (mBluetoothLEService != null) {
            mBluetoothLEService.setBluetoothLEListener(mBluetoothLEListener);
        }
    }

    /**
     * Register broadcast receiver for current context.
     */
    private void registerReceiver(){
        if (mBluetoothLEListener == null || mContext == null){
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);

        mReceiver = new BlueToothReceiver(mBluetoothLEListener);
        mContext.registerReceiver(mReceiver, filter);
    }

    @Override
    public boolean isAvailable() {
        return mBluetoothAdapter != null;
    }

    @Override
    public boolean isEnabled() {
        if (isAvailable()){
            return mBluetoothAdapter.isEnabled();
        }
        return false;
    }

    @Override
    public boolean openBluetooth() {
        if (!isAvailable()){
            return false;
        }
        return mBluetoothAdapter.enable();
    }

    @Override
    public void closeBluetooth() {
        if (!isAvailable() && !isEnabled()){
            return;
        }
        mBluetoothAdapter.disable();
    }

    /**
     *  Check to determine whether BLE is supported on the device
     * @return
     */
    public boolean isSupportBLE(){
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    // Set Android device as a server is not support, you shouldn't call this method in BLE.
    @Override
    public boolean setDiscoverable(int time) {
        return false;
    }

    @Override
    public int getBluetoothState() {
        if (!isAvailable()){
            return BluetoothAdapter.STATE_OFF;
        }
        return mBluetoothAdapter.getState();
    }

    @Override
    public boolean startScan() {
        if (!isAvailable() && !isEnabled()){
            return false;
        }
        if (Build.VERSION.SDK_INT >= 21){
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            mLeSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            mLeFilters = new ArrayList<ScanFilter>();
        }
        scanLeDevice();
        return true;
    }

    private void scanLeDevice(){
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                cancelScan();
            }
        }, mScanTime);
        if (Build.VERSION.SDK_INT < 21) {
            // 搜索指定UUID的外设, startLeScan(UUID[], BluetoothAdapter.LeScanCallback)
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }else {
            if (mCbtScanCallback == null){
                mCbtScanCallback = new CBTScanCallback();
            }
            mLEScanner.startScan(mLeFilters, mLeSettings, mCbtScanCallback);
        }
    }

    @Override
    public boolean cancelScan() {
        if (!isAvailable() && !isEnabled()){
            return false;
        }
        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }else {
            mLEScanner.stopScan(mCbtScanCallback);
        }
        return true;
    }

    @Override
    public Set<BluetoothDevice> getBondedDevices() {
        if (!isAvailable() || !isEnabled()){
            throw new RuntimeException("Bluetooth is not avaliable!");
        }
        return mBluetoothAdapter.getBondedDevices();
    }

    @Override
    public BluetoothDevice findDeviceByMac(String mac) {
        if (!isAvailable() || !isEnabled()){
            throw new RuntimeException("Bluetooth is not avaliable!");
        }
        return mBluetoothAdapter.getRemoteDevice(mac);
    }

    // Set Android device as a server is not support.
    @Override
    public void startAsServer() {}

    @Override
    public void connect(String mac) {
        if (mBluetoothLEService != null) {
            mBluetoothLEService.connect(mContext, mBluetoothAdapter.getRemoteDevice(mac));
        }
    }

    @Override
    public void reConnect(String mac) {
        if (mBluetoothLEService != null) {
            mBluetoothLEService.reConnect();
        }
    }

    @Override
    public void disconnect() {
        if (mBluetoothLEService != null) {
            mBluetoothLEService.disConnect();
        }
    }

    @Override
    public void write(byte[] data) {
        if (mBluetoothLEService != null) {
            mBluetoothLEService.write(data);
        }
    }

    /**
     * Set scan time(unit millisecond)
     * @param time
     */
    public void setScanTime(int time){
        mScanTime = time;
    }

    /**
     * Get scan time.
     * @return
     */
    public int getScanTime() {
        return mScanTime;
    }

    @Override
    public BluetoothDevice getConnectedDevice() {
        return null;
    }



    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (mBluetoothLEListener != null) {
                mBluetoothLEListener.onActionDeviceFound(device);
            }
        }

    };

    private CBTScanCallback mCbtScanCallback;

    private class CBTScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (mBluetoothLEListener != null) {
                mBluetoothLEListener.onActionDeviceFound(result.getDevice());
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    }
}
