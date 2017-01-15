#!/bin/bash

SERVER=$(readlink -f $(dirname ${BASH_SOURCE[0]}))
docker build -t igcs/imaging-server ${SERVER}
