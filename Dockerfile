FROM anapsix/alpine-java:8_server-jre_unlimited

MAINTAINER joey

RUN mkdir -p /joey/server

WORKDIR /joey/server

ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 29222

ADD https://ghproxy.com/https://github.com/spacey0409/DynamicsDefendTorrent/releases/download/1.0.0/DynamicsDefendTorrent-1.0.0.jar ./DynamicsDefendTorrent-1.0.0.jar

ENTRYPOINT ["java", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "DynamicsDefendTorrent-1.0.0.jar"]
