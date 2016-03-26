import pika
from threading import Thread


def pikaL():
	print("Here")
	connection = pika.BlockingConnection(pika.ConnectionParameters(host = 'localhost'))
	channel = connection.channel()
	channel.queue_declare(queue="lol")
	print("There")
	while True:
		pass


t1 = Thread(target=pikaL)
t2 = Thread(target=pikaL)



t1.start()
t2.start()
while True:
	pass
