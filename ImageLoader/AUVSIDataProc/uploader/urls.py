from django.conf.urls import include, url
from django.contrib import admin

from .views import *


droidpatterns =[
    url(r'^droidconnect',DroneConnectDroid.as_view(),name="droidconnect"),
    url(r'^gcsconnect',DroneConnectGCS.as_view(),name='gcsconnect'),
    url(r'^droidtrigger',TriggerDroid.as_view(),name='droidtrigger'),
    url(r'^gcstrigger',TriggerGCS.as_view(),name='gcstrigger'),
]

urlpatterns = [
    # Examples:
    # url(r'^$', 'AUVSIDataProc.views.home', name='home'),
    # url(r'^blog/', include('blog.urls')),

    url(r'^upload$',Upload.as_view(),name='upload'),
    url(r'^viewer$',Index.as_view(),name='index'),
    url(r'^attrform$',AttributeFormCheck.as_view(),name='attrform'),
    url(r'^deletepic$',DeletePicture.as_view(),name='deletepic'),
    url(r'^getpicturedata$',GetPictureData.as_view(),name='getpicturedata'),
    url(r'^gettargets$',GetTargets.as_view(),name='gettargets'),
    url(r'^gettargetdata$',GetTargetData.as_view(),name='gettargetdata'),
    url(r'^droid/',include(droidpatterns)),
]


