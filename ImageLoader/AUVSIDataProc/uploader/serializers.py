#django-rest
from rest_framework import serializers

#django
from .models import *

#serializers for model data
class PictureSerializer(serializers.ModelSerializer):

    class Meta:
        model = Picture
        fields = ('azimuth','pitch','roll','lat','lon','alt')

class TargetSerializer(serializers.ModelSerializer):

    class Meta:
        model = Target
        fields = ('color','lcolor','orientation','shape','letter','lat','lon')
