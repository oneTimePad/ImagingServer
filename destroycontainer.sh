#!/bin/bash
read -p "WARNING: you are destroying container imaging-server. Press any key to proceed or CTL-C to cancel."
sudo docker stop imaging-server
sudo docker rm imaging-server

