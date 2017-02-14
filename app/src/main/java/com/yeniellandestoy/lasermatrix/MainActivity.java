package com.yeniellandestoy.lasermatrix;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private String ARDUINO_MAC = "98:D3:34:90:65:5E";
    private BluetoothService servicio;
    private BluetoothAdapter bAdapter;
    private EditText editTextMessage;
    private Button btnSend;
    private Button btnRepeat;
    private Button btnAnimation;
    private ToggleButton btnRow1;
    private ToggleButton btnRow2;
    private ToggleButton btnRow3;
    private ToggleButton btnRow4;
    private ToggleButton btnRow5;
    private ToggleButton btnRow6;
    private ToggleButton btnRow7;
    private ToggleButton btnRow8;
    private Button btnCorners;
    private Button btnAll;
    private Button btnReset;
    private Button btnWriteTwitter;
    private Button btnClearTextEdit;
    private Button btnConnectToArduino;
    private ArrayList<Integer> rowsToSend = new ArrayList<>();

    private final Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg)
        {
            byte[] buffer 	= null;
            String mensaje 	= null;

            // Atendemos al tipo de mensaje
            switch(msg.what)
            {
                // Mensaje de lectura: se mostrara en el TextView
                case BluetoothService.MSG_LEER:
                {
                    buffer = (byte[])msg.obj;
                    mensaje = new String(buffer, 0, msg.arg1);
//                    textView.setText(mensaje);
                    break;
                }

                // Mensaje de escritura: se mostrara en el Toast
                case BluetoothService.MSG_ESCRIBIR:
                {
                    buffer = (byte[])msg.obj;
                    mensaje = new String(buffer);
                    mensaje = "Enviando mensaje: " + mensaje;
                    Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                    break;
                }

                // Mensaje de cambio de estado
                case BluetoothService.MSG_CAMBIO_ESTADO:
                {
                    switch(msg.arg1)
                    {
                        case BluetoothService.ESTADO_ATENDIENDO_PETICIONES:
                            break;

                        // CONECTADO: Se muestra el dispositivo al que se ha conectado y se activa el boton de enviar
                        case BluetoothService.ESTADO_CONECTADO:
                        {
                            mensaje = "Conectado con Arduino"; // + servicio.getNombreDispositivo();
                            Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                            break;
                        }

                        // REALIZANDO CONEXION: Se muestra el dispositivo al que se esta conectando
                        case BluetoothService.ESTADO_REALIZANDO_CONEXION:
                        {
                            mensaje = "Conectando con Arduino";
                            Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                            break;
                        }

                        // NINGUNO: Mensaje por defecto. Desactivacion del boton de enviar
                        case BluetoothService.ESTADO_NINGUNO:
                        {
                            mensaje = "Sin conexión";
                            Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                            break;
                        }
                        default:
                            break;
                    }
                    break;
                }

                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getViewReferences();
        setViewListeners();

        bAdapter = BluetoothAdapter.getDefaultAdapter();
        connectToArduino();
    }

    private void getViewReferences() {
        btnSend = (Button)findViewById(R.id.btnSend);
        editTextMessage = (EditText)findViewById(R.id.editTextMessage);
        btnRepeat = (Button)findViewById(R.id.btnRepeat);
        btnAnimation = (Button)findViewById(R.id.btnSendAnimation);
        btnRow1 = (ToggleButton)findViewById(R.id.btnRow1);
        btnRow2 = (ToggleButton)findViewById(R.id.btnRow2);
        btnRow3 = (ToggleButton)findViewById(R.id.btnRow3);
        btnRow4 = (ToggleButton)findViewById(R.id.btnRow4);
        btnRow5 = (ToggleButton)findViewById(R.id.btnRow5);
        btnRow6 = (ToggleButton)findViewById(R.id.btnRow6);
        btnRow7 = (ToggleButton)findViewById(R.id.btnRow7);
        btnRow8 = (ToggleButton)findViewById(R.id.btnRow8);
        btnCorners = (Button)findViewById(R.id.btnCorners);
        btnAll = (Button)findViewById(R.id.btnAll);
        btnReset = (Button)findViewById(R.id.btnReset);
        btnWriteTwitter = (Button)findViewById(R.id.btnWriteTwitter);
        btnClearTextEdit = (Button)findViewById(R.id.btnClearTextEdit);
        btnConnectToArduino = (Button)findViewById(R.id.btnConnectToArduino);
    }

    private void setViewListeners() {
        btnSend.setOnClickListener(this);
        btnRepeat.setOnClickListener(this);
        btnAnimation.setOnClickListener(this);
        btnRow1.setOnClickListener(this);
        btnRow2.setOnClickListener(this);
        btnRow3.setOnClickListener(this);
        btnRow4.setOnClickListener(this);
        btnRow5.setOnClickListener(this);
        btnRow6.setOnClickListener(this);
        btnRow7.setOnClickListener(this);
        btnRow8.setOnClickListener(this);
        btnCorners.setOnClickListener(this);
        btnAll.setOnClickListener(this);
        btnReset.setOnClickListener(this);
        btnWriteTwitter.setOnClickListener(this);
        btnClearTextEdit.setOnClickListener(this);
        btnConnectToArduino.setOnClickListener(this);
    }

    public void connectToArduino() {
        if (bAdapter != null) {
            Set<BluetoothDevice> dispositivosEnlazados = bAdapter.getBondedDevices();
            boolean founded = false;

            for (BluetoothDevice dispositivo : dispositivosEnlazados) {
                if (dispositivo.getAddress().equals(ARDUINO_MAC)) {
                    founded = true;
                    servicio = new BluetoothService(this, handler, bAdapter);
                    conectarDispositivo(ARDUINO_MAC);
                }
            }

            if (!founded) {
                Toast.makeText(getBaseContext(), "ERROR: No se ha podido conectar", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void conectarDispositivo(String direccion) {
        if (servicio != null) {
            BluetoothDevice dispositivoRemoto = bAdapter.getRemoteDevice(direccion);
            servicio.solicitarConexion(dispositivoRemoto);
        }
    }

    @Override
    public void onClick(View v) {
        String message;

        switch (v.getId()) {
            case R.id.btnSend: {
                if (servicio != null) {
                    message = editTextMessage.getText().toString();
                    servicio.enviar(message.getBytes());
                }

                break;
            }

            case R.id.btnRepeat: {
                if (servicio != null) {
                    message = getString(R.string.commandRepeat) + editTextMessage.getText().toString();
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    servicio.enviar(message.getBytes());
                }

                break;
            }

            case R.id.btnSendAnimation:
                if (servicio != null) {
                    message = getString(R.string.commandSendAnimation);
                    servicio.enviar(message.getBytes());
                }

                break;

            case R.id.btnRow1:
                sendRowsCommand(btnRow1, 0);
                break;

            case R.id.btnRow2:
                sendRowsCommand(btnRow2, 1);
                break;

            case R.id.btnRow3:
                sendRowsCommand(btnRow3, 2);
                break;

            case R.id.btnRow4:
                sendRowsCommand(btnRow4, 3);
                break;

            case R.id.btnRow5:
                sendRowsCommand(btnRow5, 4);
                break;

            case R.id.btnRow6:
                sendRowsCommand(btnRow6, 5);
                break;

            case R.id.btnRow7:
                sendRowsCommand(btnRow7, 6);
                break;

            case R.id.btnRow8:
                sendRowsCommand(btnRow8, 7);
                break;

            case R.id.btnCorners:
                if (servicio != null) {
                    message = getString(R.string.commandTurnOnCorners);
                    servicio.enviar(message.getBytes());
                }

                break;

            case R.id.btnAll:
                if (servicio != null) {
                    message = getString(R.string.commandTurnOnAll);
                    servicio.enviar(message.getBytes());
                }

                break;

            case R.id.btnReset:
                if (servicio != null) {
                    btnRow1.setChecked(false);
                    btnRow2.setChecked(false);
                    btnRow3.setChecked(false);
                    btnRow4.setChecked(false);
                    btnRow5.setChecked(false);
                    btnRow6.setChecked(false);
                    btnRow7.setChecked(false);
                    btnRow8.setChecked(false);
                    rowsToSend.clear();
                    message = getString(R.string.commandReset);
                    servicio.enviar(message.getBytes());
                }

                break;

            case R.id.btnWriteTwitter:
                editTextMessage.setText(editTextMessage.getText() + getString(R.string.twitterAccount));
                break;

            case R.id.btnClearTextEdit:
                editTextMessage.getText().clear();
                break;

            case R.id.btnConnectToArduino:
                connectToArduino();
                break;

        }
    }

    private void sendRowsCommand(ToggleButton rowButton, Integer rowIndex) {
        if (servicio != null) {
            if (rowButton.isChecked()) {
                rowsToSend.add(rowIndex);
            } else {
                rowsToSend.remove(Integer.valueOf(rowIndex));
            }

            String message = getString(R.string.commandTurnOnRows);

            for (Integer row : rowsToSend) {
                message += row.toString();
            }

            servicio.enviar(message.getBytes());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(servicio != null) {
            servicio.finalizarServicio();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        if(servicio != null) {
            if(servicio.getEstado() == BluetoothService.ESTADO_NINGUNO) {
                servicio.iniciarServicio();
            }
        }
    }
}
