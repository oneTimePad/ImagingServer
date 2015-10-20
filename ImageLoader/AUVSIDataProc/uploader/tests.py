from django.test import TestCase

from django.utils import unittest
from django.test.client import RequestFactory, Client



import json as simplejson
from .views import *

IMAGE_PATH = '/home/lie/Desktop/'
IMAGE_FOLDER = 'picss/'
IMAGE_NAME = 'capt0001.jpg'
STORAGE='PHOTOS/'

#Acts as a client posting
class ClientRequest(unittest.TestCase):

	#create client
	def setUp(self):
		self.client = Client()

	
	def test_get(self):


		image = open(IMAGE_PATH+IMAGE_FOLDER+IMAGE_NAME)
		
		#create http request object
		data = {'text':'hello','image':image}
		
		response =self.client.post('/upload',data)

		self.assertEqual(response.content,"success")

		
		response = self.client.post('/viewpic',{'pk':1,},HTTP_X_REQUESTED_WITH='XMLHttpRequest')

		#test response
		self.assertEqual(response.content,simplejson.dumps({'picture':IMAGE_PATH+STORAGE+IMAGE_NAME}))
