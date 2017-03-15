from .exceptions import InteropError
from .types import AUVSITarget,MovingObstacle,StationaryObstacle,Mission
import requests
import json

BAD_REQUEST = 400
NOT_FOUND = 404
INTERNAL_SERVER_ERROR = 500

class JSSession(requests.Session):
	"""
	extends requests.session to provide json serializability
	for storing in Django's cache
	"""

	def serialize(self):
		attrs = ['headers','cookies','auth','timeout','proxies','hooks','params','config','verify']
		
		session_data = {}
		
		for attr in attrs:
			session_data[attr] = getattr(self,attr)

		return json.dumps(session_data)
	
	@classmethod
	def deserialize(cls,s):
		if isinstance(s,cls):
			return s
		session_data = json.loads(s)

		if 'auth' in session_data:
			session_data['auth'] = tuple(session_data['auth'])

		if 'cookies' in session_data:
			session_data['cookies'] = dict((key.encode() ,val) for key, val in session_data['cookies'].items())

		req.session(**session_data)


class InteropProxy(object):
	"""
	contains the data and methods required to speak to the interop server
	"""



	def __init__(self,username,password,server,tout=5, restored_session = None):
		#attempt to restore the serialized session if possible
		self.session = JSSession.deserialize(restored_session) if restored_session is not None else JSSession()
		self.tout = tout
		self.username = username
		self.password = password
		self.server = server
	def serialize(self):
		"""
		converts the object the json format to be stored in django's cache
		"""
		serial = {}
		for attr in self.attrs:
			data = self.__dict[attr]
			if isinstance(data,JSSession):
				serial[attr] = data.serialize()
			#must be string then
			else:
				serial[attr]  = data
		return serial

	@classmethod
	def deserialize(cls,ser):
		"""
		deserializes the InteropProxy object
		"""
		if isinstance(ser,cls):
			return ser
		else:
			return cls(**ser)

	def login(self):
		global BAD_REQUEST
		global NOT_FOUND
		global INTERNAL_SERVER_ERROR
		#attempt to contact the login api for the interop server
		try:
			self.session.post(self.server+'/api/login',data=
				{'username':username,
				'password':password},timeout=self.tout)
			return None
		#catch exceptions
		except InteropError as serverExp:
			code,reason,text =  serverExp.errorData()

			if code == BAD_REQUEST:
				return "The current user/pass combo (%s, %s) is wrong. Please try again." % username,password
			elif code == NOT_FOUND:
				return "A server at %s was not found. Please reenter the server IP address." % (self.server)
			elif code == INTERNAL_SERVER_ERROR:
				return "Internal issues with their code. Stopping."

		#deals with timeout error
		except requests.ConnectionError:
			return "A server at %s was not found. Please reenter the server IP address." % (self.server)

		except requests.Timeout:
			return "The server timed out."

		except requests.TooManyRedirects:
			return "The URL redirects to itself; reenter the address:"

		except requests.URLRequired:
			return "The URL is invalid; reenter the address:"

		except requests.RequestException as e:
			# catastrophic error. bail.
			return e.strerror
		except Exception as e:
			return "Unknown error: %s" % str(e)


	def get_missions(self):
		"""GET missions.

		Returns:
			List of Mission.
		Raises:
			InteropError: Error from server.
			requests.Timeout: Request timeout.
			ValueError or AttributeError: Malformed response from server.
		"""
		r = self.session.get(self.server+'/api/missions', timeout=self.tout)
		if not r.ok:
			raise InteropError(r)
		return [Mission.deserialize(m) for m in r.json()]

	def post_telemetry(self,telem):
		"""POST new telemetry.

		Args:
			telem: Telemetry object containing telemetry state.
		Raises:
			InteropError: Error from server.
			requests.Timeout: Request timeout.
		"""
		r = self.session.post(self.server+'/api/telemetry', timeout=self.tout, data=telem.serialize())
		if not r.ok:
			raise InteropError(r)
		return r

	def get_obstacles(self):
		"""GET obstacles.

		Returns:
			List of StationaryObstacles and list of MovingObstacles
				i.e., ([StationaryObstacle], [MovingObstacles]).
		Raises:
			InteropError: Error from server.
			requests.Timeout: Request timeout.
			ValueError or AttributeError: Malformed response from server.
		"""
		r = self.session.get(self.server+'/api/obstacles', timeout=self.tout)
		if not r.ok:
			raise InteropError(r)

		d = r.json()

		stationary = []
		for o in d['stationary_obstacles']:
			stationary.append(StationaryObstacle.deserialize(o))

		moving = []
		for o in d['moving_obstacles']:
			moving.append(MovingObstacle.deserialize(o))

		return stationary, moving


	def post_target(self,target):
		"""POST target.

		Args:
			target: The target to upload.
		Returns:
			The target after upload, which will include the target ID and user.
		Raises:
			InteropError: Error form server.
			requests.Timeout: Request timeout.
			ValueError or AttributeError: Malformed response from server.
		"""

		r = self.session.post(self.server+'/api/targets', timeout = self.tout,data=json.dumps(target.serialize()))
		if not r.ok:
			return InteropError(r)
		return r.json()


	def post_target_image(target_id, image_binary):
		"""ADDS or UPDATES the target image thumbnail

		Args:
			Content-Type: image/jpeg or image/png
			raw image information
		Raises:
			400 Bad Request: Request was not a valid JPEG or PNG image.
			403 Forbidden: User not authenticated.
			404 Not Found: Target not found. Check target ID.
			413 Request Entity Too Large: Image exceeded 1MB in size.
		"""
		r = self.session.post(self.server+'/api/targets/%d/image' % target_id, headers={'Content-Type':'image/jpg'},timeout = self.tout, data=image_binary)
		if not r.ok:
			return InteropError(r)
		return r

	def put_target(self, target_id, target):
			"""PUT target.
			Args:
				target_id: The ID of the target to update.
				target: The target details to update.
			Returns:
				Future object which contains the return value or error from the
				underlying Client.
			"""
			r = self.session.post(self.server+'/api/targets/%d' % target_id, data=json.dumps(target))
			if not r.ok:
				return InteropError(r)
			return r.json()

	def delete_target(self, target_id):
			"""DELETE target.
			Args:
				target_id: The ID of the target to delete.
			Returns:
				Future object which contains the return value or error from the
				underlying Client.
			"""
			r = self.session.delete(self.server+'/api/targets/%d' % target_id, data=json.dumps(target.serialize()))
			if not r.ok:
				return InteropError(r)
			return r.json()
	
	def delete_target_image(self, target_id):
			"""DELETE target image.
			Args:
				target_id: The ID of the target to delete.
			Returns:
				Future object which contains the return value or error from the
				underlying Client.
			"""
			r = self.session.delete(self.server+'/api/targets/%d/image' % target_id)			
			if not r.ok:
				return InteropError(r)
			return r.json()
	

	def get_target_image(self, target_id):
			"""GET target image.
			Args:
				target_id: The ID of the target for which to get the image.
			Returns:
				The image data that was previously uploaded.
			Returns:
				Future object which contains the return value or error from the
				underlying Client.
			"""
			r = self.session.post(self.server+'/api/targets/%d' % target_id, timeout = self.tout,data=json.dumps(target.serialize()))
			if not r.ok:
				return InteropError(r)
			return r.json()
