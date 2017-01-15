#!/bin/bash
source ~/.bashrc
source ~/.virtualenvs/auvsi/bin/activate
cd /home/ruautonomous/Auvsi/ImagingServer/ImagingServer
#sleep 100;
./manage.py runserver 0.0.0.0:8000
