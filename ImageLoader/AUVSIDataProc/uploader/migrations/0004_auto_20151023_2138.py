# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models
import django.core.files.storage


class Migration(migrations.Migration):

    dependencies = [
        ('uploader', '0003_auto_20151020_1843'),
    ]

    operations = [
        migrations.AlterField(
            model_name='picture',
            name='photo',
            field=models.ImageField(default=0, storage=django.core.files.storage.FileSystemStorage(location=b'/var/www/html/PHOTOS'), upload_to=b''),
        ),
    ]
