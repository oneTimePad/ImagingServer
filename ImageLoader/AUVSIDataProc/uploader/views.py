from django.shortcuts import render

from django.http import HttpResponse,HttpResponseRedirect,HttpResponseForbidden
import json as simplejson
# Create your views here.

from django.views.generic.base import View, TemplateResponseMixin, ContextMixin
from django.views.generic.list import MultipleObjectMixin, MultipleObjectTemplateResponseMixin
from django.views.generic.edit import FormMixin



from .models import *
#import pdb; pdb.set_trace()

from django.db.models.signals import *
from django.dispatch import *

from ws4redis.publisher import RedisPublisher
from ws4redis.redis_store import RedisMessage

from .forms import AttributeForm
import pdb; 



#prev_pic = 0

IMAGE_STORAGE = "http://localhost:80/PHOTOS"


image_done = Signal(providing_args=["num_pic"])

#make so that RedisPublish doesn't get initilized
def initialize(func):

	def wrapper(*args,**kwargs):
		#used for broadcast msg to websocket
		audience = {'broadcast': True}
		redis_publisher = RedisPublisher(facility='viewer',**audience)
		
		return func(redis_publisher,*args,**kwargs)

	return wrapper





class Upload(View):

	#post request to create pictures
	def post(self,request,*args,**kwargs):
		#get request

		pdb.set_trace()
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


#triggered when image object created	`
@initialize
def send_pic(pub,num_pic,**kwargs):
	#create pic
	picture = Picture.objects.get(pk=num_pic)


	#get the local path of the pic

	path = picture.photo

	#Serialize pathname
	response_data = simplejson.dumps({'image':IMAGE_STORAGE+str(path)[1:],'pk':picture.pk})

	#send to url to websocket
	pub.publish_message(RedisMessage(response_data))


image_done.connect(send_pic)

#server webpage
class Index(View,TemplateResponseMixin,ContextMixin):
	template_name = 'indexWS.html'

	content_type='text/html'




	def get_context_data(self,**kwargs):

		context = super(Index,self).get_context_data(**kwargs)
		context['form'] = AttributeForm

		return context
		


	def get(self,request):

		return self.render_to_response(self.get_context_data())






class AttributeFormCheck(View):

	

	def post(self,request):
		
		if request.is_ajax():
			
			post_vars= self.request.POST

			post_vars=dict(post_vars)
			parent_image = post_vars['pk']

			
			color = post_vars['attr[color]']
			

			size_data=(post_vars['crop[corner][]'][0],post_vars['crop[corner][]'][1],post_vars['crop[height]'],post_vars['crop[width]'])


			
			Target.crop(picture_pk=parent_image,color = color,size_data=size_data)
			return HttpResponse("success")
