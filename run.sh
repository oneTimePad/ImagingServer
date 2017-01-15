#!/bin/bash
# Runs the Interop Server in a container.

docker run -d --restart=unless-stopped --interactive --tty --publish 8443:80 --name imaging-server igcs/imaging-server

echo "You may now use sudo docker stop imaging-server to stop the container and then sudo docker start imaging-server to start it up again";
