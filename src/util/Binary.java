package util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


 // Строки внутренне в java всегда хранятся в utf-16 в том числе и суррогатные пары

public final class Binary {
	
	static final int MAX_CODEPOINTS=8; // По самым длинным emoji
	
	static public long longHash(final String str) {
		if(str==null) return 0; // Такой же как от пустой строки
		
		final String str1=str.substring(0,str.length()/2);
		final String str2=str.substring(str.length()/2);
		
		return ((long)str1.hashCode()<<32) + str2.hashCode(); 
	}
	
	
	static public byte[] InputStreamToByteArray(final InputStream in) { // in autoclosable
		if(in==null) return new byte[0];
		try {
	        final int available=in.available();
	        final byte[] chunk = new byte[available+1024]; // FIXME: Загребать можем и огромными кусками для производительности
	        final ByteArrayOutputStream ret=new ByteArrayOutputStream(available); // Cам пухнит когда надо
	        int cnt; while( ( cnt=in.read(chunk) ) != -1 ) ret.write(chunk,0,cnt); ret.flush(); in.close();
	        return ret.toByteArray();
		}
		catch (IOException e) { e.printStackTrace();
			try {in.close();} catch (IOException _e) {_e.printStackTrace();};
			return new byte[0];
		}
	}
	

	static public String utf8BytesToString(final byte [] bytes) {
		return new String(bytes, StandardCharsets.UTF_8);
	}
	static public byte[] stringToUtf8Bytes(final String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}	
	
	static public byte[] hexStringToByteArray(final String s) { // FIXME: можно улучшить производительность
	    final int len = s.length(); final byte[] data = new byte[ (len+1) / 2];
	    for (int i=0, j=0; i < len; ) {
	    	final int Hi=Character.digit(s.charAt(i++), 16); if(Hi<0) continue; // откручивается на начало hex
	    	final int Lo=Character.digit(s.charAt(i++), 16); if(Lo<0) continue; // и пропускает не hex
	    	data[j++] = (byte) ((Hi << 4) + Lo);
	    }
	    return data;
	}
	static public String byteArrayToHexString(final byte [] bytes) {
		final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
		final char[] hexChars = new char[bytes.length * 2];
		for ( int i = 0; i < bytes.length; i++ ) {
		    int b = 0xFF & bytes[i];
		    hexChars[i*2] = hexArray[b/16];
		    hexChars[i*2 + 1] = hexArray[b%16];
		}
		return new String(hexChars);
	}

	static public String unescape(final String s) {  if (s == null) return null; // Работает также с мульти-байтовой формой \\u00xx ...
        
		final byte[] in=s.getBytes(StandardCharsets.UTF_8); // Берем байты в последовательности utf-8
		
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length); // максимум
        
        for(int i=0; i<in.length; i++) {
            final byte b = in[i];
           
            try {
	            if(b=='\\') { // Подозрение на escape
	            	final byte n = in[i+1];
	            	
	            	if(n=='u') // далее должно быть два байта в hex
		                try{          
		                    final char c= (char)Integer.parseInt(new String(in,i+2,4,StandardCharsets.UTF_8),16);
		                    final byte b01=(byte)(0xFF&(c>>>8)), b00=(byte)(0xFF&(c>>>0));
		                    
		                    if(b01==0) out.write(b00); // Заэскапенная последовательность байт utf-8 вида \\u00xx ...
		                    else {
		                    	for(final byte _b: Character.toString(c).getBytes(StandardCharsets.UTF_8)) out.write(_b); // заэскапенный нормальный char (utf-16)
		                    }
		                    
		                    i+=5;
		                }
		                catch(NumberFormatException | IndexOutOfBoundsException e1) {out.write('u'); i+=1;}
	            	else switch(n) {
	            		case 'b':
	            			out.write('\b'); i+=1;
	            			break;
	            		case 'n':
	            			out.write('\n'); i+=1;
	            			break;
	            		case 't':
	            			out.write('\t'); i+=1;
	            			break;
	            		case 'f':
	            			out.write('\f'); i+=1;
	            			break;
	            		case 'r':
	            			out.write('\r'); i+=1;
	            			break;
	            		default:
	            			out.write(n); i+=1;
	            			break;
	            	}
	            }
	            else out.write(b);
            }
            catch(ArrayIndexOutOfBoundsException e) {
            	out.write(b);
            }
            
        }
        
        return out.toString(StandardCharsets.UTF_8); // Готовили как utf-8
	}
	
	
	static public String escape_script(final String str) { if (str == null) return null;
        
        StringBuilder out = new StringBuilder(str.length()); // как минимум
        
        final int size=str.length();
        for (int i = 0; i < size; i++) {
            char ch = str.charAt(i);
            
            // handle unicode
            if (ch > 0x7f) {
            	out.append("\\u").append(Integer.toHexString(0x10000 | ch).substring(1));
            } else if (ch < 32) {
                switch (ch) {
                    case '\b':
                        out.append('\\').append('b');
                        break;
                    case '\n':
                        out.append('\\').append('n');
                        break;
                    case '\t':
                        out.append('\\').append('t');
                        break;
                    case '\f':
                        out.append('\\').append('f');
                        break;
                    case '\r':
                        out.append('\\').append('r');
                        break;
                    default:
                    	out.append("\\u").append(Integer.toHexString(0x10000 | ch).substring(1));
                        break;
                }
            } else {
                switch (ch) {
                    case '\'':
                    case '/':                  
                    case '`':
                    case '"':
                    case '\\':
                    	out.append('\\').append(ch);
                        break;
                    default:
                        out.append(ch);
                        break;
                }
            }
        }
        
        return out.toString();
    }

	static public String escape_json(final String str) { if (str == null) return null;
	
	    StringBuilder out = new StringBuilder(str.length()); // как минимум
	    
	    final int size=str.length();
	    for (int i = 0; i < size; i++) {
	        char ch = str.charAt(i);
	        
	        // handle unicode
	        if (ch > 0x7f) {
	        	out.append("\\u").append(Integer.toHexString(0x10000 | ch).substring(1));
	        } else if (ch < 32) {
	            switch (ch) {
	                case '\b':
	                    out.append('\\').append('b');
	                    break;
	                case '\n':
	                    out.append('\\').append('n');
	                    break;
	                case '\t':
	                    out.append('\\').append('t');
	                    break;
	                case '\f':
	                    out.append('\\').append('f');
	                    break;
	                case '\r':
	                    out.append('\\').append('r');
	                    break;
	                default:
	                	out.append("\\u").append(Integer.toHexString(0x10000 | ch).substring(1));
	                    break;
	            }
	        } else {
	            switch (ch) {
	                case '"':
	                case '\\':
	                	out.append('\\').append(ch);
	                    break;
	                default:
	                    out.append(ch);
	                    break;
	            }
	        }
	    }
	    
	    return out.toString();
	}
	
	
	
	static public String codePointsToHex(String str) { // 1F46E-1F3FB-200D-2640-FE0F
		if(str==null) return null;
		
		final int[] codePoints = str.codePoints().toArray();
			
		String hex= (codePoints[0]>0xFFFF || codePoints[0]<0) ? 
					       Integer.toHexString(codePoints[0]) : 
					    	   Integer.toHexString(0x10000 | codePoints[0]).substring(1); // Ведушие нули для кодов в 4-ре 16-тиричных знака 
		
		final StringBuilder sb = new StringBuilder(hex.toUpperCase());
		
		for(int i=1; i<codePoints.length; i++)  {
			hex= (codePoints[i]>0xFFFF || codePoints[i]<0) ? 
				      	Integer.toHexString(codePoints[i]) : 
				      		Integer.toHexString(0x10000 | codePoints[i]).substring(1); 					
			sb.append("-").append(hex.toUpperCase());
		}
		
		return sb.toString();
	}
	static public String hexToCodePoints(String str) { // 1F468-200D-2764-FE0F-200D-1F48B-200D-1F468
		if(str==null) return null;
		
		String[] codePoints=str.split("-",MAX_CODEPOINTS);
		
		final StringBuilder sb=new StringBuilder();
		
		try {
			for(String hex: codePoints) sb.appendCodePoint(Integer.parseInt(hex, 16));
		}
		catch(NumberFormatException e) {
			return null;
		}
		
		return sb.toString();
	}
	
	
	
	static private boolean findSignature(final String s, final String[] signatures, final int offsetInBytes) {
		try {
		L1:	for(final String sig: signatures) {
				for(int i=0; i<sig.length(); i++) 
					if(Character.toUpperCase(s.charAt(i+offsetInBytes*2)) != sig.charAt(i)) continue L1;
				return true;
			}
			return false;
		}
		catch(IndexOutOfBoundsException e) {
			return false;
		}
	}
	static private boolean findSignature(final byte[] data, final String[] signatures, final int offsetInBytes) {
		try {
		L1:	for(final String sig: signatures) { final byte[] sigBytes= hexStringToByteArray(sig); // FIXME: Надеюсь движок поймет что это константы
				for(int i=0; i<sigBytes.length; i++) 
					if(data[i+offsetInBytes]!=sigBytes[i]) continue L1;	
				return true;
			}
			return false;
		}
		catch(ArrayIndexOutOfBoundsException e) {
			return false;
		}
	}
	
	static private final String[] signaturePNG= {"89504E470D0A1A0A"};
		static public boolean isPNG(final String s) {return findSignature(s,signaturePNG,0);}
		static public boolean isPNG(final byte[] data) {return findSignature(data,signaturePNG,0);}
			
	static private final String[] signatureGIF= {"474946383761","474946383961"}; // gif87, gif89
		static public boolean isGIF(final String s) {return findSignature(s,signatureGIF,0);}
		static public boolean isGIF(final byte[] data) {return findSignature(data,signatureGIF,0);}			
	
	static private final String[] signatureJPG= {"FFD8FFDB","FFD8FFE0","FFD8FFE1"}; // jpg, jpg0, jpg1
		static public boolean isJPG(final String s) {return findSignature(s,signatureJPG,0);}
		static public boolean isJPG(final byte[] data) {return findSignature(data,signatureJPG,0);}	
	
	static public final String[] signatureMP3= {"494433"};
		static public boolean isMP3(final String s) {return findSignature(s,signatureMP3,0);}
		static public boolean isMP3(final byte[] data) {return findSignature(data,signatureMP3,0);}		
	
	static public final String[] signatureMPEG= {"FFFB","FFF3","FFF2", "FFE3"}; // FIXME FFE3 - взялось из тестовых data: в base64"
		static public boolean isMPEG(final String s) {return findSignature(s,signatureMPEG,0);}
		static public boolean isMPEG(final byte[] data) {return findSignature(data,signatureMPEG,0);}	
	
	static public final String[] signatureMP4= {"6674797069736F6D","667479704D534E56"}; // offset 4 bytes
		static public boolean isMP4(final String s) {return findSignature(s,signatureMP4,4);}
		static public boolean isMP4(final byte[] data) {return findSignature(data,signatureMP4,4);}		
		
	// TODO wav|mp3|mpeg|m4a|aac|mp4)
	
}
