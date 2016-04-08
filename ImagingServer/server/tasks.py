from __future__ import absolute_import

from celery import shared_task
from django.core.cache import cache
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
    client = cachge.get("InteropClient")
    connection = pika.BlockingConnection(pika.ConnectionParameters(host='localhost'))
    channel = connection.channel()
    queue = channel.queue_declare(queue='targets')
