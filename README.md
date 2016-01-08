# Prequistes (Instructions to come soon)
 - install ant, java, eclipse, and git
 - git clone agility folder (if you haven't already done so)
 - Set up your $KARAF_HOME to <git cloned agility folder>/karaf
 - git clone https://github.com/acohen27/dockerStuff onto anywhere else outside the agility folder
 - cd into the dockerstuff folder and cp ./bin/karaf to $KARAF_HOME/bin/karaf
 - cd into the dockerstuff folder and cp ./bin/jvm-opts to $KARAF_HOME/bin/jvm-opts
(prequistes need to be polished)

------------------
# Instructions

1. Download docker engine from https://docs.docker.com/
2. open up your bash profile (vi ~/.bash_profile) and add this:
alias dockeron='docker-machine start default; eval "$(docker-machine env default)"'

*FYI : You will need to run this command every time you open a terminal and want to access the docker containers.* 

*FYI : (docker-machine start) boots up the docker-machine. (You need to do this every time you reboot your laptop)*

*FYI : (docker-machine env default) exports these variables for you*
```
	export DOCKER_TLS_VERIFY="1"
	export DOCKER_HOST="tcp://192.168.99.100:2376"
	export DOCKER_CERT_PATH="/Users/acohen27/.docker/machine/machines/default"
	export DOCKER_MACHINE_NAME="default"
```

3. dockeron
*to initialize docker*

4. cd <dockerStuff folder>

5. docker-compose -f <pick a docker-compose.yml you want to work with> up -d

*FYI: You will want to stick with the same docker-compose.yml for the rest of the process. Otherwise, you'll end up recreating a container that uses a different program or version constantly.*

*FYI: docker-compose (an automation tool) will read the docker-compose.yml. The file instructs docker to pull and build a docker imagee for mysql, rabbitmq, zookeeper, and agility (karaf).*

6. docker-compose -f <docker-compose-####.yml> ps 

*FYI: you should see something like this:*
```
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
```

7. docker-compose -f <docker-compose####.yml> stop agilitykaraf

*FYI: Techincally karaf will work. You can watch it via "docker-compose logs agilitykaraf", but our other services aren't initialized yet.*

8. cd $KARAF_HOME/../

9. ant reset_demo_dev

*Important Tip: You need to be connected to the network internally via work or VPN.*

*FYI: This will set up the databases in your new mysql docker container*

10. cd <dockerStuff folder>

11. docker-compose -f <docker-compose####.yml> restart agilitykaraf

12. Feel free to watch it with "docker-compose logs agilitykaraf" until it completes loading

13. docker-machine ip default

14. "sudo vi /etc/hosts" and add this at the of the file
<docker-machien ip default> docker

15. Test the connectivity to your services
```
  open up your favorite browser and run this http://docker:8080 (or http://<docker-machine ip default>:8080)
  or
  curl -L -k http://docker:8080
```
```
  - ssh karaf@docker -p 8022
  Password: karaf
  enter "logout" to exit
```
```
  - open up your favorite browser and run this http://docker:8181 to see your databases.
  Username: admin Password: x0cloud
```

16. Test the debugger in your Eclipse. Few reminders:
- Update the host's value to "docker" (or <docker-machine ip default>)
- "JUNIT_NAMESPACE=qa" need to be set in your ~/.bash_profile

You should be set to go!

# Routine Work Procedure: 

* git pull whatever
* $KARAF_HOME/.. and ant clean deploy
*FYI: It will compile everything and put all the jars into $KARAF_HOME/deploy*
* cd <path to dockerStuff> && docker-compose restart agilitykaraf
FYI: It will restart agility (karaf)
* Play away with agility, eclipse, or whatever

# Tricks and Tips:

1. I am sick of specifying which file (-f) in docker-compose.yml. So I created a symlink. 
ln -s docker-compose-oraclejava8.yml docker-compose.yml. 
Ahh, "docker-compose ps" => so much better :)

