# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models
import django.core.files.storage


class Migration(migrations.Migration):

    dependencies = [
        ('uploader', '0002_picture_photo'),
    ]

    operations = [
        migrations.RenameField(
            model_name='picture',
            old_name='picture',
            new_name='text',
        ),
        migrations.AlterField(
            model_name='picture',
            name='photo',
            field=models.ImageField(default=0, storage=django.core.files.storage.FileSystemStorage(location=b'/home/lie/Desktop/PHOTOS'), upload_to=b''),
        ),
    ]
