#!/bin/bash
# Runs the Interop Server in a container.

docker run -d --restart=unless-stopped --interactive --tty --publish 8443:80 --name imaging-server igcs/imaging-server
