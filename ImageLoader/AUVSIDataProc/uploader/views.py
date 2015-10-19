from django.shortcuts import render

from django.http import HttpResponse,HttpResponseRedirect
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
			
		




