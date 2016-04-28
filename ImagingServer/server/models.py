#general
from PIL import Image
from matplotlib import cm
from io import BytesIO
import math
#import cv2
import numpy as np
import os
import pdb
from decimal import *

#django
from django.db import models
from django.core.files.uploadedfile import InMemoryUploadedFile
from django.core.files.storage import FileSystemStorage
from django.contrib.auth.models import AbstractUser
from django.contrib.sessions.models import Session
from django.conf import settings

#django-rest
from rest_framework.authtoken.models import Token

#important storage constants
STORAGE = os.getenv("PICTURE_STORAGE", '/var/www/html/PHOTOS/')
STORAGE_Target = os.getenv("TARGET_STORAGE",'/var/www/html/TARGETS/')

#uses django storage, change path to fit yours
fs = FileSystemStorage(location=STORAGE)
fs_targets = FileSystemStorage(location=STORAGE_Target)

# Camera information. Nexus 6P in portrait
# Field of View angles (1/2 image viewing angles)
fovV = 35.0 # Portrait
fovH = 26.5 # Landscape

# Conversion between meters to GPS coordinate degrees
# ONLY FOR MARYLAND LOCATION
# Every 0.8627 meters is about 0.00001 degrees Lat/Lon
METER_TO_DEGREE_CONVERSION = 0.00001/0.8627

class ImagingUser(AbstractUser):

	userType = models.CharField(max_length=100,default="none")
	REQUIRED_FIELDS = ['userType']

class GCSSession(models.Model):
	user = models.ForeignKey(settings.AUTH_USER_MODEL)
	session =models.ForeignKey(Session)


class Picture(models.Model):
	fileName = models.CharField(max_length=100,default="photo")
	photo = models.ImageField(storage=fs,default=0)
	azimuth = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	pitch = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	roll =models.DecimalField(max_digits=9, decimal_places=6,default=0)
	lat = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	lon = models.DecimalField(max_digits=9, decimal_places=6,default=0)
	alt = models.DecimalField(max_digits=9, decimal_places=6,default=0)


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
		('circle','circle'),
		('semicircle','semicircle'),
		('quarter circle','quarter circle'),
		('triangle','triangle'),
		('square','square'),
		('rectangle','rectangle'),
		('trapezoid','trapezoid'),
		('pentagon','pentagon'),
		('hexagon','hexagon'),
		('heptagon','heptagon'),
		('octagon','octagon'),
		('star','star'),
		('cross','cross'),
	)

	TARGET_TYPES = (
		('standard','Standard'),
		('qrc','QR Code'),
		('off_axis','Off-Axis'),
		('emergent','Emergent')
	)

	picture = models.ImageField(storage=fs_targets,default=0)
	#target data
	ptype = models.CharField(max_length=20,choices=TARGET_TYPES)
	sent = models.BooleanField(default=False)
	#latitude and longitude for top left corner of target cropped image
	latitude = models.DecimalField(max_digits=9, decimal_places=6, default=0)
	longitude = models.DecimalField(max_digits=9, decimal_places=6, default=0)
	orientation = models.CharField(max_length=2,null=True,blank=True,choices=ORIENTATION_CHOICES)
	shape = models.CharField(max_length=14,null=True,blank=True,choices=SHAPE_CHOICES)
	background_color = models.CharField(max_length=20,null=True,blank=True)
	alphanumeric = models.CharField(max_length=1,null=True,blank=True)
	alphanumeric_color = models.CharField(max_length=20,null=True,blank=True)
	description = models.CharField(max_length=200,null=True,blank=True)

	def edit(self,edits):
		self.alphanumeric=edits['alphanumeric']
		self.alphanumeric_color = edits['alphanumeric_color']
		self.background_color = edits['background_color']
		shapeChoices = dict((x,y) for x,y in Target.SHAPE_CHOICES)
		self.shape = str(shapeChoices[edits['shape']])
		self.orientation = edits['orientation']
		self.ptype = edits['ptype']
		self.description = edits['description']
		self.save()

	def wasSent(self):
		self.sent = True

	def findWorldCoords(self,x,y,orig_width,orig_height):
		# divide full width / height by 2 cuz we don't need that crap
		orig_width = orig_width / 2
		orig_height = orig_height / 2

		# set (0.0) as center of image
		x -= orig_width
		y -= orig_height

		# find real-life location of click point
		# assume altitude is 1 for now,
		# since it gets rescaled later based off rotation
		tempX = (x / orig_width) * math.tan(math.radians(fovH))
		tempY = (y / orig_height) * math.tan(math.radians(fovV))

		return np.matrix([[tempX], [tempY], [1]])

	def rotateByAngles(self, worldCoords, altitude, azimuth, pitch, roll):
		# woo wikipedia
		rotX = np.matrix([ 	[1, 0, 0],
					[0, math.cos(pitch), -math.sin(pitch)],
					[0, math.sin(pitch), math.cos(pitch)] ])

		rotY = np.matrix([ 	[math.cos(roll), 0, math.sin(roll)],
					[0, 1, 0],
					[-math.sin(roll), 0, math.cos(roll)] ])

		rotZ = np.matrix([ 	[math.cos(azimuth), -math.sin(azimuth), 0],
					[math.sin(azimuth), math.cos(azimuth), 0],
					[0, 0, 1] ])

		# compose matrix, rotate the world coords
		rotFull = np.dot(rotX, np.dot(rotY, rotZ))
		rotatedCoords = np.dot(rotFull, worldCoords)

		# rescale so that they touch the ground
		scaledCoords = []
		for coord in np.nditer(rotatedCoords):
			scaledCoords.append(float(altitude / rotatedCoords[2]) * coord)

		return scaledCoords

	'''GEOTAGGING STUFF GOES HERE '''
	#crop target from image
	def crop(self,size_data,parent_pic):#right now the gps coordinates are not right, need to change based on the app

		#get the file name of pic=pk and open image
		original_image = Image.open(str(parent_pic.photo.file))

		#convert strange json format to integers
		orig_width,orig_height = original_image.size #1020 for AUVSI camera

		#unpackage crop data
		scale_width = int(size_data['scaleWidth'])
		x = int(int(size_data['x']) * orig_width / scale_width)
		y = int(int(size_data['y']) * orig_width / scale_width)
		width = int(int(size_data['width']) * orig_width / scale_width)
		height = int(int(size_data['height']) * orig_width / scale_width)

		if not scale_width or not orig_width or not orig_height:
			print('Data is screwy. Exiting early.')
			return

		cropped_image = original_image.crop((x,y,x+width,y+height))

		image_io = BytesIO()

		#save image to stringIO file as JPEG
		cropped_image.save(image_io,format='JPEG')

		#convert image to django recognized format
		django_cropped_image = InMemoryUploadedFile(image_io,None,"Target"+str(self.pk).zfill(4)+'.jpeg','image/jpeg',image_io.getbuffer().nbytes,None)

		#assign target image to target object
		self.picture=django_cropped_image

		# GEOTAGGING STUFF
		# Get information on camera angles
		azimuth = float(math.radians(parent_pic.azimuth)) # Angle from North
		pitch = float(math.radians(parent_pic.pitch)) # Forward/back angle
		roll = float(math.radians(parent_pic.roll)) # Left/Right angle
		altitude = float(parent_pic.alt)
		gpsLatitude = float(parent_pic.lat)
		gpsLongitude = float(parent_pic.lon)

		if not altitude:
			print('Altitude is 0. Skipping geotagging.')
			self.save()
			return

		worldCoords = findWorldCoords(x,y,orig_width,orig_height)
		rotatedCoords = rotateByAngles(worldCoords, altitude, azimuth, pitch, roll)
		latOffset, lonOffset, _ = [METER_TO_DEGREE_CONVERSION * num for num in rotatedCoords]

		# ************************* MOST IMPORTANT INFORMATION ******************************
		# This is the calculated Latitude, Longitude of the point
		self.latitude = latOffset + gpsLatitude
		self.longitude = lonOffset + gpsLongitude

		#save to db
		self.save()
