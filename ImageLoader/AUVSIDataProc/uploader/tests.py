from django.test import TestCase

from django.utils import unittest
from django.test.client import RequestFactory, Client

import json as simplejson
from .views import *


#Acts as a client posting
class ClientRequest(unittest.TestCase):

	#create client
	def setUp(self):
		self.factory = RequestFactory()

	
	def test_post(self):
		#create http request object
		request = self.factory.post('/upload',{'text':'hello'})

		up=Upload()
		#post
		response = Upload.post(up,request)
		#print the server's response	
		
		#test response
		self.assertEqual(response.content,"success")
	def test_get(self):

		#create http request object
		request = self.factory.post('/upload',{'text':'hello'})

		up=Upload()
		#post
		response = Upload.post(up,request)
		#print the server's response	
		


		client = Client()
		#data = simplejson.dumps({"pk":1})
		#print(data)
		response = client.post('/viewpic',{'pk':1,},HTTP_X_REQUESTED_WITH='XMLHttpRequest')

		#test response
		self.assertEqual(response.content,simplejson.dumps({'picture':'hello',}))
		