from .client improt AsyncClient
from .types import InteropError


class InteropClient:

	def __init__(self,server,username,password):
		self.client = None
		while not self.client:
			try:
				self.client = AsyncClient(server,username,password)
			except InteropError as serverExp:
				#@RUAutonomous-autopilot
				#We might need more exceptions here and below
				code,reason,text =  serverExp.errorData()
				print "Error code : %d Error Reason: %s" %(code,reason)
				print "Reason: \n%s" %(text)

				if code == 400:
					print "The current user/pass combo (%s, %s) is wrong. Please try again." % (username,password)
					enterLoginCredentials()
					username = os.getenv('INTEROP_USER','testuser')
					password = os.getenv('INTEROP_PASS','testpass')

				elif code == 404:
					print "A server at %s was not found. Please reenter the server IP address." % (server)
					enterAUVSIServerAddress()
					server = os.getenv('INTEROP_SERVER','http://localhost')

				elif code == 500:
					print "Internal issues with their code. Stopping."

			#deals with timeout error
			except requests.ConnectionError:
				print "A server at %s was not found. Please reenter the server IP address." % (server)
				enterAUVSIServerAddress()
				server = os.getenv('INTEROP_SERVER','http://localhost')

			except requests.Timeout:
				print "The server timed out. Waiting for a second, then retrying."
				sleep(1)

			except requests.TooManyRedirects:
				print "The URL redirects to itself; reenter the address:"
				enterAUVSIServerAddress()
				server = os.getenv('INTEROP_SERVER','http://localhost')

			except requests.URLRequired:
				print "The URL is invalid; reenter the address:"
				enterAUVSIServerAddress()
				server = os.getenv('INTEROP_SERVER','http://localhost')

			except requests.RequestException as e:
				# catastrophic error. bail.
				print e
				sys.exit(1)

			except concurrent.futures.CancelledError:
				print "Multithreading failed. Waiting for a second, then retrying."
				sleep(1)

			except concurrent.futures.TimeoutError:
				print "Multithreading timed out. Waiting for a second, then retrying."
				sleep(1)

			except:
				print "Unknown error: %s" % (sys.exc_info()[0])
				sys.exit(1)

		print "[DEBUG]: Server successfully connected to %s." % (server)
		print "[DEBUG]: Logged in as %s." % (username)
