from django.db import models

import Image

from matplotlib import cm

import StringIO

import cv2

import numpy as np


from django.core.files.uploadedfile import InMemoryUploadedFile

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

		
		

		file_name  =str(Picture.objects.get(pk=picture_pk[0]).photo.file)
		original_image = cv2.imread(file_name)

		x = int(x)
		y= int(y)

		#x-=30
		#y-=21
		cropped_image = original_image[y:(y+int(height[0])),x:(x+int(width[0])),]
		
		
		target = Target.objects.create(color=int(color[0]))




		#path = STORAGE+"/target"+str(target.pk).zfill(4)+'.jpg'
		
		

		im = Image.fromarray(cropped_image,mode='RGB')

		im_io = StringIO.StringIO()

		im.save(im_io,format='JPEG')

		pdb.set_trace()

		im_file = InMemoryUploadedFile(im_io,None,str(target.pk).zfill(4)+'.jpeg','image/jpeg',im_io.len,None)

		target.target_pic=im_file

		target.pictures.add(Picture.objects.get(pk=int(picture_pk[0])))
		target.save()


