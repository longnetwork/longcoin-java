package coredrv;


import util.LOGGER;
import static util.Binary.stringToUtf8Bytes;
import static util.Binary.InputStreamToByteArray;

import java.util.Base64;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Для работы в отдельном потоке создавать свой объект RPCHandler и херачить запросы можно хуеву тучу
public class RPCHandler {
	static Integer sId = 0; // Уникальные номера для каждых объектов RPCHandler (инкремент синхронизирован между потоками)
	final int id;
	
	final String rpcURL; final String rpcAuth;
	
	public RPCHandler() {this(8878,"user","password");}
	public RPCHandler(int rpcPort, String rpcUser, String rpcPassword) {this("127.0.0.1",rpcPort,rpcUser,rpcPassword);}
	public RPCHandler(String rpcAddress, int rpcPort, String rpcUser, String rpcPassword) {
		this.rpcURL="http://"+rpcAddress+":"+rpcPort;
		this.rpcAuth="Basic " + Base64.getEncoder().encodeToString( stringToUtf8Bytes(rpcUser + ":" + rpcPassword) ); 
	
		synchronized(RPCHandler.sId) {this.id=++RPCHandler.sId;}
		
		LOGGER.info("Using RPC "+rpcUser+":"+rpcPassword+"@"+rpcAddress+":"+rpcPort);
	}
	
	public static class Response { // Для возврата за раз данных и кода завершения 
		public byte[] data=new byte[0]; 
		public int code=0;
	} 
	// Успех, когда rawRequest().data.length>0 и нах исключения
	public synchronized Response rawRequest(final byte req[]) { // FIXME: Каждый раз создаем запрос с заголовком, получаем ответ и соединение закрывается
		// Из разных потоков и экземпляров объекта обращения к одному и тому-же RPC не конфликтуют. Ответы приходят асинхронно и корректно (протестировано ЗБС)
		
		final Response res=new Response();
		
		URL url=null; HttpURLConnection urlCon=null; OutputStream out=null;
		try {
			url = new URL(this.rpcURL); urlCon = (HttpURLConnection) url.openConnection();
	        urlCon.setRequestMethod("POST"); urlCon.setDoOutput(true); urlCon.setDoInput(true); urlCon.setUseCaches(false);
	        //urlCon.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
	        urlCon.setRequestProperty("Content-Type", "application/json; charset=utf-8");
	        urlCon.setRequestProperty ("Authorization", this.rpcAuth);
	        
	        out = urlCon.getOutputStream(); out.write(req); out.flush(); out.close();
	        
	        res.data=InputStreamToByteArray(urlCon.getInputStream());  res.code=urlCon.getResponseCode();
	       
	        //LOGGER.console("RPC request size="+res.data.length); // TODO debug
	        
			return res; 	// и Пиздос (1 байт - 1 символ в JSON формате) 
		} catch (IOException e) {
			if(out!=null) { // Выше дошли до отправки запроса, но сервер ответил в urlCon.getErrorStream()
				res.data=InputStreamToByteArray(urlCon.getErrorStream()); 
				try{
					res.code=urlCon.getResponseCode(); // Если нет подключения или блокчейн занят (HttpURLConnection.HTTP_INTERNAL_ERROR (500))
													   // В res.data может быть более детальная информация (Loading block index...)
				} catch (IOException _e) { res.code=-1; }
				
				LOGGER.warning(e.toString());	// XXX e.getMessage() не информативно
			}
			else { res.code=-1;						// В соединении отказано (Connection refused) 
				LOGGER.error(e.toString());		// когда нет rpc сервера на заданном порте и url.openConnection() выпал в исключение
			}
			
			return res;
		}	
	}
}


