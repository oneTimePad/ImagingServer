The important program here is image_poster.py. It polls the camera_images directory for new images and sends them to django.

Everything else here is only here as a convenience so that you can test the image_poster.py program. Here is an explanation of all the directories/files:
    camera_images (directory): image_taker.py saves pictures in this directory and image_poster.py polls this directory for new images
    image_server (directory), image_server_app (directory), db.sqlite3 (file), manage.py (file): django files and directories. Note that
                this django project is not the real one that we will use at competition - it's just a simple django project that I hacked
                together that allows for viewing and posting images.
    media (directory): this is where my django project saves and stores posted images (they do not get saved in the sql db). This directory
                should contain the same images as the camera_images directory. If it does not, then something is wrong with either 1) my
                program or 2) the manner in which you attempted to test my program.
    image_poster.py: poolls camera_images for new images and sends them to django (Dylan, this is the file that you asked me for)
    image_taker.py: uses opencv to take images, then saves those images in the camera_images directory
    logs-imageposter.txt: date-time info for image_poster.py gets logged here
    logs-imagetaker.txt: date-time info for image_taker.py gets logged here

HOW TO TEST MY PROGRAM (make sure you do it in this order):
    1) start the django server on port 8000 (if you want to use a different port, you'll have to edit the image_poster.py file)
    2) start the image_poster.py program (just type "python image_poster.py" in command line)
    3) start the image_taker.py program (just type "python image_taker.py" in command line)
