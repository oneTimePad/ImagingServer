from django.conf.urls import include, url
from django.contrib import admin

from .views import *
from django.views.decorators.csrf import csrf_exempt

droidpatterns =[
    #url(r'^droidconnect',csrf_exempt(DroneConnectDroid.as_view()),name="droidconnect"),
    #url(r'^gcsconnect',DroneConnectGCS.as_view(),name='gcsconnect'),
    url(r'^droidtrigger',csrf_exempt(TriggerDroid.as_view()),name='droidtrigger'),
    url(r'^gcstrigger',TriggerGCS.as_view(),name='gcstrigger'),
    url(r'^upload$',csrf_exempt(Upload.as_view()),name='upload'),
]

urlpatterns = [
    url(r'^viewer$',Index.as_view(),name='index'),
    url(r'^targetedit$',TargetEdit.as_view(),name='targetedit'),
    url(r'^attrform$',AttributeFormCheck.as_view(),name='attrform'),
    url(r'^deletepic$',DeletePicture.as_view(),name='deletepic'),
    url(r'^deletetarget$',DeletePicture.as_view(),name='deletetarget'),
    url(r'^gettarget$',GetTarget.as_view(),name='gettarget'),
    url(r'^gettargets$',GetTargets.as_view(),name='gettargets'),
    url(r'^gettargetdata$',GetTargetData.as_view(),name='gettargetdata'),
    url(r'^droid/',include(droidpatterns)),
]
