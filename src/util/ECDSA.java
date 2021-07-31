package util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import static util.Binary.hexStringToByteArray;
import static util.Binary.byteArrayToHexString;

public final class ECDSA {


    public static PrivateKey hexStringToPrivateKey(final String key) throws Exception {
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(hexStringToByteArray(key)));
    }
    public static String privateKeyToHexString(PrivateKey key) {
    	return byteArrayToHexString(key.getEncoded());
    }
    public static PublicKey hexStringToPublicKey(final String key) throws Exception {
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(hexStringToByteArray(key)));
    }
    public static String publicKeyToHexString(PublicKey key) {
    	return byteArrayToHexString(key.getEncoded());
    }    
    
    public static String sign(PrivateKey privateKey, String data) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] sign = signature.sign();
        return byteArrayToHexString(sign);

    }
 
    public static boolean verify(PublicKey publicKey, String signed, String data) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] hex = hexStringToByteArray(signed);
        boolean bool = signature.verify(hex);
        return bool;
    }
    
    public static KeyPair genKeyPair() throws Exception {
    	 
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
        KeyPairGenerator kf = KeyPairGenerator.getInstance("EC");
        kf.initialize(ecSpec, new SecureRandom());
        KeyPair keyPair = kf.generateKeyPair();
        return keyPair; // keyPair.getPublic(); keyPair.getPrivate(); 
    }
    

}
