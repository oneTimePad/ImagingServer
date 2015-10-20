from django.test import TestCase

from django.utils import unittest
from django.test.client import RequestFactory, Client



import json as simplejson
from .views import *


#Acts as a client posting
class ClientRequest(unittest.TestCase):

	#create client
	def setUp(self):
		self.client = Client()

	
	def test_post(self):

		image = open('/home/lie/Desktop/picss/capt0001.jpg')
		
		#create http request object
		data = {'text':'hello','image':image}
		
		response =self.client.post('/upload',data)
		
		#test response
		self.assertEqual(response.content,"success")
	def test_get(self):

		image = open('/home/lie/Desktop/picss/capt0001.jpg')
		
		#create http request object
		data = {'text':'hello','image':image}
		
		response =self.client.post('/upload',data)

		
		response = self.client.post('/viewpic',{'pk':1,},HTTP_X_REQUESTED_WITH='XMLHttpRequest')

		#test response
		self.assertEqual(response.content,simplejson.dumps({'picture':'hello',}))
