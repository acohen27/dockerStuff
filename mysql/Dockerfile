# pull base image
FROM mysql:5.5

# Open to other Dockers containers only via linking
EXPOSE 3306:3306

# setup environment for agility config
ENV MYSQL_ROOT_PASSWORD=x0cloud\
    MYSQL_USER=admin\
    MYSQL_PASSWORD=x0cloud

COPY libudf.so /usr/local/mysql/lib/plugin/libudf.so

COPY my.cnf /etc/mysql/conf.f/agility.cnf

COPY docker-entrypoint.sh /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["mysqld"]

