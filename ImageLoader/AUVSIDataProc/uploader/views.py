#django
from django.http import HttpResponse
from django.core import serializers
from django.core.cache import cache
from django.dispatch import *
from django.views.generic.base import View, TemplateResponseMixin, ContextMixin
from .forms import AttributeForm
from .models import *
#websockets
from ws4redis.publisher import RedisPublisher
from ws4redis.redis_store import RedisMessage
#django-rest
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from rest_framework_jwt.authentication import JSONWebTokenAuthentication
from rest_framework import viewsets
from rest_framework.decorators import list_route
from .serializers import *
#general
import os
import time
import _thread
import json as simplejson
from decimal import Decimal
#debug
import pdb


#constants from Environment Vars
IMAGE_STORAGE = os.getenv("IMAGE_STORAGE","http://localhost:80/PHOTOS")
TARGET_STORAGE = os.getenv("TARGET_STORAGE", "http://localhost:80/TARGETS")

#important time constants
PICTURE_SEND_DELAY = 7
DRONE_DISCONNECT_TIMEOUT = 20

class DroneConnectionCheck:

    def __init__(self,id,timeout):
        self.id = id
        self.timeout = timeout
    def startLoop(self):
        while True:
            if not cache.has_key(self.id):
                cache.delete("connectLoop")
                redis_publisher = RedisPublisher(facility="viewer",broadcast=True)
                redis_publisher.publish_message(RedisMessage(simplejson.dumps({'disconnnected':'disconnected'})))
                break
            time.sleep(self.timeout)
class DroneViewset(viewsets.ModelViewSet):

    authentication_classes = (JSONWebTokenAuthentication,)
    permission_classes = (IsAuthenticated,)

    @list_route(methods=['post'])
    def serverContact(self,request,pk=None):
            global EXPIRATION
            #fetch phone client information
            androidId = request.data['id']
            requestTime = request.data['timeCache']
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
                redis_publisher = RedisPublisher(facility="viewer",broadcast=True)
                redis_publisher.publish_message(RedisMessage(simplejson.dumps({'connected':'connected'})))

            try:
                #attempt to make picture model entry
                picture = request.FILES['Picture']
                #form image dict
                imageData = {elmt : Decimal(request.data[elmt]) for elmt in ('azimuth','pitch','roll','lat','lon','alt')}
                #make obj
                pictureObj = PictureSerializer(data = imageData)
                #save img to obj
                pictureObj.photo = picture
                pictureObj.fileName = IMAGE_STORAGE+"/"+(str(pictureObj.photo).replace(' ','_').replace(',','').replace(':',''))
                pictureObj.save()
            except Exception:
                #there was no picture sent
                pass

            #check if drone is allowed to trigger
            if cache.has_key('trigger'):
                #start triggering
                if cache.get('trigger') == '1':
                    if cache.has_key('time'):
                        #send time to trigger
                        responseData = simplejson.dumps({'time':cache.get('time')})
                        cache.delete('time')
                        return Response(responseData)
                #stop triggering
                elif cache.get('trigger') == '0':
                        return Response(simplejson.dumps({'STOP':'1'}))
            #no info to send
            return Response(simplejson.dumps({'NOINFO':'1'}))

class GCSViewset(viewsets.ModelViewSet):

		@list_route(methods=['post'])
		def cameraTrigger(self,request,pk=None):
            #attempting to trigger
			triggerStatus = request.data['trigger']
            #if attempting to trigger and time is 0 or there is no time
			if triggerStatus != "0" and (float(request.data['time']) == 0 or not request.data['time']):
                # don't do anything
				return Response(simplejson.dumps({'nothing':'nothing'}))
            #if attempting to trigger and time is less than 0
			if request.data['time'] and float(request.data['time']) < 0:
                #say invalid
				return Response(simplejson.dumps({'failure':'invalid time interval'}))
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
			return Response(simplejson.dumps({'Success':'Success'}))

		@list_route(methods=['post'])
		def getTarget(self,request,pk=None):
			try:
				picture = Picture.objects.get(pk=request.data['pk'])
			except Picture.DoesNotExist:
				return HttpResponseForbidden()
			targ = picture.target_set.all()
			#for dictionary of targets
			i = 0
			targetDict = {'target'+str((lambda j :j+1)(i)): (lambda x:{'pk':x.pk,'image':''.join(TARGET_STORAGE).join('Target').join(str(str(x.pk).zfill(4))).join('.jpeg')})(t) for t in targ }
            #return targets if there are any
			response = (Response(simplejson.dumps(targetDict)) if len(targetDict)!=0 else Response(simplejson.dumps({'Notargets':'0'})))
			return response
		@list_route(methods=['post'])
		def getTargetData(self,request,pk=None):
			try:
                #return target data dictionary
				target = Target.objects.get(pk = request.data['pk'])
				return Response(simplejson.dumps(dict(target)))
			except Target.DoesNotExist:
				return HttpResponseForbidden()

		@list_route(methods=['post'])
		def targetEdit(self,request,pk=None):
			try:
                #edit target with new values
				target = Targets.objects.get(pk=request.data['pk'][0])
				target.edit(request.data)
				return HttpResponseForbidden('Success')
			except Target.DoesNotExist:
				pass
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

#manual attribute form
class AttributeFormCheck(View):

	def post(self,request):
		#post data
		post_vars= self.request.POST
		#convert to dict
		post_vars=dict(post_vars)
		#get parent image pk
		parent_image = post_vars['pk']
		#get parent pic from db
		parent_pic = Picture.objects.get(pk=parent_image[0])
		#create target object
		target = Target.objects.create(picture=parent_pic)
		#package crop data to tuple
		size_data=(post_vars['crop[corner][]'][0],post_vars['crop[corner][]'][1],post_vars['crop[height]'],post_vars['crop[width]'],post_vars['crop[scaleWidth]'])
		#crop target
		target.crop(size_data=size_data,parent_pic=parent_pic)
		target.edit(post_vars)

		return HttpResponse(simplejson.dumps({'pk':target.pk,'image':TARGET_STORAGE+"/Target"+str(target.pk).zfill(4)+'.jpeg'}),'application/json')


#server webpage
class Index(View,TemplateResponseMixin,ContextMixin):

	template_name = 'index.html'
	content_type='text/html'

	def get_context_data(self,**kwargs):
		#put attrbribute form  in template context
		context = super(Index,self).get_context_data(**kwargs)
		context['form'] = AttributeForm
		return context

	def get(self,request):
		return self.render_to_response(self.get_context_data())
