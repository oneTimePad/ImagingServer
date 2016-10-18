import os
import cv2
import datetime
import time

log_file = open("logs-imagetaker.txt", 'a')
save_dir = "camera_images/"
sleep_time = 1


img_num = 1
while True:
	potential_img_name = save_dir + str(img_num) + ".png"
	if os.path.isfile(potential_img_name):
		img_num += 1
	else:
		break

		
vid_feed = cv2.VideoCapture(0)
while(True):
	print(img_num)
	img_name = str(img_num) + ".png"
	log_file.write("Preparing to take " + img_name + " at: " + str(datetime.datetime.now()) + "\n")
	img_taken, img = vid_feed.read()
	if img_taken == False:
		log_file.write("FAILED to take " + img_name + " at: " + str(datetime.datetime.now()) + ", trying again\n")
		continue
	log_file.write("Took " + img_name + " at: " + str(datetime.datetime.now()) + "\n")
	img_saved = cv2.imwrite(save_dir+img_name, img)
	if img_saved == False:
		log_file.write("FAILED to save " + img_name + " at: " + str(datetime.datetime.now()) + ", trying again\n")
		continue
	log_file.write("Saved " + img_name + " at: " + str(datetime.datetime.now()) + "\n")
	img_num += 1
	time.sleep(1)
	

vid_feed.release()
log_file.close()
	
