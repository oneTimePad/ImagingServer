from django import forms

class AttributeForm(forms.Form):

	color = forms.CharField(label="Target Color",max_length=10)
	lettercol = forms.CharField(label="Alphanumeric Color",max_length=10)
	orientation = forms.CharField(max_length=2,widget=forms.Select(choices=ORIENTATION_CHOICES))
	shape = forms.CharField(max_length=3,widget=forms.Select(choices=SHAPE_CHOICES))
	letter = forms.CharField(label="Alphanumeric",max_length=1)
