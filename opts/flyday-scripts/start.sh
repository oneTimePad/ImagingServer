#!/bin/bash
#gnome-terminal -x "/home/ruautonomous/Auvsi/ImagingGCS/opts/flyday-scripts/ipstart.sh" &
gnome-terminal -x "/home/ruautonomous/Auvsi/ImagingGCS/opts/flyday-scripts/serverstart.sh" &
sleep 10;
gnome-terminal -e "/usr/bin/firefox -new-tab -url \"http://127.0.0.1:8000/\" -new-tab -url \"http://127.0.0.1:8000/interop/serverlogin\""
read -p "Press key to start interop"
#gnome-terminal -e "/home/ruautonomous/Auvsi/interoptelemstart.sh"
