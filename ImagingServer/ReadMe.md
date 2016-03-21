# Imaging Django Webserver and imaging GUI

#running
- ```./manage loaddata uploader/fixtures/users.json```
- ```./manage runserver 0.0.0.0:2000 ```

#clean database
- ```./cleandb```

#Dependencies
- Python3.4
- (Django-1.9) [ https://github.com/django/django ]
- (Django-Rest-Framework) [ https://github.com/tomchristie/django-rest-framework ]
- (Django-Rest-Framework-JWT) [ https://github.com/GetBlimp/django-rest-framework-jwt ]
- OpenCV3
	-use .so file in repo. add it to site-packages

###CheckList:
- [X] drone login form
- [X] gui login form
- [X] drone side authentication
- [X] gui side authentication
- [ ] image data dump to competition electronic format in real-time
- [ ] deploy to apache2 with TLS
- [ ] HTML/CSS to viewer and login
	- nicer gallery, etc...
- [ ] geotagging

