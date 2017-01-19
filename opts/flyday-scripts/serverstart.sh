#!/bin/bash
source ~/.bashrc
source ~/.virtualenvs/igcs/bin/activate
cd /home/ruautonomous/Auvsi/ImagingGCS
#sleep 100;
./manage.py runserver 0.0.0.0:8000
