package com.example.exemplebluetooth;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothConnectionService {
    // Debugging
    private static final String TAG = "BluetoothChatService";

    private static  final  String appName = "ExampleBT";

    private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final BluetoothAdapter bluetoothAdapter;

    private AcceptThread mInsecureAcceptThread;

    private ConnectThread connectThread;
    private BluetoothDevice appDevice;
    private UUID deviceUUID;
    private ProgressDialog bTProgressDialog;
    private ConnectedThread connectedThread;

    private Context context;



    public BluetoothConnectionService(Context context) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.context = context;
        start();
    }

    private class AcceptThread extends Thread{

        private final BluetoothServerSocket serverSocket;

        public AcceptThread(){
            BluetoothServerSocket temporary = null;
            try {
                temporary = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID_INSECURE);

                Log.d(TAG, "AcceptThread: Setting up server using " + MY_UUID_INSECURE);

            } catch (IOException e){
                e.printStackTrace();
            }
            serverSocket = temporary;
        }

        public void run (){
            Log.d(TAG, "run: AcceptThread running");

            BluetoothSocket socket = null;

            Log.d(TAG, "run: RFCOMM server socket start...");

            try {
                socket =  serverSocket.accept();
            } catch (IOException e) {
                Log.d(TAG, "run: IOEXCEPTION: " + e.getMessage());
                e.printStackTrace();
            }
            if (socket != null){
                connected(socket, appDevice);
            }

            Log.d(TAG, "run: endAcceptThread");

        }

        public void cancel() {
            Log.d(TAG, "cancel: Canceling AcceptThread");

            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "cancel: IOEXCEPTION closing serversocket in acceptThread failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

    }
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket connectSocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "ConnectThread: started");
            appDevice = device;
            deviceUUID = uuid;

        }
        public void run (){
            BluetoothSocket tempSocket = null;
            Log.i(TAG, "RUN ConnectThread");

            try {
                tempSocket = appDevice.createInsecureRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                Log.e(TAG, "run: IOEXCEPTION could not create RFcommSocket in ConnectThread : " + e.getMessage());
                e.printStackTrace();
            }

            connectSocket = tempSocket;

            bluetoothAdapter.cancelDiscovery();

            try {
                connectSocket.connect();
            } catch (IOException e) {
                try{
                    connectSocket.close();
                    Log.e(TAG, "run: closed socket in ConnectThread : " + e.getMessage());
                } catch (IOException el) {
                    Log.e(TAG, "run: IOEXCEPTION unable to connect socket in ConnectThread : " + el.getMessage());
                }
                Log.d(TAG, "run: IOEXCEPTION could not connect socket in ConnectThread : " + e.getMessage());
                e.printStackTrace();
            }
            connected(connectSocket, appDevice);
        }
        public void cancel() {
            Log.d(TAG, "cancel: Canceling ConnectThread");

            try {
                connectSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: IOEXCEPTION closing server socket in ConnectThread failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    private void connected(BluetoothSocket bluetoothSocket, BluetoothDevice bluetoothDevice){
        Log.d(TAG, "Connected: starting");
        connectedThread = new ConnectedThread(bluetoothSocket);
        connectedThread.start();
    }

    public void write(byte[] out){
        ConnectedThread theConnectedThread;

        Log.d(TAG, "write: writing message");
        connectedThread.write(out);
    }
    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        if(connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (mInsecureAcceptThread == null){
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    public void startClient(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startClient: started");

        bTProgressDialog = bTProgressDialog.show(context,
                "Connecting Bluetooth",
                "Please Wait...",
                true);
        connectThread = new ConnectThread(device, uuid);
        connectThread.start();
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket connectTSocket;
        private final InputStream inputStreamSeed;
        private final OutputStream outputStreamSeed;

        public ConnectedThread(BluetoothSocket connectTSocket) {
            Log.d(TAG, "ConnectionThread: starting");
            this.connectTSocket = connectTSocket;
            InputStream tempInput = null;
            OutputStream tempOutput =null;
            try {
                bTProgressDialog.dismiss();
            } catch (NullPointerException e){
                e.printStackTrace();
            }

            try {
                tempInput = connectTSocket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "cancel: IOEXCEPTION closing server socket in ConnectThread failed: " + e.getMessage());
                e.printStackTrace();
            }

            try {
                tempOutput = connectTSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "cancel: IOEXCEPTION closing server socket in ConnectThread failed: " + e.getMessage());
                e.printStackTrace();
            }

            inputStreamSeed = tempInput;
            outputStreamSeed = tempOutput;

        }

        public void run (){
            byte[] bufferSeed = new byte[1024];
            int bytesReturned;
            while(true){
                try {
                    bytesReturned = inputStreamSeed.read(bufferSeed);
                    String incomingSeed = new String(bufferSeed,0,bytesReturned);
                    Log.d(TAG, "InputStream: " + incomingSeed);
                } catch (IOException e) {
                    Log.d(TAG, "failed writing to input Stream");
                    e.printStackTrace();
                    break;
                }

            }
        }

        public void write(byte[] bytes){
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "Writing to outputStream");
            try {
                outputStreamSeed.write(bytes);
            } catch (IOException e) {
                Log.d(TAG, "failed writing to output Stream");
            }
        }

        public void cancel() {
            try{
                connectTSocket.close();
            } catch (IOException e){

            }
        }

    }

}
