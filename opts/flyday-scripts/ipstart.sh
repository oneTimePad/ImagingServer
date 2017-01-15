#!/bin/bash

sudo ip link set down eth0
sudo ip addr add 192.168.123.200/24 dev eth0
sudo ip route add 192.168.123.0/24 dev eth0
sudo ip link set up eth0
sudo dhcpd -d eth0
