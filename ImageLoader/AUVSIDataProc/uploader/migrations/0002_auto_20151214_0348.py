# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('uploader', '0001_initial'),
    ]

    operations = [
        migrations.RemoveField(
            model_name='picture',
            name='alt',
        ),
        migrations.RemoveField(
            model_name='picture',
            name='azimuth',
        ),
        migrations.RemoveField(
            model_name='picture',
            name='lat',
        ),
        migrations.RemoveField(
            model_name='picture',
            name='latLon',
        ),
        migrations.RemoveField(
            model_name='picture',
            name='lon',
        ),
        migrations.RemoveField(
            model_name='picture',
            name='pitch',
        ),
        migrations.RemoveField(
            model_name='picture',
            name='ppm',
        ),
        migrations.RemoveField(
            model_name='picture',
            name='roll',
        ),
    ]
