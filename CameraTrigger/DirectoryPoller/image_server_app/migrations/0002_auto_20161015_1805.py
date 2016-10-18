# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models
import datetime
from django.utils.timezone import utc


class Migration(migrations.Migration):

    dependencies = [
        ('image_server_app', '0001_initial'),
    ]

    operations = [
        migrations.AlterField(
            model_name='imagepost',
            name='time_stamp',
            field=models.DateTimeField(default=datetime.datetime(2016, 10, 15, 22, 5, 54, 787005, tzinfo=utc)),
        ),
    ]
