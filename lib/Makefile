GRMM_JARS = \
  jgrapht-0.6.0.jar \
  mtj-0.9.9.jar

JARS = \
ant.jar \
bsh.jar \
common-logging-1.0.4.jar \
crimson.jar \
jdom-1.0.jar \
junit-3.8.1.jar \
jwnl-1.3.jar \
lucene-1.5-rc1-dev.jar \
openjgraph.jar \
secondstring-serializable.jar \
trove-2.0.2.jar \
xml-apis.jar \
xmlParserAPIs.jar \
$(GRMM_JARS)

# note: jython.jar not included above, because only needed by bin/mallethon

unpack: $(JARS)
	rm -rf $@
	mkdir $@
	for f in $(JARS) ; do \
	  (cd $@ ; jar -xf ../$$f) ; \
	done
	rm -rf $@/META-INF
	rm -rf $@/junit3.7

mallet-deps.jar: unpack
	cd $< ; \
	jar -cf ../$@ *

grmm-unpack: $(GRMM_JARS)
	rm -rf $@
	mkdir $@
	for f in $(GRMM_JARS) ; do \
	  (cd $@ ; jar -xf ../$$f) ; \
	done
	rm -rf $@/META-INF

grmm-deps.jar: grmm-unpack
	cd $<; jar -cf ../$@ *

clean:
	rm -rf unpack
	rm -f mallet-deps.jar
