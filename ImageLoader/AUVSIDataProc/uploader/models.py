from PIL import Image
from matplotlib import cm
from io import BytesIO
import cv2
import numpy as np
import pdb
from django.db import models
from django.core.files.uploadedfile import InMemoryUploadedFile
from django.core.files.storage import FileSystemStorage
# Create your models here.

STORAGE = '/var/www/html/PHOTOS/'
STORAGE_Target = '/var/www/html/TARGETS/'

#uses django storage, change path to fit yours
fs = FileSystemStorage(location=STORAGE)
fs_targets = FileSystemStorage(location=STORAGE_Target)

class Picture(models.Model):
	#picture object
	#use a related manager to get the list of targets for a specific picture
	fileName = models.CharField(max_length=100,default="photo")
	photo = models.ImageField(storage=fs,default=0)

	# These are just to make backups. None of this is actually
	#needed
	azimuth = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	pitch = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	roll =models.DecimalField(max_digits=9, decimal_places=6,default=0)

	lat = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	lon = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	alt = models.DecimalField(max_digits=9, decimal_places=6,default=0)

	#pixels per meter
	ppm = models.DecimalField(max_digits=9, decimal_places=6,default=0)

	topLeftX = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	topLeftY = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	topRightX = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	topRightY = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	bottomLeftX = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	bottomLeftY = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	bottomRightX = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	bottomRightY = models.DecimalField(max_digits=9, decimal_places=6,default=0)

class Target(models.Model):
	ORIENTATION_CHOICES = (
		('N','N'),
		('NE','NE'),
		('E','E'),
		('SE','SE'),
		('S','S'),
		('SW','SW'),
		('W','W'),
		('NW','NW'),
	)

	SHAPE_CHOICES = (
		('CIR','Circle'),
		('SCI','Semicircle'),
		('QCI','Quarter Circle'),
		('TRI','Triangle'),
		('SQU','Square'),
		('REC','Rectangle'),
		('TRA','Trapezoid'),
		('PEN','Pentagon'),
		('HEX','Hexagon'),
		('HEP','Heptagon'),
		('OCT','Octagon'),
		('STA','Star'),
		('CRO','Cross'),
	)
	#targets relate to pictures
	picture = models.ForeignKey('Picture')
	target_pic = models.ImageField(storage=fs_targets,default=0)
	color = models.CharField(max_length=10)
	lcolor = models.CharField(max_length=10)
	orientation = models.CharField(max_length=2,choices=ORIENTATION_CHOICES)
	shape = models.CharField(max_length=3,choices=SHAPE_CHOICES)
	letter = models.CharField(max_length=1)
	#latitude and longitude for top left corner of target cropped image
	lat = models.DecimalField(max_digits=9, decimal_places=6, default=0)
	lon = models.DecimalField(max_digits=9, decimal_places=6, default=0)

	#crop target from image
	def crop(self,size_data,parent_pic):#right now the gps coordinates are not right, need to change based on the app



		self.picture=parent_pic


		#unpackage crop data
		x,y,height,width = size_data


		#get the file name of pic=pk
		file_name  =str(parent_pic.photo.file)

		#read in that image
		#original_image = cv2.imread(file_name)
		#original_image = cv2.resize(original_image,(400,400))
		original_image = Image.open(file_name)

		#convert strange json format to integers
		orig_height = 960 #1020 for AUVSI camera
		view_height = 400
		x = int(int(x)*orig_height/view_height)
		y= int(int(y)*orig_height/view_height)
		width = int(int(width[0])*orig_height/view_height)
		height = int(int(height[0])*orig_height/view_height)

		#crop the image
		#cropped_image = original_image[y:(y+height),x:(x+width)]
		cropped_image = original_image.crop((x,y,x+width,y+height))

		#convert numpy array to image
		#image_cropped_image = Image.fromarray(cropped_image,mode='RGB')

		#string as file
		image_io = BytesIO()

		#save image to stringIO file as JPEG
		cropped_image.save(image_io,format='JPEG')


		#convert image to django recognized format
		django_cropped_image = InMemoryUploadedFile(image_io,None,"Target"+str(self.pk).zfill(4)+'.jpeg','image/jpeg',image_io.getbuffer().nbytes,None)

		#assign target image to target object
		self.target_pic=django_cropped_image


		#save to db
		self.save()
