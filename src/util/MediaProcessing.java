package util;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.imageio.*;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.*;

public final class MediaProcessing {
	
	static final String TMPFILE_PREFIX=MediaProcessing.class.getSimpleName();
	
	static final int MIN_MEDIA_SIZE=35; // 1x1 gif white or black
	
	static final int PROSESS_TIMEOUT=30;	// sec
	static final int MAX_ITERATION=10; 		// Попыток ужатия одного файла

	static final int MAX_COMPRESSION_RATIO=128;
	
	
	static final int START_BITRATE=96; static final int MIN_BITRATE=1; // k
	
	public static File voiceCompressing(File ifile, final long desiredSize) throws Exception {
		
		long isize=ifile.length();
		
		if(isize<MIN_MEDIA_SIZE) throw new RuntimeException("Voice size is too small");
		
		if(isize>desiredSize*MAX_COMPRESSION_RATIO) throw new RuntimeException("Voice size is too big");
		
		if(isize<=desiredSize) return ifile;
		
		String ffmpeg="ffmpeg";
		
		String OSName = System.getProperty("os.name").toLowerCase();
		if (OSName.indexOf("win") >= 0) ffmpeg="ffmpeg.exe"; 
//		else if (OSName.indexOf("mac") >= 0) ffmpeg="ffmpeg";
		else if (OSName.indexOf("nix") >= 0 || OSName.indexOf("nux") >= 0 || OSName.indexOf("aix") > 0 ) ffmpeg="ffmpeg";
//		else if (OSName.indexOf("sunos") >= 0) ffmpeg="ffmpeg";
		else throw new RuntimeException("Unknow OS");
		
		final int ar[]= {44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000}; // Допустимые значения в опции -ar
		
		// ￮ - разделитель команд с кодом 65518 (в именах фалах не должен быть или маловероятен, в отличии от пробела)
		ffmpeg+="￮-nostdin￮-y￮-hide_banner￮-loglevel￮error￮-i￮%s￮-movflags￮faststart￮-ac￮1￮-acodec￮libmp3lame￮-b:a￮%dk￮-ar￮%d￮-vn￮%s";
		
		File ofile= Files.createTempFile(TMPFILE_PREFIX, ".mp3").toFile(); // TMPFILE_PREFIXУникальныйКодПоВыборуСистемы.расширение
			ofile.deleteOnExit();
		
		
		double k=1 + 0*Math.sqrt((double)desiredSize/isize); // Фактор ужатия
		
		double bitRate=START_BITRATE;
		double sampleRate=ar[0];
		
		
		int cnt=MAX_ITERATION;
		
		do{
			int sr=(int)sampleRate; for(int r: ar) {sr=r; if(sampleRate>=(sr-Double.MIN_VALUE)) break;} // sr имеет минимум на последнем элементе ar[]
			
			String cmd=String.format(ffmpeg,ifile.getAbsolutePath(), (int)bitRate,sr, ofile.getAbsolutePath());
			
			ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(Arrays.asList(cmd.split("￮")))).inheritIO();
			
			LOGGER.info(pb.command().toString()); // TODO debug
			
			Process proc = pb.start();
	        	 
			boolean completed = proc.waitFor(PROSESS_TIMEOUT, TimeUnit.SECONDS);
			if(!completed || proc.exitValue()!=0) {
				proc.destroyForcibly();
				throw new RuntimeException("ffmpeg process error");
			}
	             
			long osize=ofile.length();
	            
	        LOGGER.console(String.format("Voice compressing: new size=%d (k=%.4f br=%dk sr=%d)", osize, k, (int)bitRate,  sr)); // TODO debug
			
            if(osize<=desiredSize && osize>=MIN_MEDIA_SIZE) return ofile; // Что делать с результатом во временном файле определяет внешний код
            if(osize<MIN_MEDIA_SIZE || (--cnt<=0) ) throw new RuntimeException("Voice is not compressed to the desired size");
            
            // готовим новую итерацию
            
            ofile.delete(); ofile.createNewFile(); ofile.deleteOnExit();	
            
            k= Math.sqrt((double)desiredSize/(osize));
            
            bitRate=bitRate*k-0; if(bitRate<MIN_BITRATE) bitRate=MIN_BITRATE;
         
            sampleRate=sampleRate*k; 
            
		}
		while(true);
		
	}	
	

	static final int START_CRF=18; 	static final int MAX_CRF=50;
	
	public static File AnimationCompressing(File ifile, final long desiredSize) throws Exception {
		
		long isize=ifile.length();
		
		if(isize<MIN_MEDIA_SIZE) throw new RuntimeException("Animation size is too small");
		
		if(isize>desiredSize*MAX_COMPRESSION_RATIO) throw new RuntimeException("Animation size is too big");
		
		if(isize<=desiredSize) return ifile;
		
		String ffmpeg="ffmpeg";
		
		String OSName = System.getProperty("os.name").toLowerCase();
		if (OSName.indexOf("win") >= 0) ffmpeg="ffmpeg.exe"; 
//		else if (OSName.indexOf("mac") >= 0) ffmpeg="ffmpeg";
		else if (OSName.indexOf("nix") >= 0 || OSName.indexOf("nux") >= 0 || OSName.indexOf("aix") > 0 ) ffmpeg="ffmpeg";
//		else if (OSName.indexOf("sunos") >= 0) ffmpeg="ffmpeg";
		else throw new RuntimeException("Unknow OS");
		
		ffmpeg+="￮-nostdin￮-y￮-hide_banner￮-loglevel￮error￮-i￮%s￮-movflags￮faststart￮-pix_fmt￮yuv420p￮-vf￮scale=trunc(iw*%.4f)*2:trunc(ih*%.4f)*2￮-crf￮%d￮-an￮%s";
		
		File ofile= Files.createTempFile(TMPFILE_PREFIX, ".mp4").toFile(); // TMPFILE_PREFIXУникальныйКодПоВыборуСистемы.расширение
			ofile.deleteOnExit();
		
		
		double k= Math.cbrt((double)desiredSize/isize); // Фактор ужатия
		
		double crf=START_CRF; // увеличение приводит к снижению битрэйта 
						   // в доках говорится об экспоненте такой, что +6 к crf уменьшает битрейт в двое 
						   // (основание экспоненты 0.5^(1/6)
						   // вероятно коррекция для фактора ужатия k: crf = crf - 6*log2(k), k<1
		int cnt=MAX_ITERATION;
		
		do{
			
			String cmd=String.format(Locale.ROOT,ffmpeg,ifile.getAbsolutePath(),k,k,(int)crf,ofile.getAbsolutePath()); // Locale.ROOT - точка десятичный разделитель
			
			ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(Arrays.asList(cmd.split("￮")))).inheritIO();
			
			LOGGER.info(pb.command().toString()); // TODO debug
			
			Process proc = pb.start();
	        	 
			boolean completed = proc.waitFor(PROSESS_TIMEOUT, TimeUnit.SECONDS);
			if(!completed || proc.exitValue()!=0) {
				proc.destroyForcibly();
				throw new RuntimeException("ffmpeg process error");
			}
	             
			long osize=ofile.length();
	            
	        LOGGER.console(String.format("Animation compressing: new size=%d (k=%.4f crf=%d)", osize,  k,  (int)crf)); // TODO debug
			
            if(osize<=desiredSize && osize>=MIN_MEDIA_SIZE) return ofile; // Что делать с результатом во временном файле определяет внешний код
            if(osize<MIN_MEDIA_SIZE || (--cnt<=0) ) throw new RuntimeException("Animation is not compressed to the desired size");
            
            // готовим новую итерацию
            
            ofile.delete(); ofile.createNewFile(); ofile.deleteOnExit();	
            
            k= k*Math.cbrt((double)desiredSize/(osize));
            
            //crf= (crf-6*Math.log(k)/Math.log(2d)); if(crf>MAX_CRF) crf=MAX_CRF;
            crf= (crf-6/10.5d*Math.log(k)/Math.log(2d)); if(crf>MAX_CRF) crf=MAX_CRF;
            
		}
		while(true);
		
	}
	

	static final float START_QUALITY=0.86f; // Стартовое качество после которого начинается отличие на глаз и линейное снижение размера файла 
	static final double ROBUST=1.025;      // Для ускорения сходимости итераций (как бы процент завышения результирующего размера)
	
	public static File ImageCompressing(File ifile, final long desiredSize) throws Exception {
		
		long isize=ifile.length();
		
		if(isize<MIN_MEDIA_SIZE) throw new RuntimeException("Picture size is too small");
		
		if(isize>desiredSize*MAX_COMPRESSION_RATIO) throw new RuntimeException("Picture size is too big");
		
		if(isize<=desiredSize) return ifile;
		
		
		ImageInputStream istream= new FileImageInputStream(ifile);
		
		BufferedImage iimage = ImageIO.read(istream);
		
		int iheight=iimage.getHeight();
		int iwidth=iimage.getWidth();
		
				
		File ofile= Files.createTempFile(TMPFILE_PREFIX, ".jpg").toFile(); // TMPFILE_PREFIXУникальныйКодПоВыборуСистемы.расширение
			ofile.deleteOnExit();
		
		ImageOutputStream ostream=null;
			
			double k= Math.cbrt((double)desiredSize/isize); // Фактор ужатия
			
			int owidth= (int)(iwidth*1); //int owidth= (int)(iwidth*k);
			int oheight= (int)(iheight*1); //int oheight= (int)(iheight*k);
			float quality=START_QUALITY;
			int cnt=MAX_ITERATION;
			
			do {
				BufferedImage oimage = new BufferedImage(owidth,oheight,BufferedImage.TYPE_INT_RGB); // TYPE_INT_RGB TYPE_3BYTE_BGR TYPE_USHORT_565_RGB
		        Graphics2D o2d=oimage.createGraphics();
		            o2d.drawImage(iimage,0,0,owidth,oheight,null); o2d.dispose();
			
		        ostream= new FileImageOutputStream(ofile);    
		            
	            ImageWriter owriter = ImageIO.getImageWritersByFormatName("jpg").next();
	            	owriter.setOutput(ostream);
	            	
	            JPEGImageWriteParam params = new JPEGImageWriteParam(Locale.getDefault());
	            	params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
	            	params.setCompressionQuality(quality);
	            	
	            owriter.write(null, new IIOImage(oimage, null, null), params);
	            
	            ostream.flush(); ostream.close(); owriter.dispose();
	
	            long osize=ofile.length();
	            
	            LOGGER.console(String.format("Image compressing: new size=%d (%dx%d %f)", osize,  owidth,oheight,  quality)); // TODO debug
	            
	            if(osize<=desiredSize && osize>=MIN_MEDIA_SIZE) return ofile; // Что делать с результатом во временном файле определяет внешний код
	            if(osize<MIN_MEDIA_SIZE || (--cnt<=0) ) throw new RuntimeException("Picture is not compressed to the desired size");
	            
	            // готовим новую итерацию
	            
	            ofile.delete(); ofile.createNewFile(); ofile.deleteOnExit();
	            
	            k= Math.cbrt((double)desiredSize/(osize*ROBUST)); // Чтобы в точках близких к desiredSize повысить сходимость
	            
	            owidth= (int)(owidth*k); // Как минимум на 1-цу меньше
	            oheight= (int)(oheight*k);
	            quality= (float)(quality*k*Math.cbrt(ROBUST)); // Чтобы на quality ROBUST не особо влиял
		
			}
			while(true);	
	}
	 

	
}
