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
		request = request.POST
		#get data
		text = request['text']
		#create picture
		Picture.objects.create(picture=text)
		#return success
		return HttpResponse("success")	

class ViewPictures(View):

	def post(self,request):
		
		if request.is_ajax():
			
			req_post = request.POST
					
			num_pic = req_post["pk"]

			

			
			picture = Picture.objects.get(pk=num_pic)


			response_data = simplejson.dumps({'picture':picture.picture})


			return HttpResponse(response_data,content_type="application/json")
		else:
			return HttpResponseForbidden()


			
		




