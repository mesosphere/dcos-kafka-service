package com.mesosphere.dcos.kafka.config;

public class KafkaJmxConfigUtils {

    private KafkaJmxConfigUtils() {
        // Do not instantiate.
    }

    private static final String BLANK = " ";
    private static final String EMPTY_STRING = "";


    /**
     * Returns the Java command flags for JMX Settings
     * "-Dcom.sun.management.jmxremote=true"
     *
     * @param jmxConfig a JmxConfig object
     */
    public static String toJavaOpts(JmxConfig jmxConfig) {

        if (jmxConfig == null) {
            return EMPTY_STRING;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("-Dcom.sun.management.jmxremote=");
        sb.append(jmxConfig.isRemote());
        sb.append(BLANK);

        sb.append("-Dcom.sun.management.jmxremote.port=");
        sb.append(jmxConfig.getRemotePort());
        sb.append(BLANK);

        sb.append("-Dcom.sun.management.jmxremote.registry.ssl=");
        sb.append(jmxConfig.isRemoteRegistrySsl());
        sb.append(BLANK);

        sb.append("-Dcom.sun.management.jmxremote.ssl=");
        sb.append(jmxConfig.isRemoteSsl());
        sb.append(BLANK);

        if (jmxConfig.getRemoteSslEnabledProtocols() != null) {
            sb.append("-Dcom.sun.management.jmxremote.ssl.enabled.protocols=");
            sb.append(jmxConfig.getRemoteSslEnabledProtocols());
            sb.append(BLANK);
        }

        if (jmxConfig.getRemoteSslEnabledCipherSuites() != null) {
            sb.append("-Dcom.sun.management.jmxremote.ssl.enabled.cipher.suites=");
            sb.append(jmxConfig.getRemoteSslEnabledCipherSuites());
            sb.append(BLANK);
        }

        sb.append("-Dcom.sun.management.jmxremote.ssl.need.client.auth=");
        sb.append(jmxConfig.isRemoteSslNeedClientAuth());
        sb.append(BLANK);

        sb.append("-Dcom.sun.management.jmxremote.authenticate=");
        sb.append(jmxConfig.isRemoteAuthenticate());
        sb.append(BLANK);

        if (jmxConfig.getRemotePasswordFile() != null) {
            sb.append("-Dcom.sun.management.jmxremote.password.file=");
            sb.append(jmxConfig.getRemotePasswordFile());
            sb.append(BLANK);
        }

        if (jmxConfig.getRemoteAccessFile() != null) {
            sb.append("-Dcom.sun.management.jmxremote.access.file=");
            sb.append(jmxConfig.getRemoteAccessFile());
            sb.append(BLANK);
        }

        if (jmxConfig.getRemoteLoginConfig() != null) {
            sb.append("-Dcom.sun.management.jmxremote.login.config=");
            sb.append(jmxConfig.getRemoteLoginConfig());
            sb.append(BLANK);
        }

        //remove trailing whitespace (if any)
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

}