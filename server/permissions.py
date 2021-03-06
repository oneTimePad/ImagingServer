from rest_framework.permissions import BasePermission
import pdb

"""
	defines what each user of the system can and cannot do
"""

class DroneAuthentication(BasePermission):

    def has_permission(self,request,view):
        return request.user and request.user.is_authenticated() and request.user.userType =="drone"

class GCSAuthentication(BasePermission):

    def has_permission(self,request,view):
        return request.user and request.user.is_authenticated() and request.user.userType =="gcs"

class InteroperabilityAuthentication(BasePermission):

    def has_permission(self,request,view):
        return request.user and request.user.is_authenticated() and request.user.userType =="intr"
