#django-rest
from rest_framework import serializers
from server.interop import AUVSITarget
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
		extracts targets characteristics just for interop
	"""
	class Meta:
		model = Target
		fields = ('ptype','latitude','longitude','orientation','shape','background_color','alphanumeric','alphanumeric_color','description')

class TargetInteropSerializer(object):
	"""
		formats the Target object for proper submission to the Interop Server
	"""
	def __init__(self,target):
		self.ser_target = TargetSubmissionSerializer(target).data
		dataDict = dict(self.ser_target)
		#type is a reservered word by Python,can't use it in model declaration
		dataDict['type'] = dataDict.pop('ptype')
		for key in dataDict.keys():
			if dataDict[key] == '':
				dataDict[key] = None
		self.target = AUVSITarget(**dataDict)
	def get_target(self):
		return self.target

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
