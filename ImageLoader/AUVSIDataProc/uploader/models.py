from django.db import models

from django.core.files.storage import FileSystemStorage
# Create your models here.

STORAGE = '/home/lie/Desktop/PHOTOS'

#uses django storage, change path to fit yours
fs = FileSystemStorage(location=STORAGE)

class Picture(models.Model):
	#picture object
	text = models.CharField(max_length=100)

	photo = models.ImageField(storage=fs,default=0)




class Target(models.Model):

	#targets relate to pictures
	picture = models.ForeignKey('Picture')

	'attributes'


