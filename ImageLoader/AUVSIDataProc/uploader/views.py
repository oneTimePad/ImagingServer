from django.shortcuts import render

from django.http import HttpResponse,HttpResponseRedirect,HttpResponseForbidden
import json as simplejson
# Create your views here.

from django.views.generic.base import View
from .models import *

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
		Picture.objects.create(text=text,photo=pic)
		#return success
		return HttpResponse("success")	

class ViewPictures(View):

	def post(self,request):
		
		#look for ajax request
		if request.is_ajax():
			
			req_post = request.POST
					
			#the pk of the picture the client is looking for
			num_pic = req_post["pk"]

			

			#get the picture from SQL
			picture = Picture.objects.get(pk=num_pic)

			#get the local path of the pic
			path = picture.photo.file

			#Serialize pathname
			response_data = simplejson.dumps({'picture':str(path)})

			#response with path name JSON
			return HttpResponse(response_data,content_type="application/json")
		else:
			#else forbidden request
			return HttpResponseForbidden()


			
		




