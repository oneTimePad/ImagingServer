from rest_framework.permissions import BasePermission
import pdb



class DroneAuthentication(BasePermission):

    def has_permission(self,request,view):
        return request.user and request.user.is_authenticated() and request.user.userType =="drone"

class GCSAuthentication(BasePermission):

    def has_permission(self,request,view):
        return request.user and request.user.is_authenticated() and request.user.userType =="gcs"

class TelemetryAuthentication(BasePermission):
    
    def has_permission(self,request,view):
        return request.user and request.user.is_authenticated() and request.user.userType =="telemetry"
