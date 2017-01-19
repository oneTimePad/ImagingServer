# Imaging Django Webserver and imaging GUI

#Deployment:
	- the server is now deployed with docker
	- Instructions and Requirements:
		- requires Ubuntu 16.04
		- install docker: sudo apt-get install docker
		- start the docker daemon : sudo service docker start
		- build the container: sudo ./build.sh
		- run sudo ./run.sh
		- contact http://localhost:8443
		- note please be patient [server takes time to start up see docker logs imaging-server for info]

#Issues:
	- the docker version has not been tested with websockets

#Users
- username: viewer1, password: ruautonomous [user for image gui]
- username: drone, password: ruautonomous [user for phone]
- username: telemuser, password: ruautonomous [user for posting telemetry]
- username: serverobstaclesuser, password: ruautonomous [user for fetching obstacles and getting server time]
