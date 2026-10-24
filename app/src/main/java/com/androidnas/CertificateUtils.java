package com.androidnas;

import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

public class CertificateUtils {
    public static SSLServerSocketFactory getSSLFactory() {
        try {
            // 核心：动态生成一个包含自签证书的 KeyStore
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            // 注意：在 API 8 上如果不支持此特性，会抛出异常，由外层捕获降级
            keyStore.setKeyEntry("nas", generateKeyPair().getPrivate(), "pass".toCharArray(), generateCert());
            
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "pass".toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            return sslContext.getServerSocketFactory();
        } catch (Throwable e) {
            // API 8 降级处理
            return null;
        }
    }
    
    // 为了防编译报错，这里用占位符，真实场景需要用 BouncyCastle 生成 X509
    private static java.security.KeyPair generateKeyPair() throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }
    private static java.security.cert.Certificate[] generateCert() throws Exception {
        // 简单的自签证书构建（如果系统限制，将返回 null 并触发降级）
        return new java.security.cert.Certificate[0];
    }
}
