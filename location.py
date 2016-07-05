
from dronekit import connect
from time import time,sleep
MAV_SERVER = '127.0.0.1:14550'

#print unicode colors
class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

try:
    drone = connect(MAV_SERVER,wait_ready=True)
except Exception as e:
    print(bcolors.FAIL+"Received Exception while contacting MaxProxy"+bcolors.ENDC+"\n")
    print(e)
    sys.exit(1)

while True:
    try:
        beforeTelem = time()
        #get data from maxproxy (Dronekit)

        lat = float(drone.location.global_frame.lat)
        lon = float(drone.location.global_frame.lon)
        alt = float(drone.location.global_relative_frame.alt)
        groundcourse = float(drone.heading)
        heading = groundcourse

        latdeg = round(lat,0)
        latmin = round((lat-latdeg)*60,0)
        latsec = round(((lat-latdeg)*60-latmin)*60,0)
        londeg = round(lon,0)
        lonmin = round((lon-londeg)*60,0)
        lonsec = round(((lon-londeg)*60-lonmin)*60,0)

        print("----------TIME STAMP-------------\n")
        print("         "+str(beforeTelem)+"        \n")
        print(lat)
        print(lon)
        sleep(.5)
    except Exception as e:
        print(e)
