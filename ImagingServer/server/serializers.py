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

        return Picture.objects.create(**self.validated_data)

class TargetSerializer(serializers.ModelSerializer):

    class Meta:
        model = Target
        fields = ('picture','ptype','latitude','longitude','orientation','shape','background_color','alphanumeric','alphanumeric_color')

    def deserialize(self):
        return Target.objects.create(**self.validated_data)

class TargetSubmissionSerialzer(serializers.Serializer):
        ptype = serializers.CharField(max_length=20)
        latitude = serializers.FloatField()
        longitude = serializers.FloatField()
        orientation = serializers.CharField(max_length=2)
        shape = serializers.CharField(max_length=20)
        background_color = serializers.CharField(max_length=20)
        alphanumeric = serializers.CharField(max_length=1)
        alphanumeric_color = serializers.CharField(max_length=20)


class ServerCredsSerializer(serializers.Serializer):
        username = serializers.CharField(max_length=100,allow_blank = False)
        password = serializers.CharField(max_length=100,allow_blank = False)
        server = serializers.URLField(max_length=100,allow_blank = False)

class TelemetrySerializer(serializers.Serializer):
        latitude = serializers.FloatField()
        longitude = serializers.FloatField()
        altitude_msl = serializers.FloatField()
        uas_heading = serializers.FloatField()