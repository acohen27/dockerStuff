# Pull base image.
FROM isuper/java-oracle

# Open to other Dockers containers only via linking
EXPOSE 2181:2181

RUN groupadd -r zookeeper && useradd -r -g zookeeper zookeeper

RUN mkdir -p /opt

COPY zookeeper-3.4.2 /opt/zookeeper-3.4.2

RUN chown -R zookeeper:zookeeper /opt/zookeeper-3.4.2

RUN mkdir -p /data/zookeeper && chown zookeeper:zookeeper /data/zookeeper

# Already there
#COPY zoo.cfg /opt/zookeeper-3.4.2/conf/

# Define commonly used JAVA_HOME variable. (Already exists in there)
# ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64

USER zookeeper

ENTRYPOINT ["/opt/zookeeper-3.4.2/bin/zkServer.sh"]
CMD ["start-foreground"]
