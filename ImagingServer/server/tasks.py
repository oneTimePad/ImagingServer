from __future__ import absolute_import

from celery import shared_task
from .models import Target
import xmlrpclib
import pika




#for the posting pictures via interoperability
#celery will obtain pics via RabbitMQ...however
# we need to be able to share the Client() object between
#django and Celery, the cache is the ideal option
#however...closing the connection nicely might be a problem as
# we can't use the context manager...
@shared_task
def interopTargets():
    '''
    server = xmlrpclib.ServerProxy('http://' + host + ':' + port)

    while True:
        connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
        channel = connection.channel()
        channel.queue_delete(queue='targets')
        '''
