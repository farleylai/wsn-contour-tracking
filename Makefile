bs:
	make -C BaseStation iris install,0 mib520,/dev/ttyUSB0

mote:
	SENSORBOARD=mda100 make -C ContourTracking iris install,1 mib520,/dev/ttyUSB0

gui:
	make -C ContourTracking/gui

clean:
	make -C BaseStation clean
	make -C ContourTracking clean
	make -C ContourTracking/gui clean
