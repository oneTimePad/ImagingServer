#!/bin/bash
read -p "WARNING: you are destroying ALL CONTAINERS. Press any key to proceed or CTL-C to cancel."
sudo docker stop imaging-server
sudo docker rm imaging-server
sudo docker rm $(sudo docker ps -a -q)
sudo docker rm $(sudo docker images -q)


sudo docker rmi $(docker images -f "dangling=true" -q)
