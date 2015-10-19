from django.test import TestCase

from django.utils import unittest
from django.test.client import RequestFactory


from .views import *


#Acts as a client posting
class ClientRequest(unittest.TestCase):

	#create client
	def setUp(self):
		self.factory = RequestFactory()


	def test_details(self):
		#create http request object
		request = self.factory.post('/upload',{'text':'hello'})

		up=Upload()
		#post
		response = Upload.post(up,request)
		#print the server's response
		print(response)