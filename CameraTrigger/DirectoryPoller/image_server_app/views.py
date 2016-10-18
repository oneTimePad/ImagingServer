from django.shortcuts import render
from django.http import HttpResponseForbidden, HttpResponseRedirect, HttpResponse
from .models import ImagePost

# Create your views here.
def home(request):
	return render(request, 'home.html')
def redirect_to_home(request):
	return HttpResponseRedirect("/home")
def view_images(request):
	return render(request, 'view_images.html', {'image_list': ImagePost.objects.all()})
def upload_image(request):
	return render(request, 'upload_image.html')
def post_image(request):
	if request.method != 'POST':
		return HttpResponseForbidden('allowed only via POST')
	if request.FILES.__contains__("image") == False:
		return HttpResponseForbidden('need o upload a file')
	image = request.FILES['image']
	image_post = ImagePost()
	image_post.pic.save(image.name, image, save=True)
	return HttpResponseRedirect("/home")