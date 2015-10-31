from django.db import models

from django.core.files.storage import FileSystemStorage
# Create your models here.

STORAGE = '/var/www/html/PHOTOS'
STORAGE_Target = '/var/www/html/TARGETS'

#uses django storage, change path to fit yours
fs = FileSystemStorage(location=STORAGE)
fs_targets = FileSystemStorage(location=STORAGE_Target)

class Picture(models.Model):
	#picture object
	text = models.CharField(max_length=100)

	photo = models.ImageField(storage=fs,default=0)




class Target(models.Model):

	#targets relate to pictures
	pictures = models.ManyToManyField(Picture)
	target_pic = models.ImageField(storage=fs_targets)
	color = models.IntegerField()
	
	@staticmethod
	def crop(**attributes):
		

		picture_pk = attributes['picture_pk']
		color = attributes['color']
		size_data = attributes['size_data']


		top_left,height,width = size_data




		original_image = cv2.imread(picture_name)
		cropped_image = original_image[top_left[0]:top_left[0]+width,top_left[1]:top_left+height]
		#cv2.imwrite(STORAGE+"/targets"+"/"+"")

		target = Target.objects.create(target_pic=cropped_image,color=color)
		
		target.pictures.add(pictures_pk)


