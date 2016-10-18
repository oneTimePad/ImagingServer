from django.db import models
from django.utils import timezone

# Create your models here.
class ImagePost(models.Model):
	pic = models.ImageField(upload_to = 'pics/')
	time_stamp = models.DateTimeField(default=timezone.localtime(timezone.now()))
	
	def __str__(self):
		return ("Name: " + str(self.pic.name) + ", Timestamp: " + str(self.time_stamp))