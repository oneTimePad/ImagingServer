#django-rest
from rest_framework import serializers

#django
from .models import *

#serializers for model data
class PictureSerializer(serializers.ModelSerializer):

    class Meta:
        model = Picture
        fields = ('pitch','roll','lat','lon','alt','yaw','timeReceived')

    def deserialize(self):

        return Picture.objects.create(**self.validated_data)

class TargetSerializer(serializers.ModelSerializer):

    class Meta:
        model = Target
        fields = ('picture','ptype','latitude','longitude','orientation','shape','background_color','alphanumeric','alphanumeric_color','description','sent')

    def deserialize(self):
        return Target.objects.create(**self.validated_data)

class TargetSubmissionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Target
        fields = ('ptype','latitude','longitude','orientation','shape','background_color','alphanumeric','alphanumeric_color','description')





class ServerCredsSerializer(serializers.Serializer):
        username = serializers.CharField(max_length=100,allow_blank = False)
        password = serializers.CharField(max_length=100,allow_blank = False)
        server = serializers.URLField(max_length=100,allow_blank = False)

class TelemetrySerializer(serializers.Serializer):
        latitude = serializers.FloatField()
        longitude = serializers.FloatField()
        altitude_msl = serializers.FloatField()
        uas_heading = serializers.FloatField()
