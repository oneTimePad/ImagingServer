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

#Users
- username: viewer1, password: ruautonomous [user for image gui]
- username: drone, password: ruautonomous [user for phone]
- username: telemuser, password: ruautonomous [user for posting telemetry]
- username: serverobstaclesuser, password: ruautonomous [user for fetching obstacles and getting server time]
