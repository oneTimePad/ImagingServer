#django-rest
from rest_framework import serializers

#django
from .models import *

"""
	Serializers are used to verify the data sent by client so it
	can be converted to a Model object and put in the db.
	The also allow the Model object to be converted into a format suitable for the client to
	parse
"""


#serializers for model data
class PictureSerializer(serializers.ModelSerializer):
	"""
		allows serialization and deserialization of the Picture object
	"""
	class Meta:
		model = Picture
		fields = ('pitch','roll','lat','lon','alt','rel_alt','yaw','fileName','timeReceived')

	def deserialize(self):

		return Picture.objects.create(**self.validated_data)

class TargetSerializer(serializers.ModelSerializer):
	"""
		allows serialization and deserialization of the Target object
	"""

	class Meta:
		model = Target
		fields = ('picture','ptype','latitude','longitude','orientation','shape','background_color','alphanumeric','alphanumeric_color','description','sent')

	def deserialize(self):
		return Target.objects.create(**self.validated_data)

class TargetSubmissionSerializer(serializers.ModelSerializer):
	"""
		formats the Target object for proper submission to the Interop Server
	"""
	class Meta:
		model = Target
		fields = ('ptype','latitude','longitude','orientation','shape','background_color','alphanumeric','alphanumeric_color','description')


class ServerCredsSerializer(serializers.Serializer):
		"""
			verifies the credentials sent for the interop server
		"""
		username = serializers.CharField(max_length=100,allow_blank = False)
		password = serializers.CharField(max_length=100,allow_blank = False)
		server = serializers.URLField(max_length=100,allow_blank = False)

class TelemetrySerializer(serializers.Serializer):
		"""
			verifies the data that will be submitted as Telemetry to the interop server
		"""
		latitude = serializers.FloatField()
		longitude = serializers.FloatField()
		altitude_msl = serializers.FloatField()
		uas_heading = serializers.FloatField()
