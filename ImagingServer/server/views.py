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
import time
import _thread
import json as simplejson
from decimal import Decimal
import csv
#debug
import pdb


#constants from Environment Vars
IMAGE_STORAGE = os.getenv("IMAGE_STORAGE","http://localhost:80/PHOTOS")
TARGET_STORAGE = os.getenv("TARGET_STORAGE", "http://localhost:80/TARGETS")

#important time constants
PICTURE_SEND_DELAY = 7
DRONE_DISCONNECT_TIMEOUT = 20
GCS_SEND_TIMEOUT = 10
EXPIRATION = 8
lock = None

'''
saves session for logged in gcs user
'''
def gcs_logged_in_handler(sender,request,user,**kwargs):
	GCSSession.objects.get_or_create(
		user=user,
		session_id = request.session.session_key
	)
user_logged_in.connect(gcs_logged_in_handler)

'''
creates a list of the sessions of all logged in gcs users
'''
def gcsSessions():
	return [ sess.session_id for sess in GCSSession.objects.all().filter(user__userType="gcs") ]

'''
sends pictures to gcs
'''
class GCSPictureSender:


	def __init__(self,timeout):
		self.timeout = timeout
		self.latestpk = -1

	def startLoop(self):
		while True:
			#get latest picture based on pk
			try:
				picture = Picture.objects.latest('pk')
			except Picture.DoesNotExist:
				continue
			#serialize pic
			if (picture.pk!=self.latestpk):
				serPic = PictureSerializer(picture)
				#picture info
				responseData = simplejson.dumps({'type':'picture','pk':picture.pk,'image':serPic.data})

				#send to gcs
				redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
				redis_publisher.publish_message(RedisMessage(responseData))
				self.latestpk = picture.pk
			#wait delay
			time.sleep(self.timeout)


'''
used to check if drone is still connected
'''
class DroneConnectionCheck:

	def __init__(self,id,timeout):
		self.id = id
		self.timeout = timeout
	def startLoop(self):
		while True:
			#if drone cache entry is gone
			if not cache.has_key(self.id):
				#then drone hasn't connected in a while
				#delete looper obj
				cache.delete("connectLoop")
				#tell gcs drone disconnected
				redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
				redis_publisher.publish_message(RedisMessage(simplejson.dumps({'disconnected':'disconnected'})))
				break
			time.sleep(self.timeout)


'''
used for drone endpoints
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
		global lock

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
		if not cache.has_key(androidId):
            #if no set its cache entry
			cache.set(androidId,requestTime,EXPIRATION)
		else:
            #else delete the old one
			cache.delete(androidId)
            #create a new one
			cache.set(androidId,requestTime,EXPIRATION)
        #if drone connection check not started
		if not cache.has_key('connectLoop'):
            #create connection check object
			connectLoop = DroneConnectionCheck(androidId,DRONE_DISCONNECT_TIMEOUT)
            #save it in cache
			cache.set('connectLoop',connectLoop)
            #start loop in new thread
			_thread.start_new_thread(connectLoop.startLoop,())
            #tell gcs that drone is connected
			redis_publisher = RedisPublisher(facility="viewer",sessions=gcsSessions())
			redis_publisher.publish_message(RedisMessage(simplejson.dumps({'connected':'connected'})))

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
			#start gcs picture send loop if not started
			if not cache.has_key("sendLoop"):
				#create loop obj and save in cache
				sendLoop = GCSPictureSender(GCS_SEND_TIMEOUT)
				cache.set("sendLoop",sendLoop)
				_thread.start_new_thread(sendLoop.startLoop,())

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

	@list_route(methods=['post'])
	def getTargetData(self,request,pk=None):
		try:
            #return target data dictionary
			targetData = TargetSerializer(Target.objects.get(pk = request.data['pk']))
			return Response(targetData.data)
		except Target.DoesNotExist:
			return HttpResponseForbidden()


	@list_route(methods=['post'])
	def targetCreate(self,request,pk=None):
		try:
			picture = Picture.objects.get(pk=request.data['pk'])
		except Picture.DoesNotExist:
			return HttpResponseForbidden()

		target = TargetSerializer(data={key : request.data[key] for key in ('color','lcolor','orientation','shape','letter')})
		if not target.is_valid():
			return HttpResponse("Not valid")
		sizeData = tuple( request.data[key] for key in ('x','y','scaleWidth','width','height'))
		target = target.deserialize()
		target.crop(size_data=sizeData,parent_pic=picture)

		return Response({'pk':target.pk,'image':TARGET_STORAGE+"/Target"+str(target.pk).zfill(4)+'.jpeg'})

	@list_route(methods=['post'])
	def targetEdit(self,request,pk=None):
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
		try:
            #get target photo path and delete it
			target = Target.objects.get(pk=request.data['pk'])
			os.remove(target.target_pic.path)
			return HttpResponse('Success')
		except Target.DoesNotExist:
			pass
		return HttpResponseForbidden()

	@list_route(methods=['post'])
	def deletePicture(self,request,pk=None):
		try:
            #get target photo path and delete it
			picture = Picture.objects.get(pk=request.data['pk'])
			os.remove(picture.photo.path)
            #delete all of the picture's targets
			for t in picture.target_set.all():
				os.remove(t.target_pic.path)
				t.delete()
			picture.delete()
			return HttpResponse('Success')
		except Picture.DoesNotExist:
			pass
		return HttpResponseForbidden()

	@list_route(methods=['post'])
	def dumpTargetData(self,request,pk=None):
		response = HttpResponse(content_type='text/csv')
		response['Content-Disposition'] = 'attachment; filename=RU.txt'
		targets = Target.objects.all()
		writer = csv.writer(response,delimiter='\t')
		count = 1
		for t in targets:
			writer.writerow([count, 'STD', t.lat, t.lon, t.orientation, t.shape, t.color, t.letter, t.lcolor, t.picture.url])
			count+=1
		return response

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
