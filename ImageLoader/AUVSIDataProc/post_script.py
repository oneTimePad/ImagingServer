from django.test.client import Client

c = Client()

data = {'text':'test','image':open('/home/lie/Desktop/picss/capt0001.jpg')}

c.post('/upload',data)
