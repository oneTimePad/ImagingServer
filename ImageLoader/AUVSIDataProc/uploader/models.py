from django.db import models

# Create your models here.



class Picture(models.Model):
	#picture object
	picture = models.CharField(max_length=100)




class Target(models.Model):

	#targets relate to pictures
	picture = models.ForeignKey('Picture')

	'attributes'


