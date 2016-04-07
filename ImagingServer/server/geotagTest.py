import math
import pdb
from matplotlib import pyplot as plt

# Camera information. Nexus 6P in portrait 
# Field of View angles (1/2 image viewing angles)
fovV = 35.0 # Portrait
fovH = 26.5 # Landscape

# Conversion between meters to GPS coordinate degrees
# ONLY FOR MARYLAND LOCATION
# Every 0.8627 meters is about 0.00001 degrees Lat/Lon
# METER_TO_DEGREE_CONVERSION = 0.00001/0.8627 
METER_TO_DEGREE_CONVERSION = 1

DEBUG = False

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

def printDebug(instr):
	if DEBUG:
		print(instr)

def geotag(size_data,picture_data,angle_data,currPos):#right now the gps coordinates are not right, need to change based on the app
	x,y = size_data
	orig_width,orig_height = picture_data
	azimuth,pitch,roll,altitude = angle_data
	gpsLatitude,gpsLongitude = currPos

	printDebug('x: %f' % x)
	printDebug('y: %f' % y)
	printDebug('azimuth: %f' % azimuth)
	printDebug('pitch: %f' % pitch)
	printDebug('roll: %f' % roll)
	printDebug('altitude: %f' % altitude)

	# Calculate the edge angles of the image
	# Top left of image is 0,0
	angle_V_0 = pitch + fovV # Top of image
	angle_V_1 = pitch - fovV # Bottom of image
	angle_H_0 = roll - fovH # Left sied of image
	angle_H_1 = roll + fovH # Right side of image

	printDebug('Angle_V_0: %f' % angle_V_0)
	printDebug('Angle_V_1: %f' % angle_V_1)
	printDebug('Angle_H_0: %f' % angle_H_0)
	printDebug('Angle_H_1: %f' % angle_H_1)

	# Calculate the total distance (meters) that the image spans
	totalVDistance = altitude * ( math.tan(math.radians(angle_V_0)) - math.tan(math.radians(angle_V_1)) )
	totalHDistance = altitude * ( -math.tan(math.radians(angle_H_0)) + math.tan(math.radians(angle_H_1)) )

	printDebug('TotalVDistance: %d' % totalVDistance)
	printDebug('TotalHDistance: %d' % totalHDistance)

	# Ratio between the altitude height and the vertical pixel 
	# count and vertical distance
	# Pixels      0 - img_V_pixels
	# Distance    0 - altitude m 
	altitude_pixels = (((altitude/totalVDistance)*orig_height) + ((altitude/totalHDistance)*orig_width))/2

	printDebug('Altitude pixels: %f' % altitude_pixels)

	# Calculate the distance from the center of the image to the center of gps
	deltaYGPS = altitude_pixels * math.sin(math.radians(pitch))
	deltaXGPS = altitude_pixels * math.sin(math.radians(roll))

	printDebug('deltaYGPS: %f' % deltaYGPS)
	printDebug('deltaXGPS: %f' % deltaXGPS)

	# The pixels for the y direction go "UP" when 
	# the pixel goes towards the bottom of the image
	# REMEMBER top left of image is 0,0
	#          bottom right of image is max,max
	gpsX = orig_width/2 + deltaXGPS
	gpsY = orig_height/2 + deltaYGPS

	printDebug('gpsX: %f' % gpsX)
	printDebug('gpsY: %f' % gpsY)

	# These values are to help with calculating the angle
	# between the point and the GPS center
	northX = gpsX + gpsX * math.cos( math.radians(azimuth + 90))
	northY = gpsY + gpsY * math.sin( math.radians(azimuth + 90))

	printDebug('northX: %f' % northX)
	printDebug('northY: %f' % northY)

	# Get the GPS coordinates of the crop location
	# crop_Lat,cropLon = calculate_coordinates(x,y)
	# Interpolate the relative angle from Tangent to ground
	relXAngle = (roll - fovH) + (2*fovH)*( float(x) / float(orig_width))
	relYAngle = (pitch - fovV) + (2*fovV)*( float(y) / float(orig_height))

	printDebug('relXAngle: %f' % relXAngle)
	printDebug('relYAngle: %f' % relYAngle)

	relXRadian = math.radians(relXAngle)
	relYRadian = math.radians(relYAngle)
	azimuthRadian = math.radians(azimuth)

	printDebug('relXRadian: %f' % relXRadian)
	printDebug('relYRadian: %f' % relYRadian)
	printDebug('azimuthRadian: %f' % azimuthRadian)

	# Use the relative angle from GPS tangent to point
	# to determine the distance removed from GPS center
	# Value calculated in pixels 
	deltaX = altitude_pixels * math.sin(relXRadian)
	deltaY = altitude_pixels * math.sin(relYRadian)
	deltaMagnitude = math.hypot(deltaX, deltaY)

	printDebug('deltaX: %f' % deltaX)
	printDebug('deltaY: %f' % deltaY)
	printDebug('deltaMagnitude: %f' % deltaMagnitude)

	# Angle between the Camera's North and the Point
	angleNorthPt = angle_between_points([northX-gpsX,northY-gpsY], [x-gpsX,y-gpsY])

	printDebug('angleNorthPt: %f' % angleNorthPt)

	# Check if the click is to the right or left of the North line
	# This equation determines if the click is to the left or right of the line
	# Negative = Left
	# Positive = Right
	# Don't need to check for if North/South, calculation already goes from -180 to 180
	isRight = (northX - gpsX)*(y - gpsY) - (northY - gpsY)*(x - gpsX)
	if (isRight < 0):
		angleNorthPt = -angleNorthPt

	printDebug(isRight)

	# Now use the deltaMagnitude and compute the North/South and West/East components
	deltaNS = deltaMagnitude * math.cos(angleNorthPt) # The N/S component of the magnitude (in pixels)
	deltaWE = deltaMagnitude * math.sin(angleNorthPt) # The W/E component of the magnitude (in pixels)

	printDebug('deltaNS: %f' % deltaNS)
	printDebug('deltaWE: %f' % deltaWE)

	# When the lines are drawn, they are drawn as though the image is pointing NORTH
	# So clicking on the green line will draw a blue line pointing up
	# Have to convert this value into GPS Lat and Long based on conversion of
	# Altitude / pixels from below
	ptX_meters = deltaWE * (altitude/altitude_pixels)
	ptY_meters = deltaNS * (altitude/altitude_pixels)

	printDebug('ptX_meters: %d' % ptX_meters)
	printDebug('ptY_meters: %d' % ptY_meters)

	# ************************* MOST IMPORTANT INFORMATION ******************************
	# This is the calculated Latitude, Longitude of the point
	lat = (ptY_meters * METER_TO_DEGREE_CONVERSION) + gpsLatitude
	lon = (ptX_meters * METER_TO_DEGREE_CONVERSION) + gpsLongitude

	if DEBUG:
		pdb.set_trace()

	return (lat, lon)

def main():
	# scale_width = int(size_data[2])
	# x,y,_,width,height = [int(int(data) * orig_width / scale_width) for data in size_data]
	# orig_width,orig_height = picture_data
	# azimuth,pitch,roll,altitude = angle_data

	width = 25
	height = 25
	edgeThresh = 5

	picture_data = [width,height]
	angle_data = [0.0,0.0,10.0,100.0]
	currPos = [width / 2,height / 2]

	orig_x = []
	orig_y = []
	res_x = []
	res_y = []	

	for x in range(edgeThresh, width - edgeThresh):
		for y in range(edgeThresh, height - edgeThresh):
			size_data = [x,y]
			orig_x.append(x)
			orig_y.append(y)

			# if x is int(width / 2) and y is int(height / 2):
			lat,lon = geotag(size_data,picture_data,angle_data,currPos)
			res_x .append(lat)
			res_y.append(lon)

	fig, ax = plt.subplots()
	ax.plot(orig_x, orig_y, 'or')
	ax.plot(res_x, res_y, 'ob')
	plt.show()

if __name__ == '__main__':
	main()