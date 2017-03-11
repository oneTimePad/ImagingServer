#django
import ImagingServer.settings
from django.http import HttpResponse,HttpResponseForbidden
from django.core import serializers
from django.core.cache import cache
from django.dispatch import *
from django.views.generic.base import View, TemplateResponseMixin, ContextMixin

from server.models import *
from django.utils.datastructures import MultiValueDictKeyError
from django.contrib.auth import authenticate,login,logout,get_user_model
from django.shortcuts import redirect
from django.core.urlresolvers import reverse
from django.contrib.auth.signals import user_logged_in
from django.db import transaction
from django.contrib.sessions.models import Session

#django-rest
from rest_framework.response import Response
from server.permissions import DroneAuthentication,GCSAuthentication, InteroperabilityAuthentication
from rest_framework_jwt.authentication import JSONWebTokenAuthentication
from rest_framework.authentication import SessionAuthentication
from rest_framework import viewsets
from rest_framework.views import APIView
from rest_framework.decorators import list_route
from server.serializers import *
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

#interop
from server.interop import InteropProxy,InteropError

import requests

#debug
import pdb



#important time constants (chosen from testing)
PICTURE_SEND_DELAY = 7
DRONE_DISCONNECT_TIMEOUT = 10
EXPIRATION = 10

#starts up the rabbitmq connection for distributing images
connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
channel = connection.channel()
channel.queue_delete(queue='pictures')
connection.close()


def interop_error_handler(error,startTime):

	"""
		deals with handling errors associated with interop requests
	"""
	BAD_REQUEST = 400
	NOT_FOUND = 404
	FORBIDDEN = 403
	METHOD_NOT_ALLOWED = 405
	INTERNAL_SERVER_ERROR =500

	code,reason,text = error.errorData()


	#response to client accordingly
	#but keep going...if something fails, respond and ignore it
	#alert mission planner about the error though
	if code == UNAUTHORIZED:
		return Response({'time':time()-startTime,'error':"WARNING: Invalid telemetry data. Skipping"})

	elif code == NOT_FOUND:
		return Response({'time':time()-startTime,'error':"WARNING: Server might be down"})

	elif code == METHOD_NOT_ALLOWED or code == INTERNAL_SERVER_ERROR:
		return Response({'time':time()-startTime,'error':"WARNING: Interop Internal Server Error"})
	#EXCEPT FOR THIS
	elif code == FORBIDDEN:
			isession = InteropProxy.deserialize(cache.get("InteropClient"))
			times = 5
			error = None
			for i in range(0,times):
				error = isession.login()
				if error is None:
					return Response({'time':time()-startTime,'error':"Had to relogin in. Succeeded"})
			return Response({'time':time()-startTime,'error':"CRITICAL: Re-login failed with %s" % error})	



#endpoint for interoperability
class InteroperabilityViewset(viewsets.ModelViewSet):
	#defined the authentication utlized for this viewset
	authentication_classes = (JSONWebTokenAuthentication,)
	#defines the permissions for this user
	permission_classes = (InteroperabilityAuthentication,)

	@list_route(methods=['post'])
	def getMission(self,request,pk=None):
		"""
		proxy method for fecthing the mission information from the interop server
		for the mission loading client
		"""

		#fetch misisons from interop server (according to spec, more than one can be returned)
		startTime = time()

		#this is used to make requests to the interop server
		isession = InteropProxy.deserialize(cache.get("InteropProxy"))
		try:
			#make a request for the mission information	
			missions = isession.get_missions()
			#put the different missions in dictionary format and serialize them to json
			mission_resp = {'mission'+str(num):mission.serialize() for num,mission in enumerate(missions)}
			#say there is no error
			mission_resp['error'] = None
			return Response(mission_resp)
		except InteropError as e:
			#interop errors are handled differently
			#this returns something in side
			return interop_error_handler(e,startTime)
			#never comes here
		#handler all connection errors
		except requests.ConnectionError:
			return Response({'time':time()-startTime,'error':"WARNING: A server was found. Encountered connection error." })

		except requests.Timeout:
			return Response({'time':time()-startTime,'error':"WARNING: The server timed out."})

		#Why would this ever happen?
		except requests.TooManyRedirects:
			return Response({'time':time()-startTime,'error':"WARNING:The URL redirects to itself"})

		#This wouldn't happen again...
		except requests.URLRequired:
			return Response({'time':time()-startTime,'error':"The URL is invalid"})


		except requests.RequestException as e:
			# catastrophic error. bail.
			return Response({'time':time()-startTime,'error':e})

		except Exception as e:
			return Response({'time':time()-startTime,'error':"Unknown error: %s" % (e)})

	@list_route(methods=['post'])
	def getObstacles(self,request,pk=None):
		"""
		proxy method for fetching the locations(status) of the stationary/moving obstacles
		for the SDA client from the interop server
		"""
		#fetch the current time, technically not needed, can be handed by client
		#ignore it if you want
		startTime = time()
		#this is used to make requests to the interop server
		isession = InteropProxy.deserialize(cache.get("InteropProxy"))

		try:
			#attempt to fetch obstacles
			stationary,moving = isession.get_obstacles()
			#put the response in dictionary format
			resp = dict()
			#serialize the statinary obstacles to a dictionary (format for json)
			resp['stationary'] = { str(x): stat.serialize() for x,stat in enumerate(stationary)}
			#serialize the moving obstacles to a dictionary (format for json)
			resp['moving'] = {str(x) :move.serialize() for x,move in  enumerate(moving)}
			#time it took for this request from server's perspective
			resp['time'] = time()-startTime
			#there was no error
			resp['error'] = None
			return Response(resp)
		except InteropError as e:
			#interop errors are handled differently
			#this returns something in side
			return interop_error_handler(e,startTime)
			#never comes here
		#handler all connection errors
		except requests.ConnectionError:
			return Response({'time':time()-startTime,'error':"WARNING: A server was found. Encountered connection error." })

		except requests.Timeout:
			return Response({'time':time()-startTime,'error':"WARNING: The server timed out."})

		#Why would this ever happen?
		except requests.TooManyRedirects:
			return Response({'time':time()-startTime,'error':"WARNING:The URL redirects to itself"})

		#This wouldn't happen again...
		except requests.URLRequired:
			return Response({'time':time()-startTime,'error':"The URL is invalid"})


		except requests.RequestException as e:
			# catastrophic error. bail.
			return Response({'time':time()-startTime,'error':e})

		except Exception as e:

			return Response({'time':time()-startTime,'error':"Unknown error: %s" % str(e)})


	@list_route(methods=['post'])
	def postTelemetry(self,request,pk=None):
		"""
		proxy method for publishing telemetry information from the telemetry
		client to the interop server
		"""
		startTime = time()

		#this is used to make requests to the interop server
		isession = InteropProxy.deserialize(cache.get("InteropProxy"))

		#verify telemtry data
		telemData = TelemetrySerializer(data = request.data)
		if not telemData.is_valid():
			return Response({'time':time()-startTime,'error':"Invalid data"})

		#create the telemetry data object
		t = Telemetry(**dict(telemData.validated_data))

		try:
			#make the post request to the interop server with the telemetry data
			isession.post_telemetry(telem=t)

			return Response({'time':time()-startTime,'error':None})
		#catch the exceptions that could occur
		except InteropError as e:
			return interop_error_handler(e)

		except requests.ConnectionError:
			return Response({'time':time()-startTime,'error':"WARNING: A server was found. Encountered connection error." })

		except requests.Timeout:
			return Response({'time':time()-startTime,'error':"WARNING: The server timed out."})

		#Why would this ever happen?
		except requests.TooManyRedirects:
			return Response({'time':time()-startTime,'error':"WARNING:The URL redirects to itself"})

		#This wouldn't happen again...
		except requests.URLRequired:
			return Response({'time':time()-startTime,'error':"The URL is invalid"})


		except requests.RequestException as e:
			# catastrophic error. bail.
			return Response({'time':time()-startTime,'error':e})

		except Exception as e:
			return Response({'time':time()-startTime,'error':"Unknown error: %s" % str(e)})


class InteropLogin(View,TemplateResponseMixin,ContextMixin):
	"""
	the imaging GCS needs to login to the interop server to obtain the requests.session object
	"""
	template_name = 'interoplogin.html'
	content_type = 'text/html'

	def post(self,request,format=None):
		#validate interop credential data (url,username,password)
		serverCreds = ServerCredsSerializer(data=request.POST)
		if not serverCreds.is_valid():
			#respond with Error
			return HttpResponseForbidden("invalid server creds %s" % serverCreds.errors)
		login_data = dict(serverCreds.validated_data)

		#create client
		isession = InteropProxy(**login_data)
		error = isession.login()
		if error is not None:
			return Response({'error',str(error)})
		#serialize and store in cache
		cache.set('InteropProxy',isession.serialize())
		return HttpResponse("Success")

	def get(self,request):
		return self.render_to_response(self.get_context_data())



