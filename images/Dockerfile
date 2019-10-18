FROM debian:stable-20190610 as java8-builder

RUN apt update -qq && \
    apt install wget -y

RUN wget https://downloads.mesosphere.com/java/openjdk-jre-8u212b03-hotspot-linux-x64.tar.gz -P /srv && \
    mkdir /srv/jdk && \
    tar -xvvf /srv/openjdk-jre-8u212b03-hotspot-linux-x64.tar.gz --strip-components=1 -C /srv/jdk

# using debian:stretch-20190610-slim
FROM debian@sha256:9490c476443a3869e39c2897fa66c91daf5dcbbfca53c976dac7bbdc45775b28

RUN apt-get update && apt-get install -y curl openssl netcat procps
RUN rm -rf /var/lib/apt/lists/*

COPY --from=java8-builder /srv/jdk /usr/share/java

ENV PATH="$PATH:/usr/share/java/bin" \
    JAVA_HOME="/usr/share/java" \
    KAFKA_HOME="/opt/kafka" \
    SCALA_VERSION="2.12" \
    JMX_EXPORTER_PATH="/opt/jmx-exporter" \
    JMX_EXPORTER_VERSION="0.1.0"

ARG KAFKA_VERSION
ARG UNAME=kafka
ARG UID=1000
ARG GID=1000
RUN groupadd -g $GID -o $UNAME && \
    useradd -r -m -u $UID -g $GID $UNAME

RUN curl -O https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz && \
    mkdir $KAFKA_HOME && \
    tar xvfz kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz -C $KAFKA_HOME --strip-components=1 && \
    rm -f kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz* && \
    chown -R "$UNAME:$UNAME" $KAFKA_HOME

RUN curl -O "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/${JMX_EXPORTER_VERSION}/jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar" && \
    curl -O "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/${JMX_EXPORTER_VERSION}/jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar.sha1" && \
    echo "$(cat "jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar.sha1")" "jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar" | sha1sum -c - && \
    rm "jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar.sha1" && \
    mkdir -p $JMX_EXPORTER_PATH/config  && \
    mv "jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar" "/opt/jmx-exporter/jmx_prometheus_javaagent.jar"

RUN curl "https://downloads.mesosphere.com/kafka/assets/kafka-custom-principal-builder-1.0.0.jar" --output ${KAFKA_HOME}/libs/kafka-custom-principal-builder-1.0.0.jar

WORKDIR $KAFKA_HOME

COPY scripts/metrics-config.yml /opt/kafka/

USER $UNAME
