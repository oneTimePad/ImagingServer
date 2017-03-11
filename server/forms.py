from django import forms

from .models import Target
class AttributeForm(forms.Form):

	"""
		renders the form for the GCS viewer to be able to submit a target
	"""
	ptype = forms.CharField(label="Target Type",max_length=3,widget=forms.Select(choices=Target.TARGET_TYPES))
	orientation = forms.CharField(max_length=2,widget=forms.Select(choices=Target.ORIENTATION_CHOICES))
	shape = forms.CharField(max_length=3,widget=forms.Select(choices=Target.SHAPE_CHOICES))
	alphanumeric = forms.CharField(label="Alphanumeric",max_length=1)
	description = forms.CharField(label="Description",max_length=200)
