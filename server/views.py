#django
import ImagingServer.settings
from django.http import HttpResponse,HttpResponseForbidden
from django.core import serializers
from django.core.cache import cache
from django.dispatch import *
from django.views.generic.base import View, TemplateResponseMixin, ContextMixin
from .forms import AttributeForm
from .models import *
from django.utils.datastructures import MultiValueDictKeyError
from django.contrib.auth import authenticate,login,logout,get_user_model
from django.shortcuts import redirect
from django.core.urlresolvers import reverse
from django.contrib.auth.signals import user_logged_in
from django.db import transaction
from django.contrib.sessions.models import Session
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

#interop
from interop import InteropProxy,InteropError,AUVSITarget

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



'''
saves session for logged in gcs user
'''
def gcs_logged_in_handler(sender,request,user,**kwargs):

	"""
	This is called when a person logs in to the viewer
	the gcs viewer utilizes session authentication and session storage.
	in the storage is a Stack containing the last images the viewer went through
	this is so then can go bck to previous images
	"""

	sess, created =GCSSession.objects.get_or_create(user=user,session=request.session.session_key)

	if user.userType == 'gcs':
		#create the stack for this user
		request.session['picstack'] = []

user_logged_in.connect(gcs_logged_in_handler)

'''
receive current gcs sessions
'''
def gcsSessions():
	return [ sess.session for sess in GCSSession.objects.all().filter(user__userType="gcs") ]

'''
callback to receive N, MQ messages
'''
class CountCallback(object):
	def __init__(self,size,sendNum,picList,type):
		#pdb.set_trace()
		self.type = type
		self.picList = picList
		self.count = (size if sendNum > size else sendNum)
		self.count = self.count if self.count >0 else 1
	def __call__(self,ch,method,properties,body):
		#pdb.set_trace()
		ch.basic_ack(delivery_tag=method.delivery_tag)
		if self.type == 'int':
			self.picList.append(int(body))
		else:
			self.picList.append(str(body))
		self.count-=1
		if self.count == 0:
			ch.stop_consuming()
def interop_error_handler(error,startTime):
	code,reason,text = error.errorData()


	#response to client accordingly
	#but keep going...if something fails, respond and ignore it
	#alert mission planner about the error though
	if code == 400:
		return Response({'time':time()-startTime,'error':"WARNING: Invalid telemetry data. Skipping"})

	elif code == 404:
		return Response({'time':time()-startTime,'error':"WARNING: Server might be down"})

	elif code == 405 or code == 500:
		return Response({'time':time()-startTime,'error':"WARNING: Interop Internal Server Error"})
	#EXCEPT FOR THIS
	elif code == 403:
			creds = cache.get("Creds")
			times = 5
			for i in range(0,times):
				try:
					interop_login(username=creds['username'],password=creds['password'],server=creds['server'],tout=5)
					return Response({'time':time()-startTime,'error':"Had to relogin in. Succeeded"})
				except Exception as e:
					sleep(2)
					continue
			code,_,__ = e.errorData()
			#Everyone should be alerted of this
			resp = {'time':time()-startTime,'error':"CRITICAL: Re-login has Failed. We will login again when allowed\nLast Error was %d" % code}
			# redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
			# redis_publisher.publish_message(RedisMessage(json.dumps({'warning':resp})))
			return Response(resp)





#endpoint for interoperability
class InteroperabilityViewset(viewsets.ModelViewSet):
	#defined the authentication utilized for this viewset
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


class GCSLogin(View,TemplateResponseMixin,ContextMixin):
	"""
	Used for logging in GCS view station via session auth
	"""
	template_name = 'loginpage.html'
	content_type='text/html'

	def post(self,request,format=None):
		#log ground station in
		username = request.POST['username']
		password = request.POST['password']

		if cache.has_key('trigger') == "true":	#clears triggering key from cache if it exists
			cache.set('trigger', "false", None)
		
		#authenticate the user, we use session auth here
		user = authenticate(username=username,password=password)
		if user is not None:
			#if user is active log use in and return redirect
			if user.is_active:
				login(request,user)


				#redirect to viewer page
				return redirect(reverse('index'))
		#failed to login
		return HttpResponseForbidden()

	def get(self,request):
		"""
		just renders the template for the login page
		"""
		return self.render_to_response(self.get_context_data())




#endpoint for GCS
class GCSViewset(viewsets.ModelViewSet):

	authentication_classes = (SessionAuthentication,)
	permission_classes = (GCSAuthentication,)


	@list_route(methods=['post'])
	def logout(self,request):
		#log user out
		logout(request)
		#redirect to login page
		return redirect(reverse('gcs-login'))

	@list_route(methods=['post'])
	def cameraGain(self,request,pk=None):
		"""
			allows GCS viewer to control the gain of the camera in flight
			args := new_gain (new analog gain)
		"""
		if cache.get('trigger') == "false":
			return Response({'error': 'not triggering'})
		cache.set('new_gain',request.data['new_gain'])
		return Response({})

	@list_route(methods=['post'])
	def cameraTrigger(self,request,pk=None):
		"""
			start the camera trigger
			args := fps (frame rate), gain (initial anlog gain)
		"""
        #attempting to trigger
		triggerStatus = request.data['trigger']
        #if attempting to trigger and time is 0 or there is no time
		#TODO: fix this statement
		if triggerStatus != "false" and (float(request.data['fps']) == 0 or not request.data['fps']):
            # don't do anything
			return Response({'nothing':'nothing'})
        #if attempting to trigger and time is less than 0
		if request.data['fps'] and float(request.data['fps']) < 0:
            #say invalid
			return Response({'failure':'invalid fps'})
		if request.data['gain'] and float(request.data['gain']) < 0:
			return Response({'failure':'invalid gain'})
        # if attempting to trigger
		if triggerStatus == "true":
            #set cache to yes
			cache.set('trigger',"true",None)
            #settime
			cache.set('fps',float(request.data['fps']))
			cache.set('gain',float(request.data['gain']))
        #if attempting to stop triggering
		elif triggerStatus == "false":
            # set cache
			cache.set('trigger',"false",None)
        #Success
		return Response({'Success':'Success'})

	@transaction.atomic
	@list_route(methods=['post'])
	def forwardPicture(self,request,pk=None):
		"""
			called by GCS viewer to request more pictures to view in the 'forward direction'
			arg:= numPics (number of pics requested)
		"""

		#set up the connection to the RabbitMq image Queue
		connection = pika.BlockingConnection(pika.ConnectionParameters(host='localhost'))
		channel = connection.channel()
		queue = channel.queue_declare(queue='pictures')
		#create a list of pics for 'numPics' and wait until we have at least numPics to return
		picList = []
		numPics = int(request.POST['numPics'])
		callback = CountCallback(queue.method.message_count,numPics,picList,"int")
		channel.basic_consume(callback,queue='pictures')
		channel.start_consuming()
		# we now have at least numpics in the list
		connection.close()
		pics = [Picture.objects.get(pk=int(id)) for id in picList]
		#get the GCS viewer's picStack (pics they have viewed so far)
		picStack = request.session['picstack']
		#push these by id onto the session stack
		for pk in picList:
			picStack.insert(0,int(pk))
		request.session['picstack'] = picStack
		#serialize the picture objects to json and return them to the GCS viewer
		serPics = [{'pk':picture.pk,'image':PictureSerializer(picture).data,'timeSent':time()} for picture in pics ]
		return Response(serPics)

	@list_route(methods=['post'])
	def reversePicture(self,request,pk=None):
		"""
			utilizes the viewers pic stack to requests older pictures this viewer saw but 
			that the GCS client side no longer has cached (cache miss)
			arg:= currPic (current pic the viewer is at)
		"""
		# get the pic stack and verify the index the user is at is valid
		index = request.POST['curPic']
		picStack = request.session['picstack']
		if int(index) >= len(picStack):
			return Response({'type':'nopicture'})
		#get the request pic
		picture = Picture.objects.get(pk=picStack[int(index)])
		serPic = PictureSerializer(picture)
		#serialize and return
		return Response({'type':'picture','pk':picture.pk,'image':serPic.data})


	@list_route(methods=['post'])
	def getTargetData(self,request,pk=None):
		"""
			allows the GCS viewer to request the data associated with a given target
			args := pk the primary key for the requested target
		"""
		if not "pk" in request.data:
			return HttpResponseForbidden();
		try:
            #serialize the target and return
			targetData = TargetSerializer(Target.objects.get(pk = request.data['pk']))
			return Response(targetData.data)
		except Target.DoesNotExist:
			return HttpResponseForbidden()

	@list_route(methods=['post'])
	def getAllTargets(self,request,pk=None):
		"""
			used to synchronize the current view of the targets in the system among viewers
		"""
		#return serialized data for all targets
		data = [{'pk':t.pk, 'image':"/targets/Target"+str(t.pk).zfill(4)+'.jpeg', 'sent':str(t.sent)} for t in Target.objects.all()]
		return Response(json.dumps({'targets':data}))

	@list_route(methods=['post'])
	def targetCreate(self,request,pk=None):
		"""
			form target from picture
			args := pk (primary key for picture this target is being cropped from)
			data about the size of the target
		"""

		if not "scaleWidth" in request.data or int(request.data['scaleWidth'])==0 :
			return HttpResponseForbidden("No crop given!")
		if not "width" in request.data or int(request.data['width']) == 0:
			return HttpResponseForbidden("No crop given!")
		if not "height" in request.data or int(request.data['height']) ==0:
			return HttpResponseForbidden("No crop given!")

		# get the picture this target is from
		try:
			picture = Picture.objects.get(pk=request.data['pk'])
		except Picture.DoesNotExist:
			return HttpResponseForbidden()
		#create a model object for this target
		target = TargetSerializer(data={key : (request.data[key] if key in request.data else None)  for key in ('background_color','alphanumeric_color','orientation','shape','alphanumeric','ptype','description')})
		#verify all the data is valid
		if not target.is_valid():
			return HttpResponseForbidden()
		#get the data about the size of the targt (used for actual cropping)
		sizeData = request.data
		#get the actual target object
		target = target.deserialize()
		#discrepencies among grey vs gray
		if target.background_color == "grey":
			target.background_color = "gray"
		if target.alphanumeric_color =="grey":
			target.alphanumeric_color = "gray"
		#actually crop the target out
		target.crop(size_data=sizeData,parent_pic=picture)
		#save the changes
		target.save()

		return Response("success")

	@list_route(methods=['post'])
	def targetEdit(self,request,pk=None):
		"""
			edit a target locally
			args := pk (primary key for the target to edit)
		"""
		try:

            #edit target with new values
			target = Target.objects.get(pk=request.data['pk'])
			target.edit(request.data)
			return HttpResponse('Success')
		except Target.DoesNotExist:
			return HttpResponseForbidden()
		return HttpResponseForbidden()

	@list_route(methods=['post'])
	def deleteTarget(self,request,pk=None):
		"""
			remove the target from the imaging GCS
			args := pk (primary key of target to remove)
		"""
		try:
            #get target photo path and delete it
			target = Target.objects.get(pk=request.data['pk'])
			os.remove(target.picture.path)
			return HttpResponse('Success')
		except Target.DoesNotExist:
			pass
		return HttpResponseForbidden("Target does not exist")

	@list_route(methods=['post'])
	def updateTarget(self,request,pk=None):
		#i'm thinking about adding this...
		pass

	@list_route(methods=['post'])
	def sendTargetToInterop(self,request,pk=None):
		"""
			submits target to the Interop Server
			args := pk (primary key of target to send)
		"""

		try:	
			#this is used to make requests to the interop server
			isession = InteropProxy.deserialize(cache.get("InteropProxy"))	
			if not cache.has_key("InteropClient"):
				return Response(json.dumps({'error':"Not logged into interop!"}))
			#fetch the target and verify it is not already sent
			targatAtPk = Target.objects.get(pk=int(request.data['pk']))
			if target.sent:
				return Response(json.dumps({'sent','Target was sent\n Would you like to send an edit?'}))

			#serialize the target
			pretarget = TargetSubmissionSerializer(targatAtPk)

			data = None
			try:
				#create dictionary to use to create AUVSITarget
				dataDict = dict(pretarget.data)
				dataDict['type'] = dataDict.pop('ptype')
				for key in dataDict:
					if dataDict[key]=='':
						dataDict[key] =None
				target = AUVSITarget(**dataDict)
				#post the target
				data = isession.post_target(target)
				#test for interop error and respond accordingly/MIGHT BE AN ISSUE HAVE TO TEST
				if isinstance(data,InteropError):
					code, reason,text = data.errorData()
					errorStr = "Error: HTTP Code %d, reason: %s" % (code,reason)
					return Response(json.dumps({'error':errorStr}))
				#retrieve image binary for sent image
				pid = data['id']
				f = open(targatAtPk.picture.path, 'rb')
				picData = f.read()

				resp = isession.post_target_image(target_id=pid, image_binary=picData)
				#test for interop error and respond accordingly
				if isinstance(resp,InteropError):
					code, reason,text = redis_publisher.errorData()
					errorStr = "Error: HTTP Code %d, reason: %s" % code,reason
					return Response(json.dumps({'error':errorStr}))
				#mark target as sent
				target.wasSent()
				return Response(json.dumps({'response':"Success"}))
			except Exception as e:
				return Response({'error':str(e)})
		except Target.DoesNotExist:
			return Response(json.dumps({'error':'Image does not exist'}))

	@list_route(methods=['post'])
	def dumpTargetData(self,request,pk=None):
		"""
			used to dump all target data from the db so it can be submitted as file
		"""

		ids = json.loads(request.data['ids'])
		data = ''
		count = 1
		for pk in ids:
			try:
				target = Target.objects.get(pk = pk)
				target.wasSent()
				data+=str(count)+'\t'+str(target.ptype)+'\t'+str(target.latitude)+'\t'+str(target.longitude)+'\t'+target.orientation+'\t'+target.shape+'\t'+target.background_color+'\t'+target.alphanumeric+'\t'+target.alphanumeric_color+'\t'+target.picture.url+'\n'
				count+=1
			except Target.DoesNotExist:
				continue

		return Response({'data':data})

	@list_route(methods=['post'])
	def getHeartbeat(self, request, pk=None):
		"""
			client side viewer requests status update about whether the OBC is connected
			and its triggering status so all viewers can synchronize on the state of the OBC
		"""
		heartbeat = cache.get('heartbeat', 'disconnected') # connected if drone posted heartbeat or defaults to disconnected
		if cache.has_key('trigger'):
			trigger = cache.get('trigger')
		else:
			trigger = "false"
		return Response(json.dumps({'heartbeat':heartbeat, 'triggering':trigger}))



class GCSViewer(APIView,TemplateResponseMixin,ContextMixin):
	"""
		serves the actual GCS viewer page to the client
	"""

	template_name = 'index.html'
	content_type='text/html'

	def get_context_data(self,**kwargs):
		#put attribute form  in template context
		context = super(GCSViewer,self).get_context_data(**kwargs)
		context['form'] = AttributeForm
		return context

	def get(self,request):
		return self.render_to_response(self.get_context_data())
