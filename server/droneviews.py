#django
import ImagingServer.settings
from django.http import HttpResponse,HttpResponseForbidden
from django.core import serializers
from django.core.cache import cache
from django.dispatch import *
from django.views.generic.base import View, TemplateResponseMixin, ContextMixin

from .models import *
from django.utils.datastructures import MultiValueDictKeyError

#websockets
# from ws4redis.publisher import RedisPublisher
# from ws4redis.redis_store import RedisMessage
#django-rest
from rest_framework.response import Response
from .permissions import DroneAuthentication,GCSAuthentication, InteroperabilityAuthentication
from rest_framework_jwt.authentication import JSONWebTokenAuthentication
from rest_framework.authentication import SessionAuthentication
from rest_framework import viewsets
from rest_framework.views import APIView
from rest_framework.decorators import list_route
from .serializers import *
from rest_framework.parsers import MultiPartParser,JSONParser,FormParser
#general
import os
from time import time,sleep
import json
from decimal import Decimal
import csv
import pika
import sys
from PIL import Image


import requests

#debug
import pdb



#constants holding the path to dirs for pictures and targets on the server
IMAGE_STORAGE = "/pictures"
TARGET_STORAGE = "/targets"



#important time constants (chosen from testing)
PICTURE_SEND_DELAY = 7
DRONE_DISCONNECT_TIMEOUT = 10
EXPIRATION = 10

#starts up the rabbitmq connection for distributing images
connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
channel = connection.channel()
channel.queue_delete(queue='pictures')
connection.close()




class DroneViewset(viewsets.ModelViewSet):

	"""
	This viewset contain all endpoints that could be called
	by the onboard computer.
	"""
	#utilized json-web-token auth
	authentication_classes = (JSONWebTokenAuthentication,)
	permission_classes = (DroneAuthentication,)
	parser_classes = (JSONParser,MultiPartParser,FormParser)

	@list_route(methods=['post'])
	def postImage(self,request,pk=None):
		"""
		called by OBC to post images and their associated data
		"""

		dataDict = request.data
		timeReceived = time()
		#code is receiving data and storing it in dataDict
		try:
           	#fetch the image from the request data
			picture = request.FILES['image']
			#parse out all the image data to form the dict that will be put in the db
			imageData = {elmt : round(Decimal(dataDict[elmt]),5) for elmt in ('pitch','roll','lat','lon','alt','rel_alt','yaw')}
			#add the image file name
			imageData['fileName'] = IMAGE_STORAGE+"/"+(str(picture.name).replace(' ','_').replace(',','').replace(':',''))
			#add the timereceived by the server (system time)
			imageData['timeReceived'] = timeReceived
			#takes in the image data in json to form an object for the db
			pictureObj = PictureSerializer(data = imageData)
			#valid all the info is good and there for putting the image data in the db
			if pictureObj.is_valid():
				#take the serialized image data and convert it to a 'Picture' object from models
				pictureObj = pictureObj.deserialize()
				#add the actual image to the 'Picture' object
				pictureObj.photo = picture
				#save the changes
				pictureObj.save()
				#open the image queue (RabbitMQ connection)
				connection=pika.BlockingConnection(pika.ConnectionParameters(host ='localhost'))
				channel = connection.channel()
				#publish the image to the queue so it can be viewed
				channel.queue_declare(queue = 'pictures')
				channel.basic_publish(exchange='',routing_key='pictures',body=str(pictureObj.pk))
				connection.close()

			else:
				return Response({"error":str(pictureObj.errors)})

		#thrown when a certain key in the request data doesn't exist
		except MultiValueDictKeyError as e:
            #there was no picture sent
			return Response({"error":str(e)})
		#unknown error occured
		except Exception as e:
			return Response({"error":str(e)})

		#all good, just return an empty response
		return Response({})



	@list_route(methods=['post'])
	def postHeartbeat(self, request, pk=None):

		"""
		heartbeats are posted by the OBC to notify the viewer it is still connected
		In addition, they are used to respond with commands from the imaging server (trigger,change gain...)
		"""

		#bring the defined expiration defined as 'connection loss' into scope
		global EXPIRATION

		#if the cache entry doesn't exist add it
		#if the cache entry expired, restablish it as the OBC just contacted us
		if not cache.has_key('heartbeat'):
			#create a new cache entry that will expire in EXPIRATION
			cache.set('heartbeat','connected',EXPIRATION)
		else:
            #else delete the old one
			cache.delete('heartbeat')
            #create a new one
			cache.set('heartbeat','connected',EXPIRATION)

		#form the heartbeat response containing the status of triggering
		response = {'heartbeat':cache.get('trigger')}
		if cache.has_key('trigger'):
			#add the initla fps and gain settings (won't hurt to keep sending it )
			response.update({'fps':cache.get('fps'),'gain':cache.get('gain')})
		#if there was a new gain change, send it (the view for gain change already checks if triggering)
		if cache.has_key('new_gain'):
			response.update({'new_gain':float(cache.get('new_gain'))})
			cache.delete('new_gain')

		return Response(response)



