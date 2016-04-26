from django.conf.urls import include, url
from django.contrib import admin
from django.contrib.auth.decorators import login_required

from .views import *
from rest_framework.routers import SimpleRouter
from rest_framework_jwt.views import obtain_jwt_token,refresh_jwt_token,verify_jwt_token

urlpatterns =[
    url(r'^$',login_required(GCSViewer.as_view()),name='approot'),
    url(r'^gcs/viewer$',login_required(GCSViewer.as_view()),name='index'),
]

droneauthentication =[
    url(r'^drone/login$',obtain_jwt_token),
    url(r'^drone/refresh$',refresh_jwt_token),
    url(r'^drone/verify$',verify_jwt_token),

]

interopauthentication = [
    url(r'^interop/login$',obtain_jwt_token),
    url(r'^interop/refresh$',refresh_jwt_token),
    url(r'^interop/verify$',verify_jwt_token),
    url(r'^interop/serverlogin$',InteropLogin.as_view(),name='serverlogin'),
]


gcsauthentication = [
    url(r'^gcs/login$',GCSLogin.as_view(),name="gcs-login"),
]

urlpatterns+=droneauthentication
urlpatterns+=gcsauthentication
urlpatterns+=interopauthentication

router = SimpleRouter(trailing_slash=False)
router.register(r'drone',DroneViewset,'drone')
urlpatterns+=router.urls
router = SimpleRouter(trailing_slash=False)
router.register(r'gcs',GCSViewset,'gcs')
urlpatterns+=router.urls
router = SimpleRouter(trailing_slash=False)
router.register(r'interop',InteroperabilityViewset,'interop')
urlpatterns+=router.urls
