# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models
import django.core.files.storage


class Migration(migrations.Migration):

    dependencies = [
        ('uploader', '0001_initial'),
    ]

    operations = [
        migrations.AddField(
            model_name='picture',
            name='photo',
            field=models.ImageField(default=0, storage=django.core.files.storage.FileSystemStorage(location=b'/media/photos'), upload_to=b''),
        ),
    ]
