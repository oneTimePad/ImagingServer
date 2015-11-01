from django.db import models

import cv2



from django.core.files.storage import FileSystemStorage
# Create your models here.

STORAGE = '/var/www/html/PHOTOS'
STORAGE_Target = '/var/www/html/TARGETS'

#uses django storage, change path to fit yours
fs = FileSystemStorage(location=STORAGE)
fs_targets = FileSystemStorage(location=STORAGE_Target)

import pdb

class Picture(models.Model):
	#picture object
	text = models.CharField(max_length=100)

	photo = models.ImageField(storage=fs,default=0)




class Target(models.Model):

	#targets relate to pictures
	pictures = models.ManyToManyField(Picture)
	target_pic = models.ImageField(storage=fs_targets)
	color = models.IntegerField()
	
	@staticmethod
	def crop(**attributes):
		
		
		picture_pk = attributes['picture_pk']
		color = attributes['color']
		size_data = attributes['size_data']


		x,y,height,width = size_data

		
		pdb.set_trace()

		file_name  =str(Picture.objects.get(pk=picture_pk[0]).photo.file)
		original_image = cv2.imread(file_name)

		x = int(x)
		y= int(y)

		x-=30
		y-=21
		cropped_image = original_image[x:(x+int(width[0])),y:(y+int(height[0]))]
		#cv2.imwrite(STORAGE+"/targets"+"/"+"")

		target = Target.objects.create(target_pic=cropped_image,color=color)
		
		target.pictures.add(pictures_pk)


