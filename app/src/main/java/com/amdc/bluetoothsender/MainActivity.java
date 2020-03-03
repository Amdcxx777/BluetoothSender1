package com.amdc.bluetoothsender;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    Button buttonOn, buttonOff, buttonScan, buttonList, buttonSend;
    ListView listView;
    TextView msgBox, textStatus;
    EditText writeMsg;
    ArrayAdapter<String> arrayAdapter;
    BluetoothAdapter myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothDevice[] btArray;
    BluetoothDevice device;
    BluetoothSocket socket;
    SendReceive sendReceive;

    Intent btEnablingIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    IntentFilter scanIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;
    int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final String APP_NAME = "BTChat";
    private static final UUID MY_UUID = UUID.fromString("db2c9139-c8da-4dcc-92b5-89611bc605af");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonOn = findViewById(R.id.btnOn);
        buttonOff = findViewById(R.id.btnOff);
        buttonList = findViewById(R.id.buttonList);
        buttonScan = findViewById(R.id.btnScan);
        buttonSend = findViewById(R.id.btnSend);
        listView = findViewById(R.id.listView);
        textStatus = findViewById(R.id.txtStatus);
        msgBox = findViewById(R.id.textView3);
        writeMsg = findViewById(R.id.editText);

        registerReceiver(scanModeReceiver, scanIntentFilter);

        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myBluetoothAdapter == null) {
                    Toast.makeText(getApplicationContext(), "Bluetooth does not support this Devices", Toast.LENGTH_SHORT).show();
                } else if (!myBluetoothAdapter.isEnabled()) {
                    Toast.makeText(getApplicationContext(), "Need turn On the Bluetooth Device", Toast.LENGTH_SHORT).show();
//                    startActivityForResult(btEnablingIntent, REQUEST_ENABLE_BLUETOOTH);
                } else {
                    ServerClass serverClass = new ServerClass();
                    serverClass.start();
                }
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ClientClass clientClass = new ClientClass(btArray[position]);
                clientClass.start();
                textStatus.setText("Bluetooth status: Connecting");
            }
        });

        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (textStatus.getText().equals("Bluetooth status: Connected")) {
                    String string = String.valueOf(writeMsg.getText());
                    sendReceive.write(string.getBytes());
                } else Toast.makeText(getApplicationContext(), "Bluetooth status not connected!", Toast.LENGTH_SHORT).show();
            }
        });

        buttonOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myBluetoothAdapter == null) {
                    Toast.makeText(getApplicationContext(), "Bluetooth does not support this Devices", Toast.LENGTH_SHORT).show();
                } else if (!myBluetoothAdapter.isEnabled()) {
                    startActivityForResult(btEnablingIntent, REQUEST_ENABLE_BLUETOOTH);
                }
            }
        });

        buttonOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myBluetoothAdapter != null && myBluetoothAdapter.isEnabled() && textStatus.getText() != "Bluetooth status: Connecting") {
                    myBluetoothAdapter.disable();
//                    sendReceive.cancel();
                    Toast.makeText(getApplicationContext(), "Bluetooth is Disable", Toast.LENGTH_SHORT).show();
                } else Toast.makeText(getApplicationContext(), "Status Bluetooth is disable!", Toast.LENGTH_SHORT).show();
            }
        });

        buttonList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<BluetoothDevice> bt = myBluetoothAdapter.getBondedDevices();
                String[] strings = new String[bt.size()];
                btArray = new BluetoothDevice[bt.size()];
                int index = 0;
                if (bt.size() > 0) {
                    for (BluetoothDevice device : bt) {
                        btArray[index] = device;
                        strings[index] = device.getName();
                        index++;
                    }
                    arrayAdapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, strings);
                    listView.setAdapter(arrayAdapter);
                }
            }
        });
    }

    private class ServerClass extends Thread {
        private BluetoothServerSocket serverSocket;
        ServerClass() {
            try {
                serverSocket = myBluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run() {
            BluetoothSocket socket = null;
            while (true) {
                try {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTING;
                    handler.sendMessage(message);
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }
                if (socket != null) {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED;
                    handler.sendMessage(message);
                        sendReceive = new SendReceive(socket);
                        sendReceive.start();
                    break;
                }
            }
        }
    }

    private class ClientClass extends Thread {
//        private BluetoothSocket socket;
        private ClientClass(BluetoothDevice device1) {
            device = device1;
            try { // получаем BluetoothSocket чтобы соединиться с BluetoothDevice
                socket = device.createRfcommSocketToServiceRecord(MY_UUID); // MY_UUID это UUID, который используется и в сервере
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run() {
            // Отменяем сканирование, поскольку оно тормозит соединение
            myBluetoothAdapter.cancelDiscovery();
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException connectException) {
                connectException.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
                try { // Невозможно соединиться. Закрываем сокет и выходим.
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private class SendReceive extends Thread {
        private final InputStream inputStream;
        private final OutputStream outputStream;
        SendReceive(BluetoothSocket socket) {
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try { // Получить входящий и исходящий потоки данных
                tempIn = socket.getInputStream();
                tempOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = tempIn;
            outputStream = tempOut;
        }
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) { // Прослушиваем InputStream пока не произойдет исключение
                try {
                    bytes = inputStream.read(buffer); // читаем из InputStream
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget(); // посылаем прочитанные байты главной деятельности
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        void write(byte[] bytes) { //Вызываем этот метод из главной деятельности, чтобы отправить данные удаленному устройству
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        void cancel(){ //Вызываем этот метод из главной деятельности, чтобы разорвать соединение
            try{
                socket.close();
            } catch(IOException ignored){}
        }
    }

    Handler handler = new Handler(new Handler.Callback() {
        @SuppressLint("SetTextI18n")
        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (myBluetoothAdapter.isEnabled()) {
            switch (message.what) {
                case STATE_LISTENING:
                    textStatus.setText("Bluetooth status: Listening");
                    break;
                case STATE_CONNECTING:
                    textStatus.setText("Bluetooth status: Connecting");
                    break;
                case STATE_CONNECTED:
                    textStatus.setText("Bluetooth status: Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    textStatus.setText("Bluetooth status: Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuff = (byte[]) message.obj;
                    String tempMag = new String(readBuff, 0, message.arg1);
                    msgBox.setText(tempMag);
                    break;
                }
            } else Toast.makeText(getApplicationContext(), "Bluetooth is Off!!!", Toast.LENGTH_SHORT).show();
            return true;
        }
    });

    BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            assert action != null; //correct
            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
                int modeValue = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                if (modeValue == BluetoothAdapter.SCAN_MODE_CONNECTABLE) {
                    Toast.makeText(MainActivity.this, "The Device is not in discoverable mode but can still receive connection", Toast.LENGTH_SHORT).show();
                } else if (modeValue == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    Toast.makeText(MainActivity.this, "The Device is in discoverable mode", Toast.LENGTH_SHORT).show();
                } else if (modeValue == BluetoothAdapter.SCAN_MODE_NONE) {
                    Toast.makeText(MainActivity.this, "The Device is not in discoverable mode and can not receive connections", Toast.LENGTH_SHORT).show();
                } else Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(getApplicationContext(), "Bluetooth is Enable", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), "Bluetooth Enabling Cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
