#django
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
#websockets
from ws4redis.publisher import RedisPublisher
from ws4redis.redis_store import RedisMessage
#django-rest
from rest_framework.response import Response
from .permissions import DroneAuthentication,GCSAuthentication
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
import json as simplejson
from decimal import Decimal
import csv
import pika
#telemetry
from .client import Client, AsyncClient
from .types import  Telemetry
from .exceptions import InteropError
import requests
#debug
import pdb


#constants from Environment Vars
IMAGE_STORAGE = os.getenv("IMAGE_STORAGE","http://localhost:80/PHOTOS")
TARGET_STORAGE = os.getenv("TARGET_STORAGE", "http://localhost:80/TARGETS")




#important time constants
PICTURE_SEND_DELAY = 7
DRONE_DISCONNECT_TIMEOUT = 20
EXPIRATION = 10
connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
channel = connection.channel()
channel.queue_delete(queue='pictures')
connection.close()


'''
saves session for logged in gcs user
'''
def gcs_logged_in_handler(sender,request,user,**kwargs):
	GCSSession.objects.get_or_create(
		user=user,
		session_id = request.session.session_key
	)
	if user.userType == 'gcs':
		request.session['picstack'] = []

user_logged_in.connect(gcs_logged_in_handler)

'''
'''
def gcsSessions():
	return [ sess.session_id for sess in GCSSession.objects.all().filter(user__userType="gcs") ]

'''
sends pictures to gcs
'''


class TelemetryInteropViewset(viewsets.ModelViewSet):
	authentication_classes = (JSONWebTokenAuthentication,)
	permission_classes = (TelemetryAuthentication,)
	#started writing some telemetry posting STUFF
	#this is where all the telemetry interop  stuff should go
	#mission planner requests this endpoint via HTTPS
	#it firsts needs to login to django
	#logging in is done via the JSONWEBTOKEN obtain token endpoint
	#need to add Authorization flag to verify token
	@list_route(methods=['post'])
	def postTelemetry(self,request,pk=None):
		pass
		'''
		startTime = time()
		t = Telemetry(latitude=request.data['lat'],
            		longitude=request.data['lon'],
					altitude_msl=request.data['alt'],
					uas_heading=request.data['heading'])
					'''
		'''
		#post it
			#@RUAutonomous-autopilot
			#might be where exception catching goes, look for a InteropError obj
			successful = False
			while not successful:
				try:
					postTime = time()
					self.client.post_telemetry(t).result()
					print "Time to post: %f" % (time() - postTime)
					successful = True
				except InteropError as e:
					#@RUAutonomous-autopilot
					#We might need more exceptions here and below
					code,reason,text = e.errorData()
					print "POST /api/telmetry has failed."
					print "Error code : %d Error Reason: %s" %(code,reason)
					print "Text Reason: \n%s" %(text)

					if code == 400:
						print "Invalid telemetry data. Stopping."
						sys.exit(1)

					elif code == 404:
						print "Server Might be down.\n Trying again at 1Hz"
						sleep(1)

					elif code == 405 or code == 500:
						print "Internal error (code: %s). Stopping." % (str(code))
						sys.exit(1)

					elif code == 403:
						#@RUAutonomous-autopilot
						# TODO: Ask to reenter credentials after n tries or reset that mysterious cookie
						print "Server has not authenticated this login. Attempting to relogin."
						username = os.getenv('INTEROP_USER','testuser')
						password = os.getenv('INTEROP_PASS','testpass')
						self.post('/api/login', data={'username': username, 'password': password})

				except requests.ConnectionError:
					print "A server at %s was not found. Waiting for a second, then retrying." % (server)
					sleep(1)

				except requests.Timeout:
					print "The server timed out. Waiting for a second, then retrying."
					sleep(1)

				except requests.TooManyRedirects:
					print "The URL redirects to itself; reenter the address:"
					enterAUVSIServerAddress()
					self.client.url = os.getenv('INTEROP_SERVER')
					sleep(1)

				except requests.URLRequired:
					print "The URL is invalid; reenter the address:"
					enterAUVSIServerAddress()
					self.client.url = os.getenv('INTEROP_SERVER')
					sleep(1)

				except requests.RequestException as e:
					# catastrophic error. bail.
					print e
					sys.exit(1)

				except concurrent.futures.CancelledError:
					print "Multithreading failed. Waiting for a second, then retrying."
					sleep(1)

				except concurrent.futures.TimeoutError:
					print "Multithreading timed out. Waiting for a second, then retrying."
					sleep(1)

				except:
					print "Unknown error: %s" % (sys.exc_info()[0])
					sys.exit(1)

			'''



class DroneViewset(viewsets.ModelViewSet):

	authentication_classes = (JSONWebTokenAuthentication,)
	permission_classes = (DroneAuthentication,)
	parser_classes = (JSONParser,MultiPartParser,FormParser)

	@list_route(methods=['post'])
	def serverContact(self,request,pk=None):
		global EXPIRATION
		global DRONE_DISCONNECT_TIMEOUT
		global GCS_SEND_TIMEOUT

		#fetch phone client information
		dataDict = {}
		androidId=0
		try:
			dataDict = request.data
			androidId = dataDict['id']
		except MultiValueDictKeyError:

			dataDict =  simplejson.loads(str(request.data['jsonData'].rpartition('}')[0])+"}")
			androidId = dataDict['id']



		requestTime = dataDict['timeCache']
        #determine if drone has contacted before
		if not cache.has_key("android"):
			redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
			redis_publisher.publish_message(RedisMessage(simplejson.dumps({'connected':'connected'})))
			cache.set("checkallowed",True)
            #if no set its cache entry
			cache.set("android",requestTime,EXPIRATION)
		else:
            #else delete the old one
			cache.delete("android")
            #create a new one
			cache.set("android",requestTime,EXPIRATION)



		try:
            #attempt to make picture model entry
			picture = request.FILES['Picture']

			if dataDict['triggering'] == 'true':
				redis_publisher = RedisPublisher(facility="viewer",sessions=gcsSessions())
				redis_publisher.publish_message(RedisMessage(simplejson.dumps({'triggering':'true'})))
			elif dataDict['triggering']:
				redis_publisher = RedisPublisher(facility="viewer",sessions=gcsSessions())
				redis_publisher.publish_message(RedisMessage(simplejson.dumps({'triggering':'false'})))



			#set cache to say that just send pic
			if cache.has_key(androidId+"pic"):
				cache.delete(androidId+"pic")
            #form image dict
			imageData = {elmt : round(Decimal(dataDict[elmt]),5) for elmt in ('azimuth','pitch','roll','lat','lon','alt')}
			imageData['fileName'] = IMAGE_STORAGE+"/"+(str(picture.name).replace(' ','_').replace(',','').replace(':',''))

			#make obj
			pictureObj = PictureSerializer(data = imageData)
			if pictureObj.is_valid():
				pictureObj = pictureObj.deserialize()
	            #save img to obj
				pictureObj.photo = picture
				pictureObj.save()
				connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
				channel = connection.channel()

				channel.queue_declare(queue = 'pictures')
				channel.basic_publish(exchange='',
									routing_key='pictures',
									body=str(pictureObj.pk))
				connection.close()

		except MultiValueDictKeyError:
            #there was no picture sent
			pass

        #check if drone is allowed to trigger
		if cache.has_key('trigger'):

            #start triggering
			if cache.get('trigger') == 1:

				if cache.has_key('time'):
                    #send time to trigger
					responseData = {'time':cache.get('time')}
					cache.delete('time')

					return Response(responseData)
            #stop triggering
			elif cache.get('trigger') == 0:
				return Response({'STOP':'1'})
        #no info to send
		return Response({'NOINFO':'1'})

'''
Used for logging in GCS station via session auth
'''
class GCSLogin(View,TemplateResponseMixin,ContextMixin):

	template_name = 'loginpage.html'
	content_type='text/html'

	def post(self,request,format=None):
		#log ground station in
		username = request.POST['username']
		password = request.POST['password']
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
		return self.render_to_response(self.get_context_data())



class CountCallback(object):
	def __init__(self,size,sendNum,picList):
		#pdb.set_trace()
		self.picList = picList
		self.count = (size if sendNum > size else sendNum)
		self.count = self.count if self.count >0 else 1
	def __call__(self,ch,method,properties,body):
		#pdb.set_trace()
		ch.basic_ack(delivery_tag=method.delivery_tag)
		self.picList.append(int(body))
		self.count-=1
		if self.count == 0:
			ch.stop_consuming()



def connectionCheck():

	if cache.has_key("checkallowed"):

		if not cache.has_key("android"):
			redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
			redis_publisher.publish_message(RedisMessage(simplejson.dumps({'disconnected':'disconnected'})))
			cache.delete("checkallowed")


'''
used for GCS endpoints
'''
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
	def cameraTrigger(self,request,pk=None):
		connectionCheck()
        #attempting to trigger
		triggerStatus = request.data['trigger']
        #if attempting to trigger and time is 0 or there is no time
		if triggerStatus != "0" and (float(request.data['time']) == 0 or not request.data['time']):
            # don't do anything
			return Response({'nothing':'nothing'})
        #if attempting to trigger and time is less than 0
		if request.data['time'] and float(request.data['time']) < 0:
            #say invalid
			return Response({'failure':'invalid time interval'})
        # if attempting to trigger

		if triggerStatus == '1':
            #set cache to yes
			cache.set('trigger',1)
            #settime
			cache.set('time',float(request.data['time']))
        #if attempting to stop triggering
		elif triggerStatus == '0':
            # set cache
			cache.set('trigger',0)
        #Success
		return Response({'Success':'Success'})


	@transaction.atomic
	@list_route(methods=['post'])
	def forwardPicture(self,request,pk=None):
		connectionCheck()

		connection = pika.BlockingConnection(pika.ConnectionParameters(host='localhost'))
		channel = connection.channel()
		queue = channel.queue_declare(queue='pictures')
		picList = []
		numPics = int(request.POST['numPics'])
		callback = CountCallback(queue.method.message_count,numPics,picList)
		channel.basic_consume(callback,queue='pictures')
		channel.start_consuming()
		#pdb.set_trace()
		connection.close()
		pics = [Picture.objects.get(pk=int(id)) for id in picList]
		#pdb.set_trace()
		picStack = request.session['picstack']
		for pk in picList:
			picStack.insert(0,int(pk))
		request.session['picstack'] = picStack
		serPics = [{'pk':picture.pk,'image':PictureSerializer(picture).data} for picture in pics ]
		return Response(serPics)

	@list_route(methods=['post'])
	def reversePicture(self,request,pk=None):
		connectionCheck()
		#pdb.set_trace()
		index = request.POST['curPic']
		picStack = request.session['picstack']
		if int(index) >= len(picStack):
			return Response({'type':'nopicture'})
		picture = Picture.objects.get(pk=picStack[int(index)])
		serPic = PictureSerializer(picture)
		return Response({'type':'picture','pk':picture.pk,'image':serPic.data})



	@list_route(methods=['post'])
	def getTargetData(self,request,pk=None):
		connectionCheck()
		try:
            #return target data dictionary
			targetData = TargetSerializer(Target.objects.get(pk = request.data['pk']))
			return Response(targetData.data)
		except Target.DoesNotExist:
			return HttpResponseForbidden()

	@list_route(methods=['post'])
	def getAllTargets(self,request,pk=None):
		connectionCheck()
		data = [{'pk':t.pk, 'image':TARGET_STORAGE+"/Target"+str(t.pk).zfill(4)+'.jpeg'} for t in Target.objects.all()]
		return Response(simplejson.dumps({'targets':data}))


	@list_route(methods=['post'])
	def targetCreate(self,request,pk=None):
		connectionCheck()
		try:
			picture = Picture.objects.get(pk=request.data['pk'])
		except Picture.DoesNotExist:
			return HttpResponseForbidden()
		target = TargetSerializer(data={key : request.data[key] for key in ('color','lcolor','orientation','shape','letter')})
		if not target.is_valid():
			return HttpResponseForbidden()
		sizeData = tuple( request.data[key] for key in ('x','y','scaleWidth','width','height'))
		target = target.deserialize()
		target.crop(size_data=sizeData,parent_pic=picture)
		redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
		redis_publisher.publish_message(RedisMessage(simplejson.dumps({'target':'create','pk':target.pk,'image':TARGET_STORAGE+"/Target"+str(target.pk).zfill(4)+'.jpeg'})))
		return Response()



	@list_route(methods=['post'])
	def targetEdit(self,request,pk=None):
		connectionCheck()
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
		connectionCheck()
		try:
            #get target photo path and delete it
			target = Target.objects.get(pk=request.data['pk'])
			os.remove(target.picture.path)
			target.delete()
			redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
			redis_publisher.publish_message(RedisMessage(simplejson.dumps({'target':'delete','pk':target.pk})))
			return HttpResponse('Success')
		except Target.DoesNotExist:
			pass
		return HttpResponseForbidden()


	@list_route(methods=['post'])
	def dumpTargetData(self,request,pk=None):
		connectionCheck()
		targets = Target.objects.all()
		data = ''.join([str(n+1)+'\tSTD\t'+str(targets[n].lat)+'\t'+str(targets[n].lon)+'\t'+targets[n].orientation+'\t'+targets[n].shape+'\t'+targets[n].color+'\t'+targets[n].letter+'\t'+targets[n].lcolor+'\t'+targets[n].picture.url+'\n' for n in range(0,len(targets))])
		return Response({'data':data})

#server webpage
class GCSViewer(APIView,TemplateResponseMixin,ContextMixin):



	template_name = 'index.html'
	content_type='text/html'

	def get_context_data(self,**kwargs):
		#put attrbribute form  in template context
		context = super(GCSViewer,self).get_context_data(**kwargs)
		context['form'] = AttributeForm
		return context

	def get(self,request):
		return self.render_to_response(self.get_context_data())
