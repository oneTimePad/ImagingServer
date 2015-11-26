from django.shortcuts import render

from django.http import HttpResponse,HttpResponseRedirect,HttpResponseForbidden,JsonResponse
import json as simplejson
# Create your views here.

from django.views.generic.base import View, TemplateResponseMixin, ContextMixin

from django.views.generic.edit import FormMixin

import os

from .models import *
#import pdb; pdb.set_trace()

from django.db.models.signals import *
from django.dispatch import *

from ws4redis.publisher import RedisPublisher
from ws4redis.redis_store import RedisMessage

from .forms import AttributeForm
import pdb





#hard coded
IMAGE_STORAGE = "http://localhost:80/PHOTOS"


image_done = Signal(providing_args=["num_pic"])
#Have a signal for creating a new target?

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
		#pdb.set_trace()

		req_post = request.POST
		#get data


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
	response_data = simplejson.dumps({'type':'picture','image':IMAGE_STORAGE+str(path)[1:],'pk':picture.pk})

	#send to url to websocket
	pub.publish_message(RedisMessage(response_data))


image_done.connect(send_pic)

#would this work so that the redis publisher is made only once?
# @initialize
# def send_target(pub,num_target,**kwargs):
# 	target = Target.objects.get(pk=num_target)
# 	path = target.target_pic
# 	response_data = simplejson.dumps({'type','target','image':IMAGE_STORAGE+str(path)[1:],'pk':target.pk})
# 	pub.publish_message(RedisMessage(response_data))
#actually I could just have it sent when the user focuses on a new image...

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

			pdb.set_trace()
			pic_id = request.POST['pk']

			picture = Picture.objects.get(pk=pic_id)

			photo_path = picture.photo.path
			os.remove(photo_path)

			picture.delete()

			return HttpResponse("success")

class GetTargets(View):

	def get(self,request):
		if request.is_ajax():
			pdb.set_trace()
			pic_id = request.GET['pk']
			picture = Picture.objects.get(pk=pic_id)
			#should get all the related targets using a related manager
			targets = picture.target_set
			dict={}
			tdata=[]
			for t in targets:
				tdata.append({"pk":t.pk,"image":t.target_pic})
			dict["targets"]=tdata
			response_data = simplejson.dumps(dict)
			return JsonResponse(response_data)

#manual attribute form
class AttributeFormCheck(View):



	def post(self,request):

		if request.is_ajax():


			pdb.set_trace()

			#post data
			post_vars= self.request.POST
			#convert to dict
			post_vars=dict(post_vars)

			#get client color
			color = post_vars['attr[color]']

			#create target object
			target = Target.objects.create(color=int(color[0]))

			#package crop data to tuple
			size_data=(post_vars['crop[corner][]'][0],post_vars['crop[corner][]'][1],post_vars['crop[height]'],post_vars['crop[width]'])


			#get parent image pk
			parent_image = post_vars['pk']
			#get parent pic from db
			parent_pic = Picture.objects.get(pk=parent_image[0])

			#add parent to target relation
			#target.pictures.add(parent_pic)
			target.picture=parent_pic

			#crop target
			target.crop(size_data=size_data,parent_pic=parent_pic)

			return HttpResponse("success")
