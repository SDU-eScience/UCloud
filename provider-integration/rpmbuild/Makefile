TARGETS := $(subst /,,$(wildcard */))

.PHONY: $(TARGETS)

$(TARGETS):
	cd $@; \
	mkdir -p SOURCES; \
	spectool -g SPECS/$@.spec -C SOURCES; \
	rpmbuild --define "_topdir $(shell pwd)/$@" -ba SPECS/$@.spec
