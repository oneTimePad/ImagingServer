from django.conf.urls import include, url
from django.contrib import admin

from .views import *

urlpatterns = [
    # Examples:
    # url(r'^$', 'AUVSIDataProc.views.home', name='home'),
    # url(r'^blog/', include('blog.urls')),

    url(r'^upload$',Upload.as_view(),name='upload'),
    url(r'^viewer$',Index.as_view(),name='index'),
    url(r'^attrform$',AttributeFormCheck.as_view(),name='attrform'),
    url(r'^deletepic$',DeletePicture.as_view(),name='deletepic'),
]

