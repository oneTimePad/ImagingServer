# -*- coding: utf-8 -*-
# Generated by Django 1.9 on 2017-01-08 19:29
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('server', '0003_auto_20170106_2051'),
    ]

    operations = [
        migrations.AlterField(
            model_name='picture',
            name='alt',
            field=models.DecimalField(decimal_places=5, default=0, max_digits=9),
        ),
        migrations.AlterField(
            model_name='picture',
            name='lat',
            field=models.DecimalField(decimal_places=5, default=0, max_digits=9),
        ),
        migrations.AlterField(
            model_name='picture',
            name='lon',
            field=models.DecimalField(decimal_places=5, default=0, max_digits=9),
        ),
        migrations.AlterField(
            model_name='picture',
            name='pitch',
            field=models.DecimalField(decimal_places=5, default=0, max_digits=9),
        ),
        migrations.AlterField(
            model_name='picture',
            name='roll',
            field=models.DecimalField(decimal_places=5, default=0, max_digits=9),
        ),
        migrations.AlterField(
            model_name='picture',
            name='yaw',
            field=models.DecimalField(decimal_places=5, default=0, max_digits=9),
        ),
    ]
