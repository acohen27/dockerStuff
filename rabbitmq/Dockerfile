FROM rabbitmq:latest

# Open to other Dockers containers only via linking
EXPOSE 5672:5672

RUN echo "NODENAME=rabbit@localhost" > /etc/rabbitmq/rabbitmq-env.conf; chown rabbitmq:rabbitmq /etc/rabbitmq/rabbitmq-env.conf;

ADD rabbitmq.config /etc/rabbitmq/rabbitmq.config
