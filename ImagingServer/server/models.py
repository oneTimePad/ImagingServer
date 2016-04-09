#general
from PIL import Image
from matplotlib import cm
from io import BytesIO
import math
import cv2
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
		('Circle','Circle'),
		('Semicircle','Semicircle'),
		('Quarter Circle','Quarter Circle'),
		('Triangle','Triangle'),
		('Square','Square'),
		('Rectangle','Rectangle'),
		('Trapezoid','Trapezoid'),
		('Pentagon','Pentagon'),
		('Hexagon','Hexagon'),
		('Heptagon','Heptagon'),
		('Octagon','Octagon'),
		('Star','Star'),
		('Cross','Cross'),
	)

	#target image
	picture = models.ImageField(storage=fs_targets,default=0)

	#target data
	ptype = model.CharField(max_length=20)
	#latitude and longitude for top left corner of target cropped image
	latitude = models.DecimalField(max_digits=9, decimal_places=6, default=0)
	longitude = models.DecimalField(max_digits=9, decimal_places=6, default=0)
	orientation = models.CharField(max_length=2,choices=ORIENTATION_CHOICES)
	shape = models.CharField(max_length=14,choices=SHAPE_CHOICES)
	background_color = models.CharField(max_length=20)
	alphanumeric = models.CharField(max_length=20)
	alphanumeric_color = models.CharField(max_length=1)
	autonomous = models.BooleanField(default=False)

	def edit(self,edits):
		self.ptype = edits['ptype']
		self.letter=edits['alphanumeric']
		self.color = edits['background_color']
		self.lcolor = edits['alphanumeric_color']
		shapeChoices = dict((x,y) for x,y in Target.SHAPE_CHOICES)
		self.shape = str(shapeChoices[edits['shape']])
		self.orientation = edits['orientation']
		self.save()

	# Calculates the angle between two points.
	# Used to get the angle between the center GPS location
	#  and the cropped location.
	def angle_between_points(pt1, pt2):
		x1, y1 = pt1
		x2, y2 = pt2
		inner_product = x1*x2 + y1*y2
		len1 = math.hypot(x1, y1)
		len2 = math.hypot(x2, y2)
		return math.acos(inner_product/(len1*len2))

	'''GEOTAGGING STUFF GOES HERE '''
	#crop target from image
	def crop(self,size_data,parent_pic):#right now the gps coordinates are not right, need to change based on the app

		#get the file name of pic=pk and open image
		original_image = Image.open(str(parent_pic.photo.file))

		#convert strange json format to integers
		orig_width,orig_height = original_image.size #1020 for AUVSI camera

		#unpackage crop data
		scale_width = int(size_data[2])
		x,y,_,width,height = [int(int(data) * orig_width / scale_width) for data in size_data]

		if not scale_width or not orig_width or not orig_height:
			print('Data is screwy. Exiting early.')
			return
		# x = int(x)
		# y = int(y)
		# height = int(height)
		# width = int(width)
		# scale_width = int(scale_width)

		# x = int(x*orig_width/scale_width)
		# y = int(y*orig_width/scale_width)
		# width = int(width*orig_width/scale_width)
		# height = int(height*orig_width/scale_width)

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
		azimuth = float(parent_pic.azimuth) # Angle from North
		pitch = float(parent_pic.pitch) # Forward/back angle
		roll = float(parent_pic.roll) # Left/Right angle
		altitude = float(parent_pic.alt)
		if not altitude:
			print('Altitude is 0. Skipping geotagging.')
			self.save()
			return

		# Calculate the edge angles of the image
		# Top left of image is 0,0
		angle_V_0 = pitch + fovV # Top of image
		angle_V_1 = pitch - fovV # Bottom of image
		angle_H_0 = roll - fovH # Left sied of image
		angle_H_1 = roll + fovH # Right side of image

		# Calculate the total distance (meters) that the image spans
		totalVDistance = altitude * ( math.tan(math.radians(angle_V_0)) - math.tan(math.radians(angle_V_1)) )
		totalHDistance = altitude * ( -math.tan(math.radians(angle_H_0)) + math.tan(math.radians(angle_H_1)) )

		# Ratio between the altitude height and the vertical pixel
		# count and vertical distance
		# Pixels      0 - img_V_pixels
		# Distance    0 - altitude m
		altitude_pixels = (((altitude/totalVDistance)*orig_height) + ((altitude/totalHDistance)*orig_width))/2

		# Calculate the distance from the center of the image to the center of gps
		deltaYGPS = altitude_pixels * math.sin(math.radians(pitch))
		deltaXGPS = altitude_pixels * math.sin(math.radians(roll))

		# The pixels for the y direction go "UP" when
		# the pixel goes towards the bottom of the image
		# REMEMBER top left of image is 0,0
		#          bottom right of image is max,max
		gpsX = orig_width/2 + deltaXGPS
		gpsY = orig_height/2 + deltaYGPS

		# These values are to help with calculating the angle
		# between the point and the GPS center
		northX = gpsX + gpsX * math.cos( math.radians(azimuth + 90))
		northY = gpsY + gpsY * math.sin( math.radians(azimuth + 90))

		# Get the GPS coordinates of the crop location
		# crop_Lat,cropLon = calculate_coordinates(x,y)
		# Interpolate the relative angle from Tangent to ground
		relXAngle = (roll - fovH) + (2*fovH)*( float(x) / float(orig_height))
		relYAngle = (pitch - fovV) - (2*fovV)*( float(y) / float(orig_width))

		relXRadian = math.radians(relXAngle)
		relYRadian = math.radians(relYAngle)
		azimuthRadian = math.radians(azimuth)

		# Use the relative angle from GPS tangent to point
		# to determine the distance removed from GPS center
		# Value calculated in pixels
		deltaX = altitude_pixels * math.sin(relXRadian)
		deltaY = altitude_pixels * math.sin(relYRadian)
		deltaMagnitude = math.hypot(deltaX, deltaY)

		# Angle between the Camera's North and the Point
		angleNorthPoint = angle_between_points([northX-gpsX,northY-gpsY], [x-gpsX,y-gpsY])

		# Check if the click is to the right or left of the North line
		# This equation determines if the click is to the left or right of the line
		# Negative = Left
		# Positive = Right
		# Don't need to check for if North/South, calculation already goes from -180 to 180
		isRight = (northX - gpsX)*(y - gpsY) - (northY - gpsY)*(x - gpsX)
		if (isRight < 0):
			angleNorthPt = -angleNorthPt

		# Now use the deltaMagnitude and compute the North/South and West/East components
		deltaNS = deltaMagnitude * math.cos(angleNorthPt) # The N/S component of the magnitude (in pixels)
		deltaWE = deltaMagnitude * math.sin(angleNorthPt) # The W/E component of the magnitude (in pixels)

		# Combine these with the GPS location to obtain the distance away
		# Depends on the azimuth and where on the screen is being clicked
		# In pixels
		ptX = int(gpsX + deltaWE)
		ptY = int(gpsY - deltaNS)

		# When the lines are drawn, they are drawn as though the image is pointing NORTH
		# So clicking on the green line will draw a blue line pointing up
		# Have to convert this value into GPS Lat and Long based on conversion of
		# Altitude / pixels from below
		ptX_meters = deltaWE * (altitude/altitude_pixels)
		ptY_meters = deltaNS * (altitude/altitude_pixels)

		# ************************* MOST IMPORTANT INFORMATION ******************************
		# This is the calculated Latitude, Longitude of the point
		self.lat = (ptY_meters * METER_TO_DEGREE_CONVERSION) + gpsLatitude
		self.lon = (ptX_meters * METER_TO_DEGREE_CONVERSION) + gpsLongitude

		#save to db
		self.save()
