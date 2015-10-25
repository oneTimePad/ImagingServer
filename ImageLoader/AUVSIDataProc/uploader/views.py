from django.shortcuts import render

from django.http import HttpResponse,HttpResponseRedirect,HttpResponseForbidden
import json as simplejson
# Create your views here.

from django.views.generic.base import View, TemplateResponseMixin
from django.views.generic.list import MultipleObjectMixin, MultipleObjectTemplateResponseMixin

from .models import *
#import pdb; pdb.set_trace()

from django.db.models.signals import *
from django.dispatch import *

from ws4redis.publisher import RedisPublisher
from ws4redis.redis_store import RedisMessage




#prev_pic = 0

IMAGE_STORAGE = "http://localhost/PHOTOS"


image_done = Signal(providing_args=["num_pic"])

#make so that RedisPublish doesn't get initilized
def initialize(func):

	def wrapper(*args,**kwargs):
		#used for broadcast msg to websocket
		audience = {'broadcast': True}
		redis_publisher = RedisPublisher(facility='viewer',**audience)
		print(args)
		return func(redis_publisher,*args,**kwargs)

	return wrapper





class Upload(View):

	#post request to create pictures
	def post(self,request,*args,**kwargs):
		#get request
		req_post = request.POST
		#get data


		#get image text
		text = request.POST['text']

		#get actual image
		pic = request.FILES['image']

		#create picture
		picture = Picture.objects.create(text=text,photo=pic)

		#trigger signal
		image_done.send(sender=self.__class__,num_pic=picture.pk)
		#return success
		return HttpResponse("success")


#triggered when image object created
@initialize
def send_pic(pub,num_pic,**kwargs):
	#create pic
	picture = Picture.objects.get(pk=num_pic)


	#get the local path of the pic

	path = picture.photo

	#Serialize pathname
	response_data = simplejson.dumps(IMAGE_STORAGE+str(path)[1:])

	#send to url to websocket
	pub.publish_message(RedisMessage(response_data))


image_done.connect(send_pic)

#server webpage
class Index(View,TemplateResponseMixin):
	template_name = 'indexWS.html'

	content_type='text/html'


	def get_context(self):
		#nothing to do for now
		pass


	def get(self,request):
		return self.render_to_response(self.get_context())



'''
class ViewPictures(View):

	def post(self,request,func=None):
		print("hello")

		#look for ajax request
		if request.is_ajax():

			req_post = request.POST

			#the pk of the picture the client is looking for
			num_pic = req_post["pk"]




			#get the picture from SQL
			try:

				picture = Picture.objects.get(pk=num_pic)


			except Picture.DoesNotExist:
				data = simplejson.dumps({'picture':0})

				return HttpResponse(data,content_type="application/json")


			#get the local path of the pic

			path = picture.photo.file

			#Serialize pathname
			response_data = simplejson.dumps({'picture':str(path)})

			#response with path name JSON
			print(response_data)
			return HttpResponse(response_data,content_type="application/json")
		else:
			#else forbidden request
			return HttpResponseForbidden()

'''
