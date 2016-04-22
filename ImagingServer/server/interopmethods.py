from .exceptions import InteropError
import requests

def interop_login(username,password,server,tout):
    session = None
    try:
        session = requests.Session()
        session.post(server+'/api/login',data=
            {'username':username,
            'password':password},timeout=tout)
        return session
    except InteropError as serverExp:
        code,reason,text =  serverExp.errorData()

        if code == 400:
            return "The current user/pass combo (%s, %s) is wrong. Please try again." % username,password
        elif code == 404:
            return "A server at %s was not found. Please reenter the server IP address." % (server)
        elif code == 500:
            return "Internal issues with their code. Stopping."

    #deals with timeout error
    except requests.ConnectionError:
        return "A server at %s was not found. Please reenter the server IP address." % (server)

    except requests.Timeout:
        return "The server timed out."

    except requests.TooManyRedirects:
        return "The URL redirects to itself; reenter the address:"

    except requests.URLRequired:
        return "The URL is invalid; reenter the address:"

    except requests.RequestException as e:
        # catastrophic error. bail.
        return e.strerror
    except Exception:
        return "Unknown error: %s" % (sys.exc_info()[0])




def get_server_info(session,server, tout):
    """GET server information, to be displayed to judges.

    Returns:
        ServerInfo object.
    Raises:
        InteropError: Error from server. Note that you may receive this
            error if the server has no message configured.
        requests.Timeout: Request timeout.
        ValueError or AttributeError: Malformed response from server.
    """
    r = session.get(server+'/api/server_info', timeout=tout, **kwargs)
    if not r.ok:
        raise InteropError(r)

    return ServerInfo.deserialize(r.json())

def post_telemetry(session,server,tout, telem):
    """POST new telemetry.

    Args:
        telem: Telemetry object containing telemetry state.
    Raises:
        InteropError: Error from server.
        requests.Timeout: Request timeout.
    """
    r = session.post(server+'/api/telemetry', timeout=tout, data=telem.serialize(), **kwargs)
    if not r.ok:
        raise InteropError(r)
    return r

def get_obstacles(session,server,tout,kwargs):
    """GET obstacles.

    Returns:
        List of StationaryObstacles and list of MovingObstacles
            i.e., ([StationaryObstacle], [MovingObstacles]).
    Raises:
        InteropError: Error from server.
        requests.Timeout: Request timeout.
        ValueError or AttributeError: Malformed response from server.
    """
    r = session.get(server+'/api/obstacles', timeout=tout, **kwargs)
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


def post_target(session,server, tout,target,kwargs):
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
    r = session.post(server+'/api/targets', timeout = tout,data=json.dumps(target.serialize()))
    if not r.ok:
        raise InteropError(r)
    return Target.deserialize(r.json())


def post_target_image(session,server,tout, target_id, image_binary,kwargs):
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
    r = session.post(server+'api/targets/%d/image' % target_id,timeout = tout, data=image_binary)
    if not r.ok:
        raise InteropError(r)
    return r
