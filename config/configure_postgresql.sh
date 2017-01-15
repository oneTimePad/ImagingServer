#!/bin/bash

CONFIG=$(readlink -f $(dirname ${BASH_SOURCE[0]}))


set -e

sudo  cp ${CONFIG}/postgresql.conf /etc/postgresql/9.5/main/
sudo  service postgresql start

sudo -u postgres psql -c "CREATE USER postgresql_user WITH CREATEDB PASSWORD 'postgresql_pass';"
sudo -u postgres psql -c "CREATE DATABASE igcs_db;"
