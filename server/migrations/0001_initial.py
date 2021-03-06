# -*- coding: utf-8 -*-
# Generated by Django 1.9 on 2017-01-06 20:47
from __future__ import unicode_literals

from django.conf import settings
import django.contrib.auth.models
import django.core.files.storage
import django.core.validators
from django.db import migrations, models
import django.db.models.deletion
import django.utils.timezone
import sanitizer.models


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        ('auth', '0007_alter_validators_add_error_messages'),
    ]

    operations = [
        migrations.CreateModel(
            name='ImagingUser',
            fields=[
                ('id', models.AutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('password', models.CharField(max_length=128, verbose_name='password')),
                ('last_login', models.DateTimeField(blank=True, null=True, verbose_name='last login')),
                ('is_superuser', models.BooleanField(default=False, help_text='Designates that this user has all permissions without explicitly assigning them.', verbose_name='superuser status')),
                ('username', models.CharField(error_messages={'unique': 'A user with that username already exists.'}, help_text='Required. 30 characters or fewer. Letters, digits and @/./+/-/_ only.', max_length=30, unique=True, validators=[django.core.validators.RegexValidator('^[\\w.@+-]+$', 'Enter a valid username. This value may contain only letters, numbers and @/./+/-/_ characters.')], verbose_name='username')),
                ('first_name', models.CharField(blank=True, max_length=30, verbose_name='first name')),
                ('last_name', models.CharField(blank=True, max_length=30, verbose_name='last name')),
                ('email', models.EmailField(blank=True, max_length=254, verbose_name='email address')),
                ('is_staff', models.BooleanField(default=False, help_text='Designates whether the user can log into this admin site.', verbose_name='staff status')),
                ('is_active', models.BooleanField(default=True, help_text='Designates whether this user should be treated as active. Unselect this instead of deleting accounts.', verbose_name='active')),
                ('date_joined', models.DateTimeField(default=django.utils.timezone.now, verbose_name='date joined')),
                ('userType', models.CharField(default='none', max_length=100)),
                ('groups', models.ManyToManyField(blank=True, help_text='The groups this user belongs to. A user will get all permissions granted to each of their groups.', related_name='user_set', related_query_name='user', to='auth.Group', verbose_name='groups')),
                ('user_permissions', models.ManyToManyField(blank=True, help_text='Specific permissions for this user.', related_name='user_set', related_query_name='user', to='auth.Permission', verbose_name='user permissions')),
            ],
            options={
                'verbose_name': 'user',
                'verbose_name_plural': 'users',
                'abstract': False,
            },
            managers=[
                ('objects', django.contrib.auth.models.UserManager()),
            ],
        ),
        migrations.CreateModel(
            name='GCSSession',
            fields=[
                ('id', models.AutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('session', models.CharField(max_length=40, null=True)),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.CreateModel(
            name='Picture',
            fields=[
                ('id', models.AutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('fileName', models.CharField(default='photo', max_length=100)),
                ('photo', models.ImageField(default=0, storage=django.core.files.storage.FileSystemStorage(location='/var/www/html/PHOTOS/'), upload_to='')),
                ('yaw', models.DecimalField(decimal_places=6, default=0, max_digits=15)),
                ('pitch', models.DecimalField(decimal_places=6, default=0, max_digits=15)),
                ('roll', models.DecimalField(decimal_places=6, default=0, max_digits=15)),
                ('lat', models.DecimalField(decimal_places=6, default=0, max_digits=15)),
                ('lon', models.DecimalField(decimal_places=6, default=0, max_digits=15)),
                ('alt', models.DecimalField(decimal_places=6, default=0, max_digits=15)),
                ('timeReceived', models.FloatField(default=0)),
            ],
        ),
        migrations.CreateModel(
            name='Target',
            fields=[
                ('id', models.AutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('picture', models.ImageField(default=0, storage=django.core.files.storage.FileSystemStorage(location='/var/www/html/TARGETS/'), upload_to='')),
                ('ptype', models.CharField(choices=[('standard', 'Standard'), ('qrc', 'QR Code'), ('off_axis', 'Off-Axis'), ('emergent', 'Emergent')], max_length=20)),
                ('sent', models.BooleanField(default=False)),
                ('latitude', models.DecimalField(decimal_places=6, default=0, max_digits=9)),
                ('longitude', models.DecimalField(decimal_places=6, default=0, max_digits=9)),
                ('orientation', sanitizer.models.SanitizedCharField(blank=True, choices=[('N', 'N'), ('NE', 'NE'), ('E', 'E'), ('SE', 'SE'), ('S', 'S'), ('SW', 'SW'), ('W', 'W'), ('NW', 'NW')], max_length=2, null=True)),
                ('shape', sanitizer.models.SanitizedCharField(blank=True, choices=[('circle', 'circle'), ('semicircle', 'semicircle'), ('quarter circle', 'quarter circle'), ('triangle', 'triangle'), ('square', 'square'), ('rectangle', 'rectangle'), ('trapezoid', 'trapezoid'), ('pentagon', 'pentagon'), ('hexagon', 'hexagon'), ('heptagon', 'heptagon'), ('octagon', 'octagon'), ('star', 'star'), ('cross', 'cross')], max_length=14, null=True)),
                ('background_color', sanitizer.models.SanitizedCharField(blank=True, max_length=20, null=True)),
                ('alphanumeric', sanitizer.models.SanitizedCharField(blank=True, max_length=1, null=True)),
                ('alphanumeric_color', sanitizer.models.SanitizedCharField(blank=True, max_length=20, null=True)),
                ('description', sanitizer.models.SanitizedCharField(blank=True, max_length=200, null=True)),
            ],
        ),
    ]
