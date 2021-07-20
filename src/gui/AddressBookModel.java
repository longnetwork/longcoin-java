package gui;


import java.util.Map;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;


public class AddressBookModel {
	
	///////////////////////////////////////////////////// Это свойства для View ///////////////////////////////////////////////////////////
	// Записывать в свойства стоит только если есть изменения, иначе это постоянная перерисовка (для биндингов)
	// авто-биндинг свойств работаете через рефлексию (ебучию). При создании указывать строку с именем свойства, 
	// и создавать метод с суффиксом ...Property (также при наличии рефлексии вроде можно в стилях сослаться на имена свойств)
	
	private StringProperty account;
		public StringProperty accountProperty() { return account; } 		  // Суффикс ...Property
	 	public void setAccount(String str) { account.set(str); }
	    public String getAccount() { return account.get(); }
		    public void setAccount(final Map<String,String> rec) { // rec - Распарсеный JSON-объект одной записи
		    	this.setAccount(rec.getOrDefault("account", ""));
		    }
		    
    private StringProperty address;
	    public StringProperty addressProperty() { return address; }
	    public void setAddress(String str) { address.set(str); }
	    public String getAddress() { return address.get(); }
		    public void setAddress(final Map<String,String> rec) {
		    	this.setAddress(rec.getOrDefault("address",""));
		    }	    
		    
	private StringProperty type;
	    public StringProperty typeProperty() { return type; }
	    public void setType(String str) { type.set(str); }
	    public String getType() { return type.get(); }
		    public void setType(final Map<String,String> rec) {

		    	if(rec.getOrDefault("ismine","").equals("true")) this.setType("ismine");
		    	else if(rec.getOrDefault("iswatchonly","").equals("true")) this.setType("iswatched");
		    	else this.setType("isother");
		    	
		    }
		    
	    	
	public AddressBookModel() {
		account=new SimpleStringProperty(this,"account",""); // Экспорт имен свойств для доступа через PropertyReference<>()
		address=new SimpleStringProperty(this,"address",""); // если потребуется рефлексия
		type=new SimpleStringProperty(""); // getType() != null всегда
	}
	private Map<String,String> rec=null;
	public AddressBookModel(final Map<String,String> rec) { this(); this.rec=rec;
		if(rec.getOrDefault("isvalid", "").equals("true")) {
			this.setAccount(rec);
			this.setAddress(rec);
			this.setType(rec);
		}
	}
	public AddressBookModel(String address, String pubkey) { // генерация минимальной записи из строк без проверки валидности
															 // (нужно для работы с не сохраняемыми адресами из сообщений)
		this();
		rec=Map.of(
				"address", address,
				"ismine", "false",
				"iswatchonly","false",
				"isscript", (address.startsWith("3")) ? "true" : "false",
				"pubkey", (pubkey!=null && (pubkey.startsWith("02") || pubkey.startsWith("03") || pubkey.startsWith("04"))) ? pubkey : "",
				"addresses", "",
				"multisigs", ""
		);
		this.setAccount(rec);
		this.setAddress(rec);
		this.setType(rec);		
	}
	
	// Свойства имеет смысл создавать только для того, что используется при отображении. Для остального - достаточно методов
	public String getPubKey() { return this.rec!=null ? this.rec.getOrDefault("pubkey","") : ""; }
	public void setPubKey(String pubkey) { 
		if(this.rec!=null) this.rec.put("pubkey", (pubkey!=null && (pubkey.startsWith("02") || pubkey.startsWith("03") || pubkey.startsWith("04"))) ? pubkey : "" ); 
	}
	
	public String getPrivKey() { return this.rec!=null ? this.rec.getOrDefault("privkey","") : ""; }
	
	// p2sh адрес ( начинается на 3 )
	public boolean isScript() { return this.rec!=null && ! this.rec.getOrDefault("isscript","false").equals("false"); }
	
	public boolean isMine() { return this.rec!=null && ! this.rec.getOrDefault("ismine","false").equals("false"); }
	
	// p2sh адреса в котором замешан данный или null ( 3...,3...,... )
	public String getMultisigs() { return this.rec!=null ?  this.rec.get("multisigs") : null; }
	
	// массив адресов из которых сформирован p2sh адрес ( 1...,1...,... )
	public String getAddresses() { return this.rec!=null ?  this.rec.get("addresses") : null; }
	
	
	
	@Override public boolean equals(Object obj) { // Переопределяется всегда в паре с hashCode
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		AddressBookModel model = (AddressBookModel) obj;
		
		// null != "" , поэтому сравнение пустого свойства с rec гарантировано вернет false
		
		if(this.rec==null && model.rec==null) return true; 
			if(this.rec==null) return false; 
			if(model.rec==null) return false;
		
		if(!this.rec.getOrDefault("isvalid","").equals("true") && !model.rec.getOrDefault("isvalid","").equals("true")) return true;
			if(!this.rec.getOrDefault("isvalid","").equals("true")) return false;
			if(!model.rec.getOrDefault("isvalid","").equals("true")) return false;
			
		
		if( ! this.getAccount().equals(model.getAccount()) ) return false; // Нужно брать редактируемое поле для сравнения 
		
		if( ! this.rec.getOrDefault("address","").equals(model.rec.getOrDefault("address","")) ) return false;
		if( ! this.rec.getOrDefault("ismine","").equals(model.rec.getOrDefault("ismine","")) ) return false;
		if( ! this.rec.getOrDefault("iswatchonly","").equals(model.rec.getOrDefault("iswatchonly","")) ) return false;
		
		if( ! this.rec.getOrDefault("pubkey","").equals(model.rec.getOrDefault("pubkey","")) ) return false; // Влияет на подсветку адреса
		if( ! this.rec.getOrDefault("multisigs","").equals(model.rec.getOrDefault("multisigs","")) ) return false; // Влияет на подсветку адреса
		
		return true; 
	}
	@Override public int hashCode() { 
		// для equals объектов hashCode обязан давать одно и тоже значение, и максимально вероятное разное значения для не equals объектов
		// теоретически hashCode должно быть много быстрее equals, и только когда hashCode равны прибегают к equals для уточнения
		// то есть hashCode - быстрая проверка на НЕ равенство объектов, если hashCode НЕ равны, то объекты точно НЕ равны, а 
		// если hashCode равны, то нужно уточнить через equals
		
	    final int prime = 31; int result = 0;
	    if(this.rec==null) return result;
	    
	    if(!this.rec.getOrDefault("isvalid","").equals("true")) return result;
	    
	    
	    result=result*prime + this.getAccount().hashCode();   // Нужно брать редактируемое поле
	    
	    result=result*prime + this.rec.getOrDefault("address","").hashCode(); // "".hashCode==0 , " ".hashCode==32
	    result=result*prime + this.rec.getOrDefault("ismine","").hashCode();
	    result=result*prime + this.rec.getOrDefault("iswatchonly","").hashCode();
	    
	    result=result*prime + this.rec.getOrDefault("pubkey","").hashCode();
	    result=result*prime + this.rec.getOrDefault("multisigs","").hashCode();
	    
	    return result;
    }
	
	
	
	public boolean filter(final String flt) {
		if(flt==null || flt.isEmpty()) return true; // Если фильтр не установлен, то проходит
		
		final String[] flist= flt.split("\n"); 	   // Объединение фильтров через разделитель строк
		
		for(String f: flist) { // После split всегда как минимум есть 1 элемент даже для пустой строки на входе
			
			if(f.isEmpty()) continue;
			
			if(this.getType().equals(f)) return true;
		}
		
		return false; // Ничего под установленный фильтр не подошло
	}
	
	@Override public String toString() {
		
		final StringBuilder sb=new StringBuilder(); 
		
		if(rec!=null) {
			for (Map.Entry<String, String> entry : rec.entrySet()) {
				String key=entry.getKey(), value=entry.getValue(); value= (value!=null) ? value: "";
				sb.append(key+": "+value+"\n");
			}
		}
		
		return sb.toString();
	}
	
}
