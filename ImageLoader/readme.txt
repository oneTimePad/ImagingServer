Work and Documentation(Algorithms):

	-Research shows that storing the pictures in the SQL DB is a bad idea
	-Using django storage to store pictures locally
	-Interesting thought: we could send the location of the image on the 
	local storage, instead of sending the serialized image via AJAX?
		...Either way we are reading by local storage so we pay that coase
		...but sending the location says a lot of overhead!!
		where this was found: http://stackoverflow.com/questions/7497138/serializing-an-imagefield-in-django
		getting image location:http://stackoverflow.com/questions/15764587/django-get-imagefield-url-in-view

	-XXXXAjax picture retreval: update pic num upon success and call fct again
	-Decided to use Websocket picture retrieval:
		..Client sets up websocket with server using Redis Msg queue
		..Server post pictures via user websocket, sending the local
			url of the pic
		..Removes the need for continuous looping requests, server
			only gives client pics when it has them
Ajax pic retreval:
http://stackoverflow.com/questions/18882841/ajax-success-wont-run-function


UnitTesting:
https://docs.djangoproject.com/en/1.8/topics/testing/tools/#django.test.Client.post
UnitTesting-testing ajax requests:
http://stackoverflow.com/questions/16918450/django-testing-view-which-works-with-ajax-requests
http://stackoverflow.com/questions/11170425/how-to-unit-test-file-upload-in-django


Django and Images(possible solution):
https://cloud.google.com/appengine/docs/python/blobstore/
http://stackoverflow.com/questions/18747730/storing-images-in-db-using-django-models
uploading images via ajax:
https://github.com/bradleyg/django-ajaximage

Django Ajax functions:
https://github.com/samuelclay/NewsBlur/blob/master/utils/json_functions.py

Django Image Storage and Upload:
https://docs.djangoproject.com/en/1.8/topics/files/
https://docs.djangoproject.com/en/1.8/topics/http/file-uploads/

Django File System:
https://docs.djangoproject.com/en/1.8/topics/files/

Fetching pics with AJAX:
http://stackoverflow.com/questions/5835126/javascript-infinite-loop


File Uploads:
https://docs.djangoproject.com/en/1.8/topics/http/file-uploads/

Django Redis-Websockets:
Redis Docs:https://media.readthedocs.org/pdf/django-websocket-redis/latest/django-websocket-redis.pdf
JS Websocks:https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_client_applications
More Redis Docs:https://django-websocket-redis.readthedocs.org/en/latest/usage.html#client-side
Redis Websocks Github:https://github.com/jrief/django-websocket-redis


Python Decorators:
http://simeonfranklin.com/blog/2012/jul/1/python-decorators-in-12-steps/
http://thecodeship.com/patterns/guide-to-python-function-decorators/
