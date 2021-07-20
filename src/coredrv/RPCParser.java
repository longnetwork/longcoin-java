package coredrv;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class RPCParser extends RPCHandler { // Работает с ответами
	
	public RPCParser() {super();}
	public RPCParser(int rpcPort, String rpcUser, String rpcPassword) {super(rpcPort, rpcUser, rpcPassword);}
	public RPCParser(String rpcAddress, int rpcPort, String rpcUser, String rpcPassword) { super(rpcAddress, rpcPort, rpcUser, rpcPassword);}
	 
	// ByteBuffer.array() - всегда возвращает исходный массив, 
	// ByteBuffer.position() - смещение среза, 
	// ByteBuffer.remaining() - размер среза,
	// ByteBuffer.limit() - конец среза ( position + remaining )
	

	// "Ключ - Значение" в пределах объекта (в пределах {...}). Кавычки попадают в поля
	public Map<ByteBuffer,ByteBuffer> rawParseObject(final ByteBuffer onbjectSlise) {
		
		final byte[] raw = onbjectSlise.array(); final int offs=onbjectSlise.position(); final int length=onbjectSlise.remaining(); 
		Map<ByteBuffer,ByteBuffer> map=new HashMap<>();
		if (raw==null) return map; // isEmpty()
		
		// FIXME: Отступим в начале и в конце гарантированные скобки {} (внешние команды должны знать когда вернули объект в {})
				
		int ptr=offs; int end=offs+length;
		while(ptr<end) if(raw[ptr++]=='{') break;
		while(end>ptr) if(raw[--end]=='}') break; 
		
		
		int left=ptr; ByteBuffer key=null,val=null;
		
		int brackets=0;				// Чтобы вложенные в значение объекты целиком брал
		
		boolean quotes=false;		// Флаг "внутри кавычек"
		boolean escape=false;       // Флаг esсape символа
		
		while(ptr<end) {
			
			if(!escape) {
				if(raw[ptr]=='\\') {escape=true; ptr++; continue;}
				if(raw[ptr]=='"') {quotes= !quotes; ptr++; continue;}
			}
			
			escape=false;
			
			if(!quotes) {
				if(raw[ptr]=='{' || raw[ptr]=='[') brackets++;
				else if(raw[ptr]=='}' || raw[ptr]==']') brackets--;
				else if(brackets==0) {
					if(raw[ptr]==':') { key=ByteBuffer.wrap(raw,left,ptr-left); left=ptr+1; val=null; } 		// Ключ слева
					else if(raw[ptr]==',') { val=ByteBuffer.wrap(raw,left,ptr-left); left=ptr+1; map.put(key,val); } // Значение справа
				}
			}
			
			ptr++;
		}; 
		if( key!=null && val==null ) { val=ByteBuffer.wrap(raw,left,ptr-left); map.put(key,val); } // В конце запятой нет
		
		
		return map;
	}
	
	public Map<ByteBuffer,ByteBuffer> rawParseObject(final byte[] raw){
		return rawParseObject(ByteBuffer.wrap(raw));
	}
	
	public List<ByteBuffer> rawParseArray(final ByteBuffer arraySlise) {
		final byte[] raw = arraySlise.array(); final int offs=arraySlise.position(); final int length=arraySlise.remaining(); 
		List<ByteBuffer> list=new ArrayList<>();
		if (raw==null) return list; // isEmpty()
		
		// FIXME: Отступим в начале и в конце гарантированные скобки [] (внешние команды должны знать когда вернули массив в [])
		
		int ptr=offs; int end=offs+length;
		while(ptr<end) if(raw[ptr++]=='[') break;
		while(end>ptr) if(raw[--end]==']') break; 
		
		
		int left=ptr;
		
		int brackets=0;	// Чтобы вложенные в значение объекты целиком брал
		
		boolean quotes=false;		// Флаг "внутри кавычек"
		boolean escape=false;		// Флаг esсape символа
		
		while(ptr<end) {
			
			if(!escape) {
				if(raw[ptr]=='\\') {escape=true; ptr++; continue;}
				if(raw[ptr]=='"') {quotes= !quotes; ptr++; continue;}
			}
			
			escape=false;
			
			if(!quotes) {
				if(raw[ptr]=='{' || raw[ptr]=='[') brackets++;
				else if(raw[ptr]=='}' || raw[ptr]==']') brackets--;
				else if(brackets==0) {
					if(raw[ptr]==',') { list.add(ByteBuffer.wrap(raw,left,ptr-left)); left=ptr+1; }
				}
			}
			
			ptr++;
		}; 
		if( ptr>left ) { list.add(ByteBuffer.wrap(raw,left,ptr-left)); } // В конце запятой нет
		
		
		return list;
	}
	public List<ByteBuffer> rawParseArray(final byte[] raw){
		return rawParseArray(ByteBuffer.wrap(raw));
	}
}
