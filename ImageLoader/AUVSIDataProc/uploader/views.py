from django.shortcuts import render
from django.http import HttpResponse,HttpResponseRedirect,HttpResponseForbidden,JsonResponse
import json as simplejson
from django.views.generic.base import View, TemplateResponseMixin, ContextMixin
from .models import *
from django.db.models.signals import *
from django.dispatch import *
from ws4redis.publisher import RedisPublisher
from ws4redis.redis_store import RedisMessage
from .forms import AttributeForm
from io import BytesIO
from decimal import Decimal
import base64
from django.core import serializers
import pdb

#hard coded
IMAGE_STORAGE = "http://localhost:80/PHOTOS"
TARGET_STORAGE = "http://localhost:80/TARGETS"

image_done = Signal(providing_args=["num_pic"])
class Upload(View):

	#post request to create pictures
	def post(self,request,*args,**kwargs):
		#convert to json
		json_request = simplejson.loads((request.body).decode('utf-8'))
		#make memory img
		image = Image.open(BytesIO(base64.b64decode(json_request['file'])))

		#string as file
		image_io = BytesIO()
		image_io.seek(0)

		#save image to stringIO file as JPEG
		image.save(image_io,format='JPEG')
		#create picture
		picture = Picture.objects.create()

		#convert image to django recognized format
		django_image = InMemoryUploadedFile(image_io,None,IMAGE_STORAGE+"/Picture"+str(picture.pk).zfill(4)+'.jpeg','image/jpeg',image_io.getbuffer().nbytes,None)

		#set pic name
		picture.fileName = IMAGE_STORAGE+"/Picture"+str(picture.pk).zfill(4)+'.jpeg'
		#set Image
		picture.photo=django_image

		picture.azimuth = Decimal(json_request['Azimuth'])
		picture.pitch = Decimal(json_request['Pitch'])
		picture.roll= Decimal(json_request['Roll'])

		if "PPM" in json_request.keys():
			picture.ppm = Decimal(json_request['PPM'])
		# set latLonAlt
		if "GPS" in json_request.keys():
			latLonAlt = simplejson.loads(json_request['GPS'])
			picture.lat = latLonAlt['lat']
			picture.lon = latLonAlt['lon']
			picture.alt = latLonAlt['alt']
		#set FourCorners
		if "FourCorners" in json_request.keys():

			fourCorners = simplejson.loads(json_request['FourCorners'])
			tl = simplejson.loads(fourCorners['tl'])
			tr = simplejson.loads(fourCorners['tr'])
			bl = simplejson.loads(fourCorners['bl'])
			br = simplejson.loads(fourCorners['br'])

			picture.topLeftX = tl['X']
			picture.topLeftY = tl['Y']
			picture.topRightX = tr['X']
			picture.topRightY = tr['Y']
			picture.bottomLeftX = bl['X']
			picture.bottomLeftY = bl['Y']
			picture.bottomRightX = br['X']
			picture.bottomRightY = br['Y']

		picture.save()

		#trigger signal
		image_done.send(sender=self.__class__,num_pic=picture.pk)
		#return success
		return HttpResponse("success")

#triggered when image object created
@receiver(image_done)
def send_pic(num_pic,**kwargs):
	#create pic
	picture = Picture.objects.get(pk=num_pic)

	#Serialize pathname
	serPic = serializers.serialize("json",[picture])
	#create json response
	response_data = simplejson.dumps({'type':'picture','image':serPic})

	audience = {'broadcast': True}
	redis_publisher = RedisPublisher(facility='viewer',**audience)
	redis_wbskt=redis_publisher
	#send to url to websocket
	redis_wbskt.publish_message(RedisMessage(response_data))

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

class DeletePicture(View):

	def post(self,request):

		if request.is_ajax():
			pic_id = request.POST['pk']
			picture = Picture.objects.get(pk=pic_id)
			photo_path = picture.photo.path
			os.remove(photo_path)
			picture.delete()

			return HttpResponse("success")

class GetTargets(View):

	def get(self,request):
		if request.is_ajax():

			picture = Picture.objects.get(pk=request.GET['pk'])
			#should get all the related targets using a related manager
			targets = picture.target_set.all()

			i=0
			targetDict={}
			for t in targets:
				print(t.pk)
				targetDict["target"+str(i)]={'pk':t.pk,'image':TARGET_STORAGE+"/Target"+str(t.pk).zfill(4)+'.jpeg'}
				i+=1
			if len(targetDict) != 0:
				return HttpResponse(simplejson.dumps(targetDict),'application/json')
			else:
				return HttpResponse(simplejson.dumps({"NoTargets":"0"}),'application/json')

class GetTargetData(View):

	def get(self,request):
		if request.is_ajax():

			target = Target.objects.get(pk=request.GET['pk'])
			targetData={}
			targetData["color"]=target.color
			targetData["lcolor"]=target.lcolor
			targetData["orientation"]=target.orientation
			targetData["shape"]=target.shape
			targetData["letter"]=target.letter
			targetData['lat']=target.lat
			targetData['lon']=target.lon
			return HttpResponse(simplejson.dumps(targetData),'application/json')


#manual attribute form
class AttributeFormCheck(View):

	def post(self,request):

		if request.is_ajax():

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
			size_data=(post_vars['crop[corner][]'][0],post_vars['crop[corner][]'][1],post_vars['crop[height]'],post_vars['crop[width]'])


			#crop target
			target.crop(size_data=size_data,parent_pic=parent_pic)
			target.color = post_vars['color']
			target.lcolor = post_vars['lcolor']
			shapeChoices = dict((x,y) for x,y in Target.SHAPE_CHOICES)
			target.shape = str(shapeChoices[post_vars['attr[shape]'][0]])
			target.orientation = post_vars['attr[orientation]'][0]

			return HttpResponse("success")


#is the droid allowed to connect
connection_allowed = -1
#signal yes it is
connect = Signal(providing_args=["on"])
#signal the droid has given a status update
connection_status = Signal()

#signal connect
@receiver(connect)
def accept_connect_msg(sender,**kwargs):

	global connection_allowed

	#if yes connect, tell droid
	if kwargs["on"] == "1":

		connection_allowed=1
	# else tell to disconnect
	elif not kwargs["on"] == "0":
		connection_allowed=0
#signal status update
@receiver(connection_status)
def send_connection_status(sender,**kwargs):

	#tell the GCS viewer
	audience = {'broadcast': True}
	redis_publisher = RedisPublisher(facility='viewer',**audience)

		#Serialize pathname
	response_data = simplejson.dumps({"status":"connection failed"})

	#send to url to websocket
	redis_publisher.publish_message(RedisMessage(response_data))

#tell phone to connect
class DroneConnectGCS(View):

	def post(self,request):

		if request.is_ajax():
			connect.send(sender=self.__class__,on=request.POST["connect"])
			return HttpResponse(simplejson.dumps({"Success":"Success"}),'application/json')

#ask should phone connect
class DroneConnectDroid(View):

	def post(self, request):

		global connection_allowed

		json_request = simplejson.loads((request.body).decode('utf-8'))


		#droid asked to connect

		if json_request["connect"] == "1":

			if connection_allowed is 0:
				connection_allowed=-1

				return HttpResponse("NO")
			elif connection_allowed is 1:
				connection_allowed=-1

				return HttpResponse("YES")
			elif connection_allowed is -1:
				connection_allowed=-1

				return HttpResponse("NOINFO")


		#droid is giving a status update
		elif json_request["status"] == "1":
			print("Status")
			if json_request["connected"] == "0":

				connection_status.send(self.__class__)
				return HttpResponse("Got it")


# trigger time
time=0
#smart trigger enabled yes/no
smart_trigger=0
#trigger enabled
trigger_allowed=-1
#signal to trigger
trigger = Signal(providing_args=["on","time","smart_trigger"])
#signal status update
trigger_status = Signal(providing_args=["time"])
#signal trigger
@receiver(trigger)
def accept_trigger_msg(sender,**kwargs):
	global trigger_allowed
	global time
	global smart_trigger

	if kwargs["on"] =="1":
		trigger_allowed=1
		time=kwargs["time"]
		smart_trigger=kwargs["smart_trigger"]
	elif kwargs["on"] == "0":
		trigger_allowed=0

#send time of shutter
@receiver(trigger_status)
def status_trigger_msg(sender,**kwargs):
	#tell GCS viewer the status update
	audience = {'broadcast': True}
	redis_publisher = RedisPublisher(facility='viewer',**audience)

		#Serialize pathname
	response_data = simplejson.dumps({'time':kwargs["time"]})

	#send to url to websocket
	redis_publisher.publish_message(RedisMessage(response_data))


#ask should start taking pic
class TriggerDroid(View):
	def post(self,request):

		global trigger_allowed
		global time
		global smart_trigger
		json_request = simplejson.loads((request.body).decode('utf-8'))



		#droid is asking to trigger
		if json_request["trigger"] == "1":

			if trigger_allowed is 0:

				trigger_allowed=-1
				return HttpResponse("NO")
			elif trigger_allowed is 1:

				trigger_allowed=-1

				return HttpResponse(simplejson.dumps({"time":time,"smart_trigger":smart_trigger}),'application/json')
			elif trigger_allowed is -1:
				trigger_allowed=-1
				return HttpResponse("NOINFO")
			#droid is telling time of shutter trigger
		elif json_request["status"] == "1":

			trigger_status.send(self.__class__,time=json_request["dateTime"])
			return HttpResponse("Success")


#tell phone to start taking pics
class TriggerGCS(View):
	def post(self,request):
		if request.is_ajax():

			if time == "0":
				return HttpResponse(simplejson.dumps({"failure":"invalid time interval"}),'application/json')
			trigger.send(sender=self.__class__,on=request.POST["trigger"],time=request.POST["time"],smart_trigger=request.POST["smart_trigger"])
			return HttpResponse(simplejson.dumps({"Success":"Success"}),'application/json')
