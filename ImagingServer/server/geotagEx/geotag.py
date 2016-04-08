import cv2
import argparse
import math
import numpy as np

# [x,y] coordinates of clicks
left_clicks = list() # List of the actual pixel click location
point_clicks = list() # Calculated location of clicks for drawing the blue line
point_meters = list() # Converted the location into meters
click_gps = list() # The GPS location of the click

# Calculates the angle between the two given points
# Note: They must be from origin 0,0 (Normalize during input!!)
# Returns: Radian angle between the two points
def angle_between_points(pt1, pt2):
	x1, y1 = pt1
	x2, y2 = pt2
	inner_product = x1*x2 + y1*y2
	len1 = math.hypot(x1, y1)
	len2 = math.hypot(x2, y2)
	return math.acos(inner_product/(len1*len2))

# Define function that is called when click
def mouse_callback(event, x, y, flags, params):

	# If left click on image
	if event == 1:
		global left_clicks
		global point_clicks
		global point_meters
		global click_gps
		global img

		# Get the linearly interpolated value of the angle from the click
		# X is the pixel distance, and does not reflect the actual ground distance linearly
		# At pitch > 20 or roll > 30, the ground distance become much larger at the ends of the image
		# towards the horizon compared to the center of the image, or the opposite side of the image
		relXAngle = (roll - fovH) + (2 * fovH) * ( float(x) / float(img_H_pixels) )
		relYAngle = (pitch + fovV) - (2 * fovV) * ( float(y) / float(img_V_pixels) )

		# Convert to radians
		relXRadian = math.radians(relXAngle)
		relYRadian = math.radians(relYAngle*1.1)

		# Get the Relative X and Y distance to the point from the gps center
		# This is different from the North/South/East/West directions
		# Based on the azimuth
		deltaX = altitude_pixels * math.sin(relXRadian)
		deltaY = altitude_pixels * math.sin(relYRadian)
		deltaMagnitude = math.hypot(deltaX, deltaY) # In pixels
		azimuth_rad = math.radians(azimuth)

		# Get the angle between the Point and the North line
		# Subtract gps_ coordinates to make it like its from an origin 0,0
		angleNorthPt = angle_between_points([northX-gpsX, northY-gpsY], [x-gpsX,y-gpsY])

		# Check if the click is to the right or left of the North line
		# This equation determines if the click is to the left or right of the line
		# Negative = Left
		# Positive = Right
		# Don't need to check for if North/South, calculation already goes from -180 to 180
		isRight = (northX - gpsX)*(y - gpsY) - (northY - gpsY)*(x - gpsX)
		if (isRight < 0):
			angleNorthPt = -angleNorthPt
		#print("Angle from point to North: %.2f" % (angleNorthPt*180/math.pi) )


		# Now use the deltaMagnitude and compute the North/South and West/East components
		deltaNS = deltaMagnitude * math.cos(angleNorthPt) # The N/S component of the magnitude (in pixels)
		deltaWE = deltaMagnitude * math.sin(angleNorthPt) # The W/E component of the magnitude (in pixels)

		# Combine these with the GPS location to obtain the distance away
		# Depends on the azimuth and where on the screen is being clicked
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
		ptLatitude = (ptY_meters * METER_TO_DEGREE_CONVERSION) + gpsLatitude
		ptLongitude = (ptX_meters * METER_TO_DEGREE_CONVERSION) + gpsLongitude

		# Save the point clicked and the calculated pt
		#Store value
		if len(point_clicks) > 0:
			left_clicks.pop()
			point_clicks.pop()
			point_meters.pop()
			click_gps.pop()
		
		left_clicks.append([x,y])
		point_clicks.append([ptX,ptY])
		point_meters.append([ptX_meters,ptY_meters])
		click_gps.append([ptLatitude, ptLongitude])

		# Print the points to see if they match up
		#print "Actual point  :", left_clicks
		#print "Calculated pt :", point_clicks
		print("Delta from center point in meters: ", point_meters)
		print("Point Latitutde , Longitude: ", click_gps)
	


img_file = 'tree.jpg'
img = cv2.imread(img_file)

img_shape = img.shape # Height, Width in pixels
img_V_pixels = img_shape[0]
img_H_pixels = img_shape[1]
print("Image shape: X = %5d   Y = %5d" % (img_H_pixels, img_V_pixels) )

img_center_X = img_H_pixels / 2
img_center_Y = img_V_pixels / 2

# Camera field of view angles determined manually 
# Galaxy Note 4
#fovV = 30.96 # Portrait
#fovH = 19.03 # Landscape

# Nexus 6P
fovV = 35.0 # Portriat
fovH = 26.5 # Landscape

print 

# Get input from user
azimuth = float( input("Enter azimuth: ")) # North is pointing up
pitch = float( input("Enter pitch: "))
roll = float( input("Enter roll: "))
altitude = float( input("Enter altitude: "))
gpsLatitude  =  40.554887 # North south
gpsLongitude = -74.464286 # West East
# For every 0.8627m change in distance is approximately 0.00001 degree
METER_TO_DEGREE_CONVERSION = 0.00001/0.8627;

# Calculate the edge angles, used for linear interpolation of clicks
angle_V_0 = pitch + fovV # Top of image
angle_V_1 = pitch - fovV # Bottom of image
angle_H_0 = roll - fovH # Left sied of image
angle_H_1 = roll + fovH # Right side of image

# Calculate the total Vertical and Horizontal image size in meters
totalVDistance = altitude * ( math.tan(math.radians(angle_V_0)) - math.tan(math.radians(angle_V_1)) )
totalHDistance = altitude * ( -math.tan(math.radians(angle_H_0)) + math.tan(math.radians(angle_H_1)) )

# Ratio between the altitude height and the vertical pixel count and 
# vertical distance
# Pixels      0 - img_V_pixels
# Distance    0 - altitude m 
altitude_pixels = (((altitude/totalVDistance)*img_V_pixels) + ((altitude/totalHDistance)*img_H_pixels))/2
#print("Total V distance: %5.2f meters" % totalVDistance)
#print("Total H distance: %5.2f meters" % totalHDistance)

# Calculate the distance from the center of the image to the center of gps
deltaYGPS = altitude_pixels * math.sin(math.radians(pitch))
deltaXGPS = altitude_pixels * math.sin(math.radians(roll))

# The pixels for the y direction go "UP" when the pixel goes towards the
# bottom of the image
# REMEMBER top left of image is 0,0
#          bottom right of image is max,max
gpsX = img_center_X + deltaXGPS
gpsY = img_center_Y + deltaYGPS

# Draws a circle at the GPS location relative to the image
# If Pitch = 0 and Roll = 0 - Circle is exactly at the center of the image
# 		Camera is laying flat
# Roll > 0 - Center goes to the right of the image
# 		Camera is pointing to the Left
# Pitch > 0 - Center goes to the bottom of the image
# 		Camera is pointing forward
cv2.circle(img, (int(gpsX), int(gpsY)), 20, (0,0,255), 6 )

# Create a line to show which direction is north
#    Starts from GPS center
northX = gpsX + gpsX * math.cos( math.radians(azimuth + 90) ) # North is pointing up
northY = gpsY - gpsY * math.sin( math.radians(azimuth + 90) )

# Draws a line from the GPS center location towards NORTH
cv2.line(img, (int(gpsX),int(gpsY)), (int(northX),int(northY)), (0,255,0), 5, 0 )

print("GPS Center X: %10.2f pixels" % gpsX)
print("GPS Center Y: %10.2f pixels" % gpsY)
print("Altitude pixels: %10.0f pixels" % altitude_pixels)


# Show image 
clone = img.copy()
cv2.namedWindow('image', cv2.WINDOW_NORMAL)
window_width =  int( img_H_pixels * 1/4)
window_height = int( img_V_pixels * 1/4)
cv2.resizeWindow('image', window_width, window_height)

cv2.setMouseCallback('image', mouse_callback)



# Main loop
while True:
	cv2.imshow('image', img)

	# Check and make a line from center to click point
	if len(point_clicks) == 1:
		[clickx,clicky] = point_clicks.pop()
		point_clicks.append([clickx,clicky])
		cv2.line(img, (int(gpsX),int(gpsY)), (int(clickx),int(clicky)), (255,0,0), 5, 0 )

	key = cv2.waitKey(1) & 0xFF
	# Reset the image or close
	if key == ord("r"):
		img = clone.copy() 
		cv2.resizeWindow('image', window_width, window_height)
	elif key == ord("c"):
		break




cv2.destroyAllWindows()
