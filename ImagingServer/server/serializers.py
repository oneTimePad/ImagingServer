#django-rest
from rest_framework import serializers

#django
from .models import *

#serializers for model data
class PictureSerializer(serializers.ModelSerializer):

    class Meta:
        model = Picture
        fields = ('azimuth','pitch','roll','lat','lon','alt','fileName')

    def deserialize(self):
        
        return Picture(**self.validated_data)

class TargetSerializer(serializers.ModelSerializer):

    class Meta:
        model = Target
        fields = ('color','lcolor','orientation','shape','letter','lat','lon')

    def deserialize(self):
        return Target(**self.validated_data)
