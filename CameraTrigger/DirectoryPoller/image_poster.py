import os
import datetime
import requests
import time


poll_dir = "camera_images/"
log_file = open("logs-imageposter.txt", 'a')
post_url = "http://127.0.0.1:8000/post_image"
sleep_time = 1


img_num = 1
while True:
	potential_img_name = poll_dir + str(img_num) + ".png"
	if os.path.isfile(potential_img_name):
		img_num += 1
	else:
		break
		
		
csrf_token = "placeholder"
while(True):
	print(img_num)
	img_name = str(img_num) + ".png"
	img_path = poll_dir+img_name
	if os.path.isfile(img_path):
		log_file.write("Noticed " + img_name + " at: " + str(datetime.datetime.now()) + ", preparing to post\n")
		response = requests.post(post_url, headers={"Cookie": "csrftoken="+csrf_token}, data={"csrfmiddlewaretoken" : csrf_token}, files={"image": open(img_path, "rb")})
		if response.status_code == 200:
			log_file.write("Posted " + img_name + " at: " + str(datetime.datetime.now()) + "\n")
			img_num += 1
		else:
			log_file.write("FAILED to post " + img_name + " at: " + str(datetime.datetime.now()) + "\n")
	time.sleep(sleep_time)


log_file.close()