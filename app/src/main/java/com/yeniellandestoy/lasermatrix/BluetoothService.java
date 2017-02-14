/* 
 * (cc) 2013 Daniel Garcia <contacto {at} danigarcia.org>
 * 
 * This file is part of bluetooth.
 * 
 * Flashlight is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * bluetooth is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with bluetooth.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package com.yeniellandestoy.lasermatrix;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {

	private static final String TAG = "org.danigarcia.examples.bluetooth.BluetoothService";
	private static final boolean DEBUG_MODE		= true;
	
	private final Handler handler;
	private final Context context;
	private final BluetoothAdapter bAdapter;
	
	public static final String NOMBRE_SEGURO = "BluetoothServiceSecure";
	public static final String NOMBRE_INSEGURO = "BluetoothServiceInsecure";
	public static UUID UUID_SEGURO = UUID.randomUUID();
	public static UUID UUID_INSEGURO = UUID.randomUUID();
	
	public static final int	ESTADO_NINGUNO				= 0;
	public static final int	ESTADO_CONECTADO			= 1;
	public static final int	ESTADO_REALIZANDO_CONEXION	= 2;
	public static final int	ESTADO_ATENDIENDO_PETICIONES= 3;
	
	public static final int MSG_CAMBIO_ESTADO = 10;
	public static final int MSG_LEER = 11;
	public static final int MSG_ESCRIBIR = 12;
	public static final int MSG_ATENDER_PETICIONES = 13;
	public static final int MSG_ALERTA = 14;
	
	private int 			estado;
	private HiloServidor	hiloServidor	= null;
	private HiloCliente		hiloCliente		= null;
	private HiloConexion	hiloConexion	= null;
	
	
	public BluetoothService(Context context, Handler handler, BluetoothAdapter adapter)
	{
		debug("BluetoothService()", "Iniciando metodo");
		
		this.context	= context;
		this.handler 	= handler;
		this.bAdapter 	= adapter;
		this.estado 	= ESTADO_NINGUNO;

//		UUID_SEGURO = generarUUID();
//		UUID_INSEGURO = generarUUID();
	}
	
	private synchronized void setEstado(int estado)
	{
		this.estado = estado;
		handler.obtainMessage(MSG_CAMBIO_ESTADO, estado, -1).sendToTarget();
	}
	
	public synchronized int getEstado()
	{
		return estado;
	}
	
	public String getNombreDispositivo()
	{
		String nombre = "";
		if(estado == ESTADO_CONECTADO)
		{
			if(hiloConexion != null)
				nombre = hiloConexion.getName();
		}
		
		return nombre;
	}
	
	// Inicia el servicio, creando un HiloServidor que se dedicara a atender las peticiones
	// de conexion.
	public synchronized void iniciarServicio()
	{
		debug("iniciarServicio()", "Iniciando metodo");
		
		// Si se esta intentando realizar una conexion mediante un hilo cliente,
		// se cancela la conexion
		if(hiloCliente != null)
		{
			hiloCliente.cancelarConexion();
			hiloCliente = null;
		}
		
		// Si existe una conexion previa, se cancela
		if(hiloConexion != null)
		{
			hiloConexion.cancelarConexion();
			hiloConexion = null;
		}
		
		// Arrancamos el hilo servidor para que empiece a recibir peticiones
		// de conexion
		if(hiloServidor == null)
		{
			hiloServidor = new HiloServidor();
			hiloServidor.start();
		}
		
		debug("iniciarServicio()", "Finalizando metodo");
	}
	
	public void finalizarServicio()
	{
		debug("finalizarServicio()", "Iniciando metodo");
		
		if(hiloCliente != null)
			hiloCliente.cancelarConexion();
		if(hiloConexion != null)
			hiloConexion.cancelarConexion();
		if(hiloServidor != null)
			hiloServidor.cancelarConexion();
		
		hiloCliente = null;
		hiloConexion = null;
		hiloServidor = null;
		
		setEstado(ESTADO_NINGUNO);
		
	}
	
	// Instancia un hilo conector
	public synchronized void solicitarConexion(BluetoothDevice dispositivo)
	{
		debug("solicitarConexion()", "Iniciando metodo");
		// Comprobamos si existia un intento de conexion en curso.
		// Si es el caso, se cancela y se vuelve a iniciar el proceso
		if(estado == ESTADO_REALIZANDO_CONEXION)
		{
			if(hiloCliente != null)
			{
				hiloCliente.cancelarConexion();
				hiloCliente = null;
			}
		}
		
		// Si existia una conexion abierta, se cierra y se inicia una nueva
		if(hiloConexion != null)
		{
			hiloConexion.cancelarConexion();
			hiloConexion = null;
		}
		
		// Se instancia un nuevo hilo conector, encargado de solicitar una conexion
		// al servidor, que sera la otra parte.
		hiloCliente = new HiloCliente(dispositivo);
		hiloCliente.start();

		setEstado(ESTADO_REALIZANDO_CONEXION);
	}
	
	public synchronized void realizarConexion(BluetoothSocket socket, BluetoothDevice dispositivo)
	{
		debug("realizarConexion()", "Iniciando metodo");
		hiloConexion = new HiloConexion(socket);
		hiloConexion.start();
	}
	
	// Sincroniza el objeto con el hilo HiloConexion e invoca a su metodo escribir()
	// para enviar el mensaje a traves del flujo de salida del socket.
	public int enviar(byte[] buffer)
	{
		debug("enviar()", "Iniciando metodo");
		HiloConexion tmpConexion;
		
		synchronized(this) {
			if(estado != ESTADO_CONECTADO)
				return -1;
			tmpConexion = hiloConexion;
		}
		
		tmpConexion.escribir(buffer);
		
		return buffer.length;
		
	}
	
	// Hilo que hace las veces de servidor, encargado de escuchar conexiones entrantes y
	// crear un hilo que maneje la conexion cuando ello ocurra.
	// La otra parte debera solicitar la conexion mediante un HiloCliente.
	private class HiloServidor extends Thread
	{
		private final BluetoothServerSocket serverSocket;
		
		public HiloServidor()
		{
			debug("HiloServidor.new()", "Iniciando metodo");
			BluetoothServerSocket tmpServerSocket = null;
			
			// Creamos un socket para escuchar las peticiones de conexion
			try {
				tmpServerSocket = bAdapter.listenUsingRfcommWithServiceRecord(NOMBRE_SEGURO, UUID_SEGURO);
			} catch(IOException e) {
				Log.e(TAG, "HiloServidor(): Error al abrir el socket servidor", e);
			}
			
			serverSocket = tmpServerSocket;
		}
		
		public void run()
		{
			debug("HiloServidor.run()", "Iniciando metodo");
			BluetoothSocket socket = null;
			
			setName("HiloServidor");
			setEstado(ESTADO_ATENDIENDO_PETICIONES);
			// El hilo se mantendra en estado de espera ocupada aceptando conexiones
			// entrantes siempre y cuando no exista una conexion activa.
			// En el momento en el que entre una nueva conexion, 
			while(estado != ESTADO_CONECTADO)
			{
				try {
					// Cuando un cliente solicite la conexion se asignara valor al socket..
					socket = serverSocket.accept();
				} 
				catch(IOException e) {
					Log.e(TAG, "HiloServidor.run(): Error al aceptar conexiones entrantes", e);
					break;
				}
				
				// Si el socket tiene valor sera porque un cliente ha solicitado la conexion
				if(socket != null)
				{
					// Realizamos un lock del objeto
					synchronized(BluetoothService.this)
					{
						switch(estado)
						{
							case ESTADO_ATENDIENDO_PETICIONES:
							case ESTADO_REALIZANDO_CONEXION:
							{
								debug("HiloServidor.run()", estado == ESTADO_ATENDIENDO_PETICIONES ? "Atendiendo peticiones" : "Realizando conexion");
								// Estado esperado, se crea el hilo de conexion que recibira
								// y enviara los mensajes
								realizarConexion(socket, socket.getRemoteDevice());
								break;
							}
							case ESTADO_NINGUNO:
							case ESTADO_CONECTADO:
							{
								// No preparado o conexion ya realizada.
								// Se cierra el nuevo socket.
								try {
									debug("HiloServidor.run()", estado == ESTADO_NINGUNO ? "Ninguno" : "Conectado");
									socket.close();
								}
								catch(IOException e) {
									Log.e(TAG, "HiloServidor.run(): socket.close(). Error al cerrar el socket.", e);
								}
								break;
							}
							default:
								break;
						}
					}
				}
				
			} // End while
		}
		
		public void cancelarConexion()
		{
			debug("HiloServidor.cancelarConexion()", "Iniciando metodo");
			try {
				serverSocket.close();
			}
			catch(IOException e) {
				Log.e(TAG, "HiloServidor.cancelarConexion(): Error al cerrar el socket", e);
			}
		}
	}
	
	// Hilo encargado de solicitar una conexion a un dispositivo que este corriendo un
	// HiloServidor.
	private class HiloCliente extends Thread
	{
		private final BluetoothDevice dispositivo;
		private final BluetoothSocket socket;
		
		public HiloCliente(BluetoothDevice dispositivo)
		{
			debug("HiloCliente.new()", "Iniciando metodo");
			BluetoothSocket tmpSocket = null;
			this.dispositivo = dispositivo;
			
			// Obtenemos un socket para el dispositivo con el que se quiere conectar
			try {
				UUID uuid = dispositivo.getUuids()[0].getUuid();
				tmpSocket = dispositivo.createRfcommSocketToServiceRecord(uuid);
			}
			catch(IOException e) {
				Log.e(TAG, "HiloCliente.HiloCliente(): Error al abrir el socket", e);
			}
			
			socket = tmpSocket;
		}
		
		public void run()
		{
			debug("HiloCliente.run()", "Iniciando metodo");
			setName("HiloCliente");
			if(bAdapter.isDiscovering())
				bAdapter.cancelDiscovery();
			
			try {
				socket.connect();
				setEstado(ESTADO_REALIZANDO_CONEXION);
			}
			catch(IOException e) {
				Log.e(TAG, "HiloCliente.run(): socket.connect(): Error realizando la conexion", e);
				try {
					socket.close();
				}
				catch(IOException inner) {
					Log.e(TAG, "HiloCliente.run(): Error cerrando el socket", inner);
				}
				setEstado(ESTADO_NINGUNO);
			}
			
			// Reiniciamos el hilo cliente, ya que no lo necesitaremos mas
			synchronized(BluetoothService.this)
			{
				hiloCliente = null;
			}
			
			// Realizamos la conexion
			realizarConexion(socket, dispositivo);
		}
		
		public void cancelarConexion()
		{
			debug("cancelarConexion()", "Iniciando metodo");
			try {
				socket.close();
			}
			catch(IOException e) {
				Log.e(TAG, "HiloCliente.cancelarConexion(): Error al cerrar el socket", e);
			}
			setEstado(ESTADO_NINGUNO);
		}
	}
	
	// Hilo encargado de mantener la conexion y realizar las lecturas y escrituras
	// de los mensajes intercambiados entre dispositivos.
	private class HiloConexion extends Thread
	{
		private final BluetoothSocket 	socket;			// Socket
		private final InputStream		inputStream;	// Flujo de entrada (lecturas)
		private final OutputStream		outputStream;	// Flujo de salida (escrituras)
		
		public HiloConexion(BluetoothSocket socket)
		{
			debug("HiloConexion.new()", "Iniciando metodo");
			this.socket = socket;
			
			setName(socket.getRemoteDevice().getName() + " [" + socket.getRemoteDevice().getAddress() + "]");
			
			// Se usan variables temporales debido a que los atributos se declaran como final
			// no seria posible asignarles valor posteriormente si fallara esta llamada
			InputStream tmpInputStream = null;
			OutputStream tmpOutputStream = null;
			
			// Obtenemos los flujos de entrada y salida del socket.
			try {
				tmpInputStream = socket.getInputStream();
				tmpOutputStream = socket.getOutputStream();
			} 
			catch(IOException e){
				Log.e(TAG, "HiloConexion(): Error al obtener flujos de E/S", e);
			}
			
			inputStream = tmpInputStream;
			outputStream = tmpOutputStream;
		}
		
		// Metodo principal del hilo, encargado de realizar las lecturas
		public void run()
		{
			debug("HiloConexion.run()", "Iniciando metodo");
			byte[] buffer = new byte[1024];
			int bytes;
			setEstado(ESTADO_CONECTADO);
			// Mientras se mantenga la conexion el hilo se mantiene en espera ocupada
			// leyendo del flujo de entrada
			while(true)
			{
				try {
					// Leemos del flujo de entrada del socket
					bytes = inputStream.read(buffer);
					
					// Enviamos la informacion a la actividad a traves del handler.
					// El metodo handleMessage sera el encargado de recibir el mensaje
					// y mostrar los datos recibidos en el TextView
					handler.obtainMessage(MSG_LEER, bytes, -1, buffer).sendToTarget();
					sleep(500);
				}
				catch(IOException e) {
					Log.e(TAG, "HiloConexion.run(): Error al realizar la lectura", e);		
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		public void escribir(byte[] buffer)
		{
			debug("HiloConexion.escribir()", "Iniciando metodo");
			try {
				// Escribimos en el flujo de salida del socket
				outputStream.write(buffer);
				
				// Enviamos la informacion a la actividad a traves del handler.
				// El metodo handleMessage sera el encargado de recibir el mensaje
				// y mostrar los datos enviados en el Toast
				handler.obtainMessage(MSG_ESCRIBIR, -1, -1, buffer).sendToTarget();
			}
			catch(IOException e) {
				Log.e(TAG, "HiloConexion.escribir(): Error al realizar la escritura", e);
			}
		}
		
		public void cancelarConexion()
		{
			debug("HiloConexion.cancelarConexion()", "Iniciando metodo");
			try {
				// Forzamos el cierre del socket
				socket.close();
				
				// Cambiamos el estado del servicio
				setEstado(ESTADO_NINGUNO);
			}
			catch(IOException e) {
				Log.e(TAG, "HiloConexion.cerrarConexion(): Error al cerrar la conexion", e);
			}
		}
		
	}
	
	public void debug(String metodo, String msg)
	{
		if(DEBUG_MODE)
			Log.d(TAG, metodo + ": " + msg);
	}
	
	private UUID generarUUID()
	{
		ContentResolver appResolver = context.getApplicationContext().getContentResolver();
		String id = Secure.getString(appResolver, Secure.ANDROID_ID);
		final TelephonyManager tManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		final String deviceId = String.valueOf(tManager.getDeviceId());
		final String simSerialNumber = String.valueOf(tManager.getSimSerialNumber());
		final String androidId	= Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);

//		UUID uuid = new UUID(androidId.hashCode(), ((long)deviceId.hashCode() << 32) | simSerialNumber.hashCode());
		UUID uuid = new UUID((long)1000, (long)23);
		return uuid;
	}
}
