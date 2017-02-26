FROM ubuntu:16.04
WORKDIR /imagingGCS

RUN apt-get update && \
	apt-get -y install sudo

#RUN useradd -m docker && echo "docker:docker" | chpasswd && adduser docker sudo

#USER docker
CMD /bin/bash

#CMD sudo apt-get update

#install utilities and daemons
RUN sudo  apt-get  update && sudo apt-get install -y \
	apt-utils\
	apache2 \
	apache2-utils \
	libapache2-mod-auth-pgsql \
	libapache2-mod-wsgi-py3 \ 
	libapache2-mod-uwsgi\
	uwsgi-plugin-python3 \
	redis-server \
	rabbitmq-server \
	python3\
	python3-dev\
	python3-matplotlib \
	python3-numpy \ 
	python3-pip \
	python3-psycopg2\
	postgresql \
	postgresql-client \
	uwsgi 

#RUN sudo apt-get install libapache2-mpm-itk && \
#			libapache2-mod-wsgi
#RUN sudo a2enmod wsgi

#RUN rm /usr/bin/python
#RUN ln -s /usr/bin/python3.5 /usr/bin/python

COPY config config
#install python requirements
RUN pip3 install --upgrade pip
RUN pip3 install -r ./config/requirements.pip



COPY manage.py manage.py
COPY server server
COPY ImagingServer ImagingServer
COPY fixtures fixtures
COPY wsgi_websocket.py wsgi_websocket.py

RUN sudo a2enmod proxy_http
RUN sudo a2enmod proxy
RUN sudo a2enmod proxy_wstunnel
RUN sudo service redis-server start && \
	sudo service rabbitmq-server start && \
	sleep 20

# run configuration for db,apache and django
RUN chmod -R 700 ./config/configure_postgresql.sh && \
	chmod -R 700 ./config/configure_apache.sh && \
	chmod -R 700 ./config/configure_django.sh
RUN ./config/configure_postgresql.sh && \
    ./config/configure_apache.sh   && \
    ./config/configure_django.sh
#load static data into db (users)
#RUN sudo service redis-server start && \
#	sudo service rabbitmq-server start
RUN sudo service postgresql start && \
    sudo service redis-server start && \
    sudo service rabbitmq-server start && \
	sleep 50 && \
	python3 manage.py loaddata fixtures/users.json

#setup apache logs
VOLUME /var/log/apache2 /var/lib/postgresql/9.5/data

#start daemons
CMD sudo service redis-server start && \
    sudo service rabbitmq-server start && \
    sudo service postgresql start && \
	sleep 50 && \  
    sudo service apache2 start && \
    #sudo  service uwsgi start && \
	tail -f /dev/null

#CMD dpkg -S mod_ssl.so
#CMD a2enmod ssl

