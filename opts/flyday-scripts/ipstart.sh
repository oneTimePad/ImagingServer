#!/bin/bash

sudo ip link set down eth0
echo "1";
sudo ip link set up eth0
sudo ip addr add 192.168.123.200/24 dev eth0
echo "2";
sudo ip route add 192.168.123.0/24 dev eth0
echo "3";
sudo ip link set up eth0
echo "4";
sleep 5;
sudo dhcpd -d eth0
