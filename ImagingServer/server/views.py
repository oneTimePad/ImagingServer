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
from django.contrib.sessions.models import Session
#websockets
from ws4redis.publisher import RedisPublisher
from ws4redis.redis_store import RedisMessage
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
import qrtools
#import zbarlight
from PIL import Image
#telemetry
from .types import  Telemetry,AUVSITarget
from .exceptions import InteropError
from .interopmethods import interop_login,get_obstacles,post_telemetry,post_target_image,post_target,get_server_info
import requests
#debug
import pdb


#constants from Environment Vars
IMAGE_STORAGE = "http://localhost:8888/html/PHOTOS"
TARGET_STORAGE = "http://localhost:8888/html/TARGETS"

IMAGE  = os.getenv("IMAGE",IMAGE_STORAGE)
TARGET = os.getenv("TARGET",TARGET_STORAGE)


#important time constants
PICTURE_SEND_DELAY = 7
DRONE_DISCONNECT_TIMEOUT = 10
EXPIRATION = 10
#can be removed for compeition
connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
channel = connection.channel()
channel.queue_delete(queue='pictures')
connection.close()

connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
channel = connection.channel()
channel.queue_delete(queue='fullsize')
connection.close()



'''
saves session for logged in gcs user
'''
def gcs_logged_in_handler(sender,request,user,**kwargs):

	sess, created =GCSSession.objects.get_or_create(user=user,session=request.session.session_key)

	if user.userType == 'gcs':
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
def interop_error_handler(error):
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
			for i in xrange(0,times):
				try:
					interop_login(username=creds['username'],password=creds['password'],server=creds['server'],tout=5)
					return Response({'time':time()-startTime,'error':"Had to relogin in. Succeeded"})
				except Exception as e:
					sleep(2)
					continue
			code,_,__ = e.errorData()
			#Everyone should be alerted of this
			resp = {'time':time()-startTime,'error':"CRITICAL: Re-login has Failed. We will login again when allowed\nLast Error was %d" % code}
			redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
			redis_publisher.publish_message(RedisMessage(json.dumps({'warning':resp})))
			return Response(resp)



#check for drone connection
def connectionCheck():

	if cache.has_key("checkallowed"):
		if not cache.has_key("android"):
			redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
			redis_publisher.publish_message(RedisMessage(json.dumps({'disconnected':'disconnected'})))
			cache.delete("checkallowed")

#endpoint for interoperability
class InteroperabilityViewset(viewsets.ModelViewSet):
	authentication_classes = (JSONWebTokenAuthentication,)
	permission_classes = (InteroperabilityAuthentication,)

	#mavclient endpoint for getting SDA-obstacles
	@list_route(methods=['post'])
	def getObstacles(self,request,pk=None):
		#fetch the current time, technically not needed, can be handed by client
		#ignore it if you want
		startTime = time()
		#fetch interop server info
		session = cache.get("InteropClient")
		server = cache.get("Server")
		try:
			#attempt to fetch obstacles
			stationary,moving = get_obstacles(session,server,tout=5)
			#return a json response with the time diff(again ignore if you want)
			#json version of data (i.e. obstacles)
			#there is no error, filled in if there is an error
			return Response({'time':time()-startTime,'stationary':stat.serialize(),'moving':moving.serialize(),'error':None})
		except InteropError as e:
			#interop errors are handled differently
			#this returns something in side
			interop_error_handler(e)
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
			return Response({'time':time()-startTime,'error':"Unknown error: %s" % (sys.exc_info()[0])})



	#mavclient endpoint for getting server time
	@list_route(methods=['post'])
	def getServerInfo(self,request,pk=None):
		startTime = time()
		session = cache.get("InteropClient")
		server = cache.get("Server")
		try:
			serverInfo = get_server_info(session,server,tout=5)
			return Response({'time':time()-startTime,'data':json.dumps(serverInfo),'error':None})
		except InteropError as e:
			interop_error_handler(e)

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
			return Response({'time':time()-startTime,'error':"Unknown error: %s" % (sys.exc_info()[0])})



	#posting telemetry wendpoint for mission planner
	#mission planner client logins in and get JWT
	@list_route(methods=['post'])
	def postTelemetry(self,request,pk=None):


		startTime = time()
		#fetch cached info
		session = cache.get("InteropClient")
		server = cache.get("Server")

		#verify telemtry data
		telemData = TelemetrySerializer(data = request.data)
		if not telemData.is_valid():
			return Response({'time':time()-startTime,'error':"Invalid data"})

		#create telemtry data
		t = Telemetry(**dict(telemData.validated_data))

		try:
			post_telemetry(session,server,tout=5,telem=t)
			return Response({'time':time()-startTime,'error':None})
		except InteropError as e:
			interop_error_handler(e)

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
			return Response({'time':time()-startTime,'error':"Unknown error: %s" % (sys.exc_info()[0])})





#endpoint for drone
class DroneViewset(viewsets.ModelViewSet):

	authentication_classes = (JSONWebTokenAuthentication,)
	permission_classes = (DroneAuthentication,)
	parser_classes = (JSONParser,MultiPartParser,FormParser)

	@list_route(methods=['post'])
	def serverContact(self,request,pk=None):
		global EXPIRATION
		global DRONE_DISCONNECT_TIMEOUT
		global GCS_SEND_TIMEOUT
		#pdb.set_trace()

		dataDict = request.data
		#androidId=0	androidId shouldnt be necessary anymore
		timeReceived = time()
		#code is receiving data and storing it in dataDict
  """
        try:
			dataDict = request.data
			#androidId = dataDict['id']
		except MultiValueDictKeyError:
			dataDict =  json.loads(str(request.data['jsonData'].rpartition('}')[0])+"}")
			#androidId = dataDict['id']
        """

		#requestTime = dataDict['timeCache']
        #determine if drone has contacted before

        """
        This can be utilized for heartbeats
        """
		if not cache.has_key("android"):
			#pdb.set_trace()

			cache.set("checkallowed",True,None)
            #if no set its cache entry
			cache.set("android","contacted",EXPIRATION)
			cache.delete("trigger")
			cache.delete("time")
		else:
            #else delete the old one
			cache.delete("android")
            #create a new one
			cache.set("android","contacted",EXPIRATION)

		redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
		redis_publisher.publish_message(RedisMessage(json.dumps({'connected':'connected'})))
        """
        end of stuff for heartbeats
        """


		#fullSizedResponse = ''

		#zoomSetting = ''
		#if cache.has_key('zoom'):
		#	zoom = cache.get('zoom')
		#	cache.delete('zoom')
		#	zoomSetting = zoom['direction']
        """
        used for uploading pictures
        """
		try:
            #attempt to make picture model entry
			picture = request.FILES['Picture']


			imageData = {}
			if dataDict['url'] != 'FULL':
            #form image dict
				imageData = {elmt : round(Decimal(dataDict[elmt]),5) for elmt in ('azimuth','pitch','roll','lat','lon','alt','timeTaken')}
			imageData['url'] = dataDict['url']
			imageData['fileName'] = IMAGE+"/"+(str(picture.name).replace(' ','_').replace(',','').replace(':',''))
			imageData['timeReceived'] = timeReceived

			#make obj
			pictureObj = PictureSerializer(data = imageData)
			if pictureObj.is_valid():
				pictureObj = pictureObj.deserialize()
         #save img to obj
				pictureObj.photo = picture

				pictureObj.save()
				#pdb.set_trace()

			#	if dataDict['url'] != 'FULL':
                connection=pika.BlockingConnection(pika.ConnectionParameters(host ='localhost'))
                channel = connection.channel()

                channel.queue_declare(queue = 'pictures')
                channel.basic_publish(exchange='',
                                    routing_key='pictures',
                                    body=str(pictureObj.pk))
                connection.close()
                #pdb.set_trace()
                fullSizeList = []
                connection = pika.BlockingConnection(pika.ConnectionParameters(host='localhost'))
                channel = connection.channel()
                queue = channel.queue_declare(queue='fullsize')
                #pdb.set_trace()
                if queue.method.message_count !=0:
                    #pdb.set_trace()
                    callback = CountCallback(queue.method.message_count,1,fullSizeList,"str")
                    channel.basic_consume(callback,queue='fullsize')
                    channel.start_consuming()
                    fullSizedResponse = fullSizeList[0][2:]
                    print(fullSizedResponse)
                connection.close()


			#	else:
			#		redis_publisher = RedisPublisher(facility='viewer',sessions=dataDict['session'])
			#		redis_publisher.publish_message(RedisMessage(json.dumps({'fullSize':True,'pk':pictureObj.pk,'photo':pictureObj.fileName})))
					#get session token and picture
					#push pic to client




		except MultiValueDictKeyError:
            #there was no picture sent
			pass
"""
end picture upload stuff
"""

"""
used determine if camera is triggering might or might not be needed here,
probably used in heartbeats
"""
		if not cache.has_key('trigger'):
			cache.set("trigger",dataDict['trigger'],None)
		if not cache.has_key('time'):
			cache.set("time",dataDict['time'],None)


		#determines whether camera is triggering
		if cache.get('trigger') == 1:
			redis_publisher = RedisPublisher(facility="viewer",sessions=gcsSessions())
			redis_publisher.publish_message(RedisMessage(json.dumps({'triggering':'true','time':cache.get("time")})))
		elif cache.get('trigger')== 0:
			redis_publisher = RedisPublisher(facility="viewer",sessions=gcsSessions())
			redis_publisher.publish_message(RedisMessage(json.dumps({'triggering':'false'})))

		return Response({'trigger':cache.get("trigger"),'time':cache.get("time"),'imageformat':cache.get("imageformat"),'fullSize':fullSizedResponse,'zoom':zoomSetting})


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


class InteropLogin(View,TemplateResponseMixin,ContextMixin):
	template_name = 'interoplogin.html'
	content_type = 'text/html'

	def post(self,request,format=None):
		#pdb.set_trace()
		#validate interop credential data
		serverCreds = ServerCredsSerializer(data=request.POST)
		if not serverCreds.is_valid():
			#respond with Error
			return HttpResponseForbidden("invalid server creds %s" % serverCreds.errors)
		login_data = dict(serverCreds.validated_data)
		login_data.update({"tout":5})
		#create client
		session = interop_login(**(login_data))

		#if it did not return a client, respnd with error
		if not isinstance(session,requests.Session):
			#responsd with error
			return HttpResponse(session)
		#success
		else:
			#save session and server route
			cache.set("Creds",serverCreds,None)
			cache.set("InteropClient",session,None)
			cache.set("Server",serverCreds.validated_data['server'],None)
			return HttpResponse('Success')

	def get(self,request):
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
	def cameraTrigger(self,request,pk=None):
		#pdb.set_trace()
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
			cache.set('trigger',1,None)
            #settime
			cache.set('time',float(request.data['time']))
        #if attempting to stop triggering
		elif triggerStatus == '0':
            # set cache
			cache.set('trigger',0,None)
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
		callback = CountCallback(queue.method.message_count,numPics,picList,"int")
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
		serPics = [{'pk':picture.pk,'image':PictureSerializer(picture).data,'timeSent':time()} for picture in pics ]
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

	list_route(methods=['post'])
	def getFullSize(self,request,pk=None):
		connectionCheck()
		#pdb.set_trace()
		if not "pk" in request.data:
			return HttpResponseForbidden()

		try:
			picture = Picture.objects.get(pk= request.data['pk'])
		except Picture.DoesNotExist:
			return HttpResponseForbidden()
		connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
		channel = connection.channel()

		channel.queue_declare(queue = 'fullsize')
		channel.basic_publish(exchange='',
							routing_key='fullsize',
							body=str(request.session.session_key+'~'+picture.url))
		connection.close()
		return Response({'ok':'ok'})


	@list_route(methods=['post'])
	def getTargetData(self,request,pk=None):
		connectionCheck()
		if not "pk" in request.data:
			return HttpResponseForbidden();
		try:
            #return target data dictionary
			targetData = TargetSerializer(Target.objects.get(pk = request.data['pk']))
			return Response(targetData.data)
		except Target.DoesNotExist:
			return HttpResponseForbidden()

	@list_route(methods=['post'])
	def getAllTargets(self,request,pk=None):
		connectionCheck()
		data = [{'pk':t.pk, 'image':TARGET+"/Target"+str(t.pk).zfill(4)+'.jpeg', 'sent':str(t.sent)} for t in Target.objects.all()]
		return Response(json.dumps({'targets':data}))


	@list_route(methods=['post'])
	def targetCreate(self,request,pk=None):
		#pdb.set_trace()
		connectionCheck()
		if not "scaleWidth" in request.data or int(request.data['scaleWidth'])==0 :
			return HttpResponseForbidden("No crop given!")
		if not "width" in request.data or int(request.data['width']) == 0:
			return HttpResponseForbidden("No crop given!")
		if not "height" in request.data or int(request.data['height']) ==0:
			return HttpResponseForbidden("No crop given!")
		try:
			picture = Picture.objects.get(pk=request.data['pk'])
		except Picture.DoesNotExist:
			return HttpResponseForbidden()
		target = TargetSerializer(data={key : (request.data[key] if key in request.data else None)  for key in ('background_color','alphanumeric_color','orientation','shape','alphanumeric','ptype','description')})
		if not target.is_valid():
			return HttpResponseForbidden()
		sizeData = request.data
		target = target.deserialize()
		if target.background_color == "grey":
			target.background_color = "gray"
		if target.alphanumeric_color =="grey":
			target.alphanumeric_color = "gray"
		target.crop(size_data=sizeData,parent_pic=picture)
		target.save()

		redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
		redis_publisher.publish_message(RedisMessage(json.dumps({'target':'create','pk':target.pk,'image':TARGET+"/Target"+str(target.pk).zfill(4)+'.jpeg'})))
		return Response("success")


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
			redis_publisher.publish_message(RedisMessage(json.dumps({'target':'delete','pk':request.data['pk']})))
			return HttpResponse('Success')
		except Target.DoesNotExist:
			pass
		return HttpResponseForbidden("Target does not exist")

	@list_route(methods=['post'])
	def updateTarget(self,request,pk=None):
		#i'm thinking about adding this...
		pass



	@list_route(methods=['post'])
	def sendTarget(self,request,pk=None):

		connectionCheck()
		try:
			if not cache.has_key("Server") or not cache.has_key("InteropClient"):
				return Response(json.dumps({'error':"Not logged into interop!"}))
			#fetch the client
			session = cache.get("InteropClient")
			server = cache.get("Server")
			targatAtPk = Target.objects.get(pk=int(request.data['pk']))
			if target.sent:
				return Response(json.dumps({'sent','Target was sent\n Would you like to send an edit?'}))

			#print(targatAtPk.ptype)
			#print(targatAtPk.shape)
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
				if not cache.has_key("Creds"):
					return Response(json.dumps({'error':"Not logged into interop!"}))
				target.user = cache.get("Creds").validated_data['username']
				#post the target

				data = post_target(session,server,target,tout=5)
				#test for interop error and respond accordingly/MIGHT BE AN ISSUE HAVE TO TEST
				if isinstance(data,InteropError):
					code, reason,text = data.errorData()
					errorStr = "Error: HTTP Code %d, reason: %s" % (code,reason)
					return Response(json.dumps({'error':errorStr}))
				#retrieve image binary for sent image
				pid = data['id']
				f = open(targatAtPk.picture.path, 'rb')
				picData = f.read()

				resp = post_target_image(session,server,tout =5,target_id=pid, image_binary=picData)
				#test for interop error and respond accordingly
				if isinstance(resp,InteropError):
					code, reason,text = redis_publisher.errorData()
					errorStr = "Error: HTTP Code %d, reason: %s" % code,reason
					return Response(json.dumps({'error':errorStr}))
				target.wasSent()
				return Response(json.dumps({'response':"Success"}))
			except Exception as e:
				return Response({'error':str(e)})
		except Target.DoesNotExist:
			return Response(json.dumps({'error':'Image does not exist'}))

	@list_route(methods=['post'])
	def dumpTargetData(self,request,pk=None):
		connectionCheck()
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
		# websocket response for "sent"
		redis_publisher = RedisPublisher(facility='viewer',sessions=gcsSessions())
		redis_publisher.publish_message(RedisMessage(json.dumps({'target':'sent','ids':ids})))
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
