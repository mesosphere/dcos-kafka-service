package com.mesosphere.dcos.kafka.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JmxConfig contains the configuration for the JVM jmx props for a Kafka
 * broker.
 * 
 * http://docs.oracle.com/javase/7/docs/technotes/guides/management/agent.html
 */
public class JmxConfig {
    @JsonProperty("remote")
    private boolean remote = false;

    @JsonProperty("remote_port")
    private int remotePort = -1;

    @JsonProperty("remote_registry_ssl")
    private boolean remoteRegistrySsl = false;

    @JsonProperty("remote_ssl")
    private boolean remoteSsl = false;

    @JsonProperty("remote_ssl_enabled_protocols")
    private String remoteSslEnabledProtocols;

    @JsonProperty("remote_ssl_enabled_cipher_suites")
    private String remoteSslEnabledCipherSuites;

    @JsonProperty("remote_ssl_need_client_auth")
    private boolean remoteSslNeedClientAuth = false;

    @JsonProperty("remote_authenticate")
    private boolean remoteAuthenticate = false;

    @JsonProperty("remote_password_file")
    private String remotePasswordFile;

    @JsonProperty("remote_access_file")
    private String remoteAccessFile;

    @JsonProperty("remote_login_config")
    private String remoteLoginConfig;

    public JmxConfig() {

    }

    public JmxConfig(boolean remote, int remotePort, boolean remoteSsl, boolean remoteAuthenticate) {
        super();
        this.remote = remote;
        this.remotePort = remotePort;
        this.remoteSsl = remoteSsl;
        this.remoteAuthenticate = remoteAuthenticate;
    }

    @JsonIgnore
    public boolean isRemote() {
        return remote;
    }

    @JsonProperty("remote")
    public void setRemote(final boolean remote) {
        this.remote = remote;
    }

    public int getRemotePort() {
        return remotePort;
    }

    @JsonProperty("remote_port")
    public void setRemotePort(final int remotePort) {
        this.remotePort = remotePort;
    }

    @JsonIgnore
    public boolean isRemoteRegistrySsl() {
        return remoteRegistrySsl;
    }

    @JsonProperty("remote_registry_ssl")
    public void setRemoteRegistrySsl(final boolean remoteRegistrySsl) {
        this.remoteRegistrySsl = remoteRegistrySsl;
    }

    @JsonIgnore
    public boolean isRemoteSsl() {
        return remoteSsl;
    }

    @JsonProperty("remote_ssl")
    public void setRemoteSsl(final boolean remoteSsl) {
        this.remoteSsl = remoteSsl;
    }

    public String getRemoteSslEnabledProtocols() {
        return remoteSslEnabledProtocols;
    }

    @JsonProperty("remote_ssl_enabled_protocols")
    public void setRemoteSslEnabledProtocols(final String remoteSslEnabledProtocols) {
        this.remoteSslEnabledProtocols = remoteSslEnabledProtocols;
    }

    public String getRemoteSslEnabledCipherSuites() {
        return remoteSslEnabledCipherSuites;
    }

    @JsonProperty("remote_ssl_enabled_cipher_suites")
    public void setRemoteSslEnabledCipherSuites(final String remoteSslEnabledCipherSuites) {
        this.remoteSslEnabledCipherSuites = remoteSslEnabledCipherSuites;
    }

    @JsonIgnore
    public boolean isRemoteSslNeedClientAuth() {
        return remoteSslNeedClientAuth;
    }

    @JsonProperty("remote_ssl_need_client_auth")
    public void setRemoteSslNeedClientAuth(final boolean remoteSslNeedClientAuth) {
        this.remoteSslNeedClientAuth = remoteSslNeedClientAuth;
    }

    @JsonIgnore
    public boolean isRemoteAuthenticate() {
        return remoteAuthenticate;
    }

    @JsonProperty("remote_authenticate")
    public void setRemoteAuthenticate(final boolean remoteAuthenticate) {
        this.remoteAuthenticate = remoteAuthenticate;
    }

    public String getRemotePasswordFile() {
        return remotePasswordFile;
    }

    @JsonProperty("remote_password_file")
    public void setRemotePasswordFile(final String remotePasswordFile) {
        this.remotePasswordFile = remotePasswordFile;
    }

    public String getRemoteAccessFile() {
        return remoteAccessFile;
    }

    @JsonProperty("remote_access_file")
    public void setRemoteAccessFile(final String remoteAccessFile) {
        this.remoteAccessFile = remoteAccessFile;
    }

    public String getRemoteLoginConfig() {
        return remoteLoginConfig;
    }

    @JsonProperty("remote_login_config")
    public void setRemoteLoginConfig(final String remoteLoginConfig) {
        this.remoteLoginConfig = remoteLoginConfig;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (remote ? 1231 : 1237);
        result = prime * result + ((remoteAccessFile == null) ? 0 : remoteAccessFile.hashCode());
        result = prime * result + (remoteAuthenticate ? 1231 : 1237);
        result = prime * result + ((remoteLoginConfig == null) ? 0 : remoteLoginConfig.hashCode());
        result = prime * result + ((remotePasswordFile == null) ? 0 : remotePasswordFile.hashCode());
        result = prime * result + remotePort;
        result = prime * result + (remoteRegistrySsl ? 1231 : 1237);
        result = prime * result + (remoteSsl ? 1231 : 1237);
        result = prime * result + ((remoteSslEnabledCipherSuites == null) ? 0 : remoteSslEnabledCipherSuites.hashCode());
        result = prime * result + ((remoteSslEnabledProtocols == null) ? 0 : remoteSslEnabledProtocols.hashCode());
        result = prime * result + (remoteSslNeedClientAuth ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JmxConfig other = (JmxConfig) obj;
        if (remote != other.remote) {
            return false;
        }
        if (remoteAccessFile == null) {
            if (other.remoteAccessFile != null) {
                return false;
            }
        } else if (!remoteAccessFile.equals(other.remoteAccessFile)) {
            return false;
        }
        if (remoteAuthenticate != other.remoteAuthenticate) {
            return false;
        }
        if (remoteLoginConfig == null) {
            if (other.remoteLoginConfig != null) {
                return false;
            }
        } else if (!remoteLoginConfig.equals(other.remoteLoginConfig)) {
            return false;
        }
        if (remotePasswordFile == null) {
            if (other.remotePasswordFile != null) {
                return false;
            }
        } else if (!remotePasswordFile.equals(other.remotePasswordFile)) {
            return false;
        }
        if (remotePort != other.remotePort) {
            return false;
        }
        if (remoteRegistrySsl != other.remoteRegistrySsl) {
            return false;
        }
        if (remoteSsl != other.remoteSsl) {
            return false;
        }
        if (remoteSslEnabledCipherSuites == null) {
            if (other.remoteSslEnabledCipherSuites != null) {
                return false;
            }
        } else if (!remoteSslEnabledCipherSuites.equals(other.remoteSslEnabledCipherSuites)) {
            return false;
        }
        if (remoteSslEnabledProtocols == null) {
            if (other.remoteSslEnabledProtocols != null) {
                return false;
            }
        } else if (!remoteSslEnabledProtocols.equals(other.remoteSslEnabledProtocols)) {
            return false;
        }
        if (remoteSslNeedClientAuth != other.remoteSslNeedClientAuth) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "JmxConfig{remote=" + remote + ", remotePort=" + remotePort + ", remoteRegistrySsl=" + remoteRegistrySsl + ", remoteSsl="
                + remoteSsl + ", remoteSslEnabledProtocols=" + remoteSslEnabledProtocols + ", remoteSslEnabledCipherSuites="
                + remoteSslEnabledCipherSuites + ", remoteSslNeedClientAuth=" + remoteSslNeedClientAuth + ", remoteAuthenticate="
                + remoteAuthenticate + ", remotePasswordFile=" + remotePasswordFile + ", remoteAccessFile=" + remoteAccessFile
                + ", remoteLoginConfig=" + remoteLoginConfig + "}";
    }

}