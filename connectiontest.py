from droneapi import DroneAPI, DroneAPICallError, DroneAPIHTTPError

# use this with the DroneAPI on the onboard repo
# curl.txt can be used just to test logging in

server_ip = "127.0.0.1"
server_port = "8443"
username = "drone"
password = "ruautonomous"

server_url = "http://"+server_ip+":"+server_port
drone_api = DroneAPI(server_url, username, password)
logged_in = False
while (logged_in == False):
	try:
		drone_api.postAccess()
		logged_in = True
	except TypeError:
		print("DEBUG: Received incorrect padding, trying to log in again")
	except DroneAPICallError as e:
		print(e)
	except DroneAPIHTTPError as e:
		print(e)
	except KeyboardInterrupt:
		break
print("DEBUG: Successfully logged in to " + server_url)

try:
	r = drone_api.postHeartbeat()
	print(r)
	print(r.json())
	img_filepath = "/home/eclectic/Pictures/MacroGalaxy.png"
	r = drone_api.postImage(img_filepath, "")
	print(r)
	print(r.json())
except DroneAPICallError as e:
	print(e)
