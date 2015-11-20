from django import forms

ORIENTATION_CHOICES = (
	('N','N')
	('NE','NE')
	('E','E')
	('SE','SE')
	('S','S')
	('SW','SW')
	('W','W')
	('NW','NW')
)

TARGET_SHAPE_CHOICES = (
	('CIRC','Circle')
	('SCIRC','Semicircle')
	('QCIRC','Quarter Circle')
	('TRI','Triangle')
	('Square')
	('Rectangle')
	('Trapezoid')
	('Pentagon')
	('Hexagon')
	('Heptagon')
	('Octagon')
	('Star')
	('Cross')
)

class AttributeForm(forms.Form):

	color = forms.CharField(label="Target Color")
	orientation = forms.ChoiceField(choices=ORIENTATION_CHOICES)
	shape = forms.CharField(label="Target Shape")
	letter = forms.CharField(label="Alphanumeric")
	lettercol = forms.CharField(label="Alphanumeric Color")
	backcol = forms.CharField(label="Target Color")
