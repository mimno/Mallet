MALLET_DIR = $(shell pwd)

JAVAC = javac
JAVA_FLAGS = \
-classpath "$(MALLET_DIR)/class:$(MALLET_DIR)/lib/mallet-deps.jar:$(MALLET_DIR)/lib/jdom-1.0.jar:$(MALLET_DIR)/lib/grmm-deps.jar" \
-sourcepath "$(MALLET_DIR)/src" \
-g:lines,vars,source \
-d $(MALLET_DIR)/class \
-J-Xmx200m -source 1.5

JAVADOC = javadoc
JAVADOC_FLAGS = -J-Xmx300m
JAVADOCS=html


MALLET_VERSION=20080618
ifeq ($(BUILDING_GRMM),yes)
  DISTNAME=grmm-$(VERSION)
else
  VERSION=$(MALLET_VERSION)
  DISTNAME=mallet-$(VERSION)
endif


all: class link-resources
	$(JAVAC) $(JAVA_FLAGS) `find src -name '*.java'`

javadoc: html class
	$(JAVADOC) $(JAVADOC_FLAGS) -classpath "$(MALLET_DIR)/class:$(MALLET_DIR)/lib/mallet-deps.jar:$(MALLET_DIR)/lib/grmm-deps.jar" -d $(MALLET_DIR)/html -sourcepath $(MALLET_DIR)/src -source 1.4 -subpackages edu

grmmdoc: html class
	$(JAVADOC) $(JAVADOC_FLAGS) -classpath "$(MALLET_DIR)/class:$(MALLET_DIR)/lib/mallet-deps.jar" -d $(MALLET_DIR)/html -sourcepath $(MALLET_DIR)/src -source 1.4 -subpackages edu.umass.cs.mallet.users.casutton.graphical

copy-resources: class
	cd src ; gtar --exclude CVS -cf - `find . -type d -name resources` | (cd ../class ; gtar -xf -)

# Soft link the resources directories in mallet/src into mallet/class
link-resources: class
	cd src ; for d in `find . -type d -name resources` ; do \
	  echo $$d ; \
	  mkdir -p `dirname ../class/$$d` ; \
	  rm -f ../class/$$d ; \
          (cd ../class ; ln -s `echo $$d | sed 's,/[^/]*,/\.\.,g'`/src/$$d $$d ) ; \
	done

jar:	class
	jar -cvf lib/mallet.jar -C class cc/mallet

srcjar:	class
	jar -cvf lib/mallet.jar src README.html Makefile LICENSE HACKING -C class edu

class:
	mkdir -p class

html:
	mkdir -p html

clean:
	rm -rf class/* lib/unpack

echo-classpath:
	export CLASSPATH=$(MALLET_DIR)/class


# removed javadoc
.distfiles: FORCE jar 
	rm -f $@
	echo .emacs.mallet >> $@
	echo HACKING >> $@
	echo LICENSE >> $@
	echo Makefile >> $@
	echo OTHER-SIMILAR-SOFTWARE.html >> $@
	echo README.html >> $@
	echo TODO >> $@
	echo README.ant >> $@
	echo build.xml >> $@
	find src -name '*.java' -not -path 'src/com/*' >> $@
	find src -path '*/resources/*' -type f  -not -path '*/CVS/*' >> $@   # include resource dirs -cas
	echo lib/*.jar lib/Makefile >> $@
	#find lib/jython -type f -not -path '*/CVS/*' >> $@
	#find scripts -type f -not -path '*/CVS/*' >> $@
	#echo doc/*.html >> $@
	# Include built jars.  Wildcards cannot be used below, for these files don't exist yet. -cas
	echo dist/mallet.jar dist/mallet-deps.jar >> $@
	if [ ! -z "$$BUILDING_GRMM"]; then echo dist/grmm-deps.jar >> $@; fi
	# include the javadocs
	#find $(JAVADOCS) -type f >> $@
 	# find the executables in bin/ directory to be included
	find bin -type f -maxdepth 1 -perm -a+x -not \( -path '*/CVS/*' -or -name 'prepend-license.sh' \) >> $@
	if [ -z "$$BUILDING_GRMM" ]; then \
	  grep -v mallet/grmm $@ > /tmp/$@ ; rm $@ ; mv /tmp/$@ $@ ; \
	fi

dist/$(DISTNAME).tar.gz: .distfiles
	-mkdir dist
	# remove extant build directory
	rm -rf $(DISTNAME)
	# create temp build directory
	mkdir $(DISTNAME)
	# add other important files to dist dir for convenience
	cp lib/mallet-deps.jar lib/mallet.jar dist
	# copying files to build directory
	#cat .distfiles | xargs -n256 cp --preserve --link --parents --target-directory $(DISTNAME)
	tar --files-from .distfiles -cf - | (cd $(DISTNAME) ; tar -xpvf -)
	# tar build directory
	tar -chvf dist/$(DISTNAME).tar $(DISTNAME)
	# remove extant *.tar.gz file
	rm -f $(TARBALL)
	# gzip tar file
	gzip -9 dist/$(DISTNAME).tar
	# remove temp build directory
	rm -rf $(DISTNAME)

FORCE:
