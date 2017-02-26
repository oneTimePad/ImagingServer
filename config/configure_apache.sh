#!/bin/bash

CONFIG=$(readlink -f $(dirname ${BASH_SOURCE[0]}))

set -e

sudo chown -R www-data /var/www
sudo  cp ${CONFIG}/apache2.conf /etc/apache2/
sudo cp ${CONFIG}/igcs_apache.conf /etc/apache2/sites-enabled/
sudo  mkdir /etc/apache2/certs
sudo cp -r ${CONFIG}/certs    /etc/apache2/certs 
sudo  service apache2 restart 
