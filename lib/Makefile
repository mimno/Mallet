JARS = \
bsh.jar \
hppc-0.7.1.jar \
junit-4.12.jar \
trove-2.0.2.jar

# note: jython.jar not included above, because only needed by bin/mallethon

unpack: $(JARS)
	rm -rf $@
	mkdir $@
	for f in $(JARS) ; do \
	  (cd $@ ; jar -xf ../$$f) ; \
	done
	rm -rf $@/META-INF

mallet-deps.jar: unpack
	cd $< ; \
	jar -cf ../$@ *

clean:
	rm -rf unpack
	rm -f mallet-deps.jar
