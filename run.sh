#!/bin/bash
# Runs the Interop Server in a container.

docker run -d --restart=unless-stopped --interactive --tty --publish 8443:80 --publish 8444:90 --name imaging-server igcs/imaging-server

echo "You may now use sudo docker stop imaging-server to stop the container and then sudo docker start imaging-server to start it up again";

echo "You may use sudo docker logs imaging-server to see the status of the start up.";
