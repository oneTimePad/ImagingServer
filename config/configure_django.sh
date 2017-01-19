#!/bin/bash

CONFIG=$(readlink -f $(dirname ${BASH_SOURCE[0]}))


set -e

sudo cp ${CONFIG}/redis.conf /etc/redis/
#sudo cp ${CONFIG}/igcs_uwsgi.ini /etc/uwsgi/apps-enabled/
sudo mkdir /var/www/pictures
sudo mkdir /var/www/targets
sudo chown www-data:www-data /var/www/pictures
sudo chown www-data:www-data /var/www/targets
sudo service redis-server start
sudo service rabbitmq-server start
sudo service postgresql start


sleep 3;
cd ${CONFIG}/..
python3 manage.py collectstatic --noinput
python3 manage.py migrate
