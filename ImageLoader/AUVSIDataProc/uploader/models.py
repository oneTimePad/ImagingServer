from django.db import models

from django.core.files.storage import FileSystemStorage
# Create your models here.

fs = FileSystemStorage(location='/home/lie/Desktop/PHOTOS')

class Picture(models.Model):
	#picture object
	picture = models.CharField(max_length=100)

	photo = models.ImageField(storage=fs,default=0)




class Target(models.Model):

	#targets relate to pictures
	picture = models.ForeignKey('Picture')

	'attributes'


