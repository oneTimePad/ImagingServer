# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models
import django.core.files.storage


class Migration(migrations.Migration):

    dependencies = [
    ]

    operations = [
        migrations.CreateModel(
            name='Picture',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('fileName', models.CharField(max_length=100)),
                ('photo', models.ImageField(default=0, storage=django.core.files.storage.FileSystemStorage(location=b'/var/www/html/PHOTOS'), upload_to=b'')),
                ('azimuth', models.DecimalField(max_digits=9, decimal_places=6)),
                ('pitch', models.DecimalField(max_digits=9, decimal_places=6)),
                ('roll', models.DecimalField(max_digits=9, decimal_places=6)),
                ('lat', models.DecimalField(max_digits=9, decimal_places=6)),
                ('lon', models.DecimalField(max_digits=9, decimal_places=6)),
                ('latLon', models.DecimalField(max_digits=9, decimal_places=6)),
                ('alt', models.DecimalField(max_digits=9, decimal_places=6)),
                ('ppm', models.DecimalField(max_digits=9, decimal_places=6)),
            ],
        ),
        migrations.CreateModel(
            name='Target',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('target_pic', models.ImageField(storage=django.core.files.storage.FileSystemStorage(location=b'/var/www/html/TARGETS'), upload_to=b'')),
                ('color', models.CharField(max_length=10)),
                ('lcolor', models.CharField(max_length=10)),
                ('orientation', models.CharField(max_length=2, choices=[(b'N', b'N'), (b'NE', b'NE'), (b'E', b'E'), (b'SE', b'SE'), (b'S', b'S'), (b'SW', b'SW'), (b'W', b'W'), (b'NW', b'NW')])),
                ('shape', models.CharField(max_length=3, choices=[(b'CIR', b'Circle'), (b'SCI', b'Semicircle'), (b'QCI', b'Quarter Circle'), (b'TRI', b'Triangle'), (b'SQU', b'Square'), (b'REC', b'Rectangle'), (b'TRA', b'Trapezoid'), (b'PEN', b'Pentagon'), (b'HEX', b'Hexagon'), (b'HEP', b'Heptagon'), (b'OCT', b'Octagon'), (b'STA', b'Star'), (b'CRO', b'Cross')])),
                ('letter', models.CharField(max_length=1)),
                ('lat', models.DecimalField(max_digits=9, decimal_places=6)),
                ('lon', models.DecimalField(max_digits=9, decimal_places=6)),
                ('picture', models.ForeignKey(to='uploader.Picture')),
            ],
        ),
    ]
