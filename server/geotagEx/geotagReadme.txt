geotag.py readme

Required
	Virtual environment with cv2 and python3

Run with
	python3 geotag.py

	<Image popup>
	"c" to end
	"r" to refresh blue lines

Edit image loaded at line 110

Input:
	azimuth (From north)
	pitch 
	roll
	altitude (in meters)

Will display a green line that is the "North" direction on the image.
Clicking any point on this image gives a blue line for where the click was calculated
	The blue line is mapped with North being straight up

Example:
	If the aziumth = 30, pitch = 0, roll = 0, altitude = 300
	A green line shows up pointing up and slightly to the left
		This is the image's north
	Clicking at the opposite side of the green line (bottom right ish) will make a blue line that is pointing down
		This means, that the point clicked is south of the center
	Clicking the bottom left side is going to show a blue line pointing to the right
		This means, that the point clicked is west of the center	
