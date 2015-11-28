from django.db import models

import Image

from matplotlib import cm

import StringIO

import cv2

import numpy as np


from colorful.fields import RGBColorField


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
	#use a related manager to get the list of targets for a specific picture
	text = models.CharField(max_length=100)

	photo = models.ImageField(storage=fs,default=0)

	#latitude and longitude of camera position
	orientation = models.CharField(max_length=20)
	lat = models.DecimalField(max_digits=9, decimal_places=6)
	lon = models.DecimalField(max_digits=9, decimal_places=6)
	#pixel coordinates of camera location
	xcoord = models.IntegerField()
	ycoord = models.IntegerField()
	ppm = models.DecimalField(max_digits=9, decimal_places=6)


class Target(models.Model):
	ORIENTATION_CHOICES = (
		('N','N')
		('NE','NE')
		('E','E')
		('SE','SE')
		('S','S')
		('SW','SW')
		('W','W')
		('NW','NW')
	)

	SHAPE_CHOICES = (
		('CIR','Circle')
		('SCI','Semicircle')
		('QCI','Quarter Circle')
		('TRI','Triangle')
		('SQU','Square')
		('REC','Rectangle')
		('TRA','Trapezoid')
		('PEN','Pentagon')
		('HEX','Hexagon')
		('HEP','Heptagon')
		('OCT','Octagon')
		('STA','Star')
		('CRO','Cross')
	)
	#targets relate to pictures
	picture = models.ForeignKey('Picture')
	target_pic = models.ImageField(storage=fs_targets)
	color = models.CharField(max_length=10)
	lcolor = models.CharField(max_length=10)
	orientation = models.ChoiceField(max_length=2,choices=ORIENTATION_CHOICES)
	shape = models.ChoiceField(max_length=3,choices=SHAPE_CHOICES)
	letter = models.CharField(max_length=1)
	#latitude and longitude for top left corner of target cropped image
	lat = models.DecimalField(max_digits=9, decimal_places=6)
	lon = models.DecimalField(max_digits=9, decimal_places=6)

	#crop target from image
	def crop(self,size_data,parent_pic):#right now the gps coordinates are not right, need to change based on the app
		self.picture=parent_pic
		size_data = attributes['size_data']

		#unpackage crop data
		x,y,height,width = size_data


		#get the file name of pic=pk
		file_name  =str(parent_pic.photo.file)

		#read in that image
		original_image = cv2.imread(file_name)

		#convert strange json format to integers
		x = int(x)
		y= int(y)

		#crop the image
		cropped_image = original_image[y:(y+int(height[0])),x:(x+int(width[0])),]


		#convert numpy array to image
		image_cropped_image = Image.fromarray(cropped_image,mode='RGB')

		#string as file
		image_io = StringIO.StringIO()

		#save image to stringIO file as JPEG
		image_cropped_image.save(image_io,format='JPEG')


		#convert image to django recognized format
		django_cropped_image = InMemoryUploadedFile(im_io,None,"target"+str(target.pk).zfill(4)+'.jpeg','image/jpeg',image_io.len,None)

		#assign target image to target object
		self.target_pic=django_cropped_image

		#setting gps loc of target
		ppm = parent_pic.ppm
		imglat = parent_pic.lat
		imglong = parent_pic.long
		xcoord = parent_pic.xcoord
		ycoord = parent_pic.ycoord
		self.lat = imglat + (y-ycoord)*ppm
		self.long = imglong + (x-xcoord)*ppm

		#save to db
		self.save()
