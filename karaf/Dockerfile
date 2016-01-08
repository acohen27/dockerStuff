FROM isuper/java-oracle

# Initial Prep
RUN	apt-get update && apt-get install -y \
	procps \
	curl \ 
	sudo \
	vim \
	mysql-client-5.5 && \
	apt-get clean && \
	rm -rf /var/lib/apt/lists/* && \
	rm -rf /tmp/*

# Set up smadmin to be the new user. (karaf won't work with root)
RUN groupadd -r smadmin && \
	useradd --uid 1000 -r -g smadmin --groups staff smadmin && \
	echo "smadmin       ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers && \
	mkdir -p /home/smadmin && chown smadmin:smadmin /home/smadmin

# Set up the folder * path
RUN	mkdir -p /opt && cd /opt && \
	mkdir -p agility-platform && \
# Don't need these because 'ant clean deploy' put it into $KARAF_HOME and we will mount $KARAF_HOME instead
#	curl http://archive.apache.org/dist/karaf/3.0.4/apache-karaf-3.0.4.tar.gz | tar xzv && \
#	mv apache-karaf-3.0.4 agility-platform && \
	chown -R smadmin:smadmin agility-platform

# IMPORTANT FOR AGILITY!
RUN mkdir /var/lucene && chown smadmin:smadmin /var/lucene && \
    mkdir /var/spool/agility && chown smadmin:smadmin /var/spool/agility

WORKDIR /opt/agility-platform

# Cannot mount a volume because it "violates" the portability philosphy of docker.
# Instead do it manually (docker run -v) or via docker-compose 
# VOLUME ["$KARAF_HOME:/opt/agility-platform", "$KARAF_HOME/../ddl:/opt/agility-platform/ddl"]

# Log as smadmin
USER smadmin

# Setup Environment
# ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV JAVA_OPTS "-Xms512m -Xmx4G -XX:MaxPermSize=512m -noverify"
# ENV AGILITY_HOME "/opt/agility-platform"

# Open to other Dockers containers only via linking
# EXPOSE 5005:5005 8022:8022 8080:8080 8443:8443

#The ENTRYPOINT specifies a command that will always be executed when the container starts.
ENTRYPOINT ["/opt/agility-platform/bin/karaf"]

#The CMD specifies arguments that will be fed to the ENTRYPOINT.
CMD ["debug"]
