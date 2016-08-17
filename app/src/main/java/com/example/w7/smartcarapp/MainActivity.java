package com.example.w7.smartcarapp;

import android.os.ParcelUuid;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity
{
    TextView lblDetalle;
    EditText txtData;
    BluetoothAdapter adaptadorBT;
    BluetoothSocket socketBT;
    BluetoothDevice dispositivo;
    OutputStream streamSalida;
    InputStream streamEntrada;
    Thread hiloDeBusqueda;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean detenerBusqueda;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnAbrirCanalBT = (Button)findViewById(R.id.abrir);
        Button btnEnviarDatos = (Button)findViewById(R.id.enviar);
        Button btnCerrarCanalBT = (Button)findViewById(R.id.cerrar);
        lblDetalle = (TextView)findViewById(R.id.label);
        txtData = (EditText)findViewById(R.id.entry);

        //Open Button
        btnAbrirCanalBT.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    buscarBluetooth();
                    abrirConexionBluetooth();
                }
                catch (IOException ex) {
                    Toast.makeText(v.getContext(),ex.getMessage(),Toast.LENGTH_LONG);
                }
            }
        });

        //Send Button
        btnEnviarDatos.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    enviarDatos();
                }
                catch (IOException ex) {
                    Toast.makeText(v.getContext(),ex.getMessage(),Toast.LENGTH_LONG);
                }
            }
        });

        //Close button
        btnCerrarCanalBT.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    cerrarBT();
                }
                catch (IOException ex) { }
            }
        });
    }

    void buscarBluetooth()
    {
        adaptadorBT = BluetoothAdapter.getDefaultAdapter();
        if(adaptadorBT == null)
        {
            lblDetalle.setText("no hay adaptadores bluetooth disponibles!");
        }

        //si no tiene habilitado el bluetooth, pido para que lo habilite
        if(!adaptadorBT.isEnabled())
        {
            Intent habilitarBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(habilitarBT, 0);
        }

        Set<BluetoothDevice> dispositivosAsociados = adaptadorBT.getBondedDevices();
        if(dispositivosAsociados.size() > 0)
        {
            for(BluetoothDevice dispositivoBT : dispositivosAsociados)
            {
                //esto es hardcodeado. Va a tener una lista de los que encuentra

                //if(device.getName().equals("XT1040"))
                if(dispositivoBT.getName().equals("Ivo"))
                {
                    dispositivo = dispositivoBT;
                    break;
                }
            }
        }
        lblDetalle.setText("Dispositivo bluetooth encontrado! ");
    }

    void abrirConexionBluetooth() throws IOException
    {
        try
        {

            //UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");// SerialPortService ID (no anda)

            //Si no se usa reflection para invocar al métido de obtencion de uids, no funciona.
            Method metodo = dispositivo.getClass().getMethod("getUuids", null);
            ParcelUuid[] uuidsDispositivos = (ParcelUuid[]) metodo.invoke(dispositivo, null);
            UUID uuid = UUID.fromString(uuidsDispositivos[1].getUuid().toString());


            //SERIAL_UUID = uuidsDispositivos[1].getUuid();

            socketBT = dispositivo.createRfcommSocketToServiceRecord(uuid);

            socketBT.connect();
            streamSalida = socketBT.getOutputStream();
            streamEntrada = socketBT.getInputStream();

            obtenerDatos();

            lblDetalle.setText("enlace bluetooth abierto!");
        }
        catch (Exception ex)
        {
            lblDetalle.setText(ex.getMessage());
        }

    }

    //queda a la espera de recepcion de mensajes
    void obtenerDatos()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        detenerBusqueda = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        hiloDeBusqueda = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !detenerBusqueda)
                {
                    try
                    {
                        int bytesAvailable = streamEntrada.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            streamEntrada.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            lblDetalle.setText(data);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        detenerBusqueda = true;
                    }
                }
            }
        });

        hiloDeBusqueda.start();
    }

    void enviarDatos() throws IOException
    {
        String msg = txtData.getText().toString();
        msg += "\n";
        streamSalida.write(msg.getBytes());
        lblDetalle.setText("Paquete enviado!");
    }

    void cerrarBT() throws IOException
    {
        detenerBusqueda = true;
        streamSalida.close();
        streamEntrada.close();
        socketBT.close();
        lblDetalle.setText("enlace bluetooth cerrado!");
    }
}