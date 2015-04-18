GEN=ContourTrackingMsg.java Constants.java

all: contourtracking.jar

contourtracking.jar: ContourTracking.class
	jar cf $@ *.class

ContourTrackingMsg.java: ../ContourTracking.h
	mig -target=null -java-classname=ContourTrackingMsg java ../ContourTracking.h contourtracking -o $@

Constants.java: ../ContourTracking.h
	ncg -target=null -java-classname=Constants java ../ContourTracking.h NREADINGS DEFAULT_INTERVAL DEFAULT_THRESHOLD -o $@

ContourTracking.class: $(wildcard *.java) $(GEN)
	javac *.java

clean:
	rm -f *.class $(GEN)

veryclean: clean
	rm contourtracking.jar
