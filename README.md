# dockerStuff
Instructions

Prequistes (Instructions to come soon)
- install ant, java, eclipse, and git
- Set up your $KARAF_HOME
- git clone agility folder
- cp ./bin/karaf to $AGILITY_HOME/karaf/bin/karaf
- cp ./bin/jvm-opts to $AGILITY_HOME/karaf/bin/jvm-opts
- git clone https://github.com/acohen27/dockerStuff

1. Download https://docs.docker.com/
2. open up your bash profile (vi ~/.bash_profile) and add this:
# You will need to run this command every time you open a terminal and want to access the docker containers. 

alias dockeron='docker-machine start default; eval "$(docker-machine env default)"'

# FYI : (docker-machine start) boots up the docker-machine. (You need to do this every time you reboot your laptop)

# FYI : (docker-machine env default) exports these variables for you
export DOCKER_TLS_VERIFY="1"
export DOCKER_HOST="tcp://192.168.99.100:2376"
export DOCKER_CERT_PATH="/Users/acohen27/.docker/machine/machines/default"
export DOCKER_MACHINE_NAME="default"

3. cd <andrew docker folder>

4. docker-compose up -d

# FYI: docker-compose (an automation tool) will read the docker-compose.yml. The file instructs docker to pull and build a docker imagee for mysql, rabbitmq, zookeeper, and agility (karaf). 

5. docker-compose ps 

# FYI: you should see something like this:

Name                       Command                      State                       Ports           
-------------------------------------------------------------------------------------------------------------
agility_phpmyadmin          /bin/sh -c phpmyadmin-      Up                          0.0.0.0:8181->80/tcp      
start                                                                             
karaf                       /opt/agility-               Up                          0.0.0.0:5005->5005/tcp,   
platform/bin/ ...                                       0.0.0.0:8022->8022/tcp,   
0.0.0.0:8080->8080/tcp,   
0.0.0.0:8443->8443/tcp    
mysql                       /entrypoint.sh mysqld       Up                          0.0.0.0:3306->3306/tcp    
rabbitmq                    /docker-entrypoint.sh       Up                          25672/tcp, 4369/tcp,      
rabb ...                                                5671/tcp,                 
0.0.0.0:5672->5672/tcp    
zookeeper                   /opt/zookeeper-3.4.2/bin/   Up                          0.0.0.0:2181->2181/tcp

6. docker-compose stop agilitykaraf

# FYI: Techincally karaf will work. You can watch it via "docker-compose logs agilitykaraf", but our other services aren't initialized yet. 

7. cd $KARAF_HOME/../

8. ant reset_demo_dev

# Important Tip: You need to be connected to the network internally via work or VPN. 
# FYI: This will set up the databases in your new mysql docker container

9. cd <andrew docker folder>

10. docker-compose restart agilitykaraf

11. Feel free to watch it with "docker-compose logs agilitykaraf" until it completes loading

12. docker-machine ip default

13. "sudo vi /etc/hosts" and add this at the of the file
<docker-machien ip default> docker

14. Test the connectivity to your services
- open up your favorite browser and run this http://docker:8080 (or http://<docker-machine ip default>:8080)
or
curl -L -k http://docker:8080

- ssh karaf@docker -p 8022
Password: karaf
enter "logout" to exit

- open up your favorite browser and run this http://docker:8181 to see your databases.
Username: admin Password: x0cloud

15. Test the debugger in your Eclipse. Few reminders:
a) Update the host's value to "docker" (or <docker-machine ip default>)
b) "JUNIT_NAMESPACE=qa" need to be set in your ~/.bash_profile

You should be set to go!

Routine Work Procedure: 

1. git pull whatever
2. $KARAF_HOME/.. and ant clean deploy
# FYI: It will compile everything and put all the jars into $KARAF_HOME/deploy
3. cd <andrew_docker_images> && docker-compose restart agilitykaraf
FYI: It will restart agility (karaf)
4. Play away with agility, eclipse, or whatever

Tricks and Tips:

1. 
