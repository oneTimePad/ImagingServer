from django import forms


class AttributeForm(forms.Form):

	color = forms.CharField(label="Target Color")