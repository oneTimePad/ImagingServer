from .client import AsyncClient
from .exceptions import InteropError


class InteropClient:

	def __init__(self,server,username,password):
		self.client = None
		self.error = None
		try:
			self.client = AsyncClient(server,username,password)
		except InteropError as serverExp:
			code,reason,text =  serverExp.errorData()

			if code == 400:
				self.error = "The current user/pass combo (%s, %s) is wrong. Please try again." % (username,password)
			elif code == 404:
				self.error =  "A server at %s was not found. Please reenter the server IP address." % (server)
			elif code == 500:
				self.error = "Internal issues with their code. Stopping."

		#deals with timeout error
		except requests.ConnectionError:
			self.error =  "A server at %s was not found. Please reenter the server IP address." % (server)

		except requests.Timeout:
			self.error =  "The server timed out."

		except requests.TooManyRedirects:
			self.error = "The URL redirects to itself; reenter the address:"

		except requests.URLRequired:
			self.error = "The URL is invalid; reenter the address:"

		except requests.RequestException as e:
			# catastrophic error. bail.
			self.error = e.strerror
		except Exception:
			self.error = "Unknown error: %s" % (sys.exc_info()[0])
