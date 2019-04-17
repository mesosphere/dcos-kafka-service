# Secure JMX


Enabling secure JMX for Kafka package

To enable the secure JMX we need to add the next options in the configuration of the package: 

```
"service": {
    "name": "kafka",
    "jmx": {
        "enabled": true,
        "port": 31299,
        "rmi_port": 31298,
        "access_file": "kafka/access",
        "password_file": "kafka/passwordfile",
        "key_store": "kafka/keystore",
        "key_store_password_file": "kafka/keystorepass",
        "add_trust_store": true,
        "trust_store": "kafka/truststore",
        "trust_store_password_file": "kafka/truststorepass"
		}
}
```



**jmx.enabled** enables the secure JMX 

**jmx.port** JMX port

**jmx.rmi_port** JMX RMI port

**jmx.access_file** The access file for the JMX. This has to be name of the secret, which has been created from  the access file.

**jmx.password_file** The password file for the JMX.  This has to be name of the secret, which has been created from the password file.

**jmx.key_store** The keystore to be used for JMX. This has to be name of the secret, which has been created from the key store.

**jmx.key_store_password_file** The password file for the JMX.  This has to be name of the secret, which has been created from the key store password file.

**add_trust_store** enables the user provided trust store. 

**trust_store ** The truststore to be used for JMX. This has to be name of the secret, which has been created from the trust store.

**trust_store_password_file** The password file for the JMX.  This has to be name of the secret, which has been created from the trust store password file.



### Example with self-signed certificate



**pre-requisites**: having a DC/OS cluster configured



Generate a self-signed key store and trust store

```
keytool -genkey -alias server-cert -keyalg rsa  -dname "CN=kafka.example.com,O=Example Company,C=US"  -keystore keystore.ks -storetype JKS -storepass changeit -keypass changeit
```

```
keytool -genkey -alias server-cert -keyalg rsa  -dname "CN=kafka.example.com,O=Example Company,C=US"  -keystore truststore.ks -storetype JKS -storepass changeit -keypass changeit
```

Generate files containing the trust store and key store passwords

```
cat <<EOF >> trust_store_pass
changeit
EOF
```

```
cat <<EOF >> key_store_pass
changeit
EOF
```

Create a JMX access file

```
cat <<EOF >> access_file
admin readwrite
user  readonly
EOF
```

Create a JMX password file

```
cat <<EOF >> password_file
admin  adminpassword
user   userpassword
EOF
```



Create necessary secrets in DC/OS for JMX

```
dcos package install dcos-enterprise-cli --yes
dcos security secrets create -f keystore.ks kafka/keystore
dcos security secrets create -f key_store_pass kafka/keystorepass
dcos security secrets create -f truststore.ks kafka/truststore
dcos security secrets create -f trust_store_pass kafka/truststorepass
dcos security secrets create -f password_file kafka/passwordfile
dcos security secrets create -f access_file kafka/access
```



Now we are ready to install kafka cluster with secure JMX enabled

```
cat <<EOF >> kafka-package-options.json
{
	"service": {
    "name": "kafka",
    "jmx": {
        "enabled": true,
        "port": 31299,
        "rmi_port": 31298,
        "access_file": "kafka/access",
        "password_file": "kafka/passwordfile",
        "key_store": "kafka/keystore",
        "key_store_password_file": "kafka/keystorepass",
        "add_trust_store": true,
        "trust_store": "kafka/truststore",
        "trust_store_password_file": "kafka/truststorepass"
		}
	}
}
EOF
```





**WARNING**

Due to an upstream bug in Kafka, it doesn't allow SSL connections through RMI port. That is the standard way in most of cli based JMX tools

https://cwiki.apache.org/confluence/display/KAFKA/KIP-417%3A+Allow+JmxTool+to+connect+to+a+secured+RMI+port

Once this fixed is released in a version RMI connections will be allowed and users won't need to do any extra step in configuration to enable them. 