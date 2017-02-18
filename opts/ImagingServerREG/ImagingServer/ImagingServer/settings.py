"""
Django settings for AUVSIDataProc project.

Generated by 'django-admin startproject' using Django 1.8.

For more information on this file, see
https://docs.djangoproject.com/en/1.8/topics/settings/

For the full list of settings and their values, see
https://docs.djangoproject.com/en/1.8/ref/settings/
"""

# Build paths inside the project like this: os.path.join(BASE_DIR, ...)
import os
import datetime


BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGIN_REDIRECT_URL='/gcs/viewer'
LOGIN_URL='/gcs/login'
WS4REDIS_HEARTBEAT = '--heartbeat--'
# Quick-start development settings - unsuitable for production
# See https://docs.djangoproject.com/en/1.8/howto/deployment/checklist/

# SECURITY WARNING: keep the secret key used in production secret!
SECRET_KEY = 'pzkrv55(r^g!$bdc7u4m(_(tu#$^3&&37-m9(-2w@y9k_&9di%'

# SECURITY WARNING: don't run with debug turned on in production!
DEBUG = True

ALLOWED_HOSTS = []


AUTH_USER_MODEL = 'server.ImagingUser'


#SESSION_COOKIE_SECURE = True
#CSRF_COOKIE_SECURE = True

WEBSOCKET_URL = '/ws/'
WS4REDIS_EXPIRE = 0
WS4REDIS_PREFIX = 'ws'


SESSION_ENGINE = 'redis_sessions.session'
SESSION_REDIS_PREFIX = 'session'

CACHES = {
     'default':{
         'BACKEND': 'redis_cache.RedisCache',
         'LOCATION': '/var/run/redis/redis.sock',
     }
 }


# Application definition

INSTALLED_APPS = (
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'server',
    'ws4redis',
)
'''
REST_FRAMEWORK = {
    'DEFAULT_PERMISSION_CLASSES': (
        'rest_framework.permissions.IsAuthenticated',
    ),
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'rest_framework.authentication.SessionAuthentication',
        'rest_framework.authentication.BasicAuthentication',
        'rest_framework_jwt.authentication.JSONWebTokenAuthentication',
    ),
}
'''

JWT_AUTH={
'JWT_EXPIRATION_DELTA': datetime.timedelta(seconds= 3600),
'JWT_ALLOW_REFRESH':True,
'JWT_VERIFY_EXPIRATION':False,
}






MIDDLEWARE_CLASSES = (
    'django.middleware.cache.UpdateCacheMiddleware',
    'django.middleware.cache.FetchFromCacheMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.auth.middleware.SessionAuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
    'django.middleware.security.SecurityMiddleware',
)

ROOT_URLCONF = 'ImagingServer.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': ['/home/lie/Desktop/AUVSI/DataProcessing/ImagingServer/server/templates',],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
                'django.core.context_processors.static',
                'ws4redis.context_processors.default',
            ],
        },
    },
]
X_FRAMES_OPTION = "DENY"
WSGI_APPLICATION = 'ws4redis.django_runserver.application'
#WSGI_APPLICATION = 'AUVSIDataProc.wsgi.application'


# Database
# https://docs.djangoproject.com/en/1.8/ref/settings/#databases
'''
DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.sqlite3',
        'NAME': os.path.join(BASE_DIR, 'db.sqlite3'),
    }
}
'''



DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.postgresql_psycopg2', # Add 'postgresql_psycopg2', 'mysql', 'sqlite3' or 'oracle'.
        'NAME': 'test',                      # Or path to database file if using sqlite3.
        # The following settings are not used with sqlite3:
        'USER': 'ru',
        'PASSWORD': 'auvsi',
        'HOST': 'localhost',                      # Empty for localhost through domain sockets or           '127.0.0.1' for localhost through TCP.
        'PORT': '5432',                      # Set to empty string for default.
    }
}




# Internationalization
# https://docs.djangoproject.com/en/1.8/topics/i18n/

LANGUAGE_CODE = 'en-us'

TIME_ZONE = 'UTC'

USE_I18N = True

USE_L10N = True

USE_TZ = True


# Static files (CSS, JavaScript, Images)
# https://docs.djangoproject.com/en/1.8/howto/static-files/

STATIC_URL = '/static/'