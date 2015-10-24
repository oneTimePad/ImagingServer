from django.test.client import Client
from uploader.models import *


c = Client()
pic = open('/home/lie/Desktop/picss/capt0001.jpg')
data = {'text':'dsdad','image':pic}

c.post('/upload',data)
